package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.ExternalAppLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Malformed and unhandled addresses through the real launcher. Only schemes no app on the device
 * handles are used: a real hand-off here leaked a chooser or another app onto the CI emulator.
 */
@RunWith(AndroidJUnit4::class)
class ExternalIntentCrashSafetyInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aMalformedIntentLink_opensNothing_andDoesNotThrow() {
        assertNull(ExternalAppLauncher.launch(context, "intent:#Intent;S.x=%zz;end"))
        assertNull(ExternalAppLauncher.launch(context, "intent://x#Intent;component=;end"))
    }

    @Test
    fun aSchemeNoAppHandles_opensNothing_andDoesNotThrow() {
        assertNull(ExternalAppLauncher.launch(context, "$NO_HANDLER:x"))
        assertNull(ExternalAppLauncher.launch(context, "intent://x#Intent;scheme=$NO_HANDLER;end"))
    }

    @Test
    fun anUnhandledIntentLink_returnsOnlyAWebFallback() {
        val web = "intent://x#Intent;scheme=$NO_HANDLER;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end"
        val script = "intent://x#Intent;scheme=$NO_HANDLER;S.browser_fallback_url=javascript%3Aalert(1);end"

        assertEquals("https://example.com/", ExternalAppLauncher.launch(context, web))
        assertNull(ExternalAppLauncher.launch(context, script))
    }

    private companion object {
        const val NO_HANDLER = "thirtysix-no-handler"
    }
}
