package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.BuildSearchUrlUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.MonotonicClock
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.model.IncomingRequest
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase.OpenIncomingRequestUseCase
import com.raumanian.thirtysix.browser.feature.entrypoint.domain.usecase.ResolveIncomingRequestUseCase
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryPointViewModelTest {

    private val relay = IncomingRequestRelay()
    private val externalTabs = ExternalTabRegistry()
    private val navigation = ActiveTabNavigationRelay()
    private val messages = EntryMessageRelay()
    private val incognito = FakeIncognitoTabRepository()

    private object TestSearchEngine : SearchEngineRepository {
        override suspend fun buildSearchUrl(query: String): String = "https://search.test/?q=$query"
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModel(
        settings: FakeSettingsRepository = onboarded(),
        tabs: FakeTabRepository = FakeTabRepository(initialTabs = listOf(tab(1, 1L))),
    ) = EntryPointViewModel(
        relay = relay,
        observeUserSettings = ObserveUserSettingsUseCase(settings),
        resolve = ResolveIncomingRequestUseCase(),
        open = OpenIncomingRequestUseCase(
            buildSearchUrl = BuildSearchUrlUseCase(TestSearchEngine),
            tabs = tabs,
            observeActiveTab = ObserveActiveTabUseCase(ObserveAllTabsUseCase(tabs, incognito)),
            clock = MonotonicClock { 0L },
        ),
        externalTabs = externalTabs,
        activeTabNavigation = navigation,
        messages = messages,
        observeTabs = ObserveTabsUseCase(tabs),
        closeTab = CloseTabUseCase(tabs, NoopScreenshots),
    )

    private object NoopScreenshots : ScreenshotCache {
        override val version: StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private fun onboarded() = FakeSettingsRepository(UserSettings.DEFAULT.copy(isOnboardingCompleted = true))

    private fun tab(id: Long, lastActiveAt: Long) = Tab(
        id = id,
        url = "https://t$id.test/",
        title = "",
        position = id.toInt(),
        createdAt = 0L,
        lastActiveAt = lastActiveAt,
    )

    @Test
    fun `while onboarding is incomplete the request waits`() = runTest {
        val settings = FakeSettingsRepository(UserSettings.DEFAULT.copy(isOnboardingCompleted = false))
        val vm = viewModel(settings = settings)
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))

        vm.events.test { expectNoEvents() }
        assertNotNull(relay.pending.value)
    }

    @Test
    fun `finishing onboarding opens the held request`() = runTest {
        val settings = FakeSettingsRepository(UserSettings.DEFAULT.copy(isOnboardingCompleted = false))
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val vm = viewModel(settings = settings, tabs = tabs)
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))

        settings.setOnboardingCompleted(true)

        vm.events.test { assertEquals(EntryEvent.ShowBrowser, awaitItem()) }
        assertNull(relay.pending.value)
        assertTrue(tabs.observeTabs().first().any { it.url == "https://example.org/a" })
    }

    @Test
    fun `a new tab is marked as opened from another app`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val vm = viewModel(tabs = tabs)
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))

        vm.events.test { assertEquals(EntryEvent.ShowBrowser, awaitItem()) }
        val newTab = tabs.observeTabs().first().single { it.url == "https://example.org/a" }
        assertTrue(externalTabs.contains(newTab.id))
    }

    @Test
    fun `leaving an external tab closes it and drops its mark`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val vm = viewModel(tabs = tabs)
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))
        vm.events.test { awaitItem() }
        val id = tabs.observeTabs().first().single { it.url == "https://example.org/a" }.id

        vm.browserHooks.onLeaveExternalTab(id)

        assertFalse(externalTabs.contains(id))
        assertFalse(tabs.observeTabs().first().any { it.id == id })
    }

    @Test
    fun `a rejected address shows the browser and the refusal message`() = runTest {
        val vm = viewModel()
        relay.submit(IncomingRequest.OpenAddress("javascript:alert(1)"))

        vm.events.test { assertEquals(EntryEvent.ShowBrowser, awaitItem()) }
        messages.messages.test { assertEquals(R.string.entry_link_rejected, awaitItem()) }
    }

    @Test
    fun `empty shared text only shows the browser`() = runTest {
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        val vm = viewModel(tabs = tabs)
        relay.submit(IncomingRequest.InterpretText("   "))

        vm.events.test { assertEquals(EntryEvent.ShowBrowser, awaitItem()) }
        messages.messages.test { expectNoEvents() }
        assertEquals(1, tabs.observeTabs().first().size)
    }

    @Test
    fun `at the cap the active tab loads in place, with the notice, and is not marked external`() = runTest {
        val roster = (1L..BrowserLimits.MAX_TABS).map { id -> tab(id, lastActiveAt = id) }
        val vm = viewModel(tabs = FakeTabRepository(initialTabs = roster))
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))

        vm.events.test { assertEquals(EntryEvent.ShowBrowser, awaitItem()) }
        messages.messages.test { assertEquals(R.string.entry_tab_limit_reused, awaitItem()) }
        navigation.requests.test { assertEquals("https://example.org/a", awaitItem()) }
        assertFalse(externalTabs.contains(BrowserLimits.MAX_TABS.toLong()))
    }

    @Test
    fun `a consumed request is not opened again by a later onboarding emission`() = runTest {
        val settings = FakeSettingsRepository(UserSettings.DEFAULT.copy(isOnboardingCompleted = true))
        val tabs = FakeTabRepository(initialTabs = listOf(tab(1, 1L)))
        viewModel(settings = settings, tabs = tabs)
        relay.submit(IncomingRequest.OpenAddress("https://example.org/a"))

        settings.setThemeMode(ThemeMode.Dark)

        assertEquals(1, tabs.observeTabs().first().count { it.url == "https://example.org/a" })
    }
}
