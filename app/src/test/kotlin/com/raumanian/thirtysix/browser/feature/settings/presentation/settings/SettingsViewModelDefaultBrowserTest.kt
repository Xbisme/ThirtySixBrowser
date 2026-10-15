package com.raumanian.thirtysix.browser.feature.settings.presentation.settings

import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The default-browser row reflects the system state, re-read on demand. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDefaultBrowserTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

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
