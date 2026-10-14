package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkDescendantCount
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkSearchResult
/**
 * `openUrl` is a one-shot: the screen navigates back to the browser with it, then clears it via
 * [BookmarksViewModel.consumeOpenUrl].
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
    val descendantCountForPendingDelete: BookmarkDescendantCount? = null,
    /** Every folder, sorted by name, for the folder pickers; they compute depth from the parent chain. */
    val allFolders: List<BookmarkFolder> = emptyList(),
)

/** The one dialog on screen, if any. */
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
