@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ThirtySixChip
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
private const val CONTENT_TYPE_FOLDER_CHIP = "folder_chip"

/**
 * Top-level folders as chips at the bookmarks root; a chip does exactly what tapping the
 * folder's row does ([onFolderClick]). Never selected: the root has no current folder.
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
