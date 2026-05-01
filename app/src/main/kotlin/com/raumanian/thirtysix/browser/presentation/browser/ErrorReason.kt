package com.raumanian.thirtysix.browser.presentation.browser

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.R

/**
 * Spec 007 US3 — taxonomy of WebView load failures.
 *
 * Mapping from `WebViewClient` callbacks lives in
 * [com.raumanian.thirtysix.browser.presentation.browser.BrowserWebView]
 * (research.md R6). Sub-frame errors are filtered out before reaching the VM.
 */
sealed class ErrorReason {
    /** Generic transport failure (no DNS-vs-connect distinction needed for v1). */
    data object NetworkUnavailable : ErrorReason()

    /** DNS lookup failed — usually means hostname does not exist. */
    data object DnsFailure : ErrorReason()

    /** Server returned a 4xx / 5xx for the main frame. */
    data class HttpError(val statusCode: Int) : ErrorReason()

    /** SSL handshake failed; handler MUST be cancelled, never `proceed()`. */
    data object SslError : ErrorReason()

    /** Anything else WebView surfaced. Last-resort bucket. */
    data object Generic : ErrorReason()
}

/**
 * Returns the localized string resource ID for the user-facing message of this
 * [ErrorReason]. Spec 007 keeps the catalog at 2 messages (offline-hint vs
 * generic) per research.md R6 to limit l10n workload to 4 keys × 8 locales = 32.
 */
@StringRes
fun ErrorReason.toUserMessageRes(): Int = when (this) {
    ErrorReason.NetworkUnavailable, ErrorReason.DnsFailure -> R.string.browser_error_offline_hint
    is ErrorReason.HttpError, ErrorReason.SslError, ErrorReason.Generic ->
        R.string.browser_error_generic
}
