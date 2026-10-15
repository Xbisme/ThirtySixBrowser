package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

internal fun List<AddressBarEvent>.loads(): List<String> = filterIsInstance<AddressBarEvent.LoadUrl>().map { it.url }

/** The screen's snapshot, assembled as `BrowserRoute` does; holders not passed contribute their defaults. */
internal fun BrowserViewModel.screenState(addressBar: AddressBarViewModel? = null): BrowserUiState = browserUiState(
    currentPage.value,
    uiState.value,
    addressBar?.uiState?.value ?: AddressBarUiState(),
    PageActionsUiState(),
)

/** Every event a holder sends, in order, collected the moment it is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <E> TestScope.eventsOf(events: Flow<E>): List<E> {
    val sent = mutableListOf<E>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.collect { sent += it } }
    return sent
}
