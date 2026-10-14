package com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof

data class FireproofSitesUiState(
    /** Registrable domains, newest first. */
    val sites: List<String> = emptyList(),
    val isLoaded: Boolean = false,
    val isConfirmingRemoveAll: Boolean = false,
)
