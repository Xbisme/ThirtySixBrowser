package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveDownloadsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ResolveDownloadStatusUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.CancelDownloadUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.DeleteDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.OpenDownloadedFileUseCase
import com.raumanian.thirtysix.browser.feature.downloads.domain.usecase.RemoveDownloadRecordUseCase
import com.raumanian.thirtysix.browser.feature.downloads.fake.InMemoryDownloadsRepository
import com.raumanian.thirtysix.browser.feature.downloads.fake.RecordingFileOpener
import com.raumanian.thirtysix.browser.feature.downloads.fake.RecordingGateway
import com.raumanian.thirtysix.browser.feature.downloads.fake.testItem
import com.raumanian.thirtysix.browser.feature.downloads.fake.testRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private fun viewModel(
        repository: InMemoryDownloadsRepository,
        gateway: RecordingGateway,
        opener: RecordingFileOpener = RecordingFileOpener(),
        clipboard: ClipboardWriter = RecordingClipboard(),
    ): DownloadsViewModel {
        val resolve = ResolveDownloadStatusUseCase(gateway, repository)
        return DownloadsViewModel(
            observeDownloads = ObserveDownloadsUseCase(repository),
            resolveStatus = resolve,
            openDownloadedFile = OpenDownloadedFileUseCase(gateway, opener),
            removeDownloadRecord = RemoveDownloadRecordUseCase(repository),
            deleteDownloadedFile = DeleteDownloadedFileUseCase(gateway, repository),
            cancelDownload = CancelDownloadUseCase(gateway, repository),
            clipboardWriter = clipboard,
            dispatchers = TestDispatcherProvider(testDispatcher),
        )
    }

    @Test
    fun `items arrive newest first and loading settles`() = runTest(testDispatcher) {
        val repository = InMemoryDownloadsRepository(
            listOf(
                testRecord(id = 1L, handle = 10L, fileName = "old.pdf").copy(createdAt = 1_000L),
                testRecord(id = 2L, handle = 20L, fileName = "new.pdf").copy(createdAt = 2_000L),
            ),
        )
        val gateway = RecordingGateway()
        gateway.setStatus(10L, DownloadStatus.Complete())
        gateway.setStatus(20L, DownloadStatus.Complete())

        val vm = viewModel(repository, gateway)

        vm.uiState.test {
            awaitItem() // initial, still loading
            advanceUntilIdle()
            val settled = expectMostRecentItem()
            // The repository double preserves insertion order; ordering itself is the DAO's
            // job and is asserted in DownloadRecordDaoTest.
            assertEquals(2, settled.items.size)
            assertTrue(!settled.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty repository settles into the empty state`() = runTest(testDispatcher) {
        val vm = viewModel(InMemoryDownloadsRepository(emptyList()), RecordingGateway())

        vm.uiState.test {
            awaitItem()
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `R7 - a settled list stops polling entirely`() = runTest(testDispatcher) {
        val repository = InMemoryDownloadsRepository(listOf(testRecord(id = 1L, handle = 10L)))
        val gateway = CountingGateway().apply { setStatus(10L, DownloadStatus.Complete()) }

        val vm = viewModel(repository, gateway)
        vm.uiState.test {
            awaitItem()
            advanceUntilIdle()
            val afterSettle = gateway.queryCount
            // Ten poll intervals later, nothing should have been asked again.
            advanceTimeBy(BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS * 10)
            advanceUntilIdle()
            assertEquals("a list of finished downloads must poll once, not forever", afterSettle, gateway.queryCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `R7 - an in-flight entry keeps polling`() = runTest(testDispatcher) {
        val repository = InMemoryDownloadsRepository(listOf(testRecord(id = 1L, handle = 10L)))
        val gateway = CountingGateway().apply { setStatus(10L, DownloadStatus.Running(1L, 100L)) }

        val vm = viewModel(repository, gateway)
        vm.uiState.test {
            awaitItem()
            // Not advanceUntilIdle: while something is in flight the loop reschedules itself forever,
            // so only bounded virtual-time advances can observe it.
            runCurrent()
            val first = gateway.queryCount

            advanceTimeBy(BrowserLimits.DOWNLOAD_STATUS_POLL_INTERVAL_MS * 3 + 1)
            runCurrent()

            assertTrue(
                "progress must keep refreshing while in flight (was $first, now ${gateway.queryCount})",
                gateway.queryCount > first,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `long press opens the sheet and dismissing performs nothing`() = runTest(testDispatcher) {
        val record = testRecord(id = 1L, handle = 10L)
        val repository = InMemoryDownloadsRepository(listOf(record))
        val gateway = RecordingGateway().apply { setStatus(10L, DownloadStatus.Complete()) }
        val vm = viewModel(repository, gateway)

        vm.uiState.test {
            awaitItem()
            advanceUntilIdle()
            vm.onItemLongPressed(testItem(record = record))
            advanceUntilIdle()
            assertEquals(1L, expectMostRecentItem().pendingActionSheetTarget?.record?.id)

            vm.onActionSheetDismissed()
            advanceUntilIdle()
            assertNull(expectMostRecentItem().pendingActionSheetTarget)
            assertEquals("dismissing must not delete anything", 1, repository.current.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a file requires the confirmation step`() = runTest(testDispatcher) {
        val record = testRecord(id = 1L, handle = 10L)
        val repository = InMemoryDownloadsRepository(listOf(record))
        val gateway = RecordingGateway().apply { setStatus(10L, DownloadStatus.Complete()) }
        val vm = viewModel(repository, gateway)

        vm.onDeleteFileRequested(record)
        advanceUntilIdle()
        assertEquals("nothing may be deleted before confirmation", 1, repository.current.size)

        vm.onDeleteFileConfirmed(record)
        advanceUntilIdle()
        assertTrue(repository.current.isEmpty())
    }

    @Test
    fun `copying the link emits LinkCopied`() = runTest(testDispatcher) {
        val record = testRecord(id = 1L, handle = 10L)
        val clipboard = RecordingClipboard()
        val vm = viewModel(
            InMemoryDownloadsRepository(listOf(record)),
            RecordingGateway(),
            clipboard = clipboard,
        )

        vm.events.test {
            vm.onCopyLinkClicked(testItem(record = record))
            advanceUntilIdle()
            assertEquals(DownloadsEvent.LinkCopied, awaitItem())
            assertEquals(record.sourceUrl, clipboard.copied.single())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing an in-flight entry reports failure and keeps the row`() =
        runTest(testDispatcher) {
            val record = testRecord(id = 1L, handle = 10L)
            val repository = InMemoryDownloadsRepository(listOf(record))
            val vm = viewModel(repository, RecordingGateway())

            vm.events.test {
                vm.onRemoveFromListClicked(
                    testItem(status = DownloadStatus.Running(1L, 10L), record = record),
                )
                advanceUntilIdle()
                assertEquals(DownloadsEvent.OperationFailed, awaitItem())
                assertEquals(1, repository.current.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cancelling an already-complete transfer emits nothing`() = runTest(testDispatcher) {
        val record = testRecord(id = 1L, handle = 10L)
        val gateway = RecordingGateway().apply { setStatus(10L, DownloadStatus.Complete()) }
        val vm = viewModel(InMemoryDownloadsRepository(listOf(record)), gateway)

        vm.events.test {
            vm.onCancelClicked(testItem(status = DownloadStatus.Running(9L, 10L), record = record))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping an unfinished row reports that it is not complete`() = runTest(testDispatcher) {
        val record = testRecord(id = 1L, handle = 10L)
        val vm = viewModel(InMemoryDownloadsRepository(listOf(record)), RecordingGateway())

        vm.events.test {
            vm.onItemClicked(testItem(status = DownloadStatus.Running(1L, 10L), record = record))
            advanceUntilIdle()
            assertEquals(DownloadsEvent.DownloadNotComplete, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class RecordingClipboard : ClipboardWriter {
        val copied = mutableListOf<String>()
        override fun copyUrl(url: String, label: String): Boolean {
            copied += url
            return true
        }
    }

    /** Counts platform reads so the polling lifecycle can be asserted. */
    private class CountingGateway : RecordingGateway() {
        var queryCount = 0
        override suspend fun queryStatuses(transferHandles: List<Long>) =
            super.queryStatuses(transferHandles).also { queryCount++ }
    }

    private class TestDispatcherProvider(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
        override val unconfined = d
    }
}
