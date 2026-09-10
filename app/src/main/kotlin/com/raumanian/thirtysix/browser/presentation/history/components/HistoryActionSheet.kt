@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 014 FR-018 / FR-022 — long-press action sheet for a single history row.
 *
 * Renders exactly three actions in the order the spec fixes: **Open in new tab**,
 * **Delete entry**, **Copy URL**. Dismissal (tap-outside or system back) routes through
 * [onDismiss] and performs no action.
 *
 * "Copy URL" carries no leading icon — `material-icons-core` ships no content-copy
 * glyph, and Spec 013's `BookmarkActionSheet` set the precedent of leaving a row
 * icon-less rather than picking a misleading one.
 */
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
    val sheetState = rememberModalBottomSheetState()
    val hostname = entry.url.extractHostnameOrSelf()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(TEST_TAG_HISTORY_ACTION_SHEET),
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            Text(
                text = entry.title.ifBlank { hostname.ifBlank { entry.url } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB)
                    .clickable { onOpenInNewTab() },
                leadingContent = { Icon(Icons.Filled.Add, null) },
                headlineContent = {
                    Text(stringResource(R.string.history_action_open_in_new_tab))
                },
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEST_TAG_HISTORY_ACTION_DELETE)
                    .clickable { onDelete() },
                leadingContent = {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.history_action_delete_entry),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TEST_TAG_HISTORY_ACTION_COPY_URL)
                    .clickable { onCopyUrl() },
                headlineContent = { Text(stringResource(R.string.history_action_copy_url)) },
            )
        }
    }
}

/** Test tags exposed for instrumented tests asserting sheet contents + order. */
const val TEST_TAG_HISTORY_ACTION_SHEET: String = "history_action_sheet"
const val TEST_TAG_HISTORY_ACTION_OPEN_IN_NEW_TAB: String = "history_action_open_in_new_tab"
const val TEST_TAG_HISTORY_ACTION_DELETE: String = "history_action_delete"
const val TEST_TAG_HISTORY_ACTION_COPY_URL: String = "history_action_copy_url"
