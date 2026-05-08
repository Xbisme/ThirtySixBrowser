package com.raumanian.thirtysix.browser.presentation.bookmarks

import com.raumanian.thirtysix.browser.domain.model.Bookmark
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CountFolderDescendantsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.MoveBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.MoveFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllFoldersUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveBookmarksByFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveFolderPathUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RenameFolderUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SearchBookmarksUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateBookmarkUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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

        vm.onFolderTap(folder.copy(id = id))
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
        vm.onBookmarkTap(bookmark)
        advanceUntilIdle()

        val state = vm.uiState.first { it.openUrl != null }
        assertEquals("https://x.com", state.openUrl)
    }

    @Test
    fun `consumeOpenUrl clears the signal`() = runTest {
        val repo = FakeBookmarkRepository()
        val bookmarkId = repo.seedBookmark(Bookmark(0, "X", "https://x.com", null, 100L, 100L))
        val vm = newViewModel(repo)
        advanceUntilIdle()
        vm.onBookmarkTap(repo.getBookmark(bookmarkId)!!)
        advanceUntilIdle()

        vm.consumeOpenUrl()
        advanceUntilIdle()

        assertNull(vm.uiState.first { it.openUrl == null }.openUrl)
    }

    @Test
    fun `onCreateFolderClick sets pendingDialog to CreateFolder`() = runTest {
        val repo = FakeBookmarkRepository()
        val vm = newViewModel(repo)
        advanceUntilIdle()

        vm.onCreateFolderClick()
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
            deleteBookmark = DeleteBookmarkUseCase(repo),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepo, incognitoRepo),
            createFolder = CreateFolderUseCase(repo),
            renameFolder = RenameFolderUseCase(repo),
            moveFolder = MoveFolderUseCase(repo),
            moveBookmark = MoveBookmarkUseCase(repo),
            updateBookmark = UpdateBookmarkUseCase(repo),
            addBookmark = AddBookmarkUseCase(repo),
            deleteFolder = DeleteFolderUseCase(repo),
            countDescendants = CountFolderDescendantsUseCase(repo),
            searchBookmarks = SearchBookmarksUseCase(repo),
            observeActiveTabIsIncognito = observeActiveTabIsIncognito,
            observeActiveTab = observeActiveTab,
            observeAllFolders = ObserveAllFoldersUseCase(repo),
        )
    }
}
