package com.raumanian.thirtysix.browser.feature.bookmarks.data.repository

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.BookmarkFileDataSource
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkWriter
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkTransferLimits
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.BookmarkFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkFileRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val repository = BookmarkFileRepositoryImpl(
        files = BookmarkFileDataSource(ApplicationProvider.getApplicationContext(), Unconfined),
        parser = NetscapeBookmarkParser(),
        writer = NetscapeBookmarkWriter(),
        dispatchers = Unconfined,
    )

    private fun fileWith(bytes: ByteArray): String = Uri.fromFile(temp.newFile().apply { writeBytes(bytes) }).toString()

    @Test
    fun `reads and parses a bookmark file`() = runTest {
        val result = repository.read(fileWith(BookmarkFiles.bytes("chrome.html")), "untitled")

        assertEquals(4, (result as BookmarkFileReadResult.Parsed).file.linkCount)
    }

    @Test
    fun `a binary file is not a bookmark file`() = runTest {
        assertEquals(
            BookmarkFileReadResult.NotABookmarkFile,
            repository.read(fileWith(BookmarkFiles.bytes("photo.jpg")), "untitled"),
        )
    }

    @Test
    fun `a file over the size cap is refused`() = runTest {
        val big = ByteArray((BookmarkTransferLimits.MAX_IMPORT_FILE_BYTES + 1).toInt()) { ' '.code.toByte() }

        assertEquals(BookmarkFileReadResult.TooLarge, repository.read(fileWith(big), "untitled"))
    }

    @Test
    fun `a missing file fails to read`() = runTest {
        val gone = Uri.fromFile(File(temp.root, "gone.html")).toString()

        assertEquals(BookmarkFileReadResult.ReadFailed, repository.read(gone, "untitled"))
    }

    @Test
    fun `writes a file that reads back`() = runTest {
        val target = fileWith("old content that must be replaced entirely".toByteArray())
        val tree = Folder("", listOf(Link("A", "https://a.example/", null)))

        assertTrue(repository.write(target, tree))

        assertEquals(tree, (repository.read(target, "untitled") as BookmarkFileReadResult.Parsed).file.root)
    }

    @Test
    fun `writing to an unreachable location fails`() = runTest {
        val unreachable = Uri.fromFile(File(temp.root, "missing-dir/out.html")).toString()

        assertFalse(repository.write(unreachable, Folder("", emptyList())))
    }

    private object Unconfined : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
