@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLetter
import java.net.URI

/**
 * Spec 013 — a bookmark row; Spec 019 — an open [ListRow] led by the host's first letter,
 * hostname as the secondary line. Tap opens, long-press opens the action sheet.
 */
@Composable
fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = extractHostname(bookmark.url)
    ListRow(
        title = bookmark.title,
        subtitle = host,
        leading = { ListRowLetter(letter = hostLetter(host)) },
        onClick = onClick,
        onLongClick = onLongPress,
        modifier = modifier,
    )
}

internal fun extractHostname(url: String): String =
    runCatching { URI(url).host ?: url }.getOrDefault(url)

/** First letter of [host] without a leading `www.`, upper-cased. */
internal fun hostLetter(host: String): String =
    host.removePrefix(WWW_PREFIX).take(1).uppercase()

private const val WWW_PREFIX = "www."
