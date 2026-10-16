package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The swipe in progress. [progress] changes every frame: read it only in draw or layer blocks. */
@Stable
internal class SwipeNavigationState {
    var direction by mutableStateOf<SwipeDirection?>(null)
        private set
    var available by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    fun show(direction: SwipeDirection, progress: Float, available: Boolean) {
        this.direction = direction
        this.available = available
        this.progress = progress
    }

    fun hide() {
        direction = null
        progress = 0f
    }
}
