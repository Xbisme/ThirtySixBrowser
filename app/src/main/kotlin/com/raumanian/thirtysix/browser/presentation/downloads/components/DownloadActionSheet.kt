@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.raumanian.thirtysix.browser.presentation.downloads.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.DownloadActionAvailability
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

internal const val TEST_TAG_DOWNLOAD_ACTION_SHEET: String = "download_action_sheet"
internal const val TEST_TAG_ACTION_OPEN: String = "download_action_open"
internal const val TEST_TAG_ACTION_COPY_LINK: String = "download_action_copy_link"
internal const val TEST_TAG_ACTION_REMOVE: String = "download_action_remove"
internal const val TEST_TAG_ACTION_DELETE_FILE: String = "download_action_delete_file"

/**
 * Spec 015 FR-030 / FR-034a — long-press action sheet.
 *
 * Renders in the fixed FR-030 order — open · copy link · remove from list · delete file —
 * but shows only the entries the [DownloadActionAvailability] matrix allows for this entry's
 * current state. The matrix is a pure domain function with its own tests, so what the sheet
 * offers and what the tests assert cannot drift apart.
 *
 * Icons follow A12: `material-icons-core` has no honest glyph for "open", "copy" or
 * "remove", so those rows carry **no** icon rather than a misleading one — the precedent
 * Spec 013's `BookmarkActionSheet` and Spec 014's history sheet both set. Only "delete file"
 * gets one, because `Delete` genuinely means delete.
 *
 * `ModalBottomSheet` dismisses natively on outside-tap and system back (FR-035), so no
 * dismiss affordance — and therefore no dismiss string — is added.
 */
@Composable
fun DownloadActionSheet(
    item: DownloadListItem,
    callbacks: DownloadActionSheetCallbacks,
    modifier: Modifier = Modifier,
) {
    val availability = DownloadActionAvailability.forStatus(
        status = item.status,
        // The status resolution already established presence: anything the platform or the
        // fallback calls Complete has a file, and Missing explicitly does not.
        fileIsPresent = item.status is DownloadStatus.Complete,
    )

    ModalBottomSheet(
        onDismissRequest = callbacks.onDismiss,
        modifier = modifier.testTag(TEST_TAG_DOWNLOAD_ACTION_SHEET),
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = item.record.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )

            if (availability.canOpen) {
                ActionRow(R.string.downloads_action_open, TEST_TAG_ACTION_OPEN, callbacks.onOpen)
            }
            if (availability.canCopyLink) {
                ActionRow(R.string.downloads_action_copy_link, TEST_TAG_ACTION_COPY_LINK, callbacks.onCopyLink)
            }
            if (availability.canRemoveFromList) {
                ActionRow(R.string.downloads_action_remove, TEST_TAG_ACTION_REMOVE, callbacks.onRemoveFromList)
            }
            if (availability.canDeleteFile) {
                ActionRow(
                    labelRes = R.string.downloads_action_delete_file,
                    testTag = TEST_TAG_ACTION_DELETE_FILE,
                    onClick = callbacks.onDeleteFile,
                    icon = Icons.Filled.Delete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    labelRes: Int,
    testTag: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(end = Spacing.md),
            )
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
        )
    }
}
