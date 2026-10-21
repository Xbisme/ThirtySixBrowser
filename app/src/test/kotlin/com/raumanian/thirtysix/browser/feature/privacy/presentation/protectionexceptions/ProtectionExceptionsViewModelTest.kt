package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeProtectionExceptionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProtectionExceptionsViewModelTest {
    private val repository = FakeProtectionExceptionRepository()
    private lateinit var viewModel: ProtectionExceptionsViewModel

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        viewModel = ProtectionExceptionsViewModel(repository)
    }

    @Test
    fun `an empty list is loaded and shown as empty`() {
        assertTrue(viewModel.uiState.value.isLoaded)
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }

    @Test
    fun `sites are listed newest first and can be turned back on one by one`() = runTest {
        repository.add("old.com")
        repository.add("new.com")
        assertEquals(listOf("new.com", "old.com"), viewModel.uiState.value.sites)

        viewModel.onAction(ProtectionExceptionsAction.TurnOn("old.com"))

        assertEquals(listOf("new.com"), viewModel.uiState.value.sites)
    }

    @Test
    fun `turning all back on asks first and can be cancelled`() = runTest {
        repository.add("a.com")
        repository.add("b.com")

        viewModel.onAction(ProtectionExceptionsAction.TurnAllOnClick)
        assertTrue(viewModel.uiState.value.isConfirmingTurnAllOn)
        viewModel.onAction(ProtectionExceptionsAction.TurnAllOnDismissed)
        assertFalse(viewModel.uiState.value.isConfirmingTurnAllOn)
        assertEquals(2, viewModel.uiState.value.sites.size)

        viewModel.onAction(ProtectionExceptionsAction.TurnAllOnClick)
        viewModel.onAction(ProtectionExceptionsAction.TurnAllOnConfirmed)
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }
}
