package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
