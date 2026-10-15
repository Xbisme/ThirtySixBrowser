@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

// Collects while composed, not only while STARTED: gating on the lifecycle would change which
// drop-if-unobserved events a screen still receives in the background.
@Composable
fun <E> EventEffect(events: Flow<E>, onEvent: suspend (E) -> Unit) {
    val latest by rememberUpdatedState(onEvent)
    LaunchedEffect(events) {
        events.collect { latest(it) }
    }
}
