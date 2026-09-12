package com.raumanian.thirtysix.browser.dev

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Spec 016 T078 — **debug-build-only** tab seeder for the clear-browsing-data gates
 * (quickstart G5 and G8, research R15).
 *
 * Tops the normal-tab count up to `min(count, BrowserLimits.MAX_TABS)`. It counts existing tabs
 * rather than creating `count` new ones, because the app always holds at least one tab and the
 * cap is 50 — creating 50 more would hit the cap. Each seeded tab gets a placeholder preview and
 * a site icon under its own host, so "Cached images and files" has real files to delete.
 *
 * Lives in `app/src/debug/`, like `HistorySeeder`, so the class does not exist in a release
 * build at all.
 *
 * Usage — with the debug build installed:
 * ```
 * # top up to 50 tabs
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_TABS \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.TabSeeder
 *
 * # top up to 10 tabs
 * adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_TABS \
 *   -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.TabSeeder --ei count 10
 *
 * adb logcat -s TabSeeder
 * ```
 */
@AndroidEntryPoint
class TabSeeder : BroadcastReceiver() {

    @Inject
    lateinit var tabRepository: TabRepository

    @Inject
    lateinit var screenshotCache: ScreenshotCache

    @Inject
    lateinit var faviconCache: FaviconCache

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
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
        for (attempt in 0 until BrowserLimits.MAX_TABS) {
            val total = tabRepository.getTabCount()
            if (total >= goal) break
            val url = "https://seed-tab-$total.example.com/"
            when (val result = tabRepository.createTab(url)) {
                is Result.Success -> {
                    screenshotCache.save(
                        tabId = result.data.id,
                        bitmap = placeholder(
                            width = AppConstants.SCREENSHOT_TARGET_WIDTH_PX,
                            height = AppConstants.SCREENSHOT_TARGET_HEIGHT_PX,
                            index = total,
                        ),
                    )
                    faviconCache.save(url, placeholder(ICON_SIZE_PX, ICON_SIZE_PX, total))
                    created++
                }
                is Result.Error -> {
                    Log.w(TAG, "createTab failed after $created new tabs; stopping", result.throwable)
                    break
                }
            }
        }
        Log.i(TAG, "created $created tabs; total=${tabRepository.getTabCount()} (cap ${BrowserLimits.MAX_TABS})")
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
