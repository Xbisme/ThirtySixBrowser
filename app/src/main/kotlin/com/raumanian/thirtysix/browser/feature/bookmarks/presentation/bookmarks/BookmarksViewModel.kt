package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.CreateFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ObserveBookmarksByFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ObserveFolderPathUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.RenameFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.SearchBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.UpdateBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bookmarks screen state. Tapping a bookmark loads it into the active tab through the tabs
 * domain's use case, coordinated here rather than repository to repository.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LongParameterList")
class BookmarksViewModel @Inject constructor(
    private val observeBookmarksByFolder: ObserveBookmarksByFolderUseCase,
    private val observeFolderPath: ObserveFolderPathUseCase,
    private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase,
    private val createFolder: CreateFolderUseCase,
    private val renameFolder: RenameFolderUseCase,
    private val updateBookmark: UpdateBookmarkUseCase,
    private val addBookmark: AddBookmarkUseCase,
    private val searchBookmarks: SearchBookmarksUseCase,
    private val observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    observeActiveTab: ObserveActiveTabUseCase,
    private val bookmarks: BookmarkRepository,
) : ViewModel() {

    private var activeTabId: Long = 0L

    private val _uiState: MutableStateFlow<BookmarksUiState> = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    // Kept until the screen collects them, as the consumed-once state fields they replace were.
    private val eventChannel = Channel<BookmarksEvent>(Channel.BUFFERED)
    val events: Flow<BookmarksEvent> = eventChannel.receiveAsFlow()

    private val currentFolderIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)
    private val searchQueryFlow: MutableStateFlow<String> = MutableStateFlow("")

    private var lastKnownIsIncognito: Boolean = false

    init {
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

        // Kept current so a tap never writes an incognito tab's URL as a normal one.
        observeActiveTabIsIncognito()
            .onEach { lastKnownIsIncognito = it }
            .launchIn(viewModelScope)

        observeActiveTab()
            .onEach { tab -> activeTabId = tab?.id ?: 0L }
            .launchIn(viewModelScope)

        bookmarks.observeAllFolders()
            .onEach { all ->
                _uiState.update { state -> state.copy(allFolders = all) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: BookmarksAction) {
        when (action) {
            is BookmarksAction.FolderTap -> currentFolderIdFlow.value = action.folder.id
            BookmarksAction.BackToParent -> onBackToParent()
            is BookmarksAction.BreadcrumbSegmentTap -> currentFolderIdFlow.value = action.folderId
            is BookmarksAction.BookmarkTap -> onBookmarkTap(action.bookmark)
            is BookmarksAction.LongPressBookmark -> showDialog(PendingBookmarkDialog.BookmarkActions(action.bookmark))
            is BookmarksAction.LongPressFolder -> showDialog(PendingBookmarkDialog.FolderActions(action.folder))
            BookmarksAction.SearchOpenClick -> _uiState.update { it.copy(isSearchActive = true) }
            BookmarksAction.SearchClose -> onSearchClose()
            is BookmarksAction.SearchQueryChange -> onSearchQueryChange(action.query)
            BookmarksAction.CreateFolderClick ->
                showDialog(PendingBookmarkDialog.CreateFolder(currentFolderIdFlow.value))
            BookmarksAction.AddBookmarkClick -> showDialog(PendingBookmarkDialog.AddBookmark(currentFolderIdFlow.value))
            is BookmarksAction.RenameFolderClick ->
                showDialog(PendingBookmarkDialog.RenameFolder(action.folder.id, action.folder.name))
            is BookmarksAction.MoveFolderClick -> showDialog(PendingBookmarkDialog.MoveFolder(action.folder.id))
            is BookmarksAction.MoveBookmarkClick -> showDialog(PendingBookmarkDialog.MoveBookmark(action.bookmark.id))
            is BookmarksAction.EditBookmarkClick -> showDialog(PendingBookmarkDialog.EditBookmark(action.bookmark))
            is BookmarksAction.DeleteBookmarkClick -> onDeleteBookmarkClick(action.bookmark)
            is BookmarksAction.DeleteFolderClick -> onDeleteFolderClick(action.folder)
            BookmarksAction.DialogDismiss -> onDialogDismiss()
            is BookmarksAction.ConfirmCreateFolder -> onConfirmCreateFolder(action.name, action.parentId)
            is BookmarksAction.ConfirmRenameFolder -> onConfirmRenameFolder(action.folderId, action.newName)
            is BookmarksAction.ConfirmMoveFolder -> onConfirmMoveFolder(action.folderId, action.newParentId)
            is BookmarksAction.ConfirmMoveBookmark -> onConfirmMoveBookmark(action.bookmarkId, action.newParentId)
            is BookmarksAction.ConfirmEditBookmark -> onConfirmEditBookmark(action)
            is BookmarksAction.ConfirmAddBookmark -> onConfirmAddBookmark(action.title, action.url, action.parentId)
            is BookmarksAction.ConfirmDeleteFolder -> onConfirmDeleteFolder(action.folderId)
        }
    }

    private fun showDialog(dialog: PendingBookmarkDialog) {
        _uiState.update { it.copy(pendingDialog = dialog) }
    }

    private fun onBackToParent() {
        val parent = _uiState.value.breadcrumb.dropLast(1).lastOrNull()?.id
        currentFolderIdFlow.value = parent
    }

    private fun onBookmarkTap(bookmark: Bookmark) {
        viewModelScope.launch {
            if (activeTabId > 0L) {
                updateActiveTabUrlAndTitle(
                    tabId = activeTabId,
                    url = bookmark.url,
                    title = bookmark.title,
                    isIncognito = lastKnownIsIncognito,
                )
            }
            _uiState.update { state -> state.copy(isSearchActive = false, searchQuery = "") }
            eventChannel.trySend(BookmarksEvent.OpenUrl(bookmark.url))
        }
    }

    private fun onSearchClose() {
        searchQueryFlow.value = ""
        _uiState.update { it.copy(isSearchActive = false, searchQuery = "", searchResults = emptyList()) }
    }

    private fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun onDeleteBookmarkClick(bookmark: Bookmark) {
        viewModelScope.launch {
            mapErrorToEvent(bookmarks.deleteBookmark(bookmark.id))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onDeleteFolderClick(folder: BookmarkFolder) {
        viewModelScope.launch {
            val count = bookmarks.countDescendants(folder.id)
            if (count.total == 0) {
                mapErrorToEvent(bookmarks.deleteFolderCascade(folder.id))
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

    private fun onDialogDismiss() {
        _uiState.update {
            it.copy(pendingDialog = null, descendantCountForPendingDelete = null)
        }
    }

    private fun onConfirmCreateFolder(name: String, parentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(createFolder(name = name, parentId = parentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmRenameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            mapErrorToEvent(renameFolder(folderId = folderId, newName = newName))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmMoveFolder(folderId: Long, newParentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(bookmarks.moveFolder(folderId = folderId, newParentId = newParentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmMoveBookmark(bookmarkId: Long, newParentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(bookmarks.moveBookmarkToFolder(bookmarkId = bookmarkId, newParentId = newParentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmEditBookmark(edit: BookmarksAction.ConfirmEditBookmark) {
        viewModelScope.launch {
            mapErrorToEvent(
                updateBookmark(
                    bookmarkId = edit.bookmarkId,
                    title = edit.title,
                    url = edit.url,
                    parentFolderId = edit.parentFolderId,
                ),
            )
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmAddBookmark(title: String, url: String, parentId: Long?) {
        viewModelScope.launch {
            mapErrorToEvent(addBookmark(url = url, title = title, parentFolderId = parentId))
            _uiState.update { it.copy(pendingDialog = null) }
        }
    }

    private fun onConfirmDeleteFolder(folderId: Long) {
        viewModelScope.launch {
            mapErrorToEvent(bookmarks.deleteFolderCascade(folderId))
            _uiState.update { it.copy(pendingDialog = null, descendantCountForPendingDelete = null) }
        }
    }

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
                eventChannel.trySend(BookmarksEvent.Error(event))
            }
        }
    }
}
