package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 013 — covers CreateFolder / RenameFolder / MoveFolder / MoveBookmark
 * / UpdateBookmark use cases via the in-memory fake.
 */
class FolderUseCasesTest {

    @Test
    fun `CreateFolder rejects blank name`() = runTest {
        val repo = FakeBookmarkRepository()
        val result = CreateFolderUseCase(repo)("   ", null)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `CreateFolder caps length to MAX_FOLDER_NAME_LENGTH`() = runTest {
        val repo = FakeBookmarkRepository()
        val longName = "x".repeat(500)
        val result = CreateFolderUseCase(repo)(longName, null)
        val id = (result as Result.Success).data
        val folder = repo.getFolder(id)!!
        assertTrue(folder.name.length <= 100)
    }

    @Test
    fun `RenameFolder updates the name`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = (CreateFolderUseCase(repo)("Old", null) as Result.Success).data
        RenameFolderUseCase(repo)(id, "New")
        assertEquals("New", repo.getFolder(id)!!.name)
    }

    @Test
    fun `MoveFolder rejects cycle (folder into descendant)`() = runTest {
        val repo = FakeBookmarkRepository()
        val a = (CreateFolderUseCase(repo)("A", null) as Result.Success).data
        val b = (CreateFolderUseCase(repo)("B", a) as Result.Success).data
        val result = MoveFolderUseCase(repo)(a, b)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderCycle)
    }

    @Test
    fun `MoveBookmark updates parent folder`() = runTest {
        val repo = FakeBookmarkRepository()
        val folderId = (CreateFolderUseCase(repo)("F", null) as Result.Success).data
        val bookmarkId = repo.seedBookmark(Bookmark(0, "B", "https://b.com", null, 1L, 1L))

        MoveBookmarkUseCase(repo)(bookmarkId, folderId)

        assertEquals(folderId, repo.getBookmark(bookmarkId)!!.parentFolderId)
    }

    @Test
    fun `UpdateBookmark validates URL and trims title`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "old", "https://old.com", null, 1L, 1L))

        val result = UpdateBookmarkUseCase(repo)(
            bookmarkId = id,
            title = "  New title  ",
            url = "https://new.com",
            parentFolderId = null,
        )
        assertTrue(result is Result.Success)
        val updated = repo.getBookmark(id)!!
        assertEquals("New title", updated.title)
        assertEquals("https://new.com", updated.url)
    }

    @Test
    fun `UpdateBookmark rejects malformed URL`() = runTest {
        val repo = FakeBookmarkRepository()
        val id = repo.seedBookmark(Bookmark(0, "x", "https://x.com", null, 1L, 1L))

        val result = UpdateBookmarkUseCase(repo)(
            bookmarkId = id,
            title = "x",
            url = "ftp://bad.com",
            parentFolderId = null,
        )
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.UrlValidation)
    }
}
