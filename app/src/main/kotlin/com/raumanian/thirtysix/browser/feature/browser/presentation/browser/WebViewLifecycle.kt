package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Pauses the shown WebView while the screen is stopped and resumes it when started. */
@Composable
internal fun PauseWithLifecycle(shownWebView: () -> WebView?, onStop: (WebView) -> Unit = {}) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentWebView by rememberUpdatedState(shownWebView)
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> currentWebView()?.onPause()
                Lifecycle.Event.ON_RESUME -> currentWebView()?.onResume()
                Lifecycle.Event.ON_STOP -> currentWebView()?.let(currentOnStop)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
