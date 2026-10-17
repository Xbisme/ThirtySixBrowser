package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView

/** Feeds a page's touches and overscroll to a [SwipeGestureTracker], then goes back or forward. */
internal class SwipeNavigationHooks(
    private val state: SwipeNavigationState,
    private val isEnabled: () -> Boolean,
    private val isRtl: () -> Boolean,
    private val thresholdPx: Float,
) {
    private var tracker: SwipeGestureTracker? = null

    fun onTouch(view: WebView, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker = if (isEnabled()) newTracker(view, event) else null
            MotionEvent.ACTION_MOVE -> tracker?.move(
                x = event.x,
                y = event.y,
                canScrollLeft = view.canScrollHorizontally(-1),
                canScrollRight = view.canScrollHorizontally(1),
            )?.let { apply(view, it) }
            MotionEvent.ACTION_UP -> finish(view) { it.up() }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finish(view) { it.cancel() }
        }
    }

    fun onOverscrolledX() {
        tracker?.overscrolled()
    }

    private fun newTracker(view: WebView, event: MotionEvent) = SwipeGestureTracker(
        touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat(),
        thresholdPx = thresholdPx,
        isRtl = isRtl(),
    ).also { it.down(event.x, event.y) }

    private fun finish(view: WebView, end: (SwipeGestureTracker) -> SwipeOutput?) {
        tracker?.let(end)?.let { apply(view, it) }
        if (tracker != null) state.hide()
        tracker = null
    }

    private fun apply(view: WebView, output: SwipeOutput) {
        when (output) {
            is SwipeOutput.Progress -> state.show(output.direction, output.progress, view.canGo(output.direction))
            is SwipeOutput.Commit -> {
                if (view.canGo(output.direction)) {
                    if (output.direction == SwipeDirection.Back) view.goBack() else view.goForward()
                }
                state.hide()
            }
            SwipeOutput.Cancel -> state.hide()
        }
    }

    private fun WebView.canGo(direction: SwipeDirection): Boolean =
        if (direction == SwipeDirection.Back) canGoBack() else canGoForward()
}
