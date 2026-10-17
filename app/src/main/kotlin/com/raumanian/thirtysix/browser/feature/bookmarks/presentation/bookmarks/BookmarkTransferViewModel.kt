package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ExportBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ImportBookmarksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Import and export of the bookmark file, one at a time. Work runs in the ViewModel's scope:
 * rotation keeps it, leaving the screen cancels it, and a cancelled import writes nothing.
 */
@HiltViewModel
class BookmarkTransferViewModel @Inject constructor(
    private val importBookmarks: ImportBookmarksUseCase,
    private val exportBookmarks: ExportBookmarksUseCase,
    private val bookmarks: BookmarkRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkTransferUiState())
    val uiState: StateFlow<BookmarkTransferUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<BookmarkTransferEvent>(Channel.BUFFERED)
    val events: Flow<BookmarkTransferEvent> = eventChannel.receiveAsFlow()

    fun onAction(action: BookmarkTransferAction) {
        when (action) {
            BookmarkTransferAction.SheetOpen -> _uiState.update { it.copy(isSheetOpen = true) }
            BookmarkTransferAction.SheetDismiss -> _uiState.update { it.copy(isSheetOpen = false) }
            BookmarkTransferAction.ImportClick -> onImportClick()
            is BookmarkTransferAction.ExportClick -> onExportClick(action.suggestedFileName)
            is BookmarkTransferAction.ImportFilePicked -> onImportFilePicked(action.uri, action.names)
            is BookmarkTransferAction.ExportFilePicked -> onExportFilePicked(action.uri)
            BookmarkTransferAction.SummaryDismiss ->
                _uiState.update { it.copy(progress = BookmarkTransferProgress.Idle) }
            is BookmarkTransferAction.OpenImportedFolder -> {
                _uiState.update { it.copy(progress = BookmarkTransferProgress.Idle) }
                eventChannel.trySend(BookmarkTransferEvent.OpenFolder(action.folderId))
            }
        }
    }

    /** Closes the sheet; `false` while a transfer runs, so only one runs at a time. */
    private fun closeSheetIfIdle(): Boolean {
        val idle = !_uiState.value.isBusy
        _uiState.update { it.copy(isSheetOpen = false) }
        return idle
    }

    private fun onImportClick() {
        if (closeSheetIfIdle()) eventChannel.trySend(BookmarkTransferEvent.LaunchImportPicker)
    }

    private fun onExportClick(suggestedFileName: String) {
        if (!closeSheetIfIdle()) return
        viewModelScope.launch {
            val isEmpty = bookmarks.countBookmarks() == 0 && bookmarks.countFolders() == 0
            val event = if (isEmpty) {
                BookmarkTransferEvent.ExportFinished(BookmarkExportResult.NothingToExport)
            } else {
                BookmarkTransferEvent.LaunchExportPicker(suggestedFileName)
            }
            eventChannel.trySend(event)
        }
    }

    private fun onImportFilePicked(uri: String, names: BookmarkImportNames) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(progress = BookmarkTransferProgress.Importing) }
        viewModelScope.launch {
            val result = importBookmarks(uri, names)
            if (result is BookmarkImportResult.Imported) {
                _uiState.update { it.copy(progress = BookmarkTransferProgress.Imported(result)) }
            } else {
                _uiState.update { it.copy(progress = BookmarkTransferProgress.Idle) }
                eventChannel.trySend(BookmarkTransferEvent.ImportFailed(result))
            }
        }
    }

    private fun onExportFilePicked(uri: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(progress = BookmarkTransferProgress.Exporting) }
        viewModelScope.launch {
            val result = exportBookmarks(uri)
            _uiState.update { it.copy(progress = BookmarkTransferProgress.Idle) }
            eventChannel.trySend(BookmarkTransferEvent.ExportFinished(result))
        }
    }
}
