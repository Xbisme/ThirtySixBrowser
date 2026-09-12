@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.HistoryRetention

/**
 * Spec 016 US5 FR-020 — choose how long history is kept: exactly the four bounded windows, with
 * no "keep forever". Stateless: the caller decides whether a choice needs the FR-021 warning.
 */
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

/** The localized, plural-aware length of [retention], e.g. "30 days" (FR-003). */
@Composable
fun historyRetentionLabel(retention: HistoryRetention): String =
    pluralStringResource(R.plurals.settings_history_retention_days, retention.days, retention.days)

const val TEST_TAG_SETTINGS_RETENTION_DIALOG: String = "settings_retention_dialog"
