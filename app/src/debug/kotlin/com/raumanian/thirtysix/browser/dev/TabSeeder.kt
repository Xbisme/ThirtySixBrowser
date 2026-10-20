package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.util.pngWriter
import com.raumanian.thirtysix.browser.core.presentation.util.webpWriter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only: tops normal tabs up to `--ei count N` (default the cap), each with a placeholder
 * preview and site icon so "Cached images and files" has files to clear. It counts existing tabs
 * because the app always holds at least one.
 */
@AndroidEntryPoint
class TabSeeder : BroadcastReceiver() {

    @Inject
    lateinit var tabRepository: TabRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var screenshotCache: ScreenshotCache

    @Inject
    lateinit var faviconCache: FaviconCache

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(dispatchers.io).launch {
            try {
                if (intent.action == ACTION_SEED_TABS) {
                    seed(requested = intent.getIntExtra(EXTRA_COUNT, BrowserLimits.MAX_TABS))
                } else {
                    Log.w(TAG, "ignoring unknown action: ${intent.action}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun seed(requested: Int) {
        val goal = requested.coerceIn(1, BrowserLimits.MAX_TABS)
        var created = 0
        // Bounded by the cap, so a repository that stops counting new tabs cannot loop forever.
        var stopped = false
        repeat(BrowserLimits.MAX_TABS) {
            val total = tabRepository.observeTabs().first().size
            if (stopped || total >= goal) return@repeat
            val url = "https://seed-tab-$total.example.com/"
            when (val result = tabRepository.createTab(url)) {
                is Result.Success -> {
                    screenshotCache.save(
                        tabId = result.data.id,
                        writeImage = placeholder(
                            width = AppConstants.SCREENSHOT_TARGET_WIDTH_PX,
                            height = AppConstants.SCREENSHOT_TARGET_HEIGHT_PX,
                            index = total,
                        ).webpWriter(),
                    )
                    faviconCache.save(url, placeholder(ICON_SIZE_PX, ICON_SIZE_PX, total).pngWriter())
                    created++
                }
                is Result.Error -> {
                    Log.w(TAG, "createTab failed after $created new tabs; stopping", result.throwable)
                    stopped = true
                }
            }
        }
        val total = tabRepository.observeTabs().first().size
        Log.i(TAG, "created $created tabs; total=$total (cap ${BrowserLimits.MAX_TABS})")
    }

    private fun placeholder(width: Int, height: Int, index: Int): Bitmap =
        createBitmap(width, height).apply { eraseColor(PALETTE[index % PALETTE.size]) }

    private companion object {
        const val TAG = "TabSeeder"
        const val ACTION_SEED_TABS = "com.raumanian.thirtysix.browser.debug.SEED_TABS"
        const val EXTRA_COUNT = "count"

        /** Roughly the size a page's site icon arrives at. */
        const val ICON_SIZE_PX = 32

        /** Distinct solid colours so seeded previews are told apart at a glance. */
        val PALETTE = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.CYAN)
    }
}
