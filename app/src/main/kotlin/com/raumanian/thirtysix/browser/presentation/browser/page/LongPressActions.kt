package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import com.raumanian.thirtysix.browser.domain.usecase.OpenInBackgroundTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SwitchToTabUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** What the browser screen tells the person after a long-press action (C2.4). */
sealed interface LongPressEvent {
    data class OpenedInBackground(val tabId: Long, val incognito: Boolean) : LongPressEvent

    data object Copied : LongPressEvent

    data object TabLimitReached : LongPressEvent
}

/**
 * Spec 021 US4 — the long-press actions that change app state: a tab opened behind the active
 * one (FR-020, FR-021), Switch to it, and copying an address (FR-022). Sharing and downloading
 * are started by the screen, which owns the activity and the storage-permission gate.
 */
@HiltViewModel
class LongPressActions @Inject constructor(
    private val openInBackgroundTab: OpenInBackgroundTabUseCase,
    private val switchToTab: SwitchToTabUseCase,
    private val clipboardWriter: ClipboardWriter,
) : ViewModel() {
    private val _events = MutableSharedFlow<LongPressEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LongPressEvent> = _events.asSharedFlow()

    fun openInBackground(url: String, incognito: Boolean) {
        viewModelScope.launch {
            val event = when (val result = openInBackgroundTab(url, incognito)) {
                is Result.Success -> LongPressEvent.OpenedInBackground(result.data.id, incognito)
                is Result.Error -> LongPressEvent.TabLimitReached
            }
            _events.tryEmit(event)
        }
    }

    fun switchTo(tabId: Long) {
        viewModelScope.launch { switchToTab(tabId) }
    }

    fun copy(url: String) {
        if (clipboardWriter.copyUrl(url)) _events.tryEmit(LongPressEvent.Copied)
    }
}
