package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Spec 014 FR-001 (title backfill) — correct the title of a history row that was
 * recorded before its page announced a `<title>`.
 *
 * `WebChromeClient.onReceivedTitle` routinely fires *after*
 * `WebViewClient.onPageFinished`, so `RecordHistoryEntryUseCase` stores whatever title
 * was known at page-finish (often empty, which the row UI renders as the hostname per
 * FR-009) and `BrowserViewModel` calls this the moment the real title arrives for the
 * same navigation.
 *
 * A blank title is ignored rather than written, so a page that clears its title can
 * never blank out a row that already had a good one.
 *
 * @return rows updated — `0` when the row has since been deleted or [title] was blank.
 */
class UpdateHistoryEntryTitleUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Long, title: String): Int {
        if (id <= 0L || title.isBlank()) return 0
        return repository.updateTitle(id, title)
    }
}
