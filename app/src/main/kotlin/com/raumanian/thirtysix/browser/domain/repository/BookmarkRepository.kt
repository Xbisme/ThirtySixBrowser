package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import kotlinx.coroutines.flow.Flow

/**
 * Spec 013 — single repository covering bookmarks AND folders. The combined
 * shape is justified in research.md R1 (cascade-delete transaction lives
 * cleanly inside one repo; not a Constitution §IV Repo→Repo violation).
 *
 * All `Flow`-returning methods emit on Room's IO dispatcher; consumers
 * must `.flowOn(...)` if they need to render on Main.
 *
 * `suspend` methods are safe to call from any dispatcher; the implementation
 * switches to IO internally via [com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider.io].
 */
@Suppress("TooManyFunctions") // 18 functions span bookmark + folder CRUD; combined repo per research.md R1.
interface BookmarkRepository {

    // ──────── Bookmark queries ────────

    fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>>

    fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>>

    fun searchBookmarks(query: String): Flow<List<Bookmark>>

    suspend fun getBookmark(id: Long): Bookmark?

    suspend fun countBookmarksByUrl(url: String): Int

    // ──────── Bookmark mutations ────────

    suspend fun addBookmark(bookmark: Bookmark): Result<Long>

    suspend fun updateBookmark(bookmark: Bookmark): Result<Unit>

    suspend fun deleteBookmark(id: Long): Result<Unit>

    /** Deletes the most recently created bookmark for [url]. Used by star toggle (Q4 / R9). */
    suspend fun deleteMostRecentBookmarkByUrl(url: String): Result<Int>

    suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?): Result<Unit>

    // ──────── Folder queries ────────

    fun observeFoldersByParent(parentId: Long?): Flow<List<BookmarkFolder>>

    /** Spec 013 — reactive snapshot of every folder. Powers the folder-picker UI. */
    fun observeAllFolders(): Flow<List<BookmarkFolder>>

    suspend fun getFolder(id: Long): BookmarkFolder?

    /**
     * Walks the parent chain root → leaf for the given folder ID.
     * Empty list when the folder does not exist or is at the root.
     */
    suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder>

    /** Total descendants under [folderId]. Powers FR-020 confirm dialog. */
    suspend fun countDescendants(folderId: Long): BookmarkDescendantCount

    // ──────── Folder mutations ────────

    suspend fun createFolder(name: String, parentId: Long?): Result<Long>

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit>

    /** Cycle prevention enforced internally (R6). */
    suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit>

    /**
     * Cascade delete (R7) — single Room `withTransaction { ... }`. Removes
     * the folder and every descendant bookmark / sub-folder. Returns the
     * descendant count (excluding the target folder itself) on success.
     */
    suspend fun deleteFolderCascade(folderId: Long): Result<BookmarkDescendantCount>
}
