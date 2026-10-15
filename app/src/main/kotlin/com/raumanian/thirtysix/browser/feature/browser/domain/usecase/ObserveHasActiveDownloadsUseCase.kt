package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DownloadsRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ResolveDownloadStatusUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Whether any download is still in flight, for the hub's "downloads in progress" button. The
 * platform is polled only while something is in flight, and only while the hub collects this.
 */
class ObserveHasActiveDownloadsUseCase @Inject constructor(
    private val downloads: DownloadsRepository,
    private val resolveStatus: ResolveDownloadStatusUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(pollIntervalMs: Long = BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS): Flow<Boolean> =
        downloads.observeAll()
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
