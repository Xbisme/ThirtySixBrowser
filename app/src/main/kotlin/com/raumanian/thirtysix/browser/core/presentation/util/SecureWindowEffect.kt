@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

// FLAG_SECURE blanks the recents thumbnail and blocks screenshots while [secure], as Chrome's
// incognito does. Disposal clears it so a recreated window never inherits a stuck flag.
@Composable
fun SecureWindowEffect(secure: Boolean) {
    val view = LocalView.current
    DisposableEffect(secure) {
        val window = (view.context as? Activity)?.window
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
