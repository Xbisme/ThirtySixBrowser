package com.raumanian.thirtysix.browser.data.local.cache

import android.content.Context
import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Spec 011 favicon amendment (2026-05-03) — disk-backed cache of website
 * favicons keyed by SHA-1 of the URL hostname.
 *
 * Lookups are synchronous (file-existence check) so Composables can call
 * [fileFor] inside `remember(...)` without spinning a coroutine. Writes are
 * suspending and run on `dispatchers.io`. Hostname-keyed (not full-URL-keyed)
 * because favicons are origin-scoped — every page on `example.com` shares
 * the same favicon. SHA-1 keeps file names safe characters and bounded length.
 *
 * Interface + impl split (vs. a concrete class) so JVM unit tests can wire
 * a [TempDirFaviconCache] over a `@TempFolder` without needing Android
 * `Context` / Robolectric.
 *
 * NO Room schema migration — favicons live entirely on disk; the
 * `TabEntity` schema (Spec 005) is unchanged. NO third-party dependency —
 * uses [Bitmap.compress] for write + (consumer-side)
 * [android.graphics.BitmapFactory] for read. NO LRU eviction in v1.0 —
 * favicon files are tiny (~1–10 KB each) and bounded by the number of
 * unique hosts the user visits; explicit eviction can be added later if
 * disk usage becomes a concern.
 */
interface FaviconCache {
    /**
     * Monotonically-increasing counter that bumps on every successful
     * [save]. Composables collect this to re-trigger composition after a
     * new favicon arrives.
     */
    val version: StateFlow<Long>

    /**
     * Persist [bitmap] as the favicon for the host derived from [url].
     * Caller supplies the URL string (full URL or just hostname); the
     * implementation extracts the hostname internally. Idempotent;
     * subsequent calls overwrite the existing file.
     */
    suspend fun save(url: String, bitmap: Bitmap)

    /**
     * Synchronous lookup: returns the [File] if a favicon for [url]'s host
     * is cached, else `null`. Composable-safe — at most a stat() syscall.
     */
    fun fileFor(url: String): File?

    /**
     * Spec 016 FR-029 — delete every cached site icon.
     *
     * Contract (mirrors `ScreenshotCache.clearAll`):
     *  - Runs on the IO dispatcher. Idempotent; a missing directory is not an error.
     *  - Bumps [version] afterwards, so every composable reading an icon re-evaluates and
     *    falls back to its placeholder until the icon is fetched again.
     *  - A file that cannot be deleted is logged and skipped rather than aborting the rest;
     *    the call returns normally, and the caller treats a thrown exception — not a partial
     *    deletion — as the category's failure signal.
     */
    suspend fun clearAll()
}

/**
 * Disk-backed [FaviconCache] using `Context.cacheDir/favicons/<sha1(host)>.png`.
 * Production binding via Hilt's [com.raumanian.thirtysix.browser.di.FaviconCacheModule].
 */
@Singleton
class DiskFaviconCache @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) : FaviconCache {

    private val cacheDir: File =
        File(context.cacheDir, AppConstants.FAVICON_CACHE_DIR_NAME).apply { mkdirs() }

    private val versionState: MutableStateFlow<Long> = MutableStateFlow(0L)

    override val version: StateFlow<Long> = versionState.asStateFlow()

    override suspend fun save(url: String, bitmap: Bitmap) = withContext(dispatchers.io) {
        val key = keyFor(url) ?: return@withContext
        val file = File(cacheDir, "$key.png")
        try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
            versionState.update { it + 1 }
        } catch (e: java.io.IOException) {
            // Disk full / permission denied — favicon falls back to the
            // deterministic placeholder; no crash, no retry.
            android.util.Log.w(LOG_TAG, "Failed to write favicon for $url", e)
        }
    }

    override fun fileFor(url: String): File? {
        val key = keyFor(url) ?: return null
        val file = File(cacheDir, "$key.png")
        return if (file.exists() && file.length() > 0L) file else null
    }

    override suspend fun clearAll() = withContext(dispatchers.io) {
        cacheDir.listFiles()?.forEach { file ->
            // The file name derives from a visited host, so it is deliberately not logged (§I).
            if (!file.delete()) android.util.Log.w(LOG_TAG, "Failed to delete a cached favicon")
        }
        versionState.update { it + 1 }
    }

    private fun keyFor(url: String): String? {
        val host = url.extractHostnameOrSelf().takeIf { it.isNotBlank() } ?: return null
        return sha1Hex(host)
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance(SHA1_ALGORITHM)
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PNG_QUALITY: Int = 100
        const val SHA1_ALGORITHM: String = "SHA-1"
        const val LOG_TAG: String = "DiskFaviconCache"
    }
}
