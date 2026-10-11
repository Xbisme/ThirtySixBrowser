package com.raumanian.thirtysix.browser.presentation.entry

import com.raumanian.thirtysix.browser.domain.model.IncomingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 020 T014 — saved-state round trip for every request kind (research R3). */
class PendingRequestStateTest {

    @Test
    fun `every kind survives a round trip`() {
        listOf(
            IncomingRequest.OpenAddress("https://example.org/a"),
            IncomingRequest.InterpretText("Read https://example.org/b"),
            IncomingRequest.SearchText("phở"),
        ).forEach { request ->
            val (kind, raw) = PendingRequestState.encode(request)
            assertEquals(request, PendingRequestState.decode(kind, raw))
        }
    }

    @Test
    fun `an unknown kind decodes to null`() {
        assertNull(PendingRequestState.decode("something_else", "x"))
    }

    @Test
    fun `missing parts decode to null`() {
        assertNull(PendingRequestState.decode(null, "x"))
        assertNull(PendingRequestState.decode("search_text", null))
    }
}
