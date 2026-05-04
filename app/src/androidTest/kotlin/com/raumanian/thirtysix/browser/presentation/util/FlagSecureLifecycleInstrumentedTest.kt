package com.raumanian.thirtysix.browser.presentation.util

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T048 (US4 / FR-016, FR-016a).
 *
 * Verifies that [SecureWindowEffect]:
 *  - Sets `FLAG_SECURE` on the host Activity window when `secure = true`.
 *  - Clears `FLAG_SECURE` when the boolean flips back to `false`.
 *
 * Drives the effect with a [mutableStateOf] handle so the test can observe
 * both transitions inside a single Compose tree without recreating the
 * activity.
 */
@RunWith(AndroidJUnit4::class)
class FlagSecureLifecycleInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flagSecure_set_andCleared_onIncognitoToggle() {
        var secure by mutableStateOf(false)
        var capturedView: View? = null
        composeRule.setContent {
            capturedView = LocalView.current
            SecureWindowEffect(secure = secure)
        }

        composeRule.runOnIdle {
            val window = (capturedView?.context as? Activity)?.window
            assertEquals(
                "FLAG_SECURE off when secure=false",
                0,
                (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        secure = true

        composeRule.runOnIdle {
            val window = (capturedView?.context as? Activity)?.window
            val flagsAfterOn = window?.attributes?.flags ?: 0
            // Non-zero AND (flags & FLAG_SECURE) != 0 → flag is set.
            assertEquals(
                "FLAG_SECURE set when secure=true",
                WindowManager.LayoutParams.FLAG_SECURE,
                flagsAfterOn and WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        secure = false

        composeRule.runOnIdle {
            val window = (capturedView?.context as? Activity)?.window
            assertEquals(
                "FLAG_SECURE cleared when secure flips back to false",
                0,
                (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }
}
