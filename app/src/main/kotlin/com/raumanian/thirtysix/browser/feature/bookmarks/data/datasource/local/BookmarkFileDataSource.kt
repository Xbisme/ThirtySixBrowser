package com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local

import android.content.Context
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkTransferLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.Writer
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Reads and writes a file the person picked in the system file picker; no permission is involved. */
class BookmarkFileDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    sealed interface ReadOutcome {
        data class Text(val text: String) : ReadOutcome

        data object TooLarge : ReadOutcome

        data object Failed : ReadOutcome
    }

    suspend fun readText(uri: String): ReadOutcome = withContext(dispatchers.io) {
        try {
            val input = context.contentResolver.openInputStream(uri.toUri()) ?: return@withContext ReadOutcome.Failed
            val bytes = input.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > BookmarkTransferLimits.MAX_IMPORT_FILE_BYTES) return@withContext ReadOutcome.TooLarge
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            // Malformed UTF-8 becomes replacement characters, so a binary file simply has no bookmark markup.
            ReadOutcome.Text(bytes.toString(Charsets.UTF_8))
        } catch (_: IOException) {
            ReadOutcome.Failed
        } catch (_: SecurityException) {
            ReadOutcome.Failed
        }
    }

    suspend fun write(uri: String, content: (Writer) -> Unit): Boolean = withContext(dispatchers.io) {
        try {
            val output = context.contentResolver.openOutputStream(uri.toUri(), WRITE_TRUNCATE)
                ?: return@withContext false
            output.bufferedWriter(Charsets.UTF_8).use(content)
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val WRITE_TRUNCATE = "wt"
    }
}
