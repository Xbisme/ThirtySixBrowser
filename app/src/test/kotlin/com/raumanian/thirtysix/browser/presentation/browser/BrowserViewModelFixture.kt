package com.raumanian.thirtysix.browser.presentation.browser

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.AddBookmarkUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.domain.usecase.IsUrlBookmarkedUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ToggleBookmarkUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateHistoryEntryTitleUseCase
/**
 * Spec 022 — one place that builds a [BrowserViewModel] over fakes, for the page-tools tests.
 * Older test classes keep their own builders; new ones use this, so a new constructor
 * dependency is added here once.
 */
internal class BrowserViewModelFixture(
    val url: String = "https://example.com/",
    val tabs: FakeTabRepository = FakeTabRepository(homeUrl = url),
    val incognitoTabs: FakeIncognitoTabRepository = FakeIncognitoTabRepository(),
) {
    fun build(): BrowserViewModel {
        val bookmarks = FakeBookmarkRepository()
        val history = FakeHistoryRepository()
        val observeAllTabs = ObserveAllTabsUseCase(tabs, incognitoTabs)
        return BrowserViewModel(
            defaultHomeUrl = url,
            buildSearchUrl = BuildSearchUrlUseCase(EchoSearchEngine),
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            observeTabs = ObserveTabsUseCase(tabs),
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabs, incognitoTabs),
            createTab = CreateTabUseCase(tabs, url),
            faviconCache = FakeFaviconCache(),
            screenshotCache = FakeScreenshotCache(),
            isUrlBookmarked = IsUrlBookmarkedUseCase(bookmarks),
            toggleBookmark = ToggleBookmarkUseCase(bookmarks, AddBookmarkUseCase(bookmarks)),
            recordHistoryEntry = RecordHistoryEntryUseCase(history),
            updateHistoryEntryTitle = UpdateHistoryEntryTitleUseCase(history),
            startDownload = noOpStartDownloadUseCase(),
        )
    }

    private object EchoSearchEngine : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = "https://search.example/?q=$query"
    }
}
