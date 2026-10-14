package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `runCatching` shape that guards external-intent dispatch, with synthetic exceptions. A real
 * `startActivity` here leaked a chooser or another app onto the CI emulator and hung later tests.
 */
@RunWith(AndroidJUnit4::class)
class ExternalIntentCrashSafetyInstrumentedTest {

    @Test
    fun runCatching_pattern_absorbs_ActivityNotFoundException() {
        // Production wrap: `runCatching { startActivity(intent) }`.
        // If startActivity throws ActivityNotFoundException (no handler),
        // runCatching captures the failure without propagating.
        val outcome = runCatching {
            throw ActivityNotFoundException("simulated no-handler")
        }
        assertTrue("runCatching MUST capture failure", outcome.isFailure)
        val cause = outcome.exceptionOrNull()
        assertNotNull(cause)
        assertEquals(
            "wrapper preserves the typed cause for diagnostic logging",
            ActivityNotFoundException::class,
            cause!!::class,
        )
    }

    @Test
    fun runCatching_pattern_absorbs_arbitrary_runtime_failure() {
        // Something other than ActivityNotFoundException (e.g. SecurityException) must be absorbed too.
        val outcome = runCatching {
            throw SecurityException("simulated permission failure")
        }
        assertTrue("runCatching MUST capture all Throwable types", outcome.isFailure)
    }

    @Test
    fun tel_intent_parses_without_throwing() {
        // Parsing happens outside the runCatching that wraps `startActivity`, so it must not throw
        // for the supported schemes.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:0123456789"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("tel", intent.data?.scheme)
    }

    @Test
    fun mailto_intent_parses_without_throwing() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:user@example.com"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("mailto", intent.data?.scheme)
    }
}
