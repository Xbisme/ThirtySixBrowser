package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.IncomingRequest
import com.raumanian.thirtysix.browser.domain.model.IncomingTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 020 T051 — every `InterpretText` row of contracts C2 (FR-010, FR-013). */
class ResolveIncomingRequestUseCaseInterpretTextTest {

    private val resolve = ResolveIncomingRequestUseCase()

    private fun share(raw: String) = resolve(IncomingRequest.InterpretText(raw))

    @Test
    fun `blank text does nothing`() {
        assertEquals(IncomingTarget.Nothing, share("   "))
    }

    @Test
    fun `a bare https address opens`() {
        assertEquals(IncomingTarget.Load("https://example.org/a"), share("https://example.org/a"))
    }

    @Test
    fun `a lone domain opens the address the address bar would build`() {
        assertEquals(IncomingTarget.Load("https://example.org"), share("example.org"))
    }

    @Test
    fun `an address inside a sentence opens`() {
        assertEquals(IncomingTarget.Load("https://example.org/a"), share("Read this https://example.org/a — so good"))
    }

    @Test
    fun `the first of several addresses opens, without trailing punctuation`() {
        assertEquals(IncomingTarget.Load("https://a.org"), share("See https://a.org, then https://b.org."))
    }

    @Test
    fun `a closing bracket after the address is trimmed`() {
        assertEquals(IncomingTarget.Load("https://example.org/x"), share("(https://example.org/x)"))
    }

    @Test
    fun `a bare word with a dot inside a sentence is searched, not opened`() {
        assertEquals(IncomingTarget.Search("see file.txt today"), share("see file.txt today"))
    }

    @Test
    fun `plain words are searched`() {
        assertEquals(IncomingTarget.Search("best pho in hanoi"), share("best pho in hanoi"))
    }

    @Test
    fun `a lone non-web scheme is searched as text, never loaded`() {
        assertEquals(IncomingTarget.Search("javascript:alert(1)"), share("javascript:alert(1)"))
    }

    @Test
    fun `long text is cut to 2048 characters`() {
        val long = "word ".repeat(1_000)
        val target = share(long) as IncomingTarget.Search
        assertEquals(long.trim().take(2_048).trim(), target.text)
    }
}
