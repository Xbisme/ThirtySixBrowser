package com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions

/** Spec 023 FR-015 — the Sites without protection screen. */
data class ProtectionExceptionsUiState(
    /** Registrable domains, newest first. */
    val sites: List<String> = emptyList(),
    val isLoaded: Boolean = false,
    val isConfirmingTurnAllOn: Boolean = false,
)
