@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
// Deliberately no "keep forever": history is always bounded.
@Composable
fun HistoryRetentionChooserDialog(
    selected: HistoryRetention,
    onSelect: (HistoryRetention) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.settings_history_retention_title),
        options = HistoryRetention.entries,
        selected = selected,
        optionLabel = { historyRetentionLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_RETENTION_DIALOG),
    )
}

@Composable
fun historyRetentionLabel(retention: HistoryRetention): String =
    pluralStringResource(R.plurals.settings_history_retention_days, retention.days, retention.days)

const val TEST_TAG_SETTINGS_RETENTION_DIALOG: String = "settings_retention_dialog"
