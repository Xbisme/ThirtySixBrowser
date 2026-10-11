package com.raumanian.thirtysix.browser.presentation.settings

import com.raumanian.thirtysix.browser.domain.usecase.IsDefaultBrowserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 020 T049 — the Settings row reflects the system state, re-read on demand (FR-020, FR-022). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDefaultBrowserTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the initial state is read from the system`() {
        val vm = settingsViewModel(isDefaultBrowser = IsDefaultBrowserUseCase { true })
        assertTrue(vm.uiState.value.isDefaultBrowser)
    }

    @Test
    fun `a refresh picks up a change made outside the app`() {
        var isDefault = false
        val vm = settingsViewModel(isDefaultBrowser = IsDefaultBrowserUseCase { isDefault })
        assertFalse(vm.uiState.value.isDefaultBrowser)

        isDefault = true
        vm.refreshDefaultBrowser()
        assertTrue(vm.uiState.value.isDefaultBrowser)

        isDefault = false
        vm.refreshDefaultBrowser()
        assertFalse(vm.uiState.value.isDefaultBrowser)
    }
}
