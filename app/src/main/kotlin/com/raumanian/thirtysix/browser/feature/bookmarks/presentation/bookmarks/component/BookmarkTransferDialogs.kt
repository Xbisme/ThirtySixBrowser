@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult

/** Cannot be dismissed: leaving mid-import cancels it, which the person should do only on purpose. */
@Composable
fun BookmarkTransferProgressDialog(isImport: Boolean, modifier: Modifier = Modifier) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier.widthIn(max = Dimens.dialogMaxWidth).testTag(TEST_TAG_BOOKMARK_TRANSFER_PROGRESS),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.icon))
                Text(
                    text = stringResource(
                        if (isImport) R.string.bookmarks_transfer_importing else R.string.bookmarks_transfer_exporting,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
fun BookmarkImportSummaryDialog(
    result: BookmarkImportResult.Imported,
    onOpenFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = buildList {
        add(
            pluralStringResource(
                R.plurals.bookmarks_import_done_added,
                result.imported,
                result.imported,
                result.folderName,
            ),
        )
        if (result.duplicatesSkipped > 0) {
            add(
                pluralStringResource(
                    R.plurals.bookmarks_import_done_duplicates,
                    result.duplicatesSkipped,
                    result.duplicatesSkipped,
                ),
            )
        }
        if (result.unsupportedSkipped > 0) {
            add(
                pluralStringResource(
                    R.plurals.bookmarks_import_done_unsupported,
                    result.unsupportedSkipped,
                    result.unsupportedSkipped,
                ),
            )
        }
    }
    ConfirmDialog(
        title = stringResource(R.string.bookmarks_import_done_title),
        body = lines.joinToString(separator = "\n"),
        confirmLabel = stringResource(R.string.bookmarks_import_open_folder),
        cancelLabel = stringResource(R.string.bookmarks_import_done_dismiss),
        onConfirm = onOpenFolder,
        onDismiss = onDismiss,
        glyph = AppIcons.FileOpen,
        destructive = false,
        modifier = modifier.testTag(TEST_TAG_BOOKMARK_IMPORT_SUMMARY),
    )
}

const val TEST_TAG_BOOKMARK_TRANSFER_PROGRESS: String = "bookmark_transfer_progress"
const val TEST_TAG_BOOKMARK_IMPORT_SUMMARY: String = "bookmark_import_summary"
