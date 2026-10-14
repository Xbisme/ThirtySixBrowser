package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Handed down from `AppNavGraph` so the browser screen never creates its own `EntryPointViewModel`.
 *
 * @property loadInActiveTab URLs to load into the live page (reused tab at the tab cap).
 * @property messages string ids to show in the browser's snackbar (refused link, tab limit).
 * @property externalTabIds tabs opened for another app's link and not yet left.
 * @property onLeaveExternalTab closes such a tab; the screen then returns the person to the sender.
 */
@Immutable
data class IncomingContentHooks(
    val loadInActiveTab: Flow<String> = emptyFlow(),
    val messages: Flow<Int> = emptyFlow(),
    val externalTabIds: StateFlow<Set<Long>> = MutableStateFlow(emptySet()),
    val onLeaveExternalTab: (Long) -> Unit = {},
)
