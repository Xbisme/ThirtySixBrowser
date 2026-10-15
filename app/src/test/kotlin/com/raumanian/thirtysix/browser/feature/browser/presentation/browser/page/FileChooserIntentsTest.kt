package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The picker, the camera offer and the answer handed to the page. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FileChooserIntentsTest {

    private val photo = Uri.parse("content://app.fileprovider/capture/a.jpg")

    private fun request(vararg accept: String, multiple: Boolean = false, capture: Boolean = false) =
        FileChooserRequest(accept.toList(), multiple, capture)

    @Test
    fun `picker opens any file when the page accepts anything`() {
        val intent = FileChooserIntents.picker(request(""))
        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("*/*", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(intent.hasExtra(Intent.EXTRA_ALLOW_MULTIPLE))
    }

    @Test
    fun `picker honours one accept type and multiple selection`() {
        val intent = FileChooserIntents.picker(request("image/*", multiple = true))
        assertEquals("image/*", intent.type)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test
    fun `picker lists several accept types, extensions included`() {
        val intent = FileChooserIntents.picker(request("image/png,.pdf"))
        assertEquals("*/*", intent.type)
        assertArrayEquals(arrayOf("image/png", "application/pdf"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun `camera is offered for capture requests and media-only inputs`() {
        assertTrue(FileChooserIntents.wantsCapture(request("", capture = true)))
        assertTrue(FileChooserIntents.wantsCapture(request("image/*")))
        assertTrue(FileChooserIntents.wantsCapture(request("image/*", "video/mp4")))
        assertFalse(FileChooserIntents.wantsCapture(request("")))
        assertFalse(FileChooserIntents.wantsCapture(request("image/*", ".pdf")))
    }

    @Test
    fun `capture intents follow the accepted media`() {
        val photoOnly = FileChooserIntents.captures(request("image/*"), photo)
        assertEquals(listOf(MediaStore.ACTION_IMAGE_CAPTURE), photoOnly.map { it.action })
        assertEquals(photo, photoOnly.single().getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT))

        val both = FileChooserIntents.captures(request("", capture = true), photo)
        assertEquals(
            listOf(MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_VIDEO_CAPTURE),
            both.map { it.action },
        )
    }

    @Test
    fun `without capture the picker is launched alone`() {
        val picker = FileChooserIntents.picker(request(""))
        assertEquals(picker, FileChooserIntents.chooser(picker, emptyList(), "Choose"))
        val captures = FileChooserIntents.captures(request("image/*"), photo)
        val chooser = FileChooserIntents.chooser(picker, captures, "Choose")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    }

    @Test
    fun `cancel hands the page nothing`() {
        assertNull(FileChooserIntents.result(Activity.RESULT_CANCELED, null, photo, photoWritten = true))
        assertNull(FileChooserIntents.result(Activity.RESULT_OK, null, photo, photoWritten = false))
    }

    @Test
    fun `picked files are handed over, several included`() {
        val a = Uri.parse("content://docs/a")
        val b = Uri.parse("content://docs/b")
        val single = FileChooserIntents.result(Activity.RESULT_OK, Intent().setData(a), photo, false)
        assertArrayEquals(arrayOf(a), single)

        val data = Intent().apply {
            clipData = ClipData.newRawUri("", a).apply { addItem(ClipData.Item(b)) }
        }
        assertArrayEquals(arrayOf(a, b), FileChooserIntents.result(Activity.RESULT_OK, data, photo, false))
    }

    @Test
    fun `a written photo is handed over when the picker returned nothing`() {
        val result = FileChooserIntents.result(Activity.RESULT_OK, null, photo, photoWritten = true)
        assertArrayEquals(arrayOf(photo), result)
    }
}
