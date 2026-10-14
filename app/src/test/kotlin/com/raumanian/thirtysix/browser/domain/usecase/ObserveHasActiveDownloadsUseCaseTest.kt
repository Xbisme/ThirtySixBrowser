package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 019 FR-031 — the hub's downloads-in-progress signal. */
class ObserveHasActiveDownloadsUseCaseTest {

    private fun useCase(gateway: RecordingGateway, repository: InMemoryDownloadsRepository) =
        ObserveHasActiveDownloadsUseCase(
            observeDownloads = ObserveDownloadsUseCase(repository),
            resolveStatus = ResolveDownloadStatusUseCase(gateway, repository),
        )

    @Test
    fun noDownloads_isFalse() = runTest {
        useCase(RecordingGateway(), InMemoryDownloadsRepository()).invoke(POLL).test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRunningDownload_isTrue_andTurnsFalseWhenItCompletes() = runTest {
        val record = testRecord(handle = HANDLE)
        val gateway = RecordingGateway(statuses = mutableMapOf(HANDLE to DownloadStatus.Running(1L, 10L)))
        useCase(gateway, InMemoryDownloadsRepository(listOf(record))).invoke(POLL).test {
            assertEquals(true, awaitItem())
            gateway.setStatus(HANDLE, DownloadStatus.Complete(10L))
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onlyFinishedDownloads_isFalse() = runTest {
        val record = testRecord(handle = HANDLE)
        val gateway = RecordingGateway(statuses = mutableMapOf(HANDLE to DownloadStatus.Complete(10L)))
        useCase(gateway, InMemoryDownloadsRepository(listOf(record))).invoke(POLL).test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val HANDLE = 10L
        const val POLL = 10L
    }
}
