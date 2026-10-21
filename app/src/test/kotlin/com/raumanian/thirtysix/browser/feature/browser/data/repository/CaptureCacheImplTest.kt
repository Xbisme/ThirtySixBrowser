package com.raumanian.thirtysix.browser.feature.browser.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureCacheImplTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `clear deletes the photos in the file chooser's folder`() = runTest {
        // Not CaptureFiles.newPhoto: FileProvider caches its roots across Robolectric's per-test apps.
        val folder = File(app.cacheDir, AppConstants.CAPTURE_CACHE_DIR).apply { mkdirs() }
        val photo = File(folder, "capture-1.jpg").apply { writeText("x") }

        CaptureCacheImpl(app, TestDispatchers).clear()

        assertFalse(photo.exists())
    }

    @Test
    fun `clear with no photo folder does nothing`() = runTest {
        CaptureCacheImpl(app, TestDispatchers).clear()
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
