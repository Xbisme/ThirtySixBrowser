package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.StartDownloadResult
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Downloads the page or a long-pressed link asks for, after the storage-permission gate. */
@HiltViewModel
class DownloadRequestViewModel @Inject constructor(
    private val startDownload: StartDownloadUseCase,
) : ViewModel() {
    // Kept until the screen collects them, as the consumed-once state fields they replace were.
    private val eventChannel = Channel<DownloadSnackbarEvent>(Channel.BUFFERED)
    val events: Flow<DownloadSnackbarEvent> = eventChannel.receiveAsFlow()

    fun onAction(action: DownloadRequestAction) {
        when (action) {
            is DownloadRequestAction.Requested -> onRequested(action)
            is DownloadRequestAction.StoragePermissionDenied -> eventChannel.trySend(
                if (action.permanently) {
                    DownloadSnackbarEvent.StoragePermissionPermanentlyDenied
                } else {
                    DownloadSnackbarEvent.StoragePermissionDenied
                },
            )
        }
    }

    // Incognito downloads are recorded like any other; the tab's incognito state is not consulted.
    private fun onRequested(request: DownloadRequestAction.Requested) {
        viewModelScope.launch {
            val result = startDownload(
                url = request.url,
                contentDisposition = request.contentDisposition,
                mimeType = request.mimeType,
                userAgent = request.userAgent,
                referer = request.referer,
                cookies = request.cookies,
            )
            val event = when (result) {
                is StartDownloadResult.Started -> DownloadSnackbarEvent.Started(result.fileName)
                StartDownloadResult.ServiceUnavailable -> DownloadSnackbarEvent.ServiceUnavailable
                StartDownloadResult.Unsupported -> DownloadSnackbarEvent.Unsupported
            }
            eventChannel.trySend(event)
        }
    }
}
