@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLetter
import com.raumanian.thirtysix.browser.domain.model.BookmarkSearchResult
/**
 * Spec 013 FR-026 — search-result row showing a bookmark plus its folder
 * path inline so the user always knows where the bookmark lives.
 * Spec 019 — an open [ListRow]; the path sits under the hostname.
 */
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
    val host = extractHostname(result.bookmark.url)
    ListRow(
        title = result.bookmark.title,
        subtitle = host,
        leading = { ListRowLetter(letter = hostLetter(host)) },
        onClick = onClick,
        onLongClick = onLongPress,
        modifier = modifier,
        below = {
            Text(
                text = pathLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
