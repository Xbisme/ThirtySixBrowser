@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.domain.model.Bookmark
import java.net.URI

/**
 * Spec 013 — leading icon `Icons.Filled.FavoriteBorder` (heart-outline) for
 * "saved" affordance — `material-icons-core` lacks `Bookmark` so the heart
 * pair stands in. Tinted `MaterialTheme.colorScheme.primary`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(bookmark.title) },
        supportingContent = { Text(extractHostname(bookmark.url)) },
    )
}

internal fun extractHostname(url: String): String =
    runCatching { URI(url).host ?: url }.getOrDefault(url)
