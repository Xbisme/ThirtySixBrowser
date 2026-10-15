package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.CreateFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ObserveBookmarksByFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ObserveFolderPathUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.RenameFolderUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.SearchBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.UpdateBookmarkUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `initial state is at root with empty lists`() = runTest {
        val repo = FakeBookmarkRepository()
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertNull(state.currentFolderId)
        assertEquals(0, state.bookmarks.size)
        assertEquals(0, state.folders.size)
    }

    @Test
    fun `seeded bookmarks at root are emitted`() = runTest {
        val repo = FakeBookmarkRepository()
        repo.seedBookmark(Bookmark(0, "X", "https://x.com", null, 100L, 100L))
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.first { it.bookmarks.isNotEmpty() }
        assertEquals(1, state.bookmarks.size)
    }

    @Test
    fun `tap into folder updates currentFolderId and breadcrumb`() = runTest {
        val repo = FakeBookmarkRepository()
        val folder = BookmarkFolder(0, "Work", null, 1L)
        val id = repo.seedFolder(folder)
        val vm = newViewModel(repo)
        advanceUntilIdle()

        vm.onAction(BookmarksAction.FolderTap(folder.copy(id = id)))
        advanceUntilIdle()

        val state = vm.uiState.first { it.currentFolderId == id }
        assertEquals(id, state.currentFolderId)
        assertEquals(listOf("Work"), state.breadcrumb.map { it.name })
    }

    @Test
    fun `bookmark tap emits openUrl signal for navigation pop`() = runTest {
        val repo = FakeBookmarkRepository()
        val bookmarkId = repo.seedBookmark(Bookmark(0, "X", "https://x.com", null, 100L, 100L))
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val bookmark = repo.getBookmark(bookmarkId)!!
        vm.onAction(BookmarksAction.BookmarkTap(bookmark))
        advanceUntilIdle()

        assertEquals(BookmarksEvent.OpenUrl("https://x.com"), vm.events.first())
    }

    @Test
    fun `an opened url is delivered once`() = runTest {
        val repo = FakeBookmarkRepository()
        val bookmarkId = repo.seedBookmark(Bookmark(0, "X", "https://x.com", null, 100L, 100L))
        val vm = newViewModel(repo)
        advanceUntilIdle()
        vm.onAction(BookmarksAction.BookmarkTap(repo.getBookmark(bookmarkId)!!))
        advanceUntilIdle()

        vm.events.test {
            assertEquals(BookmarksEvent.OpenUrl("https://x.com"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `onCreateFolderClick sets pendingDialog to CreateFolder`() = runTest {
        val repo = FakeBookmarkRepository()
        val vm = newViewModel(repo)
        advanceUntilIdle()

        vm.onAction(BookmarksAction.CreateFolderClick)
        advanceUntilIdle()

        val pending = vm.uiState.first { it.pendingDialog != null }.pendingDialog
        assertEquals(PendingBookmarkDialog.CreateFolder(parentId = null), pending)
    }

    private fun newViewModel(repo: FakeBookmarkRepository): BookmarksViewModel {
        val tabRepo = FakeTabRepository(homeUrl = "https://home.example.com")
        val incognitoRepo = FakeIncognitoTabRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabRepo, incognitoRepo)
        val observeActiveTab = ObserveActiveTabUseCase(observeAllTabs)
        val observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs)
        return BookmarksViewModel(
            observeBookmarksByFolder = ObserveBookmarksByFolderUseCase(repo),
            observeFolderPath = ObserveFolderPathUseCase(repo),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepo, incognitoRepo),
            createFolder = CreateFolderUseCase(repo),
            renameFolder = RenameFolderUseCase(repo),
            updateBookmark = UpdateBookmarkUseCase(repo),
            addBookmark = AddBookmarkUseCase(repo),
            searchBookmarks = SearchBookmarksUseCase(repo),
            observeActiveTabIsIncognito = observeActiveTabIsIncognito,
            observeActiveTab = observeActiveTab,
            bookmarks = repo,
        )
    }
}
