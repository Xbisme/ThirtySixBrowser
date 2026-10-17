package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The address-bar swipe in progress. [offsetPx] changes every frame: read it only in layer blocks.
 * [incoming] and [side] change once per drag, and [holding] keeps the preview over the page until
 * the new tab's page is on screen.
 */
@Stable
internal class TabSwipeState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var laneWidthPx by mutableFloatStateOf(0f)
    var incoming by mutableStateOf<TabPreview?>(null)
        private set
    var side by mutableStateOf<TabSide?>(null)
        private set
    var holding by mutableStateOf(false)
        private set

    private val pageVisible = Channel<Unit>(Channel.CONFLATED)

    /** −1…1: the page's travel as a share of its width. */
    val fraction: Float
        get() = if (laneWidthPx > 0f) (offsetPx / laneWidthPx).coerceIn(-1f, 1f) else 0f

    fun drag(offsetPx: Float, side: TabSide, preview: TabPreview?) {
        this.offsetPx = offsetPx
        if (this.side != side) this.side = side
        if (incoming != preview) incoming = preview
    }

    suspend fun settleTo(targetPx: Float, animate: Boolean) {
        if (animate) {
            animate(initialValue = offsetPx, targetValue = targetPx) { value, _ -> offsetPx = value }
        } else {
            offsetPx = targetPx
        }
    }

    suspend fun returnToRest(animate: Boolean) {
        settleTo(0f, animate)
        clear()
    }

    /** After a commit: the page underneath is the new tab's, at rest, behind the preview. */
    fun beginHold() {
        pageVisible.tryReceive()
        holding = true
    }

    fun onPageVisible() {
        pageVisible.trySend(Unit)
    }

    suspend fun awaitPageThenRelease(waitForPage: Boolean) {
        offsetPx = 0f
        if (waitForPage) withTimeoutOrNull(TabSwipeLimits.PREVIEW_HOLD_MAX_MS) { pageVisible.receive() }
        clear()
    }

    private fun clear() {
        offsetPx = 0f
        incoming = null
        side = null
        holding = false
    }
}
