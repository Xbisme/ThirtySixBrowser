package com.raumanian.thirtysix.browser.core.presentation.util

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
 * [SecureWindowEffect] sets and clears `FLAG_SECURE`, driven by a [mutableStateOf] so both
 * transitions happen in one Compose tree without recreating the activity.
 */
@RunWith(AndroidJUnit4::class)
class FlagSecureLifecycleInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test(timeout = TEST_TIMEOUT_MS)
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

    private companion object {
        const val TEST_TIMEOUT_MS: Long = 30_000L
    }
}
