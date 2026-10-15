package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Debug-only: writes download records (`--ei count`, `--ei days`), not real transfers. Their
 * handles are unknown to the platform, so they resolve through the file-presence fallback and
 * never show live progress; start real downloads by hand for those.
 */
@AndroidEntryPoint
class DownloadSeeder : BroadcastReceiver() {

    @Inject
    lateinit var repository: DownloadsRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEED -> seed(
                count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT),
                days = intent.getIntExtra(EXTRA_DAYS, DEFAULT_DAYS),
            )

            ACTION_CLEAR -> clear()
            else -> Log.w(TAG, "ignoring unknown action: ${intent.action}")
        }
    }

    private fun seed(count: Int, days: Int) {
        // goAsync() is deliberately not used: seeding takes far longer than a receiver's
        // allowed window, and this is a debug tool where a detached scope is acceptable.
        CoroutineScope(dispatchers.io).launch {
            Log.i(TAG, "seeding $count download records across $days day(s)")
            val now = System.currentTimeMillis()
            val span = TimeUnit.DAYS.toMillis(days.toLong()).coerceAtLeast(1L)

            repeat(count) { index ->
                // Spread backwards from now so the list has a realistic day distribution.
                val createdAt = now - (span * index / count.coerceAtLeast(1))
                repository.insert(
                    DownloadRecord(
                        id = 0L,
                        sourceUrl = "https://example.com/files/seeded-$index.bin",
                        fileName = "seeded-$index.bin",
                        mimeType = MIME_TYPES[index % MIME_TYPES.size],
                        createdAt = createdAt,
                        // Unknown to the platform, so the file-presence fallback resolves it.
                        transferHandle = SEEDED_HANDLE_BASE + index,
                        localUri = null,
                    ),
                )
                if (index % PROGRESS_EVERY == 0) {
                    Log.i(TAG, "  seeded $index/$count")
                    yield()
                }
            }
            Log.i(TAG, "done — ${repository.count()} record(s) now in the database")
        }
    }

    private fun clear() {
        CoroutineScope(dispatchers.io).launch {
            val before = repository.count()
            // One at a time: the repository has no clear-all, and a debug tool should not add one.
            val ids = repository.observeAll().first().map { it.id }
            val removed = ids.count { repository.deleteById(it) }
            Log.i(TAG, "cleared $removed of $before record(s)")
        }
    }

    private companion object {
        const val TAG = "DownloadSeeder"
        const val ACTION_SEED = "com.raumanian.thirtysix.browser.debug.SEED_DOWNLOADS"
        const val ACTION_CLEAR = "com.raumanian.thirtysix.browser.debug.CLEAR_DOWNLOADS"
        const val EXTRA_COUNT = "count"
        const val EXTRA_DAYS = "days"

        /** The realistic upper bound for one person's downloads list. */
        const val DEFAULT_COUNT = 500
        const val DEFAULT_DAYS = 30
        const val PROGRESS_EVERY = 50

        const val SEEDED_HANDLE_BASE = 900_000_000L

        val MIME_TYPES = listOf(
            "application/pdf",
            "application/zip",
            "image/png",
            "text/plain",
            "video/mp4",
        )
    }
}
