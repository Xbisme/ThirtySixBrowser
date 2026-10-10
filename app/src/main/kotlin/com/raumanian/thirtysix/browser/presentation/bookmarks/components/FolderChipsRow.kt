@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.presentation.components.ThirtySixChip
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

private const val CONTENT_TYPE_FOLDER_CHIP = "folder_chip"

/**
 * Spec 019 FR-042 — top-level folders as chips at the bookmarks root. A chip is only another
 * entry point to the existing "open folder" action: tapping one does exactly what tapping the
 * folder's row does ([onFolderClick]). Never selected — the root has no current folder.
 */
@Composable
fun FolderChipsRow(
    folders: List<BookmarkFolder>,
    onFolderClick: (BookmarkFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(folders, key = { it.id }, contentType = { CONTENT_TYPE_FOLDER_CHIP }) { folder ->
            ThirtySixChip(
                label = folder.name,
                selected = false,
                onClick = { onFolderClick(folder) },
                leadingIcon = AppIcons.Folder,
            )
        }
    }
}
