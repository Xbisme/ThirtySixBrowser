package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.IncomingRequest
import com.raumanian.thirtysix.browser.domain.model.IncomingTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 020 T055 — every `SearchText` row of contracts C2 (FR-011, FR-012, FR-013). */
class ResolveIncomingRequestUseCaseSearchTextTest {

    private val resolve = ResolveIncomingRequestUseCase()

    private fun search(raw: String) = resolve(IncomingRequest.SearchText(raw))

    @Test
    fun `blank text does nothing`() {
        assertEquals(IncomingTarget.Nothing, search("  "))
    }

    @Test
    fun `text is searched`() {
        assertEquals(IncomingTarget.Search("phở"), search("phở"))
    }

    @Test
    fun `an address is still searched, because the person asked to search`() {
        assertEquals(IncomingTarget.Search("https://example.org"), search("https://example.org"))
    }

    @Test
    fun `long text is cut to 2048 characters`() {
        val target = search("x".repeat(5_000)) as IncomingTarget.Search
        assertEquals(2_048, target.text.length)
    }
}
