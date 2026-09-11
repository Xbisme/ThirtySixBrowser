package com.raumanian.thirtysix.browser.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord
import com.raumanian.thirtysix.browser.domain.usecase.CancelDownloadUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteDownloadedFileUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveDownloadsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.OpenDownloadResult
import com.raumanian.thirtysix.browser.domain.usecase.OpenDownloadedFileUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveDownloadRecordUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveDownloadStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
 * Spec 015 — the Downloads screen's ViewModel.
 *
 * ### Where the polling lives, and why it is nearly free
 *
 * Live status is read from the platform, never stored (FR-015). Two conditions gate the
 * polling loop, and together they mean the steady state costs nothing:
 *
 *  - **Screen visible.** [uiState] is shared with `WhileSubscribed`, so the loop runs only
 *    while something is collecting. Leaving the screen stops it.
 *  - **Something in flight.** The loop emits once and then *stops* when no item is still
 *    transferring. A list of finished downloads therefore polls exactly once, not once per
 *    second forever.
 *
 * A new download restarts the loop naturally: the record stream emits, `flatMapLatest`
 * cancels the settled loop and starts a fresh one.
 *
 * ### The main-thread trap this deliberately avoids
 *
 * Status resolution is `O(n)` and reaches a platform service, so it runs on
 * `dispatchers.default` via [flowOn]. Spec 014 shipped exactly this bug by deriving list
 * state inside `viewModelScope` — which dispatches on `Dispatchers.Main.immediate` — and
 * paid a 200 ms p99 frame while typing until it was found on-device. Not repeating it.
 */
// Eight collaborators, past detekt's constructorThreshold of 7. Each is a distinct
// user-visible action the screen offers (open · copy · remove · delete · cancel) plus the
// two read paths and a dispatcher; bundling them would hide that one-to-one mapping without
// removing a single dependency. Same call the project already made for `TabsViewModel`.
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class DownloadsViewModel @Inject constructor(
    observeDownloads: ObserveDownloadsUseCase,
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

    /** One-shot events for the snackbar host. */
    val events: SharedFlow<DownloadsEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemsFlow: Flow<List<DownloadListItem>> = observeDownloads()
        .flatMapLatest { records -> pollStatuses(records) }
        .flowOn(dispatchers.default)

    val uiState: StateFlow<DownloadsUiState> =
        combine(itemsFlow, interaction) { items, state ->
            DownloadsUiState(
                items = items,
                // Re-resolved against the freshest list on every emission, so the action
                // matrix is evaluated on the entry's *current* state rather than the state
                // it had when long-pressed. A row that disappears underneath an open sheet
                // (deleted from elsewhere) closes it, because the lookup yields null.
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

    /**
     * Emit once, then keep re-emitting only while at least one entry is still transferring
     * (research.md R7). The loop ends on its own the moment everything settles.
     */
    private fun pollStatuses(records: List<DownloadRecord>): Flow<List<DownloadListItem>> = flow {
        while (true) {
            val items = resolveStatus(records)
            emit(items)
            if (items.none { it.status.isInFlight }) break
            delay(BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS)
        }
    }

    // ---- interaction -------------------------------------------------------------

    /** FR-030 — long-press opens the action sheet for this entry. */
    fun onItemLongPressed(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = item.record.id) }
    }

    /** FR-035 — dismissing performs nothing. */
    fun onActionSheetDismissed() {
        interaction.update { it.copy(actionSheetTarget = null) }
    }

    /** FR-032 — deleting a file always goes through an explicit confirmation. */
    fun onDeleteFileRequested(record: DownloadRecord) {
        interaction.update { it.copy(actionSheetTarget = null, deleteConfirmation = record) }
    }

    fun onDeleteConfirmationDismissed() {
        interaction.update { it.copy(deleteConfirmation = null) }
    }

    // ---- actions -----------------------------------------------------------------

    /** FR-025 – FR-029 — tapping a row. Every failure mode reports its own event. */
    fun onItemClicked(item: DownloadListItem) {
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

    /** FR-033 — copy the address the file came from. */
    fun onCopyLinkClicked(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = null) }
        val copied = clipboardWriter.copyUrl(item.record.sourceUrl, AppConstants.CLIPBOARD_DOWNLOAD_LINK_LABEL)
        emitEvent(if (copied) DownloadsEvent.LinkCopied else DownloadsEvent.OperationFailed)
    }

    /** FR-031 — remove the row, leave the file. Withheld while in flight (FR-034b). */
    fun onRemoveFromListClicked(item: DownloadListItem) {
        interaction.update { it.copy(actionSheetTarget = null) }
        viewModelScope.launch {
            val removed = removeDownloadRecord(item)
            emitEvent(if (removed) DownloadsEvent.RecordRemoved else DownloadsEvent.OperationFailed)
        }
    }

    /** FR-032 — runs only after the confirmation dialog. */
    fun onDeleteFileConfirmed(record: DownloadRecord) {
        interaction.update { it.copy(deleteConfirmation = null) }
        viewModelScope.launch {
            val deleted = deleteDownloadedFile(record)
            emitEvent(if (deleted) DownloadsEvent.FileDeleted else DownloadsEvent.OperationFailed)
        }
    }

    /** FR-036 – FR-039 — one-tap cancel from the row. */
    fun onCancelClicked(item: DownloadListItem) {
        viewModelScope.launch {
            val cancelled = cancelDownload(item)
            // FR-039: a transfer that finished in the meantime is left alone and reports
            // nothing, rather than claiming a cancellation that did not happen.
            if (cancelled) emitEvent(DownloadsEvent.DownloadCancelled)
        }
    }

    internal fun emitEvent(event: DownloadsEvent) {
        _events.tryEmit(event)
    }

    private data class InteractionState(
        val actionSheetTarget: Long? = null,
        val deleteConfirmation: DownloadRecord? = null,
    )

    private companion object {
        /**
         * Keeps the pipeline alive briefly across configuration changes so a rotation does
         * not tear down and restart the polling loop.
         */
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L
    }
}
