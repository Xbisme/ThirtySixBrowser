package com.raumanian.thirtysix.browser.core.data.datasource.local.cache

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Readers see the old file or the whole new one, never a partial write; a failed write leaves nothing. */
internal inline fun File.writeAtomically(block: (OutputStream) -> Unit) {
    val temp = File(parentFile, "$name.tmp")
    var renamed = false
    try {
        temp.outputStream().use(block)
        if (!temp.renameTo(this)) throw IOException("Could not move the written file into place")
        renamed = true
    } finally {
        if (!renamed) temp.delete()
    }
}
