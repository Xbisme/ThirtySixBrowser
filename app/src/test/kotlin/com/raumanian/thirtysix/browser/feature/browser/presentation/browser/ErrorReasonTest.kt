package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `SslError maps to generic`() {
        assertEquals(R.string.browser_error_generic, ErrorReason.SslError.toUserMessageRes())
    }

    @Test
    fun `Generic maps to generic`() {
        assertEquals(R.string.browser_error_generic, ErrorReason.Generic.toUserMessageRes())
    }
}
