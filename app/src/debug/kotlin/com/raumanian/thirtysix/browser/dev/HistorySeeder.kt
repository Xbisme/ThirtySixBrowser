package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Debug-only: seeds history through [HistoryRepository], the production path, in batches that
 * share one transaction. A receiver gets 60 s before an ANR, so each broadcast writes at most
 * [ROWS_PER_BROADCAST] rows and sends itself the next part, all spread from the same start.
 */
@AndroidEntryPoint
class HistorySeeder : BroadcastReceiver() {

    @Inject
    lateinit var historyRepository: HistoryRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(dispatchers.io)
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

        /** Internal: carried between the parts of a split seed. */
        const val EXTRA_FROM = "from"

        const val EXTRA_STARTED_AT = "started_at"

        /** The list-performance benchmark: 10 000 rows over 30 days. */
        const val DEFAULT_ROW_COUNT = 10_000

        const val DEFAULT_DAY_SPAN = 30

        const val MAX_ROW_COUNT = 100_000
        const val MAX_DAY_SPAN = 365

        const val BATCH_SIZE = 500

        /** A 2 000-row part took 34.6 s on a slow API 24 emulator; 1 000 keeps well under 60 s. */
        const val ROWS_PER_BROADCAST = 1_000

        val SEED_HOSTS = listOf(
            "example.com",
            "developer.android.com",
            "kotlinlang.org",
            "wikipedia.org",
            "news.ycombinator.com",
        )
    }
}
