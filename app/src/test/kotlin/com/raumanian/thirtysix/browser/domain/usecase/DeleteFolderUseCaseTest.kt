package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteFolderUseCaseTest {

    @Test
    fun `cascade-deletes 3-level tree atomically`() = runTest {
        val repo = FakeBookmarkRepository()
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data
        repo.seedBookmark(Bookmark(0, "1", "https://1.com", a, 1L, 1L))
        repo.seedBookmark(Bookmark(0, "2", "https://2.com", b, 2L, 2L))
        repo.seedBookmark(Bookmark(0, "3", "https://3.com", c, 3L, 3L))
        // Sibling at root must survive.
        val survivor = repo.seedBookmark(Bookmark(0, "root", "https://root.com", null, 4L, 4L))

        val result = DeleteFolderUseCase(repo)(a)
        assertTrue(result is Result.Success)
        val count = (result as Result.Success).data
        assertEquals(3, count.bookmarks)
        assertEquals(2, count.folders)
        assertNull(repo.getFolder(a))
        assertNull(repo.getFolder(b))
        assertNull(repo.getFolder(c))
        assertEquals("root", repo.getBookmark(survivor)?.title)
    }

    @Test
    fun `non-existent folder returns FolderNotFound`() = runTest {
        val repo = FakeBookmarkRepository()
        val result = DeleteFolderUseCase(repo)(99_999L)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderNotFound)
    }
}
