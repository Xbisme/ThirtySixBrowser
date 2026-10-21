package com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof

import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FireproofSitesViewModelTest {
    private val repository = FakeFireproofSiteRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `sites are listed newest first and can be removed one by one`() = runTest {
        repository.add("old.example")
        repository.add("new.example")
        val viewModel = FireproofSitesViewModel(repository)
        assertEquals(listOf("new.example", "old.example"), viewModel.uiState.value.sites)

        viewModel.onAction(FireproofSitesAction.Remove("old.example"))

        assertEquals(listOf("new.example"), viewModel.uiState.value.sites)
        assertEquals(setOf("new.example"), repository.currentSites())
    }

    @Test
    fun `removing all asks first and can be cancelled`() = runTest {
        repository.add("a.example")
        repository.add("b.example")
        val viewModel = FireproofSitesViewModel(repository)

        viewModel.onAction(FireproofSitesAction.RemoveAllClick)
        assertTrue(viewModel.uiState.value.isConfirmingRemoveAll)
        viewModel.onAction(FireproofSitesAction.RemoveAllDismissed)
        assertFalse(viewModel.uiState.value.isConfirmingRemoveAll)
        assertEquals(2, viewModel.uiState.value.sites.size)

        viewModel.onAction(FireproofSitesAction.RemoveAllClick)
        viewModel.onAction(FireproofSitesAction.RemoveAllConfirmed)
        assertTrue(viewModel.uiState.value.sites.isEmpty())
        assertTrue(repository.currentSites().isEmpty())
    }
}
