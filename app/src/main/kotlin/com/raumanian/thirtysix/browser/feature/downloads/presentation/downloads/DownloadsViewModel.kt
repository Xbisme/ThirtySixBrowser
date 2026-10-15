package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ResolveDownloadStatusUseCase
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.CancelDownloadUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.DeleteDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.OpenDownloadResult
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.OpenDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.RemoveDownloadRecordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Polls live status only while the screen is collected and something is in flight, so a list of
 * finished downloads is resolved once. Resolution is O(n) and hits a platform service, so it runs
 * on the default dispatcher, never on `viewModelScope`'s main dispatcher.
 */
// Eight collaborators, one per user action plus the read paths and a dispatcher; bundling them hides that mapping.
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class DownloadsViewModel @Inject constructor(
    downloads: DownloadsRepository,
    private val resolveStatus: ResolveDownloadStatusUseCase,
    private val openDownloadedFile: OpenDownloadedFileUseCase,
    private val removeDownloadRecord: RemoveDownloadRecordUseCase,
    private val deleteDownloadedFile: DeleteDownloadedFileUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val clipboardWriter: ClipboardWriter,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val interaction = MutableStateFlow(InteractionState())

    private val _events = MutableSharedFlow<DownloadsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<DownloadsEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemsFlow: Flow<List<DownloadListItem>> = downloads.observeAll()
        .flatMapLatest { records -> pollStatuses(records) }
        .flowOn(dispatchers.default)

    val uiState: StateFlow<DownloadsUiState> =
        combine(itemsFlow, interaction) { items, state ->
            DownloadsUiState(
                items = items,
                // Re-resolved on every emission so the sheet reflects the entry's current state; a row that
                // disappears underneath an open sheet closes it.
                pendingActionSheetTarget = state.actionSheetTarget
                    ?.let { id -> items.firstOrNull { it.record.id == id } },
                pendingDeleteConfirmation = state.deleteConfirmation,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS),
            initialValue = DownloadsUiState(),
        )

    // Emits once, then keeps re-emitting only while an entry is still transferring.
    private fun pollStatuses(records: List<DownloadRecord>): Flow<List<DownloadListItem>> = flow {
        while (true) {
            val items = resolveStatus(records)
            emit(items)
            if (items.none { it.status.isInFlight }) break
            delay(BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS)
        }
    }

    fun onAction(action: DownloadsAction) {
        when (action) {
            is DownloadsAction.ItemClicked -> onItemClicked(action.item)
            is DownloadsAction.ItemLongPressed -> onItemLongPressed(action.item)
            DownloadsAction.ActionSheetDismissed -> onActionSheetDismissed()
            is DownloadsAction.CopyLinkClicked -> onCopyLinkClicked(action.item)
            is DownloadsAction.RemoveFromListClicked -> onRemoveFromListClicked(action.item)
            is DownloadsAction.DeleteFileRequested -> onDeleteFileRequested(action.record)
            is DownloadsAction.DeleteFileConfirmed -> onDeleteFileConfirmed(action.record)
            DownloadsAction.DeleteConfirmationDismissed -> onDeleteConfirmationDismissed()
            is DownloadsAction.CancelClicked -> onCancelClicked(action.item)
        }
    }

    private fun onItemLongPressed(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = item.record.id) }
    }

    /** Dismissing performs nothing. */
    private fun onActionSheetDismissed() {
        interaction.update { it.copy(actionSheetTarget = null) }
    }

    private fun onDeleteFileRequested(record: DownloadRecord) {
        interaction.update { it.copy(actionSheetTarget = null, deleteConfirmation = record) }
    }

    private fun onDeleteConfirmationDismissed() {
        interaction.update { it.copy(deleteConfirmation = null) }
    }

    private fun onItemClicked(item: DownloadListItem) {
        viewModelScope.launch {
            val event = when (openDownloadedFile(item)) {
                OpenDownloadResult.Opened -> null
                OpenDownloadResult.NotComplete -> DownloadsEvent.DownloadNotComplete
                OpenDownloadResult.FileMissing -> DownloadsEvent.FileMissing(item)
                OpenDownloadResult.NoAppAvailable -> DownloadsEvent.NoAppCanOpenFile
            }
            event?.let(::emitEvent)
        }
    }

    private fun onCopyLinkClicked(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = null) }
        val copied = clipboardWriter.copyUrl(item.record.sourceUrl, AppConstants.CLIPBOARD_DOWNLOAD_LINK_LABEL)
        emitEvent(if (copied) DownloadsEvent.LinkCopied else DownloadsEvent.OperationFailed)
    }

    /** Removes the row and leaves the file; withheld while in flight. */
    private fun onRemoveFromListClicked(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = null) }
        viewModelScope.launch {
            val removed = removeDownloadRecord(item)
            emitEvent(if (removed) DownloadsEvent.RecordRemoved else DownloadsEvent.OperationFailed)
        }
    }

    /** Runs only after the confirmation dialog. */
    private fun onDeleteFileConfirmed(record: DownloadRecord) {
        interaction.update { it.copy(deleteConfirmation = null) }
        viewModelScope.launch {
            val deleted = deleteDownloadedFile(record)
            emitEvent(if (deleted) DownloadsEvent.FileDeleted else DownloadsEvent.OperationFailed)
        }
    }

    private fun onCancelClicked(item: DownloadListItem) {
        viewModelScope.launch {
            val cancelled = cancelDownload(item)
            // A transfer that finished in the meantime is left alone and reports nothing.
            if (cancelled) emitEvent(DownloadsEvent.DownloadCancelled)
        }
    }

    private fun emitEvent(event: DownloadsEvent) {
        _events.tryEmit(event)
    }

    private data class InteractionState(
        val actionSheetTarget: Long? = null,
        val deleteConfirmation: DownloadRecord? = null,
    )

    private companion object {
        // Keeps the pipeline alive across a rotation so the polling loop is not restarted.
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L
    }
}
