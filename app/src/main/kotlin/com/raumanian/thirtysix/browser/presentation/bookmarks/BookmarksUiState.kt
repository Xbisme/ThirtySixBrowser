package com.raumanian.thirtysix.browser.presentation.bookmarks

import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.model.BookmarkSearchResult

/**
 * Spec 013 — immutable UI state for the BookmarksScreen.
 *
 * Fields are introduced incrementally per user-story:
 *  - US2: navigation + listing (currentFolderId, breadcrumb, folders, bookmarks, isLoading, errorEvent, openUrl).
 *  - US3: dialog state (pendingDialog).
 *  - US4: search (searchQuery, isSearchActive, searchResults).
 *  - US6: cascade-delete count (descendantCountForPendingDelete).
 *
 * `openUrl` is a one-shot signal carrying a URL the user just tapped — the
 * Composable consumes it via a `LaunchedEffect`, fires a navigation back to
 * the browser screen, and clears it via [BookmarksViewModel.consumeOpenUrl].
 */
data class BookmarksUiState(
    val currentFolderId: Long? = null,
    val breadcrumb: List<BookmarkFolder> = emptyList(),
    val folders: List<BookmarkFolder> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = false,
    val errorEvent: BookmarksErrorEvent? = null,
    val openUrl: String? = null,
    val pendingDialog: PendingBookmarkDialog? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchResults: List<BookmarkSearchResult> = emptyList(),
    val descendantCountForPendingDelete: com.raumanian.thirtysix.browser.domain.model.BookmarkDescendantCount? = null,
    /**
     * Spec 013 — full snapshot of all folders, used by FolderPickerSheet
     * (move bookmark / move folder) and the folder selector inside the
     * AddOrEdit bookmark dialog. Sorted alphabetically; depth/indentation
     * computed by the consumer from the parent chain.
     */
    val allFolders: List<BookmarkFolder> = emptyList(),
)

/**
 * Spec 013 — sealed dialog state. The screen renders at most one dialog at
 * any time. Dispatch handled by [BookmarksViewModel.onDialogConfirm] /
 * [BookmarksViewModel.onDialogDismiss].
 */
sealed class PendingBookmarkDialog {
    data class CreateFolder(val parentId: Long?) : PendingBookmarkDialog()
    data class RenameFolder(val folderId: Long, val currentName: String) : PendingBookmarkDialog()
    data class MoveBookmark(val bookmarkId: Long) : PendingBookmarkDialog()
    data class MoveFolder(val folderId: Long) : PendingBookmarkDialog()
    data class EditBookmark(val bookmark: Bookmark) : PendingBookmarkDialog()
    data class AddBookmark(val parentId: Long?) : PendingBookmarkDialog()
    data class ConfirmDeleteFolder(val folderId: Long) : PendingBookmarkDialog()
    data class BookmarkActions(val bookmark: Bookmark) : PendingBookmarkDialog()
    data class FolderActions(val folder: BookmarkFolder) : PendingBookmarkDialog()
}
