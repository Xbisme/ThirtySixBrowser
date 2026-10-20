package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A page may still be uploading an earlier photo when the next one is taken. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureFilesTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a second photo keeps the first, and clear removes both`() {
        val first = checkNotNull(CaptureFiles.newPhoto(app)).first
        val second = checkNotNull(CaptureFiles.newPhoto(app)).first

        assertTrue("the first photo was deleted by the second", first.exists())
        assertTrue(second.exists())

        CaptureFiles.clear(app)

        assertFalse(first.exists())
        assertFalse(second.exists())
    }
}
