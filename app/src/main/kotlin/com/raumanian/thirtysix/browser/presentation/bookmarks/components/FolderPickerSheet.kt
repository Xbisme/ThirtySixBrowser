@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetTitle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
private const val CONTENT_TYPE_ROOT = "picker_root"
private const val CONTENT_TYPE_FOLDER = "picker_folder"
private const val KEY_ROOT = "picker_root"

/**
 * Spec 013 R10 — folder picker bottom sheet; Spec 019 — on [SheetFrame] with open rows.
 *
 * Renders the synthetic "Root" entry plus all folders in [allFolders],
 * indented by depth (computed client-side from the parent chain).
 *
 * [excludeFolderIdAndDescendants] (optional) — when picking a destination
 * for a Move-folder operation, pass the folder being moved to filter out
 * itself + descendants up-front (UX guardrail; the use case still rejects
 * any cycle that slips through, so this is informational).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun FolderPickerSheet(
    title: String,
    allFolders: List<BookmarkFolder>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    excludeFolderIdAndDescendants: Long? = null,
) {
    val rootLabel = stringResource(R.string.bookmarks_breadcrumb_root_label)

    val excluded: Set<Long> = remember(allFolders, excludeFolderIdAndDescendants) {
        if (excludeFolderIdAndDescendants == null) {
            emptySet()
        } else {
            collectDescendantIds(allFolders, excludeFolderIdAndDescendants)
        }
    }
    val entries: List<FolderPickerEntry> = remember(allFolders, excluded) {
        flattenWithDepth(allFolders).filter { it.folder.id !in excluded }
    }

    SheetFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            SheetTitle(text = title)
            LazyColumn {
                // Synthetic Root entry pinned at index 0.
                item(key = KEY_ROOT, contentType = CONTENT_TYPE_ROOT) {
                    ListRow(
                        title = rootLabel,
                        subtitle = "",
                        leading = { ListRowIcon(icon = AppIcons.Bookmarks, accent = true) },
                        onClick = { onPick(null) },
                    )
                }
                items(entries, key = { it.folder.id }, contentType = { CONTENT_TYPE_FOLDER }) { entry ->
                    ListRow(
                        title = entry.folder.name,
                        subtitle = "",
                        leading = { ListRowIcon(icon = AppIcons.Folder, accent = true) },
                        onClick = { onPick(entry.folder.id) },
                        modifier = Modifier.padding(start = Spacing.md * (entry.depth + 1)),
                    )
                }
            }
        }
    }
}

/** Indented row entry rendered in the picker list. */
private data class FolderPickerEntry(
    val folder: BookmarkFolder,
    val depth: Int,
)

/**
 * Walks [folders] root-first, computing depth from the parent chain. Output
 * is sorted root → leaf with sibling alphabetical within each level.
 */
private fun flattenWithDepth(folders: List<BookmarkFolder>): List<FolderPickerEntry> {
    val byParent: Map<Long?, List<BookmarkFolder>> = folders.groupBy { it.parentId }
    val out = mutableListOf<FolderPickerEntry>()
    fun walk(parentId: Long?, depth: Int) {
        val children = byParent[parentId].orEmpty().sortedBy { it.name }
        for (child in children) {
            out.add(FolderPickerEntry(child, depth))
            walk(child.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

/** Returns [folderId] plus every descendant id; used to filter out cycle targets. */
private fun collectDescendantIds(folders: List<BookmarkFolder>, folderId: Long): Set<Long> {
    val byParent: Map<Long?, List<BookmarkFolder>> = folders.groupBy { it.parentId }
    val out = mutableSetOf(folderId)
    fun walk(id: Long) {
        val children = byParent[id].orEmpty()
        for (child in children) {
            out.add(child.id)
            walk(child.id)
        }
    }
    walk(folderId)
    return out
}
