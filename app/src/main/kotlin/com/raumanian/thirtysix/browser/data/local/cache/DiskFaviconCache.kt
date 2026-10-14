package com.raumanian.thirtysix.browser.data.local.cache

import android.content.Context
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

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

    override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = withContext(dispatchers.io) {
        val key = keyFor(url) ?: return@withContext
        val file = File(cacheDir, "$key.png")
        try {
            file.outputStream().use(writeImage)
            versionState.update { it + 1 }
        } catch (e: java.io.IOException) {
            // Disk full / permission denied — favicon falls back to the
            // deterministic placeholder; no crash, no retry.
            android.util.Log.w(LOG_TAG, "Failed to write a favicon", e) // Spec 021 FR-036: no address in logs.
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

    override suspend fun retainOnly(hosts: Set<String>) = withContext(dispatchers.io) {
        val keep = hosts.mapNotNullTo(HashSet()) { host -> keyFor(host)?.let { "$it.png" } }
        cacheDir.listFiles()?.forEach { file ->
            // As in clearAll: the name derives from a visited host and is never logged (§I).
            if (file.name !in keep && !file.delete()) android.util.Log.w(LOG_TAG, "Failed to delete a cached favicon")
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
        const val SHA1_ALGORITHM: String = "SHA-1"
        const val LOG_TAG: String = "DiskFaviconCache"
    }
}
