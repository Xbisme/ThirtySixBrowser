@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.presentation.components.ListRow
import com.raumanian.thirtysix.browser.presentation.components.ListRowIcon
import com.raumanian.thirtysix.browser.presentation.components.ListRowLead
import com.raumanian.thirtysix.browser.presentation.components.ListRowLetter
import com.raumanian.thirtysix.browser.presentation.components.SheetFrame
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 014 FR-018 / FR-022 — long-press action sheet for a single history row, in the shared
 * [SheetFrame] (Spec 019): the entry it acts on, then exactly three actions in the order the
 * spec fixes — **Open in new tab**, **Delete entry**, **Copy URL**. Dismissal (tap-outside,
 * drag or system back) routes through [onDismiss] and performs no action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun HistoryActionSheet(
    entry: HistoryEntry,
    onOpenInNewTab: () -> Unit,
    onDelete: () -> Unit,
    onCopyUrl: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostname = entry.url.extractHostnameOrSelf()
    SheetFrame(onDismiss = onDismiss, modifier = modifier.testTag(TEST_TAG_HISTORY_ACTION_SHEET)) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            ListRow(
                title = entry.title.ifBlank { hostname.ifBlank { entry.url } },
                subtitle = hostname,
                leading = { ListRowLetter(letter = hostname.take(1).uppercase()) },
            )
            ListRow(
                title = stringResource(R.string.history_action_open_in_new_tab),
                subtitle = "",
                leading = { ListRowIcon(icon = AppIcons.OpenInNew) },
                onClick = onOpenInNewTab,
                modifier = Modifier.testTag(TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB),
            )
            DeleteActionRow(onDelete = onDelete)
            ListRow(
                title = stringResource(R.string.history_action_copy_url),
                subtitle = "",
                leading = { ListRowIcon(icon = AppIcons.ContentCopy) },
                onClick = onCopyUrl,
                modifier = Modifier.testTag(TEST_TAG_HISTORY_ACTION_COPY_URL),
            )
        }
    }
}

/** The destructive action, its glyph tinted `error` so it reads as one. */
@Composable
private fun DeleteActionRow(onDelete: () -> Unit) {
    ListRow(
        title = stringResource(R.string.history_action_delete_entry),
        subtitle = "",
        leading = {
            ListRowLead {
                Icon(
                    painter = painterResource(AppIcons.DeleteFilled),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimens.icon),
                )
            }
        },
        onClick = onDelete,
        modifier = Modifier.testTag(TEST_TAG_HISTORY_ACTION_DELETE),
    )
}

/** Test tags exposed for instrumented tests asserting sheet contents + order. */
const val TEST_TAG_HISTORY_ACTION_SHEET: String = "history_action_sheet"
const val TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB: String = "history_action_open_in_new_tab"
const val TEST_TAG_HISTORY_ACTION_DELETE: String = "history_action_delete"
const val TEST_TAG_HISTORY_ACTION_COPY_URL: String = "history_action_copy_url"
