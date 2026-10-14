@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SectionHeader
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.error.BookmarkUrlValidationError
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.AddOrEditBookmarkDialog
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.BookmarkActionSheet
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.BookmarkRow
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.BookmarkSearchResultRow
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.BookmarksTopBar
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.BreadcrumbBar
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.CreateOrRenameFolderDialog
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.DeleteFolderConfirmDialog
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.EmptyBookmarksState
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.FolderActionSheet
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.FolderChipsRow
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.FolderPickerSheet
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.FolderRow
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.NoSearchMatchesState
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
private const val KEY_FOLDER_CHIPS = "folder_chips"
private const val KEY_HEADER_FOLDERS = "header_folders"
private const val KEY_HEADER_PAGES = "header_pages"
private const val CONTENT_TYPE_CHIPS = "chips"
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_FOLDER = "folder"
private const val CONTENT_TYPE_BOOKMARK = "bookmark"
private const val CONTENT_TYPE_SEARCH_RESULT = "search_result"

/**
 * Spec 013 — bookmarks screen replacing the Spec 002 placeholder. Renders a
 * Scaffold with topBar, breadcrumb, list (or search / empty / no-match
 * states), FAB, and a single active dialog driven by [BookmarksUiState.pendingDialog].
 *
 * Spec 019 — large title over a search well, open rows under "Folders" / "Pages" section
 * headers, top-level folder chips at the root (FR-042), the breadcrumb inside folders, the
 * brand-gradient add button. Behaviour is Spec 013's, unchanged.
 */
@Composable
@Suppress("LongMethod")
fun BookmarksScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Spec 013 — system back: walk up the folder tree first, then exit screen.
    BackHandler {
        if (state.currentFolderId != null) {
            viewModel.onBackToParent()
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(state.openUrl) {
        if (state.openUrl != null) {
            navController.popBackStack(AppDestination.Browser.route, inclusive = false)
            viewModel.consumeOpenUrl()
        }
    }

    val errorEvent = state.errorEvent
    val errorMessage = errorEvent?.let { stringResource(it.toResId()) }
    LaunchedEffect(errorEvent) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.consumeErrorEvent()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BookmarksTopBar(
                state = state,
                onSearchOpen = viewModel::onSearchOpenClick,
                onSearchClose = viewModel::onSearchClose,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onBackToParent = viewModel::onBackToParent,
                onExitScreen = { navController.popBackStack() },
                onCreateFolder = viewModel::onCreateFolderClick,
            )
        },
        floatingActionButton = {
            BookmarksFab(onClick = viewModel::onAddBookmarkClick)
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.isSearchActive && state.breadcrumb.isNotEmpty()) {
                BreadcrumbBar(
                    chain = state.breadcrumb,
                    onSegmentClick = viewModel::onBreadcrumbSegmentTap,
                )
            }
            BookmarksContent(state = state, viewModel = viewModel)
        }
    }

    BookmarksDialogs(state = state, viewModel = viewModel)
}

/** Room under the list so the last row clears the floating add button. */
private val ListBottomPadding = Dimens.fab + Spacing.lg

