@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.validator.BookmarkUrlValidator
/**
 * Spec 013 — single dialog used for both manual-add (US7) and edit (US5).
 * Validates URL inline using [BookmarkUrlValidator]; rejects empty title +
 * empty URL + malformed URL with localized error per FR-006 / FR-022.
 *
 * Folder selection: a row underneath the URL field shows the resolved path
 * (e.g. "Folder: Work / Project A" or "Folder: Root"). Tapping it opens an
 * inline FolderPickerSheet over the dialog.
 *
 * Spec 019 — on [BookmarkInputDialog]; the folder selector is a rounded well with a chevron.
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

    BookmarkInputDialog(
        title = title,
        confirmEnabled = canSubmit,
        onConfirm = {
            if (canSubmit) {
                val normalized = (urlValidation as Result.Success).data
                onConfirm(bookmarkTitle.trim(), normalized, selectedParentId)
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = bookmarkTitle,
            onValueChange = {
                if (it.length <= BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH) {
                    bookmarkTitle = it
                }
            },
            singleLine = true,
            isError = titleError,
            shape = bookmarkFieldShape(),
            supportingText = if (titleError) {
                { Text(stringResource(R.string.bookmarks_validation_title_required)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.bookmarks_field_title_label)) },
            modifier = Modifier.fillMaxWidth(),
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
            shape = bookmarkFieldShape(),
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
            modifier = Modifier.fillMaxWidth(),
        )
        // Folder selector — tap to open the FolderPickerSheet over this dialog.
        FolderSelectorRow(text = folderFieldText, onClick = { folderPickerOpen = true })
    }

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

@Composable
private fun FolderSelectorRow(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Icon(
            painter = painterResource(AppIcons.Folder),
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.bookmarks_field_folder_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(AppIcons.ChevronRight),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSmall),
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
