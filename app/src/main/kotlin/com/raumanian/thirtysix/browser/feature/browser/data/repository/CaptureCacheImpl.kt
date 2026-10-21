package com.raumanian.thirtysix.browser.feature.browser.data.repository

import android.content.Context
import com.raumanian.thirtysix.browser.core.domain.repository.CaptureCache
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** The same folder the page's file chooser writes camera photos to. */
class CaptureCacheImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CaptureCache {

    override suspend fun clear() = withContext(dispatchers.io) {
        File(context.cacheDir, AppConstants.CAPTURE_CACHE_DIR).listFiles()?.forEach(File::delete)
        Unit
    }
}
