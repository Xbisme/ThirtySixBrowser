package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Servers often send no type or a generic binary one; the file name still says what it is. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidExternalFileOpenerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val opener = AndroidExternalFileOpener(app)

    private fun sentType(fileName: String, mimeType: String): String? {
        opener.open("content://downloads/10", mimeType, fileName)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        return intent.type
    }

    @Test
    fun `a generic binary type is replaced by the type of the extension`() {
        assertEquals("application/pdf", sentType("a.pdf", "application/octet-stream"))
    }

    @Test
    fun `an empty type is replaced by the type of the extension`() {
        assertEquals("application/pdf", sentType("a.pdf", ""))
    }

    @Test
    fun `an unknown extension offers any app`() {
        assertEquals("*/*", sentType("a.zzqx", "application/octet-stream"))
    }

    @Test
    fun `a specific stored type is kept`() {
        assertEquals("text/plain", sentType("a.pdf", "text/plain"))
    }
}
