package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlinx.coroutines.CoroutineScope

/** What the swipe reads from the screen, fresh at every touch. */
internal class TabSwipeEnvironment(
    val swipe: () -> TabSwipeUiState,
    val canStart: () -> Boolean,
    val isRtl: () -> Boolean,
    val animationsEnabled: () -> Boolean,
    /** The window x range outside the system Back gesture's edge areas. */
    val gestureFreeRange: () -> ClosedFloatingPointRange<Float>,
)

internal class TabSwipeInputs(
    val state: TabSwipeState,
    val scope: CoroutineScope,
    val environment: TabSwipeEnvironment,
    val onCommit: (tabId: Long) -> Unit,
) {
    var originXInWindow: Float = 0f

    /** When the last claimed swipe lifted, in pointer uptime. */
    var lastSwipeEndMillis: Long = Long.MIN_VALUE / 2
}
