@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.BookmarkSearchResult

/**
 * Spec 013 FR-026 — search-result row showing a bookmark plus its folder
 * path inline so the user always knows where the bookmark lives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkSearchResultRow(
    result: BookmarkSearchResult,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootLabel = stringResource(R.string.bookmarks_breadcrumb_root_label)
    val pathLabel = if (result.folderPath.isEmpty()) {
        rootLabel
    } else {
        result.folderPath.joinToString(separator = " / ") { it.name }
    }
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(result.bookmark.title) },
        supportingContent = {
            Column {
                Text(extractHostname(result.bookmark.url))
                Text(
                    text = pathLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
