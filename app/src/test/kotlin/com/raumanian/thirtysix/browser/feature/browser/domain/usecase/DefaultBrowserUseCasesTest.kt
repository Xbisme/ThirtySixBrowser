package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.InviteState
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The default-browser invitation use cases, including the sync with the system role. */
class DefaultBrowserUseCasesTest {

    private class FakeInviteRepository : DefaultBrowserInviteRepository {
        val state = MutableStateFlow(InviteState.DEFAULT)
        override fun observeInviteState(): Flow<InviteState> = state
        override suspend fun markInviteDismissed(): Result<Unit> {
            state.update { it.copy(dismissed = true) }
            return Result.Success(Unit)
        }
        override suspend fun recordLaunch(): Result<Unit> {
            val cap = BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES
            state.update { it.copy(launchCount = minOf(it.launchCount + 1, cap)) }
            return Result.Success(Unit)
        }
    }

    private class FakeController(var isDefault: Boolean) : DefaultBrowserController {
        override fun isDefaultBrowser(): Boolean = isDefault
    }

    private val repository = FakeInviteRepository()

    @Test
    fun `observe, dismiss and record go to the repository`() = runTest {
        repository.recordLaunch()
        DismissDefaultBrowserInviteUseCase(repository)()
        val state = ObserveDefaultBrowserInviteUseCase(repository)().first()
        assertEquals(InviteState(dismissed = true, launchCount = 1), state)
    }

    @Test
    fun `is-default reads the controller`() {
        assertTrue(IsDefaultBrowserUseCase(FakeController(true))())
        assertFalse(IsDefaultBrowserUseCase(FakeController(false))())
    }

    @Test
    fun `sync retires the invitation once the app is the default`() = runTest {
        SyncDefaultBrowserInviteUseCase(FakeController(true), repository)()
        assertTrue(repository.state.value.dismissed)
    }

    @Test
    fun `sync leaves the invitation alone when the app is not the default`() = runTest {
        SyncDefaultBrowserInviteUseCase(FakeController(false), repository)()
        assertFalse(repository.state.value.dismissed)
    }

    @Test
    fun `sync never brings a retired invitation back`() = runTest {
        repository.markInviteDismissed()
        SyncDefaultBrowserInviteUseCase(FakeController(false), repository)()
        assertTrue(repository.state.value.dismissed)
    }
}
