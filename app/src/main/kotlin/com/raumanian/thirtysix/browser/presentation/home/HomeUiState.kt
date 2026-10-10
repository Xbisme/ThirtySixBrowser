package com.raumanian.thirtysix.browser.presentation.home

import androidx.compose.runtime.Immutable
import com.raumanian.thirtysix.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.model.HomeShortcut
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import java.io.File

/** Spec 019 — everything the Home page draws (data-model §3). Immutable for skipping (§V rule 5). */
@Immutable
data class HomeUiState(
    val greeting: GreetingPeriod,
    val shortcuts: List<HomeShortcutUi> = emptyList(),
    val recent: List<HistoryEntry> = emptyList(),
    val searchEngine: SearchEngine = SearchEngine.Google,
)

/**
 * A shortcut plus the cached images the card can show: the tab's screenshot (Spec 011) and the
 * site's favicon. Without a screenshot the card paints a mesh seeded from the host.
 */
@Immutable
data class HomeShortcutUi(
    val shortcut: HomeShortcut,
    val screenshot: File? = null,
    val favicon: File? = null,
)
