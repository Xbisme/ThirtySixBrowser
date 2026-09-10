package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.repository.DownloadsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Spec 015 T107 — **debug-build-only** bulk downloads seeder for the SC-006 performance
 * gate (quickstart **G12**: 500 records, at least 5 of them in flight).
 *
 * Lives in `app/src/debug/` so it compiles into the debug APK only, exactly like Spec 014's
 * `HistorySeeder`. That is a stronger guarantee than a `BuildConfig.DEBUG` check: the class
 * does not exist in the release artifact at all, so there is nothing for R8 to strip and no
 * manifest entry survives.
 *
 * **What "in flight" means here.** This seeder writes *records*, not real transfers. A
 * seeded record carries a transfer handle the platform has never heard of, so
 * `ResolveDownloadStatusUseCase` resolves it through the FR-024a fallback — `Complete` when
 * `localUri` points at something readable, `Missing` otherwise. That exercises the fallback
 * path at scale, which is the expensive one, but it does **not** produce live progress bars.
 * For G12's "at least 5 actively transferring" requirement, start a few real downloads by
 * hand on top of the seeded rows.
 *
 * Usage — with the debug build installed:
 * ```
 * # seed the default 500 records spread over 30 days
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_DOWNLOADS \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.DownloadSeeder
 *
 * # custom volume / span
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_DOWNLOADS \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.DownloadSeeder \
 *   --ei count 2000 --ei days 90
 *
 * # wipe what was seeded
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.CLEAR_DOWNLOADS \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.DownloadSeeder
 *
 * # follow progress
 * adb logcat -s DownloadSeeder
 * ```
 */
@AndroidEntryPoint
class DownloadSeeder : BroadcastReceiver() {

    @Inject
    lateinit var repository: DownloadsRepository

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
        CoroutineScope(Dispatchers.IO).launch {
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
                        // A handle the platform will not recognise, so the row exercises the
                        // FR-024a file-presence fallback rather than a real query result.
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
        CoroutineScope(Dispatchers.IO).launch {
            val before = repository.count()
            // Deleted one at a time because the repository deliberately has no clear-all:
            // the feature does not offer one either (bulk clearing is Spec 016's business),
            // and a debug tool is not a reason to widen the production surface.
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

        /** SC-006's envelope: 500 records is the realistic upper bound from A14. */
        const val DEFAULT_COUNT = 500
        const val DEFAULT_DAYS = 30
        const val PROGRESS_EVERY = 50

        /** Far above anything the platform would allocate, so seeded rows never collide. */
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
