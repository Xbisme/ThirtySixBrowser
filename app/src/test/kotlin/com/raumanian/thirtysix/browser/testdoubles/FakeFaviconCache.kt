package com.raumanian.thirtysix.browser.testdoubles

import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Spec 016 / 024 — [FaviconCache] that records [clearAll] and [retainOnly] into a shared [callLog] and can be made to
 * throw through [clearAllError]. Holds no icons; `save` and `fileFor` are inert.
 */
class FakeFaviconCache(
    private val callLog: MutableList<String> = mutableListOf(),
) : FaviconCache {

    private val versionState = MutableStateFlow(0L)
    override val version: StateFlow<Long> = versionState.asStateFlow()

    var clearAllError: Throwable? = null

    override suspend fun save(url: String, bitmap: Bitmap) = Unit

    override fun fileFor(url: String): File? = null

    override suspend fun clearAll() {
        callLog += CALL_CLEAR_ALL
        clearAllError?.let { throw it }
        versionState.update { it + 1 }
    }

    /** Spec 024 — the host set of the last [retainOnly] call, or `null` if never called. */
    var retainedHosts: Set<String>? = null

    override suspend fun retainOnly(hosts: Set<String>) {
        callLog += CALL_RETAIN_ONLY
        retainedHosts = hosts
        versionState.update { it + 1 }
    }

    companion object {
        const val CALL_CLEAR_ALL: String = "favicons.clearAll"
        const val CALL_RETAIN_ONLY: String = "favicons.retainOnly"
    }
}
