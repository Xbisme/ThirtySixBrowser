package com.raumanian.thirtysix.browser.data.local.cache

import android.content.Context
import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Spec 011 Q4 amendment (2026-05-03) — disk-backed cache of WebView page
 * screenshots keyed by **tab id** (not URL).
 *
 * Tab-id-keyed (vs the URL-keyed first draft) for predictable lifecycle:
 *  - Exactly ONE screenshot file per tab (the most recent capture).
 *  - Bounded by `BrowserLimits.MAX_TABS` × ~5 KB ≈ 250 KB disk worst-case.
 *  - On tab close → [delete] removes the file deterministically (no
 *    URL-tracking bookkeeping needed).
 *  - On `closeAllTabs` → [clearAll] wipes the directory.
 *  - When the user navigates within a tab, the new screenshot overwrites
 *    the old one for that same tab id.
 *
 * Lookups are synchronous (file-existence check) so Composables can call
 * [fileFor] inside `remember(...)` without spinning a coroutine. Writes are
 * suspending and run on `dispatchers.io`. Storage uses WebP for ~5× smaller
 * files than PNG at acceptable quality for the tab card preview.
 */
interface ScreenshotCache {
    /** Bumps on every successful [save] / [delete] / [clearAll]. */
    val version: StateFlow<Long>

    /** Persist [bitmap] for [tabId]. Idempotent; overwrites the prior file. */
    suspend fun save(tabId: Long, bitmap: Bitmap)

    /** Synchronous lookup. Returns the cached file or `null`. */
    fun fileFor(tabId: Long): File?

    /**
     * Delete the cached screenshot for [tabId] (called from `closeTab`).
     * No-op if the file does not exist. Bumps [version] so any visible
     * card composable re-evaluates and falls back to the placeholder.
     */
    suspend fun delete(tabId: Long)

    /**
     * Wipe every cached screenshot (called from `closeAllTabs`). Idempotent.
     */
    suspend fun clearAll()
}

@Singleton
class DiskScreenshotCache @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) : ScreenshotCache {

    private val cacheDir: File =
        File(context.cacheDir, AppConstants.SCREENSHOT_CACHE_DIR_NAME).apply { mkdirs() }

    private val versionState: MutableStateFlow<Long> = MutableStateFlow(0L)

    override val version: StateFlow<Long> = versionState.asStateFlow()

    override suspend fun save(tabId: Long, bitmap: Bitmap) = withContext(dispatchers.io) {
        if (tabId <= 0L) return@withContext
        val file = fileForId(tabId)
        try {
            file.outputStream().use { out ->
                @Suppress("DEPRECATION")
                // WEBP (without LOSSY suffix) is the API 24 form; the LOSSY
                // variant requires API 30+. minSdk = 24 forces the deprecated
                // form for back-compat. Quality 75 → ~3–8 KB at 480×270.
                bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
            }
            versionState.update { it + 1 }
        } catch (e: java.io.IOException) {
            android.util.Log.w(LOG_TAG, "Failed to write screenshot for tab $tabId", e)
        }
    }

    override fun fileFor(tabId: Long): File? {
        if (tabId <= 0L) return null
        val file = fileForId(tabId)
        return if (file.exists() && file.length() > 0L) file else null
    }

    override suspend fun delete(tabId: Long) = withContext(dispatchers.io) {
        val file = fileForId(tabId)
        if (file.exists() && file.delete()) {
            versionState.update { it + 1 }
        }
        Unit
    }

    override suspend fun clearAll() = withContext(dispatchers.io) {
        cacheDir.listFiles()?.forEach { it.delete() }
        versionState.update { it + 1 }
    }

    private fun fileForId(tabId: Long): File = File(cacheDir, "$tabId.webp")

    private companion object {
        const val WEBP_QUALITY: Int = 75
        const val LOG_TAG: String = "DiskScreenshotCache"
    }
}
