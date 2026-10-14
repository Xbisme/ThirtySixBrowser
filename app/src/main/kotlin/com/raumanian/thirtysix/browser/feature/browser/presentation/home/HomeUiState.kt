package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.feature.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.feature.browser.domain.model.HomeShortcut
import java.io.File

@Immutable
data class HomeUiState(
    val greeting: GreetingPeriod,
    val shortcuts: List<HomeShortcutUi> = emptyList(),
    val recent: List<HistoryEntry> = emptyList(),
    val searchEngine: SearchEngine = SearchEngine.Google,
)

/** A shortcut plus its cached images; without a screenshot the card paints a mesh seeded from the host. */
@Immutable
data class HomeShortcutUi(
    val shortcut: HomeShortcut,
    val screenshot: File? = null,
    val favicon: File? = null,
)
