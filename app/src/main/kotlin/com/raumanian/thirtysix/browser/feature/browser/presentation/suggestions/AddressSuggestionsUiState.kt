package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
/**
 * Spec 022 data-model §4 — the suggestions under an address entry. Empty means no list is shown
 * (FR-021). The list is stable for Compose through `compose_stability.conf`.
 */
@Immutable
data class AddressSuggestionsUiState(val suggestions: List<AddressSuggestion> = emptyList())
