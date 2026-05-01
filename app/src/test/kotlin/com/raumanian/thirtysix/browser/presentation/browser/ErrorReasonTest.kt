package com.raumanian.thirtysix.browser.presentation.browser

import com.raumanian.thirtysix.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 007 US3 — table-driven test of `ErrorReason.toUserMessageRes()`
 * mapping per research.md R6.
 */
class ErrorReasonTest {

    @Test
    fun `NetworkUnavailable maps to offline_hint`() {
        assertEquals(R.string.browser_error_offline_hint, ErrorReason.NetworkUnavailable.toUserMessageRes())
    }

    @Test
    fun `DnsFailure maps to offline_hint`() {
        assertEquals(R.string.browser_error_offline_hint, ErrorReason.DnsFailure.toUserMessageRes())
    }

    @Test
    fun `HttpError maps to generic`() {
        assertEquals(R.string.browser_error_generic, ErrorReason.HttpError(HTTP_NOT_FOUND).toUserMessageRes())
        assertEquals(R.string.browser_error_generic, ErrorReason.HttpError(HTTP_SERVER_ERROR).toUserMessageRes())
    }

    @Test
    fun `SslError maps to generic`() {
        assertEquals(R.string.browser_error_generic, ErrorReason.SslError.toUserMessageRes())
    }

    @Test
    fun `Generic maps to generic`() {
        assertEquals(R.string.browser_error_generic, ErrorReason.Generic.toUserMessageRes())
    }

    private companion object {
        const val HTTP_NOT_FOUND: Int = 404
        const val HTTP_SERVER_ERROR: Int = 500
    }
}
