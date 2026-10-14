@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/** Confirms cascade-deleting a non-empty folder; the body uses plurals for languages that need them. */
@Composable
fun DeleteFolderConfirmDialog(
    bookmarksCount: Int,
    foldersCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.bookmarks_confirm_delete_folder_title),
        body = pluralStringResource(
            R.plurals.bookmarks_confirm_delete_folder_body,
            bookmarksCount,
            bookmarksCount,
            foldersCount,
        ),
        confirmLabel = stringResource(R.string.bookmarks_confirm_delete_folder_button),
        cancelLabel = stringResource(android.R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        glyph = AppIcons.DeleteFilled,
        destructive = true,
    )
}
