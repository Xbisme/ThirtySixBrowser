package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import javax.inject.Inject

/**
 * Patches the title of a history row recorded before its page announced one: `onReceivedTitle`
 * often fires after `onPageFinished`. A blank title is ignored, so a page clearing its title never
 * blanks a good row. Returns rows updated (`0` if deleted or blank).
 */
class UpdateHistoryEntryTitleUseCase @Inject constructor(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(id: Long, title: String): Int {
        if (id <= 0L || title.isBlank()) return 0
        return repository.updateTitle(id, title)
    }
}
