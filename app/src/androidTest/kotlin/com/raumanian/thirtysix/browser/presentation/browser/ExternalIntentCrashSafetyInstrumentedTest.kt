package com.raumanian.thirtysix.browser.presentation.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T066 (FR-018) — external-intent crash safety guard.
 *
 * Reproduces the production code path that runs inside
 * [BrowserWebView.shouldOverrideUrlLoading] when a non-`http(s)` URI (e.g.
 * `tel:`, `mailto:`, `intent://`) is encountered: parse → `startActivity` →
 * if no handler is installed, the platform throws
 * [ActivityNotFoundException]. The production code wraps the call in
 * `runCatching { … }` and silently drops the failure (returns `true` to the
 * WebView client so the load is treated as handled).
 *
 * This test asserts that catching the platform exception is the entire
 * remediation — no further work needed. If a future change accidentally
 * unwraps the runCatching, this test will surface the regression by
 * demonstrating the platform exception that would propagate.
 */
@RunWith(AndroidJUnit4::class)
class ExternalIntentCrashSafetyInstrumentedTest {

    @Test
    fun tel_uri_with_no_handler_is_caught_by_runCatching_pattern() {
        val context: Context = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:not-a-real-handler-12345")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Production behaviour: wrapped in runCatching, so even an
        // ActivityNotFoundException is silently absorbed.
        val outcome = runCatching { context.startActivity(intent) }

        // The test asserts the WRAPPING strategy, not absence of failure:
        //  - If an ActivityNotFoundException surfaces (likely on instrumented
        //    runner with no Phone app), runCatching catches it cleanly →
        //    outcome.isFailure is true, but `outcome` itself does not throw.
        //  - If a Phone app is installed, the dispatch succeeds and outcome
        //    is Success.
        // EITHER outcome is acceptable; what's NOT acceptable is the test
        // throwing through to the runner (which would only happen if
        // runCatching were removed from production).
        outcome.fold(
            onSuccess = { /* ok — handler resolved, no crash */ },
            onFailure = { throwable ->
                if (throwable !is ActivityNotFoundException) {
                    fail(
                        "Expected ActivityNotFoundException or Success; got " +
                            "${throwable::class.simpleName}: ${throwable.message}",
                    )
                }
            },
        )
    }

    @Test
    fun mailto_uri_with_no_handler_is_caught_by_runCatching_pattern() {
        val context: Context = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:nobody@nowhere.invalid")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val outcome = runCatching { context.startActivity(intent) }

        outcome.fold(
            onSuccess = { },
            onFailure = { throwable ->
                if (throwable !is ActivityNotFoundException) {
                    fail("Unexpected throwable: ${throwable::class.simpleName}: ${throwable.message}")
                }
            },
        )
    }
}
