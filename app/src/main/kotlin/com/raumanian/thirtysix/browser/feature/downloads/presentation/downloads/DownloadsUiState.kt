package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadRecord
// Each persisted record paired with a status resolved at read time; nothing else is persisted.
data class DownloadsUiState(
    val items: List<DownloadListItem> = emptyList(),
    val pendingActionSheetTarget: DownloadListItem? = null,
    val pendingDeleteConfirmation: DownloadRecord? = null,
    val isLoading: Boolean = true,
) {
    // Guarded by [isLoading] so the empty state does not flash before the first database emission.
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
