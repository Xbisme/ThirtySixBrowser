package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabStateCodecTest {

    private fun engineState(bytes: ByteArray) = Bundle().apply { putByteArray(ENGINE_KEY, bytes) }

    @Test
    fun `an engine state round-trips`() {
        val payload = ByteArray(876) { it.toByte() }

        val decoded = TabStateCodec.decode(requireNotNull(TabStateCodec.encode(engineState(payload))))

        assertNotNull(decoded)
        assertEquals(setOf(ENGINE_KEY), decoded!!.keySet())
        assertArrayEquals(payload, decoded.getByteArray(ENGINE_KEY))
    }

    @Test
    fun `an empty bundle is not kept`() {
        assertNull(TabStateCodec.encode(Bundle()))
    }

    @Test
    fun `a bundle with a value that is not bytes is not kept`() {
        val bundle = engineState(byteArrayOf(1)).apply { putString("other", "x") }

        assertNull(TabStateCodec.encode(bundle))
    }

    @Test
    fun `an unknown version does not decode`() {
        val encoded = requireNotNull(TabStateCodec.encode(engineState(byteArrayOf(1, 2))))
        encoded[0] = 99

        assertNull(TabStateCodec.decode(encoded))
    }

    @Test
    fun `truncated bytes do not decode`() {
        val encoded = requireNotNull(TabStateCodec.encode(engineState(ByteArray(64) { 7 })))

        assertNull(TabStateCodec.decode(encoded.copyOf(encoded.size - 10)))
    }

    @Test
    fun `trailing bytes do not decode`() {
        val encoded = requireNotNull(TabStateCodec.encode(engineState(byteArrayOf(1))))

        assertNull(TabStateCodec.decode(encoded + byteArrayOf(0)))
    }

    @Test
    fun `garbage does not decode`() {
        assertNull(TabStateCodec.decode(byteArrayOf()))
        assertNull(TabStateCodec.decode(byteArrayOf(1, 0x7f, 0x7f, 0x7f, 0x7f)))
    }

    private companion object {
        const val ENGINE_KEY = "WEBVIEW_CHROMIUM_STATE"
    }
}
