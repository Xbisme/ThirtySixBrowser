package com.raumanian.thirtysix.browser.data.repository

import androidx.room.withTransaction
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkDao
import com.raumanian.thirtysix.browser.data.local.dao.BookmarkFolderDao
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase
import com.raumanian.thirtysix.browser.data.mapper.BookmarkFolderMapper
import com.raumanian.thirtysix.browser.data.mapper.BookmarkMapper
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Spec 013 — single repository for bookmarks AND folders. See research.md R1
 * for the combined-repo justification (cascade transaction lives cleanly in
 * one repo without violating Constitution §IV).
 *
 * Cascade delete (R7) and folder cycle prevention (R6) are implemented here.
 * The [database] dependency is required for `withTransaction { ... }`.
 */
@Singleton
@Suppress("TooManyFunctions")
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: BookmarkFolderDao,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : BookmarkRepository {

    // ──────── Bookmark queries ────────

    override fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>> =
        bookmarkDao.observeByFolder(folderId).map { list -> list.map(BookmarkMapper::toDomain) }

    override fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>> =
        bookmarkDao.observeByUrl(url).map { list -> list.map(BookmarkMapper::toDomain) }

    override fun searchBookmarks(query: String): Flow<List<Bookmark>> =
        bookmarkDao.searchByTitleOrUrl(query).map { list -> list.map(BookmarkMapper::toDomain) }

    override suspend fun getBookmark(id: Long): Bookmark? =
        withContext(dispatchers.io) { bookmarkDao.getById(id)?.let(BookmarkMapper::toDomain) }

    override suspend fun countBookmarksByUrl(url: String): Int =
        withContext(dispatchers.io) { bookmarkDao.countByUrl(url) }

    // ──────── Bookmark mutations ────────

    override suspend fun addBookmark(bookmark: Bookmark): Result<Long> =
        runCatchingResult {
            bookmarkDao.insert(BookmarkMapper.toEntity(bookmark))
        }

    override suspend fun updateBookmark(bookmark: Bookmark): Result<Unit> =
        runCatchingResult {
            val existing = bookmarkDao.getById(bookmark.id)
                ?: throw BookmarkException.BookmarkNotFound(bookmark.id)
            bookmarkDao.update(BookmarkMapper.toEntity(bookmark.copy(createdAt = existing.createdAt)))
        }

    override suspend fun deleteBookmark(id: Long): Result<Unit> =
        runCatchingResult {
            val existing = bookmarkDao.getById(id) ?: throw BookmarkException.BookmarkNotFound(id)
            bookmarkDao.delete(existing)
        }

    override suspend fun deleteMostRecentBookmarkByUrl(url: String): Result<Int> =
        runCatchingResult { bookmarkDao.deleteMostRecentByUrl(url) }

    override suspend fun moveBookmarkToFolder(
        bookmarkId: Long,
        newParentId: Long?,
    ): Result<Unit> =
        runCatchingResult {
            val existing = bookmarkDao.getById(bookmarkId)
                ?: throw BookmarkException.BookmarkNotFound(bookmarkId)
            if (newParentId != null) {
                folderDao.getById(newParentId)
                    ?: throw BookmarkException.FolderNotFound(newParentId)
            }
            bookmarkDao.update(existing.copy(parentFolderId = newParentId))
        }

    // ──────── Folder queries ────────

    override fun observeFoldersByParent(parentId: Long?): Flow<List<BookmarkFolder>> =
        folderDao.observeChildren(parentId).map { list -> list.map(BookmarkFolderMapper::toDomain) }

    override fun observeAllFolders(): Flow<List<BookmarkFolder>> =
        folderDao.observeAll().map { list -> list.map(BookmarkFolderMapper::toDomain) }

    override suspend fun getFolder(id: Long): BookmarkFolder? =
        withContext(dispatchers.io) { folderDao.getById(id)?.let(BookmarkFolderMapper::toDomain) }

    override suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder> =
        withContext(dispatchers.io) {
            val chain = ArrayDeque<BookmarkFolder>()
            var current: Long? = folderId
            val visited = HashSet<Long>()
            while (current != null && current !in visited) {
                visited.add(current)
                val entity = folderDao.getById(current) ?: return@withContext emptyList()
                val domain = BookmarkFolderMapper.toDomain(entity)
                chain.addFirst(domain)
                current = entity.parentId
            }
            chain.toList()
        }

    override suspend fun countDescendants(folderId: Long): BookmarkDescendantCount =
        withContext(dispatchers.io) { countDescendantsRecursive(folderId) }

    private suspend fun countDescendantsRecursive(folderId: Long): BookmarkDescendantCount {
        var bookmarks = bookmarkDao.countByFolder(folderId)
        var folders = 0
        val children = folderDao.getDirectChildren(folderId)
        folders += children.size
        for (child in children) {
            val sub = countDescendantsRecursive(child.id)
            bookmarks += sub.bookmarks
            folders += sub.folders
        }
        return BookmarkDescendantCount(bookmarks = bookmarks, folders = folders)
    }

    // ──────── Folder mutations ────────

    override suspend fun createFolder(name: String, parentId: Long?): Result<Long> =
        runCatchingResult {
            if (parentId != null) {
                folderDao.getById(parentId)
                    ?: throw BookmarkException.FolderNotFound(parentId)
            }
            val now = System.currentTimeMillis()
            folderDao.insert(
                com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity(
                    name = name,
                    parentId = parentId,
                    createdAt = now,
                ),
            )
        }

    override suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> =
        runCatchingResult {
            val existing = folderDao.getById(folderId)
                ?: throw BookmarkException.FolderNotFound(folderId)
            folderDao.update(existing.copy(name = newName))
        }

    override suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit> =
        runCatchingResult {
            if (folderId == newParentId) {
                throw BookmarkException.FolderCycle(folderId, newParentId)
            }
            val existing = folderDao.getById(folderId)
                ?: throw BookmarkException.FolderNotFound(folderId)
            if (newParentId != null) {
                folderDao.getById(newParentId)
                    ?: throw BookmarkException.FolderNotFound(newParentId)
                // Walk newParentId's ancestor chain — if folderId appears, reject (R6).
                var cursor: Long? = newParentId
                val visited = HashSet<Long>()
                while (cursor != null && cursor !in visited) {
                    visited.add(cursor)
                    if (cursor == folderId) {
                        throw BookmarkException.FolderCycle(folderId, newParentId)
                    }
                    cursor = folderDao.getById(cursor)?.parentId
                }
            }
            folderDao.update(existing.copy(parentId = newParentId))
        }

    override suspend fun deleteFolderCascade(folderId: Long): Result<BookmarkDescendantCount> =
        runCatchingResult {
            val target = folderDao.getById(folderId)
                ?: throw BookmarkException.FolderNotFound(folderId)
            val count = countDescendantsRecursive(folderId)
            database.withTransaction {
                deleteSubtreeDepthFirst(folderId)
                folderDao.deleteById(target.id)
            }
            count
        }

    private suspend fun deleteSubtreeDepthFirst(folderId: Long) {
        val children = folderDao.getDirectChildren(folderId)
        for (child in children) {
            deleteSubtreeDepthFirst(child.id)
            folderDao.deleteById(child.id)
        }
        // Delete leaf bookmarks BEFORE the parent folder row is removed; the
        // FK rule is SET NULL (Spec 005), so deleting the folder first would
        // orphan the bookmarks at root rather than removing them.
        bookmarkDao.deleteByParentFolder(folderId)
    }

    private suspend fun <T> runCatchingResult(block: suspend () -> T): Result<T> =
        withContext(dispatchers.io) {
            try {
                Result.Success(block())
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
                Result.Error(throwable)
            }
        }
}
