package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

data class ProtectionExceptionsUiState(
    /** Registrable domains, newest first. */
    val sites: List<String> = emptyList(),
    val isLoaded: Boolean = false,
    val isConfirmingTurnAllOn: Boolean = false,
)
