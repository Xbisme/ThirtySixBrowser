package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Every event the view model sends, in order, collected the moment it is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.eventsOf(vm: BrowserViewModel): List<BrowserEvent> {
    val events = mutableListOf<BrowserEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
    return events
}

internal fun List<BrowserEvent>.loads(): List<String> = filterIsInstance<BrowserEvent.LoadUrl>().map { it.url }

/** The screen's snapshot, assembled as `BrowserRoute` does. */
internal fun BrowserViewModel.screenState(): BrowserUiState =
    browserUiState(currentPage.value, uiState.value, PageActionsUiState())

/** Every event a holder sends, in order, collected the moment it is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <E> TestScope.eventsOf(events: Flow<E>): List<E> {
    val sent = mutableListOf<E>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.collect { sent += it } }
    return sent
}
