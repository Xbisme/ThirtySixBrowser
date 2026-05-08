@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.validator.BookmarkUrlValidator
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 013 — single dialog used for both manual-add (US7) and edit (US5).
 * Validates URL inline using [BookmarkUrlValidator]; rejects empty title +
 * empty URL + malformed URL with localized error per FR-006 / FR-022.
 *
 * Folder selection: a row underneath the URL field shows the resolved path
 * (e.g. "Folder: Work / Project A" or "Folder: Root"). Tapping it opens an
 * inline FolderPickerSheet over the dialog.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
fun AddOrEditBookmarkDialog(
    title: String,
    initialBookmarkTitle: String,
    initialUrl: String,
    initialParentId: Long?,
    allFolders: List<BookmarkFolder>,
    onConfirm: (title: String, url: String, parentId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookmarkTitle by remember { mutableStateOf(initialBookmarkTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var selectedParentId: Long? by remember { mutableStateOf(initialParentId) }
    var folderPickerOpen by remember { mutableStateOf(false) }

    val titleError = bookmarkTitle.trim().isEmpty()
    val urlValidation = BookmarkUrlValidator.validate(url)
    val urlError = urlValidation is Result.Error
    val canSubmit = !titleError && !urlError

    val folderLabel = remember(selectedParentId, allFolders) {
        resolveFolderLabel(selectedParentId, allFolders)
    }
    val rootLabel = stringResource(R.string.bookmarks_breadcrumb_root_label)
    val folderFieldText = folderLabel ?: rootLabel

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = bookmarkTitle,
                    onValueChange = {
                        if (it.length <= BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH) {
                            bookmarkTitle = it
                        }
                    },
                    singleLine = true,
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text(stringResource(R.string.bookmarks_validation_title_required)) }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.bookmarks_field_title_label)) },
                    modifier = Modifier,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        if (it.length <= BrowserLimits.MAX_BOOKMARK_URL_LENGTH) {
                            url = it
                        }
                    },
                    singleLine = true,
                    isError = urlError,
                    supportingText = if (urlError) {
                        {
                            val msg = if (url.trim().isEmpty()) {
                                stringResource(R.string.bookmarks_validation_url_required)
                            } else {
                                stringResource(R.string.bookmarks_validation_url_invalid)
                            }
                            Text(msg)
                        }
                    } else {
                        null
                    },
                    label = { Text(stringResource(R.string.bookmarks_field_url_label)) },
                )
                // Folder selector — tap to open the FolderPickerSheet over this dialog.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPickerOpen = true }
                        .padding(vertical = Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.bookmarks_field_folder_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = folderFieldText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSubmit) {
                        val normalized = (urlValidation as Result.Success).data
                        onConfirm(bookmarkTitle.trim(), normalized, selectedParentId)
                    }
                },
                enabled = canSubmit,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )

    if (folderPickerOpen) {
        FolderPickerSheet(
            title = stringResource(R.string.bookmarks_dialog_folder_picker_title),
            allFolders = allFolders,
            onPick = { picked ->
                selectedParentId = picked
                folderPickerOpen = false
            },
            onDismiss = { folderPickerOpen = false },
        )
    }
}

/**
 * Resolves the "root → leaf" path string for a given folder id by walking
 * the parent chain in [allFolders]. Returns null when [folderId] is null
 * (caller renders the localized "Root" label in that case).
 */
@Suppress("ReturnCount")
private fun resolveFolderLabel(folderId: Long?, allFolders: List<BookmarkFolder>): String? {
    if (folderId == null) return null
    val byId: Map<Long, BookmarkFolder> = allFolders.associateBy { it.id }
    val chain = ArrayDeque<String>()
    var current: Long? = folderId
    val visited = HashSet<Long>()
    while (current != null && current !in visited) {
        visited.add(current)
        val folder = byId[current] ?: return chain.joinToString(separator = " / ")
        chain.addFirst(folder.name)
        current = folder.parentId
    }
    return chain.joinToString(separator = " / ")
}
