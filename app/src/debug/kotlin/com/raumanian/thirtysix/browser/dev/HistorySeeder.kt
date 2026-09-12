package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Spec 014 T103a — **debug-build-only** bulk history seeder for the SC-005 / SC-006
 * performance gate (quickstart gate **G8**: 10 000 rows spanning 30 days, then measure
 * screen-open latency and per-keystroke search responsiveness).
 *
 * Lives in `app/src/debug/` so it is compiled into the debug APK **only** — the release
 * variant never sees this source set, which is a stronger guarantee than a
 * `BuildConfig.DEBUG` runtime check (the class simply does not exist in the release
 * artifact, so R8 has nothing to strip and no manifest entry survives).
 *
 * Usage — with the debug build installed:
 * ```
 * # seed the default 10 000 rows across 30 days
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_HISTORY \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.HistorySeeder
 *
 * # custom volume / span
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_HISTORY \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.HistorySeeder \
 *   --ei count 2000 --ei days 7
 *
 * # wipe what was seeded
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.CLEAR_HISTORY \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.HistorySeeder
 *
 * # follow progress; "seeded N rows in …" marks the end of the whole seed
 * adb logcat -s HistorySeeder
 * ```
 *
 * Rows are written through [HistoryRepository] (never the DAO directly) so the seeded
 * data travels the exact production path the benchmark is measuring. Each batch of
 * inserts shares one database transaction, and the coroutine yields between batches to
 * keep the process responsive while seeding.
 *
 * Spec 016 — a seed is split across broadcasts. A receiver may keep its broadcast open for
 * only 60 seconds before the system raises an ANR, and on the API 24 AVD a batch of
 * [BATCH_SIZE] rows takes about 3.5 s even inside a transaction, so a 10 000-row seed in one
 * broadcast ran for 60–67 s and ended in an ANR during gate G7. Each broadcast now writes at
 * most [ROWS_PER_BROADCAST] rows and then sends itself the next part; every part spreads its
 * visits from the same starting moment, so the result is identical to a single pass.
 */
@AndroidEntryPoint
class HistorySeeder : BroadcastReceiver() {

    @Inject
    lateinit var historyRepository: HistoryRepository

    /** Used only to group each batch of repository writes into one transaction. */
    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SEED_HISTORY -> seedPart(context, intent)

                    ACTION_CLEAR_HISTORY -> {
                        val removed = historyRepository.clearAll()
                        Log.i(TAG, "cleared $removed history rows")
                    }

                    else -> Log.w(TAG, "ignoring unknown action: ${intent.action}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun seedPart(context: Context, intent: Intent) {
        val total = intent.getIntExtra(EXTRA_COUNT, DEFAULT_ROW_COUNT).coerceIn(1, MAX_ROW_COUNT)
        val days = intent.getIntExtra(EXTRA_DAYS, DEFAULT_DAY_SPAN).coerceIn(1, MAX_DAY_SPAN)
        val from = intent.getIntExtra(EXTRA_FROM, 0).coerceIn(0, total)
        // Every part of one seed measures its spread, and the elapsed time, from the same moment.
        val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
        val until = minOf(from + ROWS_PER_BROADCAST, total)
        val span = TimeUnit.DAYS.toMillis(days.toLong())
        if (from == 0) Log.i(TAG, "seeding $total rows across $days days…")

        for (batchStart in from until until step BATCH_SIZE) {
            val batchEnd = minOf(batchStart + BATCH_SIZE, until)
            database.withTransaction {
                for (index in batchStart until batchEnd) {
                    // Spread visits evenly backwards from the start so every day bucket is populated.
                    val visitedAt = startedAt - (span * index / total)
                    val host = SEED_HOSTS[index % SEED_HOSTS.size]
                    historyRepository.recordVisit(
                        url = "https://$host/page-$index",
                        title = "Seeded page $index on $host",
                        visitedAt = visitedAt,
                    )
                }
            }
            Log.i(TAG, "seeded $batchEnd/$total")
            yield()
        }

        if (until < total) {
            val next = Intent(intent)
                .setComponent(ComponentName(context, HistorySeeder::class.java))
                .putExtra(EXTRA_FROM, until)
                .putExtra(EXTRA_STARTED_AT, startedAt)
            context.sendBroadcast(next)
        } else {
            val elapsedMs = System.currentTimeMillis() - startedAt
            Log.i(TAG, "seeded $total rows in ${elapsedMs}ms; total=${historyRepository.count()}")
        }
    }

    private companion object {
        const val TAG = "HistorySeeder"

        const val ACTION_SEED_HISTORY = "com.raumanian.thirtysix.browser.debug.SEED_HISTORY"
        const val ACTION_CLEAR_HISTORY = "com.raumanian.thirtysix.browser.debug.CLEAR_HISTORY"

        const val EXTRA_COUNT = "count"
        const val EXTRA_DAYS = "days"

        /** Internal: where the next part of a split seed starts. */
        const val EXTRA_FROM = "from"

        /** Internal: the moment a split seed started, shared by all of its parts. */
        const val EXTRA_STARTED_AT = "started_at"

        /** SC-005 / SC-006 benchmark volume. */
        const val DEFAULT_ROW_COUNT = 10_000

        /** SC-005 / SC-006 benchmark span. */
        const val DEFAULT_DAY_SPAN = 30

        const val MAX_ROW_COUNT = 100_000
        const val MAX_DAY_SPAN = 365

        /** How many inserts share one transaction before the coroutine yields and logs progress. */
        const val BATCH_SIZE = 500

        /**
         * Rows one broadcast writes before handing the rest to the next broadcast. On the loaded
         * API 24 AVD a 2 000-row part once took 34.6 s, too close to the 60 s limit; 1 000 keeps the
         * margin wide.
         */
        const val ROWS_PER_BROADCAST = 1_000

        /** Varied hosts so favicon lookups and hostname rendering are exercised too. */
        val SEED_HOSTS = listOf(
            "example.com",
            "developer.android.com",
            "kotlinlang.org",
            "wikipedia.org",
            "news.ycombinator.com",
        )
    }
}
