package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.fake.FakeBookmarkFileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBookmarksUseCaseTest {

    private val bookmarks = FakeBookmarkRepository()
    private val files = FakeBookmarkFileRepository()
    private val exportBookmarks = ExportBookmarksUseCase(bookmarks, files)

    @Test
    fun `exports every bookmark and folder`() = runTest {
        val outer = bookmarks.seedFolder(BookmarkFolder(0L, "Outer", null, 0L))
        val inner = bookmarks.seedFolder(BookmarkFolder(0L, "Inner", outer, 0L))
        bookmarks.seedBookmark(Bookmark(0L, "A", "https://a.example/", inner, 0L, 0L))
        bookmarks.seedBookmark(Bookmark(0L, "B", "https://b.example/", null, 0L, 0L))

        val result = exportBookmarks("content://out")

        assertEquals(BookmarkExportResult.Exported(bookmarks = 2, folders = 2), result)
        assertTrue("content://out" in files.written)
    }

    @Test
    fun `an empty library has nothing to export and writes nothing`() = runTest {
        assertEquals(BookmarkExportResult.NothingToExport, exportBookmarks("content://out"))
        assertTrue(files.written.isEmpty())
    }

    @Test
    fun `an empty folder alone is still exported`() = runTest {
        bookmarks.seedFolder(BookmarkFolder(0L, "Empty", null, 0L))

        assertEquals(BookmarkExportResult.Exported(bookmarks = 0, folders = 1), exportBookmarks("content://out"))
    }

    @Test
    fun `a write failure is reported`() = runTest {
        bookmarks.seedBookmark(Bookmark(0L, "B", "https://b.example/", null, 0L, 0L))
        files.writeSucceeds = false

        assertEquals(BookmarkExportResult.WriteFailed, exportBookmarks("content://out"))
    }
}
