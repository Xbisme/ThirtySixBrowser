@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 013 FR-020 — confirmation dialog for cascade-deleting a non-empty
 * folder. Body text uses a plurals resource so locales with non-trivial
 * pluralization rules render correctly.
 */
@Composable
fun DeleteFolderConfirmDialog(
    bookmarksCount: Int,
    foldersCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        title = { Text(stringResource(R.string.bookmarks_confirm_delete_folder_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.bookmarks_confirm_delete_folder_body,
                    bookmarksCount,
                    bookmarksCount,
                    foldersCount,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.bookmarks_confirm_delete_folder_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
