package com.raumanian.thirtysix.browser.core.data.datasource.local.cache

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two saves of the same icon or preview can race; neither may mix into or delete the other's write. */
class AtomicFileTest {

    @Test
    fun `concurrent writes leave one whole payload and no temp files`() {
        repeat(ROUNDS) { raceOnce() }
    }

    private fun raceOnce() {
        val dir = Files.createTempDirectory("atomic").toFile()
        val target = File(dir, "icon.png")
        val payloads = List(WRITERS) { index -> ByteArray(PAYLOAD_BYTES) { index.toByte() } }
        val start = CountDownLatch(1)

        val writers = payloads.map { payload ->
            thread {
                start.await()
                try {
                    target.writeAtomically { out ->
                        payload.asList().chunked(CHUNK_BYTES).forEach { out.write(it.toByteArray()) }
                    }
                } catch (_: IOException) {
                    // A writer that loses the race may fail; the file must still be whole.
                }
            }
        }
        start.countDown()
        writers.forEach { it.join() }

        val written = target.readBytes()
        assertTrue("mixed payload", payloads.any { it.contentEquals(written) })
        assertEquals(listOf(target.name), dir.list()?.toList())
        dir.deleteRecursively()
    }

    private companion object {
        const val ROUNDS = 10
        const val WRITERS = 20
        const val PAYLOAD_BYTES = 256 * 1024
        const val CHUNK_BYTES = 4 * 1024
    }
}
