package com.raumanian.thirtysix.browser.core.data.datasource.local.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** A icon that fails partway through its write must never be shown half-written. */
@RunWith(AndroidJUnit4::class)
class DiskFaviconCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a write that throws partway leaves no file and no temp file`() = runTest {
        val cache = DiskFaviconCache(context, TestDispatchers)
        cache.clearAll()

        cache.save("https://half.example/") { out ->
            out.write(ByteArray(PARTIAL_BYTES))
            throw IOException("disk full")
        }

        assertNull(cache.fileFor("https://half.example/"))
        val left = File(context.cacheDir, AppConstants.FAVICON_CACHE_DIR_NAME).list()?.toList()
        assertEquals(emptyList<String>(), left)
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        const val PARTIAL_BYTES = 16
    }
}
