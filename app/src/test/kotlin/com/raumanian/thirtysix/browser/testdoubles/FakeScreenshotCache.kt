package com.raumanian.thirtysix.browser.testdoubles

import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.data.local.cache.ScreenshotCache
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Spec 016 — [ScreenshotCache] that records [clearAll] into a shared [callLog] and can be made
 * to throw through [clearAllError]. Holds no previews; the other members are inert.
 */
class FakeScreenshotCache(
    private val callLog: MutableList<String> = mutableListOf(),
) : ScreenshotCache {

    private val versionState = MutableStateFlow(0L)
    override val version: StateFlow<Long> = versionState.asStateFlow()

    var clearAllError: Throwable? = null

    override suspend fun save(tabId: Long, bitmap: Bitmap) = Unit

    override fun fileFor(tabId: Long): File? = null

    override suspend fun delete(tabId: Long) = Unit

    override suspend fun clearAll() {
        callLog += CALL_CLEAR_ALL
        clearAllError?.let { throw it }
        versionState.update { it + 1 }
    }

    companion object {
        const val CALL_CLEAR_ALL: String = "screenshots.clearAll"
    }
}
