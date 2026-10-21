@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
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
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkUrlValidationError
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SectionHeader
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.AddOrEditBookmarkDialog
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkActionSheet
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkRow
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkSearchResultRow
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarksTopBar
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BreadcrumbBar
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.CreateOrRenameFolderDialog
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.DeleteFolderConfirmDialog
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.EmptyBookmarksState
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.FolderActionSheet
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.FolderChipsRow
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.FolderPickerSheet
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.FolderRow
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.NoSearchMatchesState
import kotlinx.coroutines.launch
private const val KEY_FOLDER_CHIPS = "folder_chips"
private const val KEY_HEADER_FOLDERS = "header_folders"
private const val KEY_HEADER_PAGES = "header_pages"
private const val CONTENT_TYPE_CHIPS = "chips"
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_FOLDER = "folder"
private const val CONTENT_TYPE_BOOKMARK = "bookmark"
private const val CONTENT_TYPE_SEARCH_RESULT = "search_result"

/**
 * Bookmarks: a title over a search well, folder chips at the root, a breadcrumb inside folders,
 * the list (or search / empty / no-match states) and at most one dialog from
 * [BookmarksUiState.pendingDialog].
 */
@Composable
@Suppress("LongParameterList")
fun BookmarksRoute(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: BookmarksViewModel = hiltViewModel(),
    transferViewModel: BookmarkTransferViewModel = hiltViewModel(),
    openTransfer: Boolean = false,
    onShowBrowser: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // System back walks up the folder tree first, then leaves the screen.
    BackHandler {
        if (state.currentFolderId != null) {
            viewModel.onAction(BookmarksAction.BackToParent)
        } else {
            navController.popBackStack()
        }
    }

    EventEffect(viewModel.events) { event ->
        when (event) {
            is BookmarksEvent.OpenUrl -> onShowBrowser()
            // Shown apart from the collector so a showing message never holds up navigation.
            is BookmarksEvent.Error -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.toResId()))
            }
        }
    }

    val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
    // Settings opens this screen to offer the transfer: once, not again after rotation.
    var transferOffered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openTransfer) {
        if (openTransfer && !transferOffered) {
            transferOffered = true
            transferViewModel.onAction(BookmarkTransferAction.SheetOpen)
        }
    }
    BookmarkTransferHost(
        state = transferState,
        events = transferViewModel.events,
        onAction = transferViewModel::onAction,
        onOpenFolder = { viewModel.onAction(BookmarksAction.OpenFolder(it)) },
        showMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )

    BookmarksScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onExitScreen = { navController.popBackStack() },
        onMore = { transferViewModel.onAction(BookmarkTransferAction.SheetOpen) },
        modifier = modifier,
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList")
fun BookmarksScreen(
    state: BookmarksUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (BookmarksAction) -> Unit,
    onExitScreen: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BookmarksTopBar(
                state = state,
                onSearchOpen = { onAction(BookmarksAction.SearchOpenClick) },
                onSearchClose = { onAction(BookmarksAction.SearchClose) },
                onSearchQueryChange = { onAction(BookmarksAction.SearchQueryChange(it)) },
                onBackToParent = { onAction(BookmarksAction.BackToParent) },
                onExitScreen = onExitScreen,
                onCreateFolder = { onAction(BookmarksAction.CreateFolderClick) },
                onMore = onMore,
            )
        },
        floatingActionButton = {
            BookmarksFab(onClick = { onAction(BookmarksAction.AddBookmarkClick) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.isSearchActive && state.breadcrumb.isNotEmpty()) {
                BreadcrumbBar(
                    chain = state.breadcrumb,
                    onSegmentClick = { onAction(BookmarksAction.BreadcrumbSegmentTap(it)) },
                )
            }
            BookmarksContent(state = state, onAction = onAction)
        }
    }

    BookmarksDialogs(state = state, onAction = onAction)
}

/** Room under the list so the last row clears the floating add button. */
private val ListBottomPadding = Dimens.fab + Spacing.lg

