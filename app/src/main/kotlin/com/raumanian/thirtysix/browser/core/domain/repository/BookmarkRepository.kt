package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkTreeNode
import com.raumanian.thirtysix.browser.core.domain.model.ImportOutcome
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

// Bookmarks and folders share one repository so a cascade delete is one transaction.
@Suppress("TooManyFunctions")
interface BookmarkRepository {

    fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>>

    fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>>

    fun observeRecentBookmarks(limit: Int): Flow<List<Bookmark>>

    fun searchBookmarks(query: String): Flow<List<Bookmark>>

    suspend fun getAllBookmarks(): List<Bookmark>

    suspend fun getBookmark(id: Long): Bookmark?

    suspend fun countBookmarksByUrl(url: String): Int

    suspend fun addBookmark(bookmark: Bookmark): Result<Long>

    suspend fun updateBookmark(bookmark: Bookmark): Result<Unit>

    suspend fun deleteBookmark(id: Long): Result<Unit>

    /** Deletes the most recently created bookmark for [url]; the star toggle relies on this. */
    suspend fun deleteMostRecentBookmarkByUrl(url: String): Result<Int>

    suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?): Result<Unit>

    fun observeFoldersByParent(parentId: Long?): Flow<List<BookmarkFolder>>

    fun observeAllFolders(): Flow<List<BookmarkFolder>>

    suspend fun getFolder(id: Long): BookmarkFolder?

    /** Ancestors root → leaf; empty when the folder is missing or at the root. */
    suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder>

    suspend fun countDescendants(folderId: Long): BookmarkDescendantCount

    suspend fun createFolder(name: String, parentId: Long?): Result<Long>

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit>

    /** Rejects a move that would create a cycle. */
    suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit>

    /** One transaction; returns the number of descendants removed, excluding the folder. */
    suspend fun deleteFolderCascade(folderId: Long): Result<BookmarkDescendantCount>

    suspend fun countBookmarks(): Int

    suspend fun countFolders(): Int

    /** Every folder and bookmark read in one transaction: child folders by name, then links in list order. */
    suspend fun getBookmarkTree(): BookmarkTreeNode.Folder

    /**
     * Writes [tree] under a new top-level folder named [rootName], or `rootName (n)` when that name
     * is taken, in one transaction: on failure nothing is written. Links keep their order in [tree].
     */
    suspend fun importTree(rootName: String, tree: BookmarkTreeNode.Folder): Result<ImportOutcome>
}
