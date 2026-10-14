package com.raumanian.thirtysix.browser.data.local.cache

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 024 T018 / FR-008 — Fire keeps only the icons of bookmarked and fireproof hosts. */
@RunWith(AndroidJUnit4::class)
class DiskFaviconCacheRetainTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `retainOnly deletes every icon except those of the kept hosts`() = runTest {
        val cache = DiskFaviconCache(context, TestDispatchers)
        cache.clearAll()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        cache.save("https://bookmarked.example/page", bitmap)
        cache.save("https://mail.fireproof.example/", bitmap)
        cache.save("https://visited.test/", bitmap)
        val before = cache.version.value

        cache.retainOnly(setOf("bookmarked.example", "mail.fireproof.example"))

        assertNotNull(cache.fileFor("https://bookmarked.example/"))
        assertNotNull(cache.fileFor("mail.fireproof.example"))
        assertNull(cache.fileFor("https://visited.test/"))
        assertTrue(cache.version.value > before)
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
