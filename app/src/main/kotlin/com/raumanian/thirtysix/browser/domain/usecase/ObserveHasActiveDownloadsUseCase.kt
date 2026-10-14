package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Spec 019 FR-031 — whether any download is still in flight, for the hub's
 * "downloads in progress" button.
 *
 * Reuses Spec 015's join ([ResolveDownloadStatusUseCase]) and polling rule: the platform is
 * asked again only while something is in flight, and the loop ends as soon as nothing is.
 * The hub collects this only while it is open (analysis U2), so a closed hub polls nothing.
 */
class ObserveHasActiveDownloadsUseCase @Inject constructor(
    private val observeDownloads: ObserveDownloadsUseCase,
    private val resolveStatus: ResolveDownloadStatusUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(pollIntervalMs: Long = BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS): Flow<Boolean> =
        observeDownloads()
            .flatMapLatest { records ->
                flow {
                    while (true) {
                        val active = resolveStatus(records).any { it.status.isInFlight }
                        emit(active)
                        if (!active) break
                        delay(pollIntervalMs)
                    }
                }
            }
            .distinctUntilChanged()
}
