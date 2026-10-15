package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// MainActivity provides the injected IO dispatcher; previews and UI tests fall back to the caller's.
val LocalIoDispatcher = staticCompositionLocalOf<CoroutineContext> { EmptyCoroutineContext }
