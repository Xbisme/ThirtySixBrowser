@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.ClearBrowsingDataCategory
import com.raumanian.thirtysix.browser.presentation.components.PillButton
import com.raumanian.thirtysix.browser.presentation.components.PillStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/**
 * Spec 016 US4 — choose what to clear, then confirm.
 *
 *  - One checkbox row per category, with [Role.Checkbox] semantics and at least the minimum
 *    interactive size (FR-039). All three start selected; the caller owns [selection] (FR-025).
 *  - Confirm is disabled while nothing is selected (FR-026).
 *  - While [inProgress], a progress indicator shows, every control is disabled, and neither an
 *    outside tap nor system back closes the dialog, so a clear can be neither re-submitted nor
 *    abandoned midway (FR-032).
 *
 * Spec 019 FR-045 — styled as the design system's confirm dialog (peach glyph, title, then a
 * full-width danger button above Cancel). It keeps its own layout rather than wrapping
 * `ConfirmDialog` because that component cannot disable its buttons, tag them, or refuse
 * dismissal, all of which FR-026 and FR-032 need.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
fun ClearBrowsingDataDialog(
    selection: Set<ClearBrowsingDataCategory>,
    inProgress: Boolean,
    onToggle: (ClearBrowsingDataCategory) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    Dialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !inProgress, dismissOnClickOutside = !inProgress),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .widthIn(max = Dimens.dialogMaxWidth)
                .testTag(TEST_TAG_SETTINGS_CLEAR_DIALOG),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Dimens.dialogGlyph)
                        .background(colors.tilePeach, MaterialTheme.shapes.large),
                ) {
                    Icon(
                        painter = painterResource(AppIcons.MopFilled),
                        contentDescription = null,
                        tint = colors.tilePeachInk,
                        modifier = Modifier.size(Dimens.icon),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_clear_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PillButton(
                        label = stringResource(R.string.settings_clear_confirm),
                        onClick = onConfirm,
                        style = PillStyle.DangerFilled,
                        enabled = selection.isNotEmpty() && !inProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TEST_TAG_SETTINGS_CLEAR_CONFIRM),
                    )
                    PillButton(
                        label = stringResource(R.string.settings_action_cancel),
                        onClick = onDismiss,
                        style = PillStyle.Text,
                        enabled = !inProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
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
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
        CircularProgressIndicator(modifier = Modifier.size(Dimens.icon))
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(
            text = stringResource(R.string.settings_clear_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
