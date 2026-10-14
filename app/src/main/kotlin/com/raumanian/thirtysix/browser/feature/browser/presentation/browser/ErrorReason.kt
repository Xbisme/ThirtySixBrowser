package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.annotation.StringRes
import com.raumanian.thirtysix.browser.R

/** WebView main-frame load failures. */
sealed class ErrorReason {
    data object NetworkUnavailable : ErrorReason()

    /** Usually means the hostname does not exist. */
    data object DnsFailure : ErrorReason()

    data class HttpError(val statusCode: Int) : ErrorReason()

    /** The handler is always cancelled, never `proceed()`ed. */
    data object SslError : ErrorReason()

    data object Generic : ErrorReason()
}

@StringRes
fun ErrorReason.toUserMessageRes(): Int = when (this) {
    ErrorReason.NetworkUnavailable, ErrorReason.DnsFailure -> R.string.browser_error_offline_hint
    is ErrorReason.HttpError, ErrorReason.SslError, ErrorReason.Generic ->
        R.string.browser_error_generic
}
