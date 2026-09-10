package com.raumanian.thirtysix.browser.presentation.downloads

import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadRecord

/**
 * Spec 015 — immutable state for the Downloads screen.
 *
 * [items] pairs each persisted record with a status resolved at read time; nothing here is
 * persisted beyond the records themselves (FR-015).
 */
data class DownloadsUiState(
    val items: List<DownloadListItem> = emptyList(),
    val pendingActionSheetTarget: DownloadListItem? = null,
    val pendingDeleteConfirmation: DownloadRecord? = null,
    val isLoading: Boolean = true,
) {
    /**
     * FR-040 — "no downloads at all" is only true once loading has settled. Without the
     * [isLoading] guard the empty state would flash on every cold open before the first
     * database emission arrives.
     */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
