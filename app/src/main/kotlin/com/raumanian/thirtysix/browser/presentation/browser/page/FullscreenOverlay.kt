@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
/**
 * Spec 021 US3 / research R4 — the page's fullscreen view over the whole browser screen: system
 * bars hidden (a swipe shows them briefly, FR-014), orientation following the phone (FR-015),
 * Back leaving fullscreen first (FR-016). The WebView underneath is untouched, so leaving
 * restores the same page, scroll position and playback. An incognito window keeps
 * `FLAG_SECURE`, which covers this overlay too (FR-017).
 */
@Composable
fun FullscreenOverlay(controller: FullscreenController) {
    val view by controller.view.collectAsStateWithLifecycle()
    val fullscreenView = view ?: return
    val activity = LocalContext.current.findActivity()

    BackHandler(onBack = controller::exit)
    DisposableEffect(activity, fullscreenView) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousOrientation = activity?.requestedOrientation
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
                addView(
                    fullscreenView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        onRelease = { it.removeAllViews() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    )
}
