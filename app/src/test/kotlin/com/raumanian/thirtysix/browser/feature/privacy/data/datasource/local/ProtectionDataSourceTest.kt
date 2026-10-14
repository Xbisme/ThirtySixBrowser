package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 023 T026 — loading the bundled lists once, off the caller's thread, failing open. */
@RunWith(AndroidJUnit4::class)
class ProtectionDataSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `awaitData loads the assets once`() {
        val source = ProtectionDataSource(context, IoDispatchers)
        assertNull(source.currentDataOrNull())

        val first = source.awaitData(TIMEOUT_MS)
        source.preload()
        val second = source.awaitData(TIMEOUT_MS)

        assertNotNull(first)
        assertSame(first, second)
        assertSame(first, source.currentDataOrNull())
    }

    @Test
    fun `a broken asset fails open`() {
        val broken = object : android.content.ContextWrapper(context) {
            override fun getAssets(): android.content.res.AssetManager = error("no assets")
        }
        val source = ProtectionDataSource(broken, IoDispatchers)

        assertNull(source.awaitData(TIMEOUT_MS))
        assertNull(source.currentDataOrNull())
    }

    private object IoDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
