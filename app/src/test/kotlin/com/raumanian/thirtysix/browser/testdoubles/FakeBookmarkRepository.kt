package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Spec 013 — in-memory fake honoring [BookmarkRepository] contract for use-case
 * and ViewModel tests. Mirrors [com.raumanian.thirtysix.browser.data.repository.BookmarkRepositoryImpl]
 * cycle prevention + cascade delete logic in pure Kotlin.
 *
 * Thread-safety: tests are single-threaded by construction; the in-memory
 * mutations are not synchronized.
 */
@Suppress("TooManyFunctions")
class FakeBookmarkRepository : BookmarkRepository {

    private val bookmarksFlow: MutableStateFlow<Map<Long, Bookmark>> = MutableStateFlow(emptyMap())
    private val foldersFlow: MutableStateFlow<Map<Long, BookmarkFolder>> = MutableStateFlow(emptyMap())
    private var nextBookmarkId: Long = 1L
    private var nextFolderId: Long = 1L

    fun seedBookmark(b: Bookmark): Long {
        val id = if (b.id == 0L) nextBookmarkId++ else b.id.also { if (it >= nextBookmarkId) nextBookmarkId = it + 1 }
        bookmarksFlow.value = bookmarksFlow.value + (id to b.copy(id = id))
        return id
    }

    fun seedFolder(f: BookmarkFolder): Long {
        val id = if (f.id == 0L) nextFolderId++ else f.id.also { if (it >= nextFolderId) nextFolderId = it + 1 }
        foldersFlow.value = foldersFlow.value + (id to f.copy(id = id))
        return id
    }

    override fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>> =
        bookmarksFlow.asStateFlow().map { map ->
            map.values
                .filter { it.parentFolderId == folderId }
                .sortedByDescending { it.createdAt }
        }

    override fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>> =
        bookmarksFlow.asStateFlow().map { map -> map.values.filter { it.url == url } }

    override fun searchBookmarks(query: String): Flow<List<Bookmark>> =
        bookmarksFlow.asStateFlow().map { map ->
            val q = query.lowercase()
            map.values
                .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getBookmark(id: Long): Bookmark? = bookmarksFlow.value[id]

    override suspend fun countBookmarksByUrl(url: String): Int =
        bookmarksFlow.value.values.count { it.url == url }

    override suspend fun addBookmark(bookmark: Bookmark): Result<Long> {
        val id = nextBookmarkId++
        bookmarksFlow.value = bookmarksFlow.value + (id to bookmark.copy(id = id))
        return Result.Success(id)
    }

    override suspend fun updateBookmark(bookmark: Bookmark): Result<Unit> {
        val existing = bookmarksFlow.value[bookmark.id]
            ?: return Result.Error(BookmarkException.BookmarkNotFound(bookmark.id))
        bookmarksFlow.value = bookmarksFlow.value + (existing.id to bookmark.copy(createdAt = existing.createdAt))
        return Result.Success(Unit)
    }

    override suspend fun deleteBookmark(id: Long): Result<Unit> {
        if (id !in bookmarksFlow.value) return Result.Error(BookmarkException.BookmarkNotFound(id))
        bookmarksFlow.value = bookmarksFlow.value - id
        return Result.Success(Unit)
    }

    override suspend fun deleteMostRecentBookmarkByUrl(url: String): Result<Int> {
        val matching = bookmarksFlow.value.values
            .filter { it.url == url }
            .sortedWith(compareByDescending<Bookmark> { it.createdAt }.thenByDescending { it.id })
        val target = matching.firstOrNull() ?: return Result.Success(0)
        bookmarksFlow.value = bookmarksFlow.value - target.id
        return Result.Success(1)
    }

    @Suppress("ReturnCount") // Three returns model the three guard branches.
    override suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?): Result<Unit> {
        val existing = bookmarksFlow.value[bookmarkId]
            ?: return Result.Error(BookmarkException.BookmarkNotFound(bookmarkId))
        if (newParentId != null && newParentId !in foldersFlow.value) {
            return Result.Error(BookmarkException.FolderNotFound(newParentId))
        }
        bookmarksFlow.value = bookmarksFlow.value + (bookmarkId to existing.copy(parentFolderId = newParentId))
        return Result.Success(Unit)
    }

