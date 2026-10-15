@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.presentation.util.searchEngineLabel
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

const val TEST_TAG_SETTINGS_SEARCH_ENGINE_DIALOG: String = "settings_search_engine_dialog"
