@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.domain.model.FireState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val TEST_TAG_FIRE_OVERLAY: String = "fire_overlay"

private const val GLYPH_START_SCALE = 0.6f
private const val GLYPH_END_SCALE = 1.6f
private const val FADE_FRACTION = 3

/**
 * Spec 024 FR-006 — covers the screen while a Fire run is in progress, in a window above everything
 * (bars, sheets, dialogs), and consumes every touch. The fire glyph grows while the surface fades
 * in; once the run has finished and at least [BrowserLimits.FIRE_OVERLAY_MIN_MS] has passed, the
 * overlay fades out and calls [onFinished].
 *
 * Every animation is a Compose animation, which follows the system animator scale: with
 * animations off (reduced motion) the overlay simply appears and disappears. Animated values are
 * read only inside `graphicsLayer` (Constitution §V).
 */
@Composable
internal fun FireOverlay(state: FireState, onFinished: () -> Unit) {
    if (state == FireState.Idle) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val shownAt = remember { SystemClock.uptimeMillis() }
        val surface = remember { Animatable(0f) }
        val glyph = remember { Animatable(GLYPH_START_SCALE) }
        LaunchedEffect(Unit) {
            launch { surface.animateTo(1f, tween(BrowserLimits.FIRE_OVERLAY_MS / FADE_FRACTION)) }
            glyph.animateTo(GLYPH_END_SCALE, tween(BrowserLimits.FIRE_OVERLAY_MS))
        }
        val finished = state is FireState.Finished
        LaunchedEffect(finished) {
            if (!finished) return@LaunchedEffect
            val remaining = BrowserLimits.FIRE_OVERLAY_MIN_MS - (SystemClock.uptimeMillis() - shownAt)
            if (remaining > 0) delay(remaining)
            surface.animateTo(0f, tween(BrowserLimits.FIRE_OVERLAY_MS / FADE_FRACTION))
            onFinished()
        }
        val done = stringResource(R.string.fire_done_announcement)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = surface.value }
                .background(MaterialTheme.colorScheme.surface)
                .testTag(TEST_TAG_FIRE_OVERLAY)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    if (finished) contentDescription = done
                },
        ) {
            Icon(
                painter = painterResource(AppIcons.Fire),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(Dimens.dialogGlyph)
                    .graphicsLayer {
                        scaleX = glyph.value
                        scaleY = glyph.value
                    },
            )
        }
    }
}
