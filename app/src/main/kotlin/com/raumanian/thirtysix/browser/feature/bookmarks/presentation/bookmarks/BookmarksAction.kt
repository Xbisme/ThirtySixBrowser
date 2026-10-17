package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder

sealed interface BookmarksAction {
    data class FolderTap(val folder: BookmarkFolder) : BookmarksAction

    data object BackToParent : BookmarksAction

    data class OpenFolder(val folderId: Long) : BookmarksAction

    data class BreadcrumbSegmentTap(val folderId: Long?) : BookmarksAction

    data class BookmarkTap(val bookmark: Bookmark) : BookmarksAction

    data class LongPressBookmark(val bookmark: Bookmark) : BookmarksAction

    data class LongPressFolder(val folder: BookmarkFolder) : BookmarksAction

    data object SearchOpenClick : BookmarksAction

    data object SearchClose : BookmarksAction

    data class SearchQueryChange(val query: String) : BookmarksAction

    data object CreateFolderClick : BookmarksAction

    data object AddBookmarkClick : BookmarksAction

    data class RenameFolderClick(val folder: BookmarkFolder) : BookmarksAction

    data class MoveFolderClick(val folder: BookmarkFolder) : BookmarksAction

    data class MoveBookmarkClick(val bookmark: Bookmark) : BookmarksAction

    data class EditBookmarkClick(val bookmark: Bookmark) : BookmarksAction

    data class DeleteBookmarkClick(val bookmark: Bookmark) : BookmarksAction

    data class DeleteFolderClick(val folder: BookmarkFolder) : BookmarksAction

    data object DialogDismiss : BookmarksAction

    data class ConfirmCreateFolder(val name: String, val parentId: Long?) : BookmarksAction

    data class ConfirmRenameFolder(val folderId: Long, val newName: String) : BookmarksAction

    data class ConfirmMoveFolder(val folderId: Long, val newParentId: Long?) : BookmarksAction

    data class ConfirmMoveBookmark(val bookmarkId: Long, val newParentId: Long?) : BookmarksAction

    data class ConfirmEditBookmark(
        val bookmarkId: Long,
        val title: String,
        val url: String,
        val parentFolderId: Long?,
    ) : BookmarksAction

    data class ConfirmAddBookmark(val title: String, val url: String, val parentId: Long?) : BookmarksAction

    data class ConfirmDeleteFolder(val folderId: Long) : BookmarksAction
}
