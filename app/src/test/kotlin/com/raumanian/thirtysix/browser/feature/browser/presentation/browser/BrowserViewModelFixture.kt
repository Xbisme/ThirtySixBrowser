package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.browser.data.repository.CurrentPageRepositoryImpl
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
/** Builds the browser holders over fakes and one shared page store, so a new dependency is added in one place. */
internal class BrowserViewModelFixture(
    val url: String = "https://example.com/",
    val tabs: FakeTabRepository = FakeTabRepository(homeUrl = url),
    val incognitoTabs: FakeIncognitoTabRepository = FakeIncognitoTabRepository(),
) {
    /** One store for every holder this fixture builds, as the browser screen shares one. */
    val pageStore = CurrentPageRepositoryImpl(url)
    val bookmarks = FakeBookmarkRepository()

    fun buildFind(): FindInPageViewModel = FindInPageViewModel(pageStore)

    fun buildPageActions(): PageActionsViewModel = PageActionsViewModel(
        defaultHomeUrl = url,
        pageStore = pageStore,
        tabs = tabs,
        isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarks),
        toggleBookmark = ToggleBookmarkUseCase(bookmarks, AddBookmarkUseCase(bookmarks)),
    )

    fun build(): BrowserViewModel {
        val history = FakeHistoryRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabs, incognitoTabs)
        return BrowserViewModel(
            searchEngine = EchoSearchEngine,
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            tabs = tabs,
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabs, incognitoTabs),
            faviconCache = FakeFaviconCache(),
            screenshotCache = FakeScreenshotCache(),
            recordHistoryEntry = RecordHistoryEntryUseCase(history),
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(history),
            pageStore = pageStore,
        )
    }

    private object EchoSearchEngine : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = "https://search.example/?q=$query"
    }
}
