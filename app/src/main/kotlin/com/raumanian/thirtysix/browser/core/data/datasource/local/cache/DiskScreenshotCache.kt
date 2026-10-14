package com.raumanian.thirtysix.browser.core.data.datasource.local.cache

import android.content.Context
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class DiskScreenshotCache @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) : ScreenshotCache {

    private val cacheDir: File =
        File(context.cacheDir, AppConstants.SCREENSHOT_CACHE_DIR_NAME).apply { mkdirs() }

    private val versionState: MutableStateFlow<Long> = MutableStateFlow(0L)

    override val version: StateFlow<Long> = versionState.asStateFlow()

    override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = withContext(dispatchers.io) {
        if (tabId <= 0L) return@withContext
        val file = fileForId(tabId)
        try {
            file.outputStream().use(writeImage)
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
        const val LOG_TAG: String = "DiskScreenshotCache"
    }
}
