@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Spec 012 (FR-016 / FR-016a) — toggles the platform "secure window" flag
 * (`WindowManager.LayoutParams.FLAG_SECURE`) on the host Activity in response
 * to the [secure] boolean.
 *
 * When `secure == true`:
 *  - The system "recents" overview thumbnail is BLANKED (no page content
 *    leaks to anyone who later peeks at recents).
 *  - User-initiated screenshots and screen recording are BLOCKED while the
 *    flag is set (Chrome's incognito posture; matches user expectation).
 *
 * Idempotency (FR-016a): both `addFlags` and `clearFlags` are platform
 * no-ops if the flag is already in the requested state. The keyed
 * [DisposableEffect] re-runs only when [secure] flips, and `onDispose`
 * defensively clears the flag so a teardown mid-incognito (process death
 * precursor) does not leave a recreated window with a stuck FLAG_SECURE.
 *
 * Caller (typical): `MainActivity` collects
 * `ObserveActiveTabIsIncognitoUseCase().collectAsStateWithLifecycle(false)`
 * and feeds the boolean here inside `ThirtySixTheme { … }`.
 */
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
