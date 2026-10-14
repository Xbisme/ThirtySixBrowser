package com.raumanian.thirtysix.browser.presentation.home

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.InviteState
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.domain.usecase.DismissDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SyncDefaultBrowserInviteUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 020 T048 — invitation visibility rules (FR-024 – FR-027, data-model §8). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBrowserInviteViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeInviteRepository(initial: InviteState) : DefaultBrowserInviteRepository {
        val state = MutableStateFlow(initial)
        override fun observeInviteState(): Flow<InviteState> = state
        override suspend fun markInviteDismissed(): Result<Unit> {
            state.update { it.copy(dismissed = true) }
            return Result.Success(Unit)
        }
        override suspend fun recordLaunch(): Result<Unit> = Result.Success(Unit)
    }

    private class Dispatchers1(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val io = d
        override val default = d
        override val unconfined = d
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private var isDefault = false
    private val controller = DefaultBrowserController { isDefault }

    private suspend fun incognitoActive(): FakeIncognitoTabRepository =
        FakeIncognitoTabRepository().also { it.createTab("https://private.test/") }

    /** One normal tab last used long ago, so a freshly created incognito tab is the active one. */
    private fun oldNormalTab() = FakeTabRepository(
        initialTabs = listOf(
            Tab(id = 1, url = "https://t.test/", title = "", position = 0, createdAt = 0L, lastActiveAt = 1L),
        ),
    )

    private fun viewModel(
        repository: FakeInviteRepository,
        incognito: FakeIncognitoTabRepository = FakeIncognitoTabRepository(),
    ) = DefaultBrowserInviteViewModel(
        observeInvite = ObserveDefaultBrowserInviteUseCase(repository),
        observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(
            ObserveAllTabsUseCase(oldNormalTab(), incognito),
        ),
        isDefaultBrowser = IsDefaultBrowserUseCase(controller),
        dismissInvite = DismissDefaultBrowserInviteUseCase(repository),
        syncInvite = SyncDefaultBrowserInviteUseCase(controller, repository),
        dispatchers = Dispatchers1(dispatcher),
    )

    private fun TestScope.shown(vm: DefaultBrowserInviteViewModel): Boolean {
        backgroundScope.launch(dispatcher) { vm.showInvite.collect {} }
        return vm.showInvite.value
    }

    @Test
    fun `visible only when not dismissed, launched enough, not default and not incognito`() = runTest(dispatcher) {
        val min = BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES
        for (dismissed in listOf(false, true)) {
            for (launches in listOf(min - 1, min)) {
                for (default in listOf(false, true)) {
                    for (incognito in listOf(false, true)) {
                        isDefault = default
                        val repo = FakeInviteRepository(InviteState(dismissed, launches))
                        val vm = viewModel(repo, if (incognito) incognitoActive() else FakeIncognitoTabRepository())
                        val expected = !dismissed && launches >= min && !default && !incognito
                        assertEquals("d=$dismissed l=$launches def=$default inc=$incognito", expected, shown(vm))
                    }
                }
            }
        }
    }

    @Test
    fun `dismissing hides it for good`() = runTest(dispatcher) {
        val repo = FakeInviteRepository(InviteState(dismissed = false, launchCount = 3))
        val vm = viewModel(repo)
        assertTrue(shown(vm))

        vm.onDismiss()

        assertTrue(repo.state.value.dismissed)
        assertFalse(vm.showInvite.value)
    }

    @Test
    fun `resuming after becoming default hides and retires it, and it stays retired`() = runTest(dispatcher) {
        val repo = FakeInviteRepository(InviteState(dismissed = false, launchCount = 3))
        val vm = viewModel(repo)
        assertTrue(shown(vm))

        isDefault = true
        vm.onResume()
        assertFalse(vm.showInvite.value)
        assertTrue(repo.state.value.dismissed)

        isDefault = false
        vm.onResume()
        assertFalse("FR-026: no comeback once retired", vm.showInvite.value)
    }

    @Test
    fun `resuming while still not default changes nothing`() = runTest(dispatcher) {
        val repo = FakeInviteRepository(InviteState(dismissed = false, launchCount = 3))
        val vm = viewModel(repo)
        assertTrue(shown(vm))

        vm.onResume() // e.g. the person declined the system confirmation (FR-027)

        assertTrue(vm.showInvite.value)
        assertFalse(repo.state.value.dismissed)
    }
}
