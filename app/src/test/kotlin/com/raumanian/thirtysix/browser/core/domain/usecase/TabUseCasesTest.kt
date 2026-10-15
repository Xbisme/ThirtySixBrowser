package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.tabs.domain.usecase.CloseAllTabsUseCase
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Each use case delegates to [FakeTabRepository] and keeps its contracts: the tab cap, recreating
 * the last tab, and last-active ordering.
 */
class TabUseCasesTest {

    private lateinit var fake: FakeTabRepository
    private lateinit var fakeIncognito: FakeIncognitoTabRepository
    private lateinit var observeAllTabs: ObserveAllTabsUseCase
    private lateinit var observeActiveTab: ObserveActiveTabUseCase
    private lateinit var closeTab: CloseTabUseCase
    private lateinit var closeAllTabs: CloseAllTabsUseCase
    private lateinit var updateTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase

    @Before
    fun setup() {
        fake = FakeTabRepository(homeUrl = HOME_URL)
        fakeIncognito = FakeIncognitoTabRepository()
        val screenshotCache = NoopScreenshotCache
        observeAllTabs = ObserveAllTabsUseCase(fake, fakeIncognito)
        observeActiveTab = ObserveActiveTabUseCase(observeAllTabs)
        closeTab = CloseTabUseCase(fake, screenshotCache)
        closeAllTabs = CloseAllTabsUseCase(fake, screenshotCache)
        updateTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(fake, fakeIncognito)
    }

    /** No-op [ScreenshotCache] for the use-case tests. */

    private object NoopScreenshotCache : ScreenshotCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    @Test
    fun `observeActiveTab returns tab with max lastActiveAt`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 100L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 300L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 200L),
            ),
        )

        val active = observeActiveTab().first()

        assertNotNull(active)
        assertEquals(2L, active!!.id)
        assertEquals("https://b.com", active.url)
    }

    @Test
    fun `closeTab on last surviving tab triggers fresh home tab seed`() = runTest {
        fake.reset()
        fake.emit(
            listOf(
                Tab(id = 7L, url = "https://only.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        closeTab(7L)

        val tabs = fake.observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
    }

    @Test
    fun `closeAllTabs wipes then seeds exactly one fresh home tab`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://a.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
                Tab(id = 2L, url = "https://b.com", title = "", position = 1, createdAt = 0L, lastActiveAt = 2L),
                Tab(id = 3L, url = "https://c.com", title = "", position = 2, createdAt = 0L, lastActiveAt = 3L),
            ),
        )

        closeAllTabs()

        val tabs = fake.observeTabs().first()
        assertEquals(1, tabs.size)
        assertEquals(HOME_URL, tabs.single().url)
    }

    @Test
    fun `updateActiveTabUrlAndTitle delegates write-through`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 5L, url = "https://before.com", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        updateTabUrlAndTitle(5L, "https://after.com", "After")

        val updated = fake.observeTabs().first().find { it.id == 5L }
        assertNotNull(updated)
        assertEquals("https://after.com", updated!!.url)
        assertEquals("After", updated.title)
    }

    @Test
    fun `updateActiveTabUrlAndTitle on missing id is a no-op`() = runTest {
        fake.emit(
            listOf(
                Tab(id = 1L, url = "https://only.com", title = "Only", position = 0, createdAt = 0L, lastActiveAt = 1L),
            ),
        )

        updateTabUrlAndTitle(MISSING_ID, "https://after.com", "After")

        val tabs = fake.observeTabs().first()
        assertEquals(1, tabs.size)
        // Original row unchanged; no synthetic row appeared.
        assertEquals("https://only.com", tabs.single().url)
        assertNull(tabs.find { it.id == MISSING_ID })
    }

    private companion object {
        const val HOME_URL: String = "https://www.google.com/"
        const val MISSING_ID: Long = 999L
    }
}
