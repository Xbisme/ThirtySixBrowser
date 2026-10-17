package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.ParsedBookmarkFile
import com.raumanian.thirtysix.browser.feature.bookmarks.fake.FakeBookmarkFileRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.BookmarkFiles
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportBookmarksUseCaseTest {

    private val files = FakeBookmarkFileRepository()
    private val bookmarks = FakeBookmarkRepository()
    private val importBookmarks = ImportBookmarksUseCase(files, bookmarks)
    private val names = BookmarkImportNames(folderBaseName = "Imported 17 Oct 2026", untitledFolderName = "Untitled")

    private fun parsed(fixture: String): BookmarkFileReadResult.Parsed =
        BookmarkFileReadResult.Parsed(NetscapeBookmarkParser().parse(BookmarkFiles.read(fixture), "Untitled")!!)

    private fun links(count: Int) = ParsedBookmarkFile(
        root = Folder("", List(count) { Link("L$it", "https://l$it.example/", null) }),
        linkCount = count,
        folderCount = 0,
        duplicatesSkipped = 0,
        unsupportedSkipped = 0,
    )

    @Test
    fun `imports under the dated folder and reports the counts`() = runTest {
        files.readResult = parsed("chrome.html")

        val result = importBookmarks("content://file", names) as BookmarkImportResult.Imported

        assertEquals("Imported 17 Oct 2026", result.folderName)
        assertEquals(listOf(4, 1, 2), listOf(result.imported, result.duplicatesSkipped, result.unsupportedSkipped))
        assertEquals(4, bookmarks.countBookmarks())
        assertEquals(4, bookmarks.countFolders())
    }

    @Test
    fun `read failures pass through and nothing is written`() = runTest {
        val cases = mapOf(
            BookmarkFileReadResult.NotABookmarkFile to BookmarkImportResult.NotABookmarkFile,
            BookmarkFileReadResult.TooLarge to BookmarkImportResult.TooLarge,
            BookmarkFileReadResult.ReadFailed to BookmarkImportResult.ReadFailed,
        )
        cases.forEach { (read, expected) ->
            files.readResult = read
            assertEquals(expected, importBookmarks("content://file", names))
        }
        assertEquals(0, bookmarks.importCalls)
    }

    @Test
    fun `a file with no links imports nothing and creates no folder`() = runTest {
        files.readResult = parsed("no-links.html")

        assertEquals(BookmarkImportResult.NothingToImport, importBookmarks("content://file", names))
        assertEquals(0, bookmarks.countFolders())
    }

    @Test
    fun `an import that would pass the bookmark limit is refused`() = runTest {
        repeat(10) { bookmarks.seedBookmark(Bookmark(0L, "b$it", "https://b$it.example/", null, 0L, 0L)) }
        files.readResult = BookmarkFileReadResult.Parsed(links(BrowserLimits.MAX_BOOKMARKS - 9))

        assertEquals(BookmarkImportResult.TooLarge, importBookmarks("content://file", names))
        assertEquals(0, bookmarks.importCalls)
    }

    @Test
    fun `an import exactly at the bookmark limit goes through`() = runTest {
        repeat(10) { bookmarks.seedBookmark(Bookmark(0L, "b$it", "https://b$it.example/", null, 0L, 0L)) }
        files.readResult = BookmarkFileReadResult.Parsed(links(BrowserLimits.MAX_BOOKMARKS - 10))

        assertEquals(true, importBookmarks("content://file", names) is BookmarkImportResult.Imported)
    }

    @Test
    fun `an import that would pass the folder limit is refused`() = runTest {
        repeat(BrowserLimits.MAX_FOLDERS - 1) { bookmarks.seedFolder(BookmarkFolder(0L, "f$it", null, 0L)) }
        files.readResult = BookmarkFileReadResult.Parsed(links(1).copy(folderCount = 1))

        assertEquals(BookmarkImportResult.TooLarge, importBookmarks("content://file", names))
    }

    @Test
    fun `a failed write reports WriteFailed`() = runTest {
        files.readResult = parsed("chrome.html")
        bookmarks.importFailure = IOException("disk full")

        assertEquals(BookmarkImportResult.WriteFailed, importBookmarks("content://file", names))
    }

    @Test
    fun `a 100-level file imports with its deep links kept`() = runTest {
        files.readResult = parsed("deep-100.html")

        val result = importBookmarks("content://file", names) as BookmarkImportResult.Imported

        assertEquals(100, result.imported)
    }
}
