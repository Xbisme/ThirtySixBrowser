package com.raumanian.thirtysix.browser.presentation.tabs

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.ScreenshotCache
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllIncognitoTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseIncognitoTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SwitchActiveTabUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 019 T070 — the Open / Incognito chip modes of the tab switcher (FR-040, clarification Q3). */
@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelChipModeTest {

    private val dispatcher = StandardTestDispatcher()
    private val normal = FakeTabRepository(homeUrl = HOME_URL)
    private val incognito = FakeIncognitoTabRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TabsViewModel(
        observeAllTabs = ObserveAllTabsUseCase(normal, incognito),
        createTab = CreateTabUseCase(normal, HOME_URL),
        createIncognitoTab = CreateIncognitoTabUseCase(incognito),
        switchActiveTab = SwitchActiveTabUseCase(normal),
        closeTab = CloseTabUseCase(normal, NoopScreenshots),
        closeIncognitoTab = CloseIncognitoTabUseCase(incognito),
        closeAllTabs = CloseAllTabsUseCase(normal, NoopScreenshots),
        closeAllIncognitoTabs = CloseAllIncognitoTabsUseCase(incognito),
        homeUrl = HOME_URL,
        faviconCache = NoopFavicons,
        screenshotCache = NoopScreenshots,
    )

    private fun TestScope.started(vm: TabsViewModel): TabsViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun tab(id: Long, active: Long, isIncognito: Boolean = false) = Tab(
        id = id,
        url = "https://$id.test/",
        title = "",
        position = 0,
        createdAt = 0L,
        lastActiveAt = active,
        isIncognito = isIncognito,
    )

    @Test
    fun `mode starts at the active tab's kind`() = runTest(dispatcher) {
        normal.emit(listOf(tab(1, active = 10)))
        incognito.emit(listOf(tab(-1, active = 20, isIncognito = true)))
        val vm = started(viewModel())

        assertEquals(TabsMode.Incognito, vm.uiState.value.mode)
        assertEquals(listOf(-1L), vm.uiState.value.visibleTabs.map { it.id })
    }

    @Test
    fun `with no incognito tabs the mode is Open even after picking Incognito`() = runTest(dispatcher) {
        normal.emit(listOf(tab(1, active = 10)))
        val vm = started(viewModel())

        vm.onModeSelected(TabsMode.Incognito)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.incognitoTabCount)
        assertEquals(TabsMode.Open, vm.uiState.value.mode)
        assertEquals(listOf(1L), vm.uiState.value.visibleTabs.map { it.id })
    }

    @Test
    fun `picking a chip filters the grid to that kind`() = runTest(dispatcher) {
        normal.emit(listOf(tab(1, active = 30), tab(2, active = 5)))
        incognito.emit(listOf(tab(-1, active = 20, isIncognito = true)))
        val vm = started(viewModel())
        assertEquals(TabsMode.Open, vm.uiState.value.mode)
        assertEquals(listOf(1L, 2L), vm.uiState.value.visibleTabs.map { it.id }.sorted())

        vm.onModeSelected(TabsMode.Incognito)
        advanceUntilIdle()

        assertEquals(listOf(-1L), vm.uiState.value.visibleTabs.map { it.id })
    }

    @Test
    fun `close-all in incognito mode closes only incognito tabs`() = runTest(dispatcher) {
        normal.emit(listOf(tab(1, active = 30)))
        incognito.emit(listOf(tab(-1, active = 20, isIncognito = true), tab(-2, active = 10, isIncognito = true)))
        val vm = started(viewModel())
        vm.onModeSelected(TabsMode.Incognito)

        vm.onCloseAllIncognitoConfirmed()
        advanceUntilIdle()

        assertTrue(incognito.snapshot().isEmpty())
        assertEquals(listOf(1L), normal.observeTabs().first().map { it.id })
        assertEquals(TabsMode.Open, vm.uiState.value.mode)
    }

    private object NoopFavicons : FaviconCache {
        override val version: StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun retainOnly(hosts: Set<String>) = Unit
        override fun fileFor(url: String): File? = null
    }

    private object NoopScreenshots : ScreenshotCache {
        override val version: StateFlow<Long> = MutableStateFlow(0L)
        override suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit) = Unit
        override fun fileFor(tabId: Long): File? = null
        override suspend fun delete(tabId: Long) = Unit
        override suspend fun clearAll() = Unit
    }

    private companion object {
        const val HOME_URL = "thirtysix:home"
    }
}
