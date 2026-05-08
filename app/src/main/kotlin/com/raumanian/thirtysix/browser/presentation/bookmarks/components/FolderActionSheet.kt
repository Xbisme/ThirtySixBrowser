@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun FolderActionSheet(
    folder: BookmarkFolder,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onOpen() },
                headlineContent = { Text(stringResource(R.string.bookmarks_action_open)) },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onRename() },
                leadingContent = { Icon(Icons.Filled.Edit, null) },
                headlineContent = { Text(stringResource(R.string.bookmarks_action_rename)) },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onMove() },
                headlineContent = { Text(stringResource(R.string.bookmarks_action_move)) },
            )
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onDelete() },
                leadingContent = {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                headlineContent = {
                    Text(
                        stringResource(R.string.bookmarks_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}