@Composable
@Suppress("LongMethod")
private fun BookmarksContent(
    state: BookmarksUiState,
    viewModel: BookmarksViewModel,
) {
    when {
        state.isSearchActive -> {
            if (state.searchResults.isEmpty() && state.searchQuery.isNotBlank()) {
                NoSearchMatchesState(query = state.searchQuery)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = ListBottomPadding),
                ) {
                    items(
                        state.searchResults,
                        key = { it.bookmark.id },
                        contentType = { CONTENT_TYPE_SEARCH_RESULT },
                    ) { result ->
                        BookmarkSearchResultRow(
                            result = result,
                            onClick = { viewModel.onBookmarkTap(result.bookmark) },
                            onLongPress = { viewModel.onLongPressBookmark(result.bookmark) },
                        )
                    }
                }
            }
        }
        state.folders.isEmpty() && state.bookmarks.isEmpty() -> {
            EmptyBookmarksState()
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ListBottomPadding),
            ) {
                // Spec 019 FR-042 — top-level folders as chips, at the root only.
                if (state.currentFolderId == null && state.folders.isNotEmpty()) {
                    item(key = KEY_FOLDER_CHIPS, contentType = CONTENT_TYPE_CHIPS) {
                        FolderChipsRow(folders = state.folders, onFolderClick = viewModel::onFolderTap)
                    }
                }
                if (state.folders.isNotEmpty()) {
                    item(key = KEY_HEADER_FOLDERS, contentType = CONTENT_TYPE_HEADER) {
                        SectionHeader(text = stringResource(R.string.bookmarks_section_folders))
                    }
                }
                items(state.folders, key = { "folder_${it.id}" }, contentType = { CONTENT_TYPE_FOLDER }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { viewModel.onFolderTap(folder) },
                        onLongPress = { viewModel.onLongPressFolder(folder) },
                    )
                }
                if (state.bookmarks.isNotEmpty()) {
                    item(key = KEY_HEADER_PAGES, contentType = CONTENT_TYPE_HEADER) {
                        SectionHeader(text = stringResource(R.string.bookmarks_section_pages))
                    }
                }
                items(
                    state.bookmarks,
                    key = { "bookmark_${it.id}" },
                    contentType = { CONTENT_TYPE_BOOKMARK },
                ) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { viewModel.onBookmarkTap(bookmark) },
                        onLongPress = { viewModel.onLongPressBookmark(bookmark) },
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun BookmarksDialogs(
    state: BookmarksUiState,
    viewModel: BookmarksViewModel,
) {
    when (val pending = state.pendingDialog) {
        is PendingBookmarkDialog.CreateFolder -> CreateOrRenameFolderDialog(
            title = stringResource(R.string.bookmarks_dialog_create_folder_title),
            initialName = "",
            onConfirm = { name -> viewModel.onConfirmCreateFolder(name = name, parentId = pending.parentId) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.RenameFolder -> CreateOrRenameFolderDialog(
            title = stringResource(R.string.bookmarks_dialog_rename_folder_title),
            initialName = pending.currentName,
            onConfirm = { name -> viewModel.onConfirmRenameFolder(folderId = pending.folderId, newName = name) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.AddBookmark -> AddOrEditBookmarkDialog(
            title = stringResource(R.string.bookmarks_dialog_add_title),
            initialBookmarkTitle = "",
            initialUrl = "",
            initialParentId = pending.parentId,
            allFolders = state.allFolders,
            onConfirm = { t, u, p -> viewModel.onConfirmAddBookmark(t, u, p) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.EditBookmark -> AddOrEditBookmarkDialog(
            title = stringResource(R.string.bookmarks_dialog_edit_title),
            initialBookmarkTitle = pending.bookmark.title,
            initialUrl = pending.bookmark.url,
            initialParentId = pending.bookmark.parentFolderId,
            allFolders = state.allFolders,
            onConfirm = { t, u, p ->
                viewModel.onConfirmEditBookmark(
                    bookmarkId = pending.bookmark.id,
                    title = t,
                    url = u,
                    parentFolderId = p,
                )
            },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.MoveBookmark -> FolderPickerSheet(
            title = stringResource(R.string.bookmarks_dialog_folder_picker_title),
            allFolders = state.allFolders,
            onPick = { newParentId -> viewModel.onConfirmMoveBookmark(pending.bookmarkId, newParentId) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.MoveFolder -> FolderPickerSheet(
            title = stringResource(R.string.bookmarks_dialog_folder_picker_title),
            allFolders = state.allFolders,
            excludeFolderIdAndDescendants = pending.folderId,
            onPick = { newParentId -> viewModel.onConfirmMoveFolder(pending.folderId, newParentId) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.ConfirmDeleteFolder -> {
            val count = state.descendantCountForPendingDelete
            if (count != null) {
                DeleteFolderConfirmDialog(
                    bookmarksCount = count.bookmarks,
                    foldersCount = count.folders,
                    onConfirm = { viewModel.onConfirmDeleteFolder(pending.folderId) },
                    onDismiss = viewModel::onDialogDismiss,
                )
            }
        }
        is PendingBookmarkDialog.BookmarkActions -> BookmarkActionSheet(
            bookmark = pending.bookmark,
            onOpen = {
                viewModel.onDialogDismiss()
                viewModel.onBookmarkTap(pending.bookmark)
            },
            onEdit = { viewModel.onEditBookmarkClick(pending.bookmark) },
            onMove = { viewModel.onMoveBookmarkClick(pending.bookmark) },
            onDelete = { viewModel.onDeleteBookmarkClick(pending.bookmark) },
            onDismiss = viewModel::onDialogDismiss,
        )
        is PendingBookmarkDialog.FolderActions -> FolderActionSheet(
            folder = pending.folder,
            onOpen = {
                viewModel.onDialogDismiss()
                viewModel.onFolderTap(pending.folder)
            },
            onRename = { viewModel.onRenameFolderClick(pending.folder) },
            onMove = { viewModel.onMoveFolderClick(pending.folder) },
            onDelete = { viewModel.onDeleteFolderClick(pending.folder) },
            onDismiss = viewModel::onDialogDismiss,
        )
        null -> Unit
    }
}

/** Spec 019 — the add button in the brand gradient (the new-tab action's look). */
@Composable
private fun BookmarksFab(onClick: () -> Unit) {
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    val description = stringResource(R.string.bookmarks_action_add_cd)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Dimens.fab)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.fabStart, colors.fabEnd)))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(AppIcons.Add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimens.iconLarge),
        )
    }
}

/** Spec 013 — UI mapping table for [BookmarksErrorEvent] → string resource. */
private fun BookmarksErrorEvent.toResId(): Int = when (this) {
    is BookmarksErrorEvent.InvalidUrl -> when (reason) {
        BookmarkUrlValidationError.Empty -> R.string.bookmarks_validation_url_required
        BookmarkUrlValidationError.InvalidScheme -> R.string.bookmarks_validation_url_invalid
        BookmarkUrlValidationError.MissingHost -> R.string.bookmarks_validation_url_invalid
    }
    BookmarksErrorEvent.EmptyTitle -> R.string.bookmarks_validation_title_required
    BookmarksErrorEvent.EmptyUrl -> R.string.bookmarks_validation_url_required
    BookmarksErrorEvent.EmptyFolderName -> R.string.bookmarks_validation_folder_name_required
    BookmarksErrorEvent.FolderCycle -> R.string.bookmarks_error_folder_cycle
    BookmarksErrorEvent.BookmarkNotFound -> R.string.bookmarks_empty_title
    BookmarksErrorEvent.FolderNotFound -> R.string.bookmarks_empty_title
}
