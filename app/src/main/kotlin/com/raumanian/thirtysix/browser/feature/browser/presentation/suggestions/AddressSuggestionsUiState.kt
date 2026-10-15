package com.raumanian.thirtysix.browser.feature.browser.presentation.suggestions

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.feature.browser.domain.model.AddressSuggestion
/** Empty means no list is shown. Stable for Compose through `compose_stability.conf`. */
@Immutable
data class AddressSuggestionsUiState(val suggestions: List<AddressSuggestion> = emptyList())
