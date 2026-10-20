package com.raumanian.thirtysix.browser.feature.bookmarks.data.repository

import androidx.room.withTransaction
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.BookmarkDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.BookmarkFolderDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.model.ImportOutcome
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.bookmarks.data.mapper.BookmarkFolderMapper
import com.raumanian.thirtysix.browser.feature.bookmarks.data.mapper.BookmarkMapper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * One repository for bookmarks and folders, so the cascade delete runs in a single transaction.
 * Also rejects folder moves that would create a cycle.
 */
@Singleton
@Suppress("TooManyFunctions")
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: BookmarkFolderDao,
    private val database: AppDatabase,
    private val dispatchers: DispatcherProvider,
) : BookmarkRepository {

    override fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>> =
        bookmarkDao.observeByFolder(folderId).map { list -> list.map(BookmarkMapper::toDomain) }

    override fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>> =
        bookmarkDao.observeByUrl(url).map { list -> list.map(BookmarkMapper::toDomain) }

    override fun observeRecentBookmarks(limit: Int): Flow<List<Bookmark>> =
        bookmarkDao.observeRecent(limit).map { list -> list.map(BookmarkMapper::toDomain) }

    override fun searchBookmarks(query: String, limit: Int): Flow<List<Bookmark>> =
        bookmarkDao.searchByTitleOrUrl(query, limit).map { list -> list.map(BookmarkMapper::toDomain) }

    override suspend fun getAllBookmarks(): List<Bookmark> =
        withContext(dispatchers.io) { bookmarkDao.getAllNewestFirst().map(BookmarkMapper::toDomain) }

    override suspend fun getBookmark(id: Long): Bookmark? =
        withContext(dispatchers.io) { bookmarkDao.getById(id)?.let(BookmarkMapper::toDomain) }

    override suspend fun countBookmarksByUrl(url: String): Int =
        withContext(dispatchers.io) { bookmarkDao.countByUrl(url) }

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

    override suspend fun createFolder(name: String, parentId: Long?): Result<Long> =
        runCatchingResult {
            if (parentId != null) {
                folderDao.getById(parentId)
                    ?: throw BookmarkException.FolderNotFound(parentId)
            }
            val now = System.currentTimeMillis()
            folderDao.insert(
                BookmarkFolderEntity(
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
                // Walk newParentId's ancestor chain; if folderId appears, reject.
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
        // Delete the bookmarks before their folder: the FK rule is SET NULL, so deleting
        // the folder first would move them to the root instead of removing them.
        bookmarkDao.deleteByParentFolder(folderId)
    }

    override suspend fun countBookmarks(): Int = withContext(dispatchers.io) { bookmarkDao.count() }

    override suspend fun countFolders(): Int = withContext(dispatchers.io) { folderDao.count() }

    override suspend fun getBookmarkTree(): BookmarkTreeNode.Folder =
        withContext(dispatchers.io) {
            val (folders, bookmarks) = database.withTransaction {
                folderDao.getAll() to bookmarkDao.getAllNewestFirst()
            }
            val foldersByParent = folders.groupBy { it.parentId }
                .mapValues { (_, children) -> children.sortedWith(compareBy({ it.name }, { it.id })) }
            val linksByParent = bookmarks.groupBy { it.parentFolderId }
                .mapValues { (_, links) -> links.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

            fun childrenOf(parentId: Long?): List<BookmarkTreeNode> =
                foldersByParent[parentId].orEmpty().map { BookmarkTreeNode.Folder(it.name, childrenOf(it.id)) } +
                    linksByParent[parentId].orEmpty().map { BookmarkTreeNode.Link(it.title, it.url, it.createdAt) }

            BookmarkTreeNode.Folder(name = "", children = childrenOf(null))
        }

    // All or nothing: one transaction, under a new top-level folder renamed "name (n)" when taken.
    override suspend fun importTree(rootName: String, tree: BookmarkTreeNode.Folder): Result<ImportOutcome> =
        runCatchingResult {
            database.withTransaction {
                val takenNames = folderDao.getDirectChildren(null).mapTo(HashSet()) { it.name }
                val name = if (rootName !in takenNames) {
                    rootName
                } else {
                    generateSequence(2) { it + 1 }.map { "$rootName ($it)" }.first { it !in takenNames }
                }
                val writer = TreeWriter(startedAt = System.currentTimeMillis())
                val rootId = writer.insertFolder(name, parentId = null)
                writer.insertChildren(tree.children, rootId)
                ImportOutcome(
                    folderId = rootId,
                    folderName = name,
                    bookmarksAdded = writer.bookmarks,
                    foldersAdded = writer.folders,
                )
            }
        }

    private inner class TreeWriter(private val startedAt: Long) {
        var bookmarks = 0
            private set
        var folders = 0
            private set

        suspend fun insertFolder(name: String, parentId: Long?): Long =
            folderDao.insert(BookmarkFolderEntity(name = name, parentId = parentId, createdAt = startedAt))
                .also { folders++ }

        suspend fun insertChildren(children: List<BookmarkTreeNode>, parentId: Long) {
            for (child in children) {
                when (child) {
                    is BookmarkTreeNode.Folder -> insertChildren(child.children, insertFolder(child.name, parentId))
                    is BookmarkTreeNode.Link -> {
                        bookmarkDao.insert(
                            BookmarkEntity(
                                title = child.title,
                                url = child.url,
                                parentFolderId = parentId,
                                createdAt = startedAt,
                                // Rows share one createdAt; the running order keeps the file's order.
                                sortOrder = startedAt + bookmarks,
                            ),
                        )
                        bookmarks++
                    }
                }
            }
        }
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
