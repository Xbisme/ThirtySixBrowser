package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

import com.raumanian.thirtysix.browser.core.testing.fake.FakeProtectionExceptionRepository
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ObserveProtectionExceptionsUseCase
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.RemoveProtectionExceptionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProtectionExceptionsViewModelTest {
    private val repository = FakeProtectionExceptionRepository()
    private lateinit var viewModel: ProtectionExceptionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ProtectionExceptionsViewModel(
            ObserveProtectionExceptionsUseCase(repository),
            RemoveProtectionExceptionsUseCase(repository),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

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

        viewModel.onTurnOn("old.com")

        assertEquals(listOf("new.com"), viewModel.uiState.value.sites)
    }

    @Test
    fun `turning all back on asks first and can be cancelled`() = runTest {
        repository.add("a.com")
        repository.add("b.com")

        viewModel.onTurnAllOnClick()
        assertTrue(viewModel.uiState.value.isConfirmingTurnAllOn)
        viewModel.onTurnAllOnDismissed()
        assertFalse(viewModel.uiState.value.isConfirmingTurnAllOn)
        assertEquals(2, viewModel.uiState.value.sites.size)

        viewModel.onTurnAllOnClick()
        viewModel.onTurnAllOnConfirmed()
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }
}
