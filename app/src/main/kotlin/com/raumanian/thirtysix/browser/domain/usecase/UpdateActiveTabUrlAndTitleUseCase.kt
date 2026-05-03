package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject

/**
 * Spec 011 — write-through bridge from `BrowserViewModel`'s WebView callbacks
 * (`onUrlChanged`, `onLoadFinished`, `onTitleReceived`) to [TabRepository]
 * (FR-022 / R4).
 *
 * The use-case-coordination layer is the project's accepted seam for cross-
 * feature wiring (Constitution §IV). `BrowserViewModel` injects this use case
 * instead of `TabRepository` directly so the `browser/` feature stays
 * decoupled from the `tabs/` data layer.
 *
 * Spec 012 extension — adds an [isIncognito] dispatch parameter. When `true`,
 * the write goes to [IncognitoTabRepository] (in-memory only — never persisted
 * per FR-002). When `false`, the existing Spec 011 [TabRepository] write path
 * is preserved. UseCase → 2 Repositories is allowed by Constitution §IV
 * (use cases are the canonical coordination layer); only Repository →
 * Repository is forbidden.
 */
class UpdateActiveTabUrlAndTitleUseCase @Inject constructor(
    private val repository: TabRepository,
    private val incognitoRepository: IncognitoTabRepository,
) {
    suspend operator fun invoke(
        tabId: Long,
        url: String,
        title: String,
        isIncognito: Boolean = false,
    ) {
        if (isIncognito) {
            incognitoRepository.updateTabUrlAndTitle(tabId, url, title)
        } else {
            repository.updateTabUrlAndTitle(tabId, url, title)
        }
    }
}
