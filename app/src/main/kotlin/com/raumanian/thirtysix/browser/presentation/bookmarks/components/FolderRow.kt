@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
    folder: BookmarkFolder,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        headlineContent = { Text(folder.name) },
    )
}
