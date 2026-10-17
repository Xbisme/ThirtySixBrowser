package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.os.Bundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * A WebView's saved state as bytes we can keep on disk. `Parcel.marshall()` is documented as
 * unfit for persistent storage, so the bundle's entries, which are byte arrays, are written
 * here as a versioned list of `(key, bytes)` pairs.
 */
internal object TabStateCodec {
    private const val VERSION: Int = 1

    /** Null when the bundle holds anything but byte arrays: such a state is not kept. */
    fun encode(bundle: Bundle): ByteArray? {
        val entries = bundle.keySet().associateWith { key -> bundle.byteArrayOrNull(key) }
        if (entries.isEmpty() || entries.values.any { it == null }) return null
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeByte(VERSION)
            data.writeInt(entries.size)
            entries.forEach { (key, value) ->
                val bytes = requireNotNull(value)
                data.writeUTF(key)
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return out.toByteArray()
    }

    /** Null for an unknown version or bytes that do not parse. */
    fun decode(bytes: ByteArray): Bundle? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            if (data.readUnsignedByte() != VERSION) return null
            val count = data.readInt()
            if (count <= 0) return null
            val bundle = Bundle()
            repeat(count) {
                val key = data.readUTF()
                val size = data.readInt()
                if (size < 0 || size > data.available()) return null
                bundle.putByteArray(key, ByteArray(size).also(data::readFully))
            }
            if (data.available() != 0) null else bundle
        }
    } catch (_: IOException) {
        null
    }

    @Suppress("DEPRECATION") // Typed getters would throw on a foreign value type; this only inspects it.
    private fun Bundle.byteArrayOrNull(key: String): ByteArray? = get(key) as? ByteArray
}