@Composable
@Suppress("LongMethod")
private fun BookmarksContent(
    state: BookmarksUiState,
    onAction: (BookmarksAction) -> Unit,
) {
    when {
        state.isSearchActive -> {
            val queryCounts = state.searchQuery.trim().length >= BrowserLimits.SEARCH_MIN_CHARS
            if (state.searchResults.isEmpty() && queryCounts) {
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
                            onClick = { onAction(BookmarksAction.BookmarkTap(result.bookmark)) },
                            onLongPress = { onAction(BookmarksAction.LongPressBookmark(result.bookmark)) },
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
                // Top-level folders as chips, at the root only.
                if (state.currentFolderId == null && state.folders.isNotEmpty()) {
                    item(key = KEY_FOLDER_CHIPS, contentType = CONTENT_TYPE_CHIPS) {
                        FolderChipsRow(
                            folders = state.folders,
                            onFolderClick = { onAction(BookmarksAction.FolderTap(it)) },
                        )
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
                        onClick = { onAction(BookmarksAction.FolderTap(folder)) },
                        onLongPress = { onAction(BookmarksAction.LongPressFolder(folder)) },
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
                        onClick = { onAction(BookmarksAction.BookmarkTap(bookmark)) },
                        onLongPress = { onAction(BookmarksAction.LongPressBookmark(bookmark)) },
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
    onAction: (BookmarksAction) -> Unit,
) {
    when (val pending = state.pendingDialog) {
        is PendingBookmarkDialog.CreateFolder -> CreateOrRenameFolderDialog(
            title = stringResource(R.string.bookmarks_dialog_create_folder_title),
            initialName = "",
            onConfirm = { name ->
                onAction(BookmarksAction.ConfirmCreateFolder(name = name, parentId = pending.parentId))
            },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.RenameFolder -> CreateOrRenameFolderDialog(
            title = stringResource(R.string.bookmarks_dialog_rename_folder_title),
            initialName = pending.currentName,
            onConfirm = { name ->
                onAction(BookmarksAction.ConfirmRenameFolder(folderId = pending.folderId, newName = name))
            },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.AddBookmark -> AddOrEditBookmarkDialog(
            title = stringResource(R.string.bookmarks_dialog_add_title),
            initialBookmarkTitle = "",
            initialUrl = "",
            initialParentId = pending.parentId,
            allFolders = state.allFolders,
            onConfirm = { t, u, p -> onAction(BookmarksAction.ConfirmAddBookmark(t, u, p)) },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.EditBookmark -> AddOrEditBookmarkDialog(
            title = stringResource(R.string.bookmarks_dialog_edit_title),
            initialBookmarkTitle = pending.bookmark.title,
            initialUrl = pending.bookmark.url,
            initialParentId = pending.bookmark.parentFolderId,
            allFolders = state.allFolders,
            onConfirm = { t, u, p ->
                onAction(
                    BookmarksAction.ConfirmEditBookmark(
                        bookmarkId = pending.bookmark.id,
                        title = t,
                        url = u,
                        parentFolderId = p,
                    ),
                )
            },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.MoveBookmark -> FolderPickerSheet(
            title = stringResource(R.string.bookmarks_dialog_folder_picker_title),
            allFolders = state.allFolders,
            onPick = { newParentId -> onAction(BookmarksAction.ConfirmMoveBookmark(pending.bookmarkId, newParentId)) },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.MoveFolder -> FolderPickerSheet(
            title = stringResource(R.string.bookmarks_dialog_folder_picker_title),
            allFolders = state.allFolders,
            excludeFolderIdAndDescendants = pending.folderId,
            onPick = { newParentId -> onAction(BookmarksAction.ConfirmMoveFolder(pending.folderId, newParentId)) },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.ConfirmDeleteFolder -> {
            val count = state.descendantCountForPendingDelete
            if (count != null) {
                DeleteFolderConfirmDialog(
                    bookmarksCount = count.bookmarks,
                    foldersCount = count.folders,
                    onConfirm = { onAction(BookmarksAction.ConfirmDeleteFolder(pending.folderId)) },
                    onDismiss = { onAction(BookmarksAction.DialogDismiss) },
                )
            }
        }
        is PendingBookmarkDialog.BookmarkActions -> BookmarkActionSheet(
            bookmark = pending.bookmark,
            onOpen = {
                onAction(BookmarksAction.DialogDismiss)
                onAction(BookmarksAction.BookmarkTap(pending.bookmark))
            },
            onEdit = { onAction(BookmarksAction.EditBookmarkClick(pending.bookmark)) },
            onMove = { onAction(BookmarksAction.MoveBookmarkClick(pending.bookmark)) },
            onDelete = { onAction(BookmarksAction.DeleteBookmarkClick(pending.bookmark)) },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        is PendingBookmarkDialog.FolderActions -> FolderActionSheet(
            folder = pending.folder,
            onOpen = {
                onAction(BookmarksAction.DialogDismiss)
                onAction(BookmarksAction.FolderTap(pending.folder))
            },
            onRename = { onAction(BookmarksAction.RenameFolderClick(pending.folder)) },
            onMove = { onAction(BookmarksAction.MoveFolderClick(pending.folder)) },
            onDelete = { onAction(BookmarksAction.DeleteFolderClick(pending.folder)) },
            onDismiss = { onAction(BookmarksAction.DialogDismiss) },
        )
        null -> Unit
    }
}

/** The add button in the brand gradient (the new-tab action's look). */
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
