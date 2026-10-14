package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase.ObserveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase.RemoveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.feature.sitepermission.domain.usecase.SetSitePermissionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 021 T026 / FR-009 — the Site permissions screen's state and actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class SitePermissionsViewModelTest {

    private val repository = FakeSitePermissionRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SitePermissionsViewModel(
        ObserveSitePermissionsUseCase(repository),
        SetSitePermissionUseCase(repository),
        RemoveSitePermissionsUseCase(repository),
    )

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

        viewModel.onDecisionClick(A, SiteCapability.Camera, SiteDecision.Allow)
        viewModel.onDecisionChosen(SiteDecision.Deny)

        assertNull(viewModel.uiState.value.editing)
        assertEquals(SiteDecision.Deny, repository.decisionsFor(A)[SiteCapability.Camera])
    }

    @Test
    fun `removing a site forgets only that site`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(B, SiteCapability.Camera, SiteDecision.Allow)
        val viewModel = viewModel()

        viewModel.onRemoveSite(A)

        assertEquals(listOf(B), viewModel.uiState.value.sites.map { it.origin })
    }

    @Test
    fun `remove all needs confirmation`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        val viewModel = viewModel()

        viewModel.onRemoveAllClick()
        assertTrue(viewModel.uiState.value.isConfirmingRemoveAll)
        viewModel.onRemoveAllDismissed()
        assertEquals(1, viewModel.uiState.value.sites.size)

        viewModel.onRemoveAllClick()
        viewModel.onRemoveAllConfirmed()
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }

    private companion object {
        const val A = "https://a.example"
        const val B = "https://b.example"
    }
}
