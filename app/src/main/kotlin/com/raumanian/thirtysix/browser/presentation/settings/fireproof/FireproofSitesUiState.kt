package com.raumanian.thirtysix.browser.presentation.settings.fireproof

/** Spec 024 FR-012 — the Fireproof sites screen. */
data class FireproofSitesUiState(
    /** Registrable domains, newest first. */
    val sites: List<String> = emptyList(),
    val isLoaded: Boolean = false,
    val isConfirmingRemoveAll: Boolean = false,
)