    override fun observeFoldersByParent(parentId: Long?): Flow<List<BookmarkFolder>> =
        foldersFlow.asStateFlow().map { map ->
            map.values.filter { it.parentId == parentId }.sortedBy { it.name }
        }

    override fun observeAllFolders(): Flow<List<BookmarkFolder>> =
        foldersFlow.asStateFlow().map { map -> map.values.toList() }

    override suspend fun getFolder(id: Long): BookmarkFolder? = foldersFlow.value[id]

    override suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder> {
        val chain = ArrayDeque<BookmarkFolder>()
        var current: Long? = folderId
        val visited = HashSet<Long>()
        while (current != null && current !in visited) {
            visited.add(current)
            val folder = foldersFlow.value[current] ?: return emptyList()
            chain.addFirst(folder)
            current = folder.parentId
        }
        return chain.toList()
    }

    override suspend fun countDescendants(folderId: Long): BookmarkDescendantCount =
        countDescendantsRecursive(folderId)

    private fun countDescendantsRecursive(folderId: Long): BookmarkDescendantCount {
        var bookmarks = bookmarksFlow.value.values.count { it.parentFolderId == folderId }
        var folders = 0
        val children = foldersFlow.value.values.filter { it.parentId == folderId }
        folders += children.size
        for (child in children) {
            val sub = countDescendantsRecursive(child.id)
            bookmarks += sub.bookmarks
            folders += sub.folders
        }
        return BookmarkDescendantCount(bookmarks, folders)
    }

    override suspend fun createFolder(name: String, parentId: Long?): Result<Long> {
        if (parentId != null && parentId !in foldersFlow.value) {
            return Result.Error(BookmarkException.FolderNotFound(parentId))
        }
        val id = nextFolderId++
        val folder = BookmarkFolder(
            id = id,
            name = name,
            parentId = parentId,
            createdAt = System.currentTimeMillis(),
        )
        foldersFlow.value = foldersFlow.value + (id to folder)
        return Result.Success(id)
    }

    override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
        val existing = foldersFlow.value[folderId]
            ?: return Result.Error(BookmarkException.FolderNotFound(folderId))
        foldersFlow.value = foldersFlow.value + (folderId to existing.copy(name = newName))
        return Result.Success(Unit)
    }

    @Suppress("ReturnCount") // Multiple guards: self-parent, missing folder, missing new parent, cycle, success.
    override suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit> {
        if (folderId == newParentId) return Result.Error(BookmarkException.FolderCycle(folderId, newParentId))
        val existing = foldersFlow.value[folderId]
            ?: return Result.Error(BookmarkException.FolderNotFound(folderId))
        if (newParentId != null) {
            if (newParentId !in foldersFlow.value) {
                return Result.Error(BookmarkException.FolderNotFound(newParentId))
            }
            var cursor: Long? = newParentId
            val visited = HashSet<Long>()
            while (cursor != null && cursor !in visited) {
                visited.add(cursor)
                if (cursor == folderId) {
                    return Result.Error(BookmarkException.FolderCycle(folderId, newParentId))
                }
                cursor = foldersFlow.value[cursor]?.parentId
            }
        }
        foldersFlow.value = foldersFlow.value + (folderId to existing.copy(parentId = newParentId))
        return Result.Success(Unit)
    }

    override suspend fun deleteFolderCascade(folderId: Long): Result<BookmarkDescendantCount> {
        if (folderId !in foldersFlow.value) return Result.Error(BookmarkException.FolderNotFound(folderId))
        val count = countDescendantsRecursive(folderId)
        deleteSubtree(folderId)
        foldersFlow.value = foldersFlow.value - folderId
        return Result.Success(count)
    }

    private fun deleteSubtree(folderId: Long) {
        val children = foldersFlow.value.values.filter { it.parentId == folderId }.map { it.id }
        for (child in children) {
            deleteSubtree(child)
            foldersFlow.value = foldersFlow.value - child
        }
        val bookmarkIdsUnderFolder = bookmarksFlow.value.values
            .filter { it.parentFolderId == folderId }
            .map { it.id }
        bookmarksFlow.value = bookmarksFlow.value - bookmarkIdsUnderFolder.toSet()
    }
}
