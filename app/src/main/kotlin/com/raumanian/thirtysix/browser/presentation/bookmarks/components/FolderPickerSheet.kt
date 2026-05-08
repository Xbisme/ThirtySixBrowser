@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 013 R10 — folder picker bottom sheet.
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
    val sheetState = rememberModalBottomSheetState()
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            // Synthetic Root entry pinned at index 0.
            Text(
                text = rootLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(null) }
                    .padding(vertical = Spacing.sm, horizontal = Spacing.sm),
            )
            LazyColumn {
                items(entries, key = { it.folder.id }) { entry ->
                    Text(
                        text = entry.folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(entry.folder.id) }
                            .padding(
                                start = (entry.depth * INDENT_DP).dp + Spacing.sm,
                                end = Spacing.sm,
                                top = Spacing.sm,
                                bottom = Spacing.sm,
                            ),
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

private const val INDENT_DP: Int = 16

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
