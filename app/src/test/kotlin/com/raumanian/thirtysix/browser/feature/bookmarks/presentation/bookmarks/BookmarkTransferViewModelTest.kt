package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ExportBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ImportBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.fake.FakeBookmarkFileRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.BookmarkFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkTransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val bookmarks = FakeBookmarkRepository()
    private val files = FakeBookmarkFileRepository()
    private val viewModel = BookmarkTransferViewModel(
        importBookmarks = ImportBookmarksUseCase(files, bookmarks),
        exportBookmarks = ExportBookmarksUseCase(bookmarks, files),
        bookmarks = bookmarks,
    )
    private val names = BookmarkImportNames("Imported", "Untitled")

    private fun chromeFile() =
        BookmarkFileReadResult.Parsed(NetscapeBookmarkParser().parse(BookmarkFiles.read("chrome.html"), "Untitled")!!)

    @Test
    fun `import from the sheet closes it and asks for a file`() = runTest {
        viewModel.onAction(BookmarkTransferAction.SheetOpen)
        assertTrue(viewModel.uiState.value.isSheetOpen)

        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.ImportClick)
            assertEquals(BookmarkTransferEvent.LaunchImportPicker, awaitItem())
        }
        assertFalse(viewModel.uiState.value.isSheetOpen)
    }

    @Test
    fun `a picked file shows progress, then the summary, then opens the folder`() = runTest {
        files.readResult = chromeFile()
        val gate = CompletableDeferred<Unit>()
        files.readGate = gate

        viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://f", names))
        advanceUntilIdle()
        assertEquals(BookmarkTransferProgress.Importing, viewModel.uiState.value.progress)

        gate.complete(Unit)
        advanceUntilIdle()
        val done = viewModel.uiState.value.progress as BookmarkTransferProgress.Imported
        assertEquals(4, done.result.imported)

        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.OpenImportedFolder(done.result.folderId))
            assertEquals(BookmarkTransferEvent.OpenFolder(done.result.folderId), awaitItem())
        }
        assertEquals(BookmarkTransferProgress.Idle, viewModel.uiState.value.progress)
    }

    @Test
    fun `a second import while one runs is ignored`() = runTest {
        files.readResult = chromeFile()
        val gate = CompletableDeferred<Unit>()
        files.readGate = gate
        viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://a", names))
        advanceUntilIdle()

        viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://b", names))
        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.ImportClick)
            expectNoEvents()
        }
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, bookmarks.importCalls)
    }

    @Test
    fun `a failed import returns to idle and reports why`() = runTest {
        files.readResult = BookmarkFileReadResult.NotABookmarkFile

        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://f", names))
            assertEquals(BookmarkTransferEvent.ImportFailed(BookmarkImportResult.NotABookmarkFile), awaitItem())
        }
        assertEquals(BookmarkTransferProgress.Idle, viewModel.uiState.value.progress)
    }

    @Test
    fun `dismissing the summary returns to idle`() = runTest {
        files.readResult = chromeFile()
        viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://f", names))
        advanceUntilIdle()

        viewModel.onAction(BookmarkTransferAction.SummaryDismiss)

        assertEquals(BookmarkTransferProgress.Idle, viewModel.uiState.value.progress)
    }

    @Test
    fun `export with nothing saved says so without opening the picker`() = runTest {
        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.ExportClick("thirtysix-bookmarks-2026-10-17.html"))
            assertEquals(BookmarkTransferEvent.ExportFinished(BookmarkExportResult.NothingToExport), awaitItem())
        }
    }

    @Test
    fun `export asks for a location with the suggested name, then reports the count`() = runTest {
        bookmarks.seedBookmark(Bookmark(0L, "A", "https://a.example/", null, 0L, 0L))

        viewModel.events.test {
            viewModel.onAction(BookmarkTransferAction.ExportClick("thirtysix-bookmarks-2026-10-17.html"))
            assertEquals(BookmarkTransferEvent.LaunchExportPicker("thirtysix-bookmarks-2026-10-17.html"), awaitItem())

            viewModel.onAction(BookmarkTransferAction.ExportFilePicked("content://out"))
            assertEquals(BookmarkTransferEvent.ExportFinished(BookmarkExportResult.Exported(1, 0)), awaitItem())
        }
        assertEquals(BookmarkTransferProgress.Idle, viewModel.uiState.value.progress)
        assertTrue("content://out" in files.written)
    }

    @Test
    fun `clearing the view model during an import writes nothing`() = runTest {
        files.readResult = chromeFile()
        val gate = CompletableDeferred<Unit>()
        files.readGate = gate
        viewModel.onAction(BookmarkTransferAction.ImportFilePicked("content://f", names))
        advanceUntilIdle()

        viewModel.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, bookmarks.importCalls)
    }
}
