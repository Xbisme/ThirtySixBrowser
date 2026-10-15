package com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase

import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveIncomingRequestUseCaseOpenAddressTest {

    private val resolve = ResolveIncomingRequestUseCase()

    private fun open(raw: String) = resolve(IncomingRequest.OpenAddress(raw))

    @Test
    fun `a valid https address loads as delivered`() {
        assertEquals(IncomingTarget.Load("https://example.org/a?b=1"), open("https://example.org/a?b=1"))
    }

    @Test
    fun `the scheme is compared case-insensitively`() {
        assertEquals(IncomingTarget.Load("HTTP://Example.org"), open("HTTP://Example.org"))
    }

    @Test
    fun `an address without a host is rejected`() {
        assertEquals(IncomingTarget.Rejected, open("http://"))
        assertEquals(IncomingTarget.Rejected, open("https:///path"))
    }

    @Test
    fun `an unparseable address is rejected`() {
        assertEquals(IncomingTarget.Rejected, open("https://exa mple.org"))
    }

    @Test
    fun `non-web schemes are rejected`() {
        listOf(
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "file:///sdcard/a",
            "content://x/y",
            "data:text/html,hi",
            "market://details?id=x",
            "about:blank",
            "chrome://flags",
        ).forEach { raw -> assertEquals(raw, IncomingTarget.Rejected, open(raw)) }
    }

    @Test
    fun `an empty or blank address is rejected`() {
        assertEquals(IncomingTarget.Rejected, open(""))
        assertEquals(IncomingTarget.Rejected, open("   "))
    }

    @Test
    fun `an address longer than 2048 characters is rejected, not truncated`() {
        val long = "https://example.org/" + "a".repeat(2_048)
        assertEquals(IncomingTarget.Rejected, open(long))
    }
}
