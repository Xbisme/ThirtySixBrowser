package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SitePermissionsViewModelTest {

    private val repository = FakeSitePermissionRepository()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModel() = SitePermissionsViewModel(repository)

    @Test
    fun `decisions are grouped by site in capability order`() = runTest {
        repository.save(B, SiteCapability.Microphone, SiteDecision.Deny)
        repository.save(A, SiteCapability.Location, SiteDecision.Allow)
        repository.save(B, SiteCapability.Camera, SiteDecision.Allow)

        val state = viewModel().uiState.value

        assertTrue(state.isLoaded)
        assertEquals(listOf(A, B), state.sites.map { it.origin })
        assertEquals(
            listOf(SiteCapability.Camera to SiteDecision.Allow, SiteCapability.Microphone to SiteDecision.Deny),
            state.sites[1].decisions,
        )
    }

    @Test
    fun `an empty store loads as empty`() {
        val state = viewModel().uiState.value
        assertTrue(state.isLoaded)
        assertTrue(state.sites.isEmpty())
    }

    @Test
    fun `changing a decision saves it`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        val viewModel = viewModel()

        viewModel.onAction(SitePermissionsAction.DecisionClick(A, SiteCapability.Camera, SiteDecision.Allow))
        viewModel.onAction(SitePermissionsAction.DecisionChosen(SiteDecision.Deny))

        assertNull(viewModel.uiState.value.editing)
        assertEquals(SiteDecision.Deny, repository.decisionsFor(A)[SiteCapability.Camera])
    }

    @Test
    fun `removing a site forgets only that site`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(B, SiteCapability.Camera, SiteDecision.Allow)
        val viewModel = viewModel()

        viewModel.onAction(SitePermissionsAction.RemoveSite(A))

        assertEquals(listOf(B), viewModel.uiState.value.sites.map { it.origin })
    }

    @Test
    fun `remove all needs confirmation`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        val viewModel = viewModel()

        viewModel.onAction(SitePermissionsAction.RemoveAllClick)
        assertTrue(viewModel.uiState.value.isConfirmingRemoveAll)
        viewModel.onAction(SitePermissionsAction.RemoveAllDismissed)
        assertEquals(1, viewModel.uiState.value.sites.size)

        viewModel.onAction(SitePermissionsAction.RemoveAllClick)
        viewModel.onAction(SitePermissionsAction.RemoveAllConfirmed)
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }

    private companion object {
        const val A = "https://a.example"
        const val B = "https://b.example"
    }
}
