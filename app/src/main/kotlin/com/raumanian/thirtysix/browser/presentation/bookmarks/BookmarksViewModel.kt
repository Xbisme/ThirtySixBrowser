package com.raumanian.thirtysix.browser.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CountFolderDescendantsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.MoveBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.MoveFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllFoldersUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveBookmarksByFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveFolderPathUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RenameFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SearchBookmarksUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Spec 013 — view model for the BookmarksScreen.
 *
 * Surface incrementally built across user stories — see `BookmarksUiState`
 * KDoc for the per-US field map. All folder navigation, dialog dispatch,
 * search, and CRUD coordination live here.
 *
 * Coordinates use cases from two domains: bookmark domain (Spec 013) and
 * tabs domain (Spec 011's [UpdateActiveTabUrlAndTitleUseCase] for the
 * "open bookmark in active tab" path per FR-010). This is ViewModel-level
 * use-case coordination, not a Repo→Repo dependency — Constitution §IV
 * compliant.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList")
class BookmarksViewModel @Inject constructor(
    private val observeBookmarksByFolder: ObserveBookmarksByFolderUseCase,
    private val observeFolderPath: ObserveFolderPathUseCase,
    private val deleteBookmark: DeleteBookmarkUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val createFolder: CreateFolderUseCase,
    private val renameFolder: RenameFolderUseCase,
    private val moveFolder: MoveFolderUseCase,
    private val moveBookmark: MoveBookmarkUseCase,
    private val updateBookmark: UpdateBookmarkUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val deleteFolder: DeleteFolderUseCase,
    private val countDescendants: CountFolderDescendantsUseCase,
    private val searchBookmarks: SearchBookmarksUseCase,
    private val observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    observeAllFolders: ObserveAllFoldersUseCase,
) : ViewModel() {

    /** Spec 013 FR-010 — captured active-tab id used by `onBookmarkTap`. */
    private var activeTabId: Long = 0L

    private val _uiState: MutableStateFlow<BookmarksUiState> = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    private val currentFolderIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    /** Spec 013 FR-010a — captures the active tab's incognito flag at tap time. */
    private var lastKnownIsIncognito: Boolean = false

    init {
        // Listing pipeline: when current folder changes, refresh folders + bookmarks + breadcrumb.
        currentFolderIdFlow
            .flatMapLatest { folderId -> observeBookmarksByFolder(folderId) }
            .onEach { byFolder ->
                _uiState.update { state ->
                    state.copy(folders = byFolder.folders, bookmarks = byFolder.bookmarks)
                }
            }
            .launchIn(viewModelScope)

        currentFolderIdFlow
            .flatMapLatest { folderId -> observeFolderPath(folderId) }
            .onEach { breadcrumb ->
                _uiState.update { state ->
                    state.copy(currentFolderId = currentFolderIdFlow.value, breadcrumb = breadcrumb)
                }
            }
            .launchIn(viewModelScope)

        // Search pipeline: when query changes, run global search.
        searchQueryFlow
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    searchBookmarks(query)
                }
            }
            .onEach { results ->
                _uiState.update { state -> state.copy(searchResults = results) }
            }
            .launchIn(viewModelScope)

        // Spec 013 FR-010a — observe incognito flag continuously so a tap-bookmark dispatch
        // always uses the latest value.
        observeActiveTabIsIncognito()
            .onEach { lastKnownIsIncognito = it }
            .launchIn(viewModelScope)

        // Spec 013 FR-010 — capture active tab id so onBookmarkTap can update the
        // currently-active tab's URL row when the user taps a bookmark.
        observeActiveTab()
            .onEach { tab -> activeTabId = tab?.id ?: 0L }
            .launchIn(viewModelScope)

        // Spec 013 — full folder snapshot for FolderPickerSheet + AddOrEdit dialog.
        observeAllFolders()
            .onEach { all ->
                _uiState.update { state -> state.copy(allFolders = all) }
            }
            .launchIn(viewModelScope)
    }

    // ──────── Navigation ────────

    fun onFolderTap(folder: BookmarkFolder) {
        currentFolderIdFlow.value = folder.id
    }

    fun onBackToParent() {
        val parent = _uiState.value.breadcrumb.dropLast(1).lastOrNull()?.id
        currentFolderIdFlow.value = parent
    }

    fun onBreadcrumbSegmentTap(folderId: Long?) {
        currentFolderIdFlow.value = folderId
    }

    // ──────── Open bookmark (FR-010 / FR-010a) ────────

    fun onBookmarkTap(bookmark: Bookmark) {
        viewModelScope.launch {
            if (activeTabId > 0L) {
                updateActiveTabUrlAndTitle(
                    tabId = activeTabId,
                    url = bookmark.url,
                    title = bookmark.title,
                    isIncognito = lastKnownIsIncognito,
                )
            }
            _uiState.update { state -> state.copy(openUrl = bookmark.url, isSearchActive = false, searchQuery = "") }
        }
    }

    fun consumeOpenUrl() {
        _uiState.update { state -> state.copy(openUrl = null) }
    }

    // ──────── Long-press → action sheets (US3+) ────────

    fun onLongPressBookmark(bookmark: Bookmark) {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.BookmarkActions(bookmark)) }
    }

    fun onLongPressFolder(folder: BookmarkFolder) {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.FolderActions(folder)) }
    }

    // ──────── Search (US4) ────────

    fun onSearchOpenClick() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun onSearchClose() {
        searchQueryFlow.value = ""
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "", searchResults = emptyList()) }
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ──────── Dialog launches (US3 + US5 + US7) ────────

    fun onCreateFolderClick() {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.CreateFolder(currentFolderIdFlow.value)) }
    }

    fun onAddBookmarkClick() {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.AddBookmark(currentFolderIdFlow.value)) }
    }

    fun onRenameFolderClick(folder: BookmarkFolder) {
        _uiState.update {
            it.copy(pendingDialog = PendingBookmarkDialog.RenameFolder(folder.id, folder.name))
        }
    }

    fun onMoveFolderClick(folder: BookmarkFolder) {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.MoveFolder(folder.id)) }
    }

    fun onMoveBookmarkClick(bookmark: Bookmark) {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.MoveBookmark(bookmark.id)) }
    }

    fun onEditBookmarkClick(bookmark: Bookmark) {
        _uiState.update { it.copy(pendingDialog = PendingBookmarkDialog.EditBookmark(bookmark)) }
    }

    fun onDeleteBookmarkClick(bookmark: Bookmark) {
        viewModelScope.launch {
            mapErrorToEvent(deleteBookmark(bookmark.id))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onDeleteFolderClick(folder: BookmarkFolder) {
        viewModelScope.launch {
            val count = countDescendants(folder.id)
            if (count.total == 0) {
                mapErrorToEvent(deleteFolder(folder.id))
                _uiState.update { it.copy(pendingDialog = null) }
            } else {
                _uiState.update {
                    it.copy(
                        pendingDialog = PendingBookmarkDialog.ConfirmDeleteFolder(folder.id),
                        descendantCountForPendingDelete = count,
                    )
                }
            }
        }
    }

    fun onDialogDismiss() {
        _uiState.update {
            it.copy(pendingDialog = null, descendantCountForPendingDelete = null)
        }
    }

    // ──────── Dialog confirmations ────────

    fun onConfirmCreateFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(createFolder(name = name, parentId = parentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmRenameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            mapErrorToEvent(renameFolder(folderId = folderId, newName = newName))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmMoveFolder(folderId: Long, newParentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(moveFolder(folderId = folderId, newParentId = newParentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmMoveBookmark(bookmarkId: Long, newParentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(moveBookmark(bookmarkId = bookmarkId, newParentId = newParentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmEditBookmark(
        bookmarkId: Long,
        title: String,
        url: String,
        parentFolderId: Long?,
    ) {
        viewModelScope.launch {
            mapErrorToEvent(
                updateBookmark(
                    bookmarkId = bookmarkId,
                    title = title,
                    url = url,
                    parentFolderId = parentFolderId,
                ),
            )
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmAddBookmark(title: String, url: String, parentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(addBookmark(url = url, title = title, parentFolderId = parentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    fun onConfirmDeleteFolder(folderId: Long) {
        viewModelScope.launch {
            mapErrorToEvent(deleteFolder(folderId))
            _uiState.update { it.copy(pendingDialog = null, descendantCountForPendingDelete = null) }
        }
    }

    fun consumeErrorEvent() {
        _uiState.update { it.copy(errorEvent = null) }
    }

    // ──────── Helpers ────────

    private fun mapErrorToEvent(result: Result<*>) {
        if (result is Result.Error) {
            val event = when (val t = result.throwable) {
                is BookmarkException.UrlValidation -> BookmarksErrorEvent.InvalidUrl(t.reason)
                is BookmarkException.FolderCycle -> BookmarksErrorEvent.FolderCycle
                is BookmarkException.BookmarkNotFound -> BookmarksErrorEvent.BookmarkNotFound
                is BookmarkException.FolderNotFound -> BookmarksErrorEvent.FolderNotFound
                else -> null
            }
            if (event != null) {
                _uiState.update { it.copy(errorEvent = event) }
            }
        }
    }
}
