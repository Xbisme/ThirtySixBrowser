package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlin.math.sign
import kotlinx.coroutines.launch

/**
 * Watches the Initial pass so the decision comes before the text field's own tap and selection
 * handling: nothing is consumed until the drag is clearly sideways, so taps still focus the field.
 */
internal fun Modifier.tabSwipe(inputs: TabSwipeInputs): Modifier = onGloballyPositioned {
    inputs.originXInWindow = it.positionInWindow().x
}.pointerInput(inputs) {
    val env = inputs.environment
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // A touch in the system Back area belongs to the system, so no exclusion is set here.
        if (inputs.originXInWindow + down.position.x !in env.gestureFreeRange()) return@awaitEachGesture
        // The text field reads a press soon after a swipe as a double tap and selects text, taking focus.
        if (down.uptimeMillis - inputs.lastSwipeEndMillis < viewConfiguration.doubleTapTimeoutMillis) down.consume()
        val swipe = env.swipe()
        val width = size.width.toFloat()
        inputs.state.laneWidthPx = width
        val tracker = TabSwipeTracker(
            touchSlop = viewConfiguration.touchSlop,
            laneWidthPx = width,
            flickVelocityPxPerS = TabSwipeLimits.FLICK_VELOCITY_DP_PER_S.dp.toPx(),
            isRtl = env.isRtl(),
        )
        tracker.down(
            x = down.position.x,
            y = down.position.y,
            canStart = env.canStart() && swipe.enabled && !inputs.state.holding,
            hasPrevious = swipe.previous != null,
            hasNext = swipe.next != null,
        )
        followDrag(down, tracker, swipe, inputs)?.let { result ->
            inputs.lastSwipeEndMillis = currentEvent.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
            settle(result, swipe, inputs)
        }
    }
}

private suspend fun AwaitPointerEventScope.followDrag(
    down: PointerInputChange,
    tracker: TabSwipeTracker,
    swipe: TabSwipeUiState,
    inputs: TabSwipeInputs,
): TabSwipeOutput? {
    val velocity = VelocityTracker().apply { addPointerInputChange(down) }
    var step: Step = Step.Continue
    while (step == Step.Continue) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id }
        val interrupted = event.changes.count { it.pressed } > 1 ||
            incomingChanged(swipe, inputs.environment.swipe(), inputs.state.side)
        step = when {
            change == null || interrupted -> Step.Done(tracker.cancel())
            change.changedToUpIgnoreConsumed() -> release(tracker, change, velocity)
            else -> {
                velocity.addPointerInputChange(change)
                onMove(tracker, change, swipe, inputs.state)
                Step.Continue
            }
        }
    }
    return (step as Step.Done).result
}

private sealed interface Step {
    data object Continue : Step

    class Done(val result: TabSwipeOutput?) : Step
}

private fun release(tracker: TabSwipeTracker, change: PointerInputChange, velocity: VelocityTracker): Step {
    velocity.addPointerInputChange(change)
    if (tracker.claimed) change.consume()
    return Step.Done(tracker.up(velocity.calculateVelocity().x))
}

private fun onMove(tracker: TabSwipeTracker, change: PointerInputChange, swipe: TabSwipeUiState, state: TabSwipeState) {
    val drag = tracker.move(change.position.x, change.position.y) as? TabSwipeOutput.Drag ?: return
    change.consume()
    state.drag(drag.offsetPx, drag.side, swipe.previewOn(drag.side))
}

private fun incomingChanged(atStart: TabSwipeUiState, now: TabSwipeUiState, side: TabSide?): Boolean =
    side != null && atStart.previewOn(side)?.tabId != now.previewOn(side)?.tabId

private fun TabSwipeUiState.previewOn(side: TabSide): TabPreview? = if (side == TabSide.Next) next else previous

private fun settle(result: TabSwipeOutput, swipe: TabSwipeUiState, inputs: TabSwipeInputs) {
    val state = inputs.state
    val animate = inputs.environment.animationsEnabled()
    val commit = result as? TabSwipeOutput.Commit
    val target = commit?.let { swipe.previewOn(it.side) }
    inputs.scope.launch {
        if (commit == null || target == null) {
            state.returnToRest(animate)
        } else {
            val rtl = inputs.environment.isRtl()
            val toward = if (state.offsetPx != 0f) state.offsetPx.sign else -commit.side.entrySign(rtl)
            state.drag(state.offsetPx, commit.side, target)
            state.settleTo(toward * state.laneWidthPx, animate)
            state.beginHold()
            inputs.onCommit(target.tabId)
        }
    }
}
