package com.raumanian.thirtysix.browser.feature.bookmarks.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.BookmarkDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Folder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode.Link
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CRUD, cycle prevention, cascade delete, ancestor chains and star-toggle semantics. */
@RunWith(AndroidJUnit4::class)
class BookmarkRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BookmarkRepositoryImpl

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        repo = BookmarkRepositoryImpl(
            bookmarkDao = db.bookmarkDao(),
            folderDao = db.bookmarkFolderDao(),
            database = db,
            dispatchers = TestDispatcherProvider(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeRecentBookmarks returns the newest across folders`() = runTest {
        // The Home page's "Saved" shortcuts.
        repo.addBookmark(bookmark(url = "https://old.com", title = "Old", createdAt = 1L))
        repo.addBookmark(bookmark(url = "https://new.com", title = "New", createdAt = 2L))
        repo.observeRecentBookmarks(limit = 1).test {
            assertEquals(listOf("https://new.com"), awaitItem().map { it.url })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addBookmark returns Success with the new id`() = runTest {
        val result = repo.addBookmark(bookmark(url = "https://example.com", title = "Example"))
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data > 0L)
    }

    @Test
    fun `updateBookmark fails for missing id`() = runTest {
        val result = repo.updateBookmark(bookmark(id = 9_999L, url = "https://x.com", title = "x"))
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.BookmarkNotFound)
    }

    @Test
    fun `deleteBookmark fails for missing id`() = runTest {
        val result = repo.deleteBookmark(9_999L)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.BookmarkNotFound)
    }

    @Test
    fun `deleteMostRecentBookmarkByUrl removes only newest of three duplicates`() = runTest {
        val url = "https://multi.example.com"
        repo.addBookmark(bookmark(url = url, title = "old", createdAt = 100L))
        repo.addBookmark(bookmark(url = url, title = "mid", createdAt = 200L))
        repo.addBookmark(bookmark(url = url, title = "new", createdAt = 300L))

        val deleted = repo.deleteMostRecentBookmarkByUrl(url)
        assertEquals(Result.Success(1), deleted)
        assertEquals(2, repo.countBookmarksByUrl(url))
    }

    @Test
    fun `createFolder rejects unknown parent`() = runTest {
        val result = repo.createFolder(name = "Orphan", parentId = 9_999L)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderNotFound)
    }

    @Test
    fun `createFolder under valid parent succeeds`() = runTest {
        val rootId = (repo.createFolder("Work", null) as Result.Success).data
        val childId = (repo.createFolder("Project", rootId) as Result.Success).data
        assertNotNull(repo.getFolder(childId))
    }

    @Test
    fun `moveFolder rejects self-parent`() = runTest {
        val id = (repo.createFolder("A", null) as Result.Success).data
        val result = repo.moveFolder(id, id)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderCycle)
    }

    @Test
    fun `moveFolder rejects descendant as new parent`() = runTest {
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data

        val result = repo.moveFolder(a, c)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderCycle)
    }

    @Test
    fun `moveFolder accepts unrelated parent`() = runTest {
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", null) as Result.Success).data
        val result = repo.moveFolder(a, b)
        assertTrue(result is Result.Success)
        assertEquals(b, repo.getFolder(a)?.parentId)
    }

    @Test
    fun `getAncestorChain returns root-to-leaf for 3 levels`() = runTest {
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data

        val chain = repo.getAncestorChain(c)
        assertEquals(listOf("A", "B", "C"), chain.map { it.name })
    }

    @Test
    fun `getAncestorChain on missing folder is empty`() = runTest {
        val chain = repo.getAncestorChain(9_999L)
        assertTrue(chain.isEmpty())
    }

    @Test
    fun `deleteFolderCascade removes folder and all descendants atomically`() = runTest {
        // Tree: A → A/B → A/B/C; A has 1 bookmark, B has 2, C has 4.
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data
        repo.addBookmark(bookmark(url = "https://1.com", title = "a", parentFolderId = a))
        repo.addBookmark(bookmark(url = "https://2.com", title = "b1", parentFolderId = b))
        repo.addBookmark(bookmark(url = "https://3.com", title = "b2", parentFolderId = b))
        repeat(4) { i ->
            repo.addBookmark(bookmark(url = "https://c$i.com", title = "c$i", parentFolderId = c))
        }
        // Sibling at root must survive.
        val survivorId = (repo.addBookmark(bookmark(url = "https://root.com", title = "root")) as Result.Success).data

        val result = repo.deleteFolderCascade(a)
        assertTrue(result is Result.Success)
        val count = (result as Result.Success).data
        // 7 bookmarks deleted (1 + 2 + 4); 2 sub-folders deleted (B + C — A itself excluded).
        assertEquals(7, count.bookmarks)
        assertEquals(2, count.folders)

        assertNull(repo.getFolder(a))
        assertNull(repo.getFolder(b))
        assertNull(repo.getFolder(c))
        assertNotNull(repo.getBookmark(survivorId))
    }

    @Test
    fun `deleteFolderCascade fails for non-existent folder`() = runTest {
        val result = repo.deleteFolderCascade(9_999L)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).throwable is BookmarkException.FolderNotFound)
    }

    @Test
    fun `countDescendants on 4-level tree returns total recursive count`() = runTest {
        val a = (repo.createFolder("A", null) as Result.Success).data
        val b = (repo.createFolder("B", a) as Result.Success).data
        val c = (repo.createFolder("C", b) as Result.Success).data
        val d = (repo.createFolder("D", c) as Result.Success).data
        repo.addBookmark(bookmark(url = "https://x1.com", title = "x", parentFolderId = a))
        repo.addBookmark(bookmark(url = "https://x2.com", title = "x", parentFolderId = b))
        repo.addBookmark(bookmark(url = "https://x3.com", title = "x", parentFolderId = c))
        repo.addBookmark(bookmark(url = "https://x4.com", title = "x", parentFolderId = d))
        repo.addBookmark(bookmark(url = "https://x5.com", title = "x", parentFolderId = d))

        val count = repo.countDescendants(a)
        // a contains 1 bm + folder b (which contains b's tree: 1+1+2 bm + 2 sub-folders)
        // bookmarks: 1 + 1 + 1 + 2 = 5
        // folders: B + C + D = 3
        assertEquals(5, count.bookmarks)
        assertEquals(3, count.folders)
    }

    @Test
    fun `countDescendants on an empty folder is zero`() = runTest {
        val a = (repo.createFolder("A", null) as Result.Success).data

        val count = repo.countDescendants(a)
        assertEquals(0, count.bookmarks)
        assertEquals(0, count.folders)
    }

    @Test
    fun `deleteBookmark removes an existing bookmark`() = runTest {
        val id = (repo.addBookmark(bookmark(url = "https://x.com", title = "X")) as Result.Success).data

        assertTrue(repo.deleteBookmark(id) is Result.Success)
        assertNull(repo.getBookmark(id))
    }

    @Test
    fun `moveBookmarkToFolder updates the parent folder`() = runTest {
        val folderId = (repo.createFolder("F", null) as Result.Success).data
        val id = (repo.addBookmark(bookmark(url = "https://b.com", title = "B")) as Result.Success).data

        repo.moveBookmarkToFolder(id, folderId)

        assertEquals(folderId, repo.getBookmark(id)!!.parentFolderId)
    }

    @Test
    fun `importTree keeps nesting and link order under a new top-level folder`() = runTest {
        val tree = Folder(
            "",
            listOf(
                Link("Zeta", "https://z.example", null),
                Folder("Inner", listOf(Link("B", "https://b.example", null), Link("A", "https://a.example", null))),
                Link("Alpha", "https://a.example", null),
            ),
        )

        val outcome = (repo.importTree("Imported", tree) as Result.Success).data

        assertEquals("Imported", outcome.folderName)
        assertEquals(4, outcome.bookmarksAdded)
        assertEquals(2, outcome.foldersAdded)
        val root = repo.getBookmarkTree().children.single() as Folder
        assertEquals("Imported", root.name)
        assertEquals(
            listOf("Inner", "Zeta", "Alpha"),
            root.children.map { (it as? Folder)?.name ?: (it as Link).title },
        )
        assertEquals(listOf("B", "A"), (root.children.first() as Folder).children.map { (it as Link).title })
    }

    @Test
    fun `importTree adds a number when the folder name is taken`() = runTest {
        repo.createFolder("Imported", null)
        repo.importTree("Imported", Folder("", emptyList()))

        val outcome = (repo.importTree("Imported", Folder("", emptyList())) as Result.Success).data

        assertEquals("Imported (3)", outcome.folderName)
    }

    @Test
    fun `importTree writes nothing when an insert fails part-way`() = runTest {
        repo.addBookmark(bookmark(url = "https://kept.example", title = "Kept"))
        val failing = BookmarkRepositoryImpl(
            bookmarkDao = FailingOnThirdInsert(db.bookmarkDao()),
            folderDao = db.bookmarkFolderDao(),
            database = db,
            dispatchers = TestDispatcherProvider(),
        )
        val tree = Folder("", List(5) { Link("L$it", "https://l$it.example", null) })

        val result = failing.importTree("Imported", tree)

        assertTrue(result is Result.Error)
        assertEquals(1, repo.countBookmarks())
        assertEquals(0, repo.countFolders())
    }

    @Test
    fun `getBookmarkTree lists folders by name then links by sort order`() = runTest {
        val b = (repo.createFolder("b", null) as Result.Success).data
        repo.createFolder("a", null)
        repo.addBookmark(bookmark(url = "https://2.example", title = "2", createdAt = 2L))
        repo.addBookmark(bookmark(url = "https://1.example", title = "1", createdAt = 1L))
        repo.addBookmark(bookmark(url = "https://in.example", title = "in", parentFolderId = b))

        val tree = repo.getBookmarkTree()

        assertEquals(
            listOf("a", "b", "1", "2"),
            tree.children.map { (it as? Folder)?.name ?: (it as Link).title },
        )
        assertEquals("in", ((tree.children[1] as Folder).children.single() as Link).title)
        assertEquals(3, repo.countBookmarks())
        assertEquals(2, repo.countFolders())
        assertTrue(repo.observeAllFolders().first().isNotEmpty())
    }

    private class FailingOnThirdInsert(private val delegate: BookmarkDao) : BookmarkDao by delegate {
        private var inserts = 0

        override suspend fun insert(bookmark: BookmarkEntity): Long {
            if (++inserts == 3) throw IOException("disk full")
            return delegate.insert(bookmark)
        }
    }

    private fun bookmark(
        id: Long = 0L,
        url: String,
        title: String,
        parentFolderId: Long? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): Bookmark = Bookmark(
        id = id,
        title = title,
        url = url,
        parentFolderId = parentFolderId,
        createdAt = createdAt,
        sortOrder = createdAt,
    )

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
