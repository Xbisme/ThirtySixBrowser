package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.compose.runtime.Immutable

/** A Fire button another screen shows; the privacy feature supplies it, confirmation included. */
@Immutable
data class FireButton(val enabled: Boolean, val onClick: () -> Unit)
