package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 020 T011 — every action the manifest declares, and the inputs that must be ignored. */
@RunWith(RobolectricTestRunner::class)
class IncomingIntentReaderTest {

    private val reader = IncomingIntentReader()

    @Test
    fun `VIEW with a web address becomes OpenAddress`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/a?b=1"))
        assertEquals(IncomingRequest.OpenAddress("https://example.org/a?b=1"), reader.read(intent))
    }

    @Test
    fun `VIEW without data is ignored`() {
        assertNull(reader.read(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `SEND with text becomes InterpretText`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Read this")
        assertEquals(IncomingRequest.InterpretText("Read this"), reader.read(intent))
    }

    @Test
    fun `SEND without text is ignored`() {
        assertNull(reader.read(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    @Test
    fun `SEND with a non-text extra is ignored`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, 42)
        assertNull(reader.read(intent))
    }

    @Test
    fun `PROCESS_TEXT becomes SearchText`() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "phở")
        assertEquals(IncomingRequest.SearchText("phở"), reader.read(intent))
    }

    @Test
    fun `WEB_SEARCH becomes SearchText`() {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "weather hanoi")
        assertEquals(IncomingRequest.SearchText("weather hanoi"), reader.read(intent))
    }

    @Test
    fun `an unknown action is ignored`() {
        assertNull(reader.read(Intent(Intent.ACTION_DIAL, Uri.parse("tel:123"))))
    }

    @Test
    fun `a null intent is ignored`() {
        assertNull(reader.read(null))
    }
}
