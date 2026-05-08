package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CountFolderDescendantsUseCaseTest {

    @Test
    fun `counts recursive descendants on 4-level tree`() = runTest {
        val repo = FakeBookmarkRepository()
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data
        val d = (repo.createFolder("D", c) as Result.Success).data
        repo.seedBookmark(Bookmark(0, "1", "https://1.com", a, 1L, 1L))
        repo.seedBookmark(Bookmark(0, "2", "https://2.com", b, 2L, 2L))
        repo.seedBookmark(Bookmark(0, "3", "https://3.com", c, 3L, 3L))
        repo.seedBookmark(Bookmark(0, "4", "https://4.com", d, 4L, 4L))
        repo.seedBookmark(Bookmark(0, "5", "https://5.com", d, 5L, 5L))

        val count = CountFolderDescendantsUseCase(repo)(a)
        // a has: 1 bm + folder b (which has 1 bm + folder c (1 bm + folder d (2 bms)))
        // bookmarks under a: 1 + 1 + 1 + 2 = 5
        // folders under a: B + C + D = 3
        assertEquals(5, count.bookmarks)
        assertEquals(3, count.folders)
    }

    @Test
    fun `empty folder returns zero counts`() = runTest {
        val repo = FakeBookmarkRepository()
        val a = (repo.createFolder("A", null) as Result.Success).data

        val count = CountFolderDescendantsUseCase(repo)(a)
        assertEquals(0, count.bookmarks)
        assertEquals(0, count.folders)
    }
}
