@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.SearchEngine

/**
 * Spec 016 US2 — choose the engine that address-bar queries go to.
 *
 * Public and stateless on purpose: it knows nothing about any view model and reports the
 * choice through [onSelect], so Spec 018's onboarding can reuse it unchanged (FR-037,
 * research.md R14).
 */
@Composable
fun SearchEngineChooserDialog(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.settings_search_engine_title),
        options = SearchEngine.entries,
        selected = selected,
        optionLabel = { searchEngineLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_SEARCH_ENGINE_DIALOG),
    )
}

/**
 * The display name of [engine]. Brand names are not translated, so these resources exist in
 * the default locale only (`translatable="false"`).
 */
@Composable
fun searchEngineLabel(engine: SearchEngine): String = stringResource(
    when (engine) {
        SearchEngine.Google -> R.string.search_engine_name_google
        SearchEngine.DuckDuckGo -> R.string.search_engine_name_duckduckgo
        SearchEngine.Bing -> R.string.search_engine_name_bing
    },
)

const val TEST_TAG_SETTINGS_SEARCH_ENGINE_DIALOG: String = "settings_search_engine_dialog"
