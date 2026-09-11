@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 016 US4 — choose what to clear, then confirm.
 *
 *  - One checkbox row per category, with [Role.Checkbox] semantics and at least the minimum
 *    interactive size (FR-039). All three start selected; the caller owns [selection] (FR-025).
 *  - Confirm is disabled while nothing is selected (FR-026).
 *  - While [inProgress], a progress indicator shows, every control is disabled, and neither an
 *    outside tap nor system back closes the dialog, so a clear can be neither re-submitted nor
 *    abandoned midway (FR-032).
 */
@Composable
@Suppress("LongParameterList")
fun ClearBrowsingDataDialog(
    selection: Set<ClearBrowsingDataCategory>,
    inProgress: Boolean,
    onToggle: (ClearBrowsingDataCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(TEST_TAG_SETTINGS_CLEAR_DIALOG),
        onDismissRequest = { if (!inProgress) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !inProgress, dismissOnClickOutside = !inProgress),
        title = { Text(text = stringResource(R.string.settings_clear_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ClearBrowsingDataCategory.entries.forEach { category ->
                    CategoryRow(
                        category = category,
                        checked = category in selection,
                        enabled = !inProgress,
                        onToggle = onToggle,
                    )
                }
                if (inProgress) ClearInProgressRow()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selection.isNotEmpty() && !inProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM),
            ) {
                Text(text = stringResource(R.string.settings_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(text = stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun CategoryRow(
    category: ClearBrowsingDataCategory,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (ClearBrowsingDataCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle(category) },
            )
            .testTag(TEST_TAG_SETTINGS_CLEAR_CATEGORY_PREFIX + category.name)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Visual only — the row carries the click and the checked state.
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(Spacing.md))
        Column {
            Text(text = stringResource(category.labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(category.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClearInProgressRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .testTag(TEST_TAG_SETTINGS_CLEAR_PROGRESS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(text = stringResource(R.string.settings_clear_in_progress))
    }
}

@get:StringRes
private val ClearBrowsingDataCategory.labelRes: Int
    get() = when (this) {
        ClearBrowsingDataCategory.History -> R.string.settings_clear_category_history
        ClearBrowsingDataCategory.CookiesAndSiteData -> R.string.settings_clear_category_cookies
        ClearBrowsingDataCategory.CachedImagesAndFiles -> R.string.settings_clear_category_cache
    }

@get:StringRes
private val ClearBrowsingDataCategory.descriptionRes: Int
    get() = when (this) {
        ClearBrowsingDataCategory.History -> R.string.settings_clear_category_history_description
        ClearBrowsingDataCategory.CookiesAndSiteData -> R.string.settings_clear_category_cookies_description
        ClearBrowsingDataCategory.CachedImagesAndFiles -> R.string.settings_clear_category_cache_description
    }

const val TEST_TAG_SETTINGS_CLEAR_DIALOG: String = "settings_clear_dialog"
const val TEST_TAG_SETTINGS_CLEAR_CONFIRM: String = "settings_clear_confirm"
const val TEST_TAG_SETTINGS_CLEAR_PROGRESS: String = "settings_clear_progress"

/** Followed by the category's enum name, e.g. `settings_clear_category_History`. */
const val TEST_TAG_SETTINGS_CLEAR_CATEGORY_PREFIX: String = "settings_clear_category_"
