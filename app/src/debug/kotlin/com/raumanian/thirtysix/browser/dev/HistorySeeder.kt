package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
 * # follow progress
 * adb logcat -s HistorySeeder
 * ```
 *
 * Rows are written through [HistoryRepository] (never the DAO directly) so the seeded
 * data travels the exact production path the benchmark is measuring. Inserts are
 * yielded between batches to keep the process responsive while seeding.
 */
@AndroidEntryPoint
class HistorySeeder : BroadcastReceiver() {

    @Inject
    lateinit var historyRepository: HistoryRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SEED_HISTORY -> seed(
                        count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_ROW_COUNT),
                        days = intent.getIntExtra(EXTRA_DAYS, DEFAULT_DAY_SPAN),
                    )

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

    private suspend fun seed(count: Int, days: Int) {
        val safeCount = count.coerceIn(1, MAX_ROW_COUNT)
        val safeDays = days.coerceIn(1, MAX_DAY_SPAN)
        val now = System.currentTimeMillis()
        val span = TimeUnit.DAYS.toMillis(safeDays.toLong())
        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "seeding $safeCount rows across $safeDays days…")

        for (index in 0 until safeCount) {
            // Spread visits evenly backwards from now so every day bucket is populated.
            val visitedAt = now - (span * index / safeCount)
            val host = SEED_HOSTS[index % SEED_HOSTS.size]
            historyRepository.recordVisit(
                url = "https://$host/page-$index",
                title = "Seeded page $index on $host",
                visitedAt = visitedAt,
            )
            if (index % BATCH_SIZE == BATCH_SIZE - 1) {
                Log.i(TAG, "seeded ${index + 1}/$safeCount")
                yield()
            }
        }

        val elapsedMs = System.currentTimeMillis() - startedAt
        Log.i(TAG, "seeded $safeCount rows in ${elapsedMs}ms; total=${historyRepository.count()}")
    }

    private companion object {
        const val TAG = "HistorySeeder"

        const val ACTION_SEED_HISTORY = "com.raumanian.thirtysix.browser.debug.SEED_HISTORY"
        const val ACTION_CLEAR_HISTORY = "com.raumanian.thirtysix.browser.debug.CLEAR_HISTORY"

        const val EXTRA_COUNT = "count"
        const val EXTRA_DAYS = "days"

        /** SC-005 / SC-006 benchmark volume. */
        const val DEFAULT_ROW_COUNT = 10_000

        /** SC-005 / SC-006 benchmark span. */
        const val DEFAULT_DAY_SPAN = 30

        const val MAX_ROW_COUNT = 100_000
        const val MAX_DAY_SPAN = 365

        /** How many inserts run before the coroutine yields and logs progress. */
        const val BATCH_SIZE = 500

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
