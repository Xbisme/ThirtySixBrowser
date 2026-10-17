package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.rememberAnimationsEnabled

/** The swipe inputs for one field: the address pill, or the Home search field standing in for it. */
@Composable
internal fun rememberTabSwipeInputs(
    state: TabSwipeState,
    swipe: TabSwipeUiState,
    canStart: Boolean,
    onAction: (TabSwipeAction) -> Unit,
): TabSwipeInputs {
    val currentSwipe by rememberUpdatedState(swipe)
    val currentCanStart by rememberUpdatedState(canStart)
    val currentOnAction by rememberUpdatedState(onAction)
    val rtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val animations by rememberUpdatedState(rememberAnimationsEnabled())
    val gestureFree by rememberUpdatedState(rememberGestureFreeRange())
    val scope = rememberCoroutineScope()
    return remember(state, scope) {
        TabSwipeInputs(
            state = state,
            scope = scope,
            environment = TabSwipeEnvironment(
                swipe = { currentSwipe },
                canStart = { currentCanStart },
                isRtl = { rtl },
                animationsEnabled = { animations },
                gestureFreeRange = { gestureFree },
            ),
            onCommit = { tabId -> currentOnAction(TabSwipeAction.SwitchTo(tabId, announce = false)) },
        )
    }
}

/** The modifiers that give a search field the tab swipe: one for the moving lane, one for the field. */
internal class FieldSwipe(val lane: Modifier, val field: Modifier)

@Composable
internal fun rememberFieldSwipe(
    state: TabSwipeState,
    swipe: TabSwipeUiState,
    canStart: Boolean,
    onAction: (TabSwipeAction) -> Unit,
): FieldSwipe {
    val inputs = rememberTabSwipeInputs(state, swipe, canStart, onAction)
    val actions = rememberTabSwipeAccessibilityActions(swipe, onAction)
    return remember(inputs, actions) {
        FieldSwipe(lane = Modifier.tabSwipe(inputs), field = Modifier.withAccessibilityActions(actions))
    }
}

@Composable
private fun rememberGestureFreeRange(): ClosedFloatingPointRange<Float> {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val width = LocalWindowInfo.current.containerSize.width.toFloat()
    val start = insets.getLeft(density, direction).toFloat()
    val end = width - insets.getRight(density, direction)
    return if (end > start) start..end else 0f..width
}

/** "Next tab" and "Previous tab" for TalkBack and Switch Access; present whatever the swipe setting. */
@Composable
internal fun rememberTabSwipeAccessibilityActions(
    swipe: TabSwipeUiState,
    onAction: (TabSwipeAction) -> Unit,
): List<CustomAccessibilityAction> {
    val nextLabel = stringResource(R.string.browser_a11y_next_tab)
    val previousLabel = stringResource(R.string.browser_a11y_previous_tab)
    val next = swipe.next
    val previous = swipe.previous
    return remember(next?.tabId, previous?.tabId, nextLabel, previousLabel, onAction) {
        listOfNotNull(
            next?.let { tab -> switchAction(nextLabel, tab, onAction) },
            previous?.let { tab -> switchAction(previousLabel, tab, onAction) },
        )
    }
}

/** Puts [actions] on the field itself, where TalkBack's focus lands. */
internal fun Modifier.withAccessibilityActions(actions: List<CustomAccessibilityAction>): Modifier =
    if (actions.isEmpty()) this else semantics { customActions = actions }

private fun switchAction(label: String, tab: TabPreview, onAction: (TabSwipeAction) -> Unit) =
    CustomAccessibilityAction(label) {
        onAction(TabSwipeAction.SwitchTo(tab.tabId, announce = true))
        true
    }
