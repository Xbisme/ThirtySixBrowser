package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.raumanian.thirtysix.browser.core.domain.model.ProtectionData
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Parses the bundled protection assets once per process on the IO dispatcher. The main thread
 * never waits ([currentDataOrNull]); WebView IO threads wait in [awaitData], bounded, and fail open.
 */
@Singleton
class ProtectionDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val started = AtomicBoolean(false)
    private val done = CountDownLatch(1)

    @Volatile
    private var data: ProtectionData? = null

    @Suppress("TooGenericExceptionCaught") // A broken asset must not take the browser down.
    fun preload() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                data = load()
            } catch (e: Exception) {
                // Protections stay off (fail open) rather than blocking pages.
                Log.e(TAG, "protection data failed to load", e)
            } finally {
                done.countDown()
            }
        }
    }

    fun currentDataOrNull(): ProtectionData? = data

    fun awaitData(timeoutMs: Long): ProtectionData? {
        data?.let { return it }
        preload()
        done.await(timeoutMs, TimeUnit.MILLISECONDS)
        return data
    }

    private fun load(): ProtectionData {
        val startedAt = SystemClock.elapsedRealtime()
        val result = ProtectionData(
            trackers = readLines(AssetPaths.PROTECTION_TRACKER_HOSTS, ProtectionAssetParser::parseTrackerHosts),
            suffixes = readLines(AssetPaths.PROTECTION_PUBLIC_SUFFIXES, ProtectionAssetParser::parsePublicSuffixes),
            params = readLines(AssetPaths.PROTECTION_TRACKING_PARAMS, ProtectionAssetParser::parseTrackingParams),
        )
        Log.i(
            TAG,
            "protection data loaded in ${SystemClock.elapsedRealtime() - startedAt} ms: " +
                "${result.trackers.size} trackers, ${result.suffixes.size} suffix rules",
        )
        return result
    }

    private fun <T> readLines(path: String, parse: (Sequence<String>) -> T): T =
        context.assets.open(path).bufferedReader().useLines(parse)

    private companion object {
        const val TAG = "ProtectionData"
    }
}
