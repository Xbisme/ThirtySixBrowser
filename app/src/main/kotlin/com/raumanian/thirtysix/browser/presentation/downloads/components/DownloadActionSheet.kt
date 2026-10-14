@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.raumanian.thirtysix.browser.presentation.downloads.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLead
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.DownloadActionAvailability
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
internal const val TEST_TAG_DOWNLOAD_ACTION_SHEET: String = "download_action_sheet"
internal const val TEST_TAG_ACTION_OPEN: String = "download_action_open"
internal const val TEST_TAG_ACTION_COPY_LINK: String = "download_action_copy_link"
internal const val TEST_TAG_ACTION_REMOVE: String = "download_action_remove"
internal const val TEST_TAG_ACTION_DELETE_FILE: String = "download_action_delete_file"

/**
 * Spec 015 FR-030 / FR-034a · Spec 019 — long-press action sheet on the shared [SheetFrame].
 *
 * Renders in the fixed FR-030 order — open · copy link · remove from list · delete file —
 * but shows only the entries the [DownloadActionAvailability] matrix allows for this entry's
 * current state. The matrix is a pure domain function with its own tests, so what the sheet
 * offers and what the tests assert cannot drift apart. "Delete file" carries the destructive
 * peach well, matching the confirmation dialog it opens.
 *
 * The sheet dismisses natively on outside-tap and system back (FR-035), so no dismiss
 * affordance — and therefore no dismiss string — is added.
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

    SheetFrame(
        onDismiss = callbacks.onDismiss,
        modifier = modifier.testTag(TEST_TAG_DOWNLOAD_ACTION_SHEET),
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = item.record.fileName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )

            if (availability.canOpen) {
                ActionRow(
                    R.string.downloads_action_open,
                    AppIcons.OpenInNew,
                    TEST_TAG_ACTION_OPEN,
                    callbacks.onOpen,
                )
            }
            if (availability.canCopyLink) {
                ActionRow(
                    R.string.downloads_action_copy_link,
                    AppIcons.ContentCopy,
                    TEST_TAG_ACTION_COPY_LINK,
                    callbacks.onCopyLink,
                )
            }
            if (availability.canRemoveFromList) {
                ActionRow(
                    R.string.downloads_action_remove,
                    AppIcons.Close,
                    TEST_TAG_ACTION_REMOVE,
                    callbacks.onRemoveFromList,
                )
            }
            if (availability.canDeleteFile) {
                ActionRow(
                    labelRes = R.string.downloads_action_delete_file,
                    icon = AppIcons.DeleteFilled,
                    testTag = TEST_TAG_ACTION_DELETE_FILE,
                    onClick = callbacks.onDeleteFile,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    @StringRes labelRes: Int,
    @DrawableRes icon: Int,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    ListRow(
        title = stringResource(labelRes),
        subtitle = "",
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        leading = {
            if (destructive) {
                val colors = ThirtySixTheme.colors
                ListRowLead(fill = colors.tilePeach) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = colors.tilePeachInk,
                        modifier = Modifier.size(Dimens.icon),
                    )
                }
            } else {
                ListRowIcon(icon = icon)
            }
        },
    )
}
