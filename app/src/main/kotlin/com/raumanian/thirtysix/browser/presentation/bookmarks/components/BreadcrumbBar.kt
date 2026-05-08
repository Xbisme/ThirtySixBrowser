@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 013 FR-014 — breadcrumb. Tappable segments: "Root / A / B / C". Tap
 * a segment to navigate to that level. Horizontal-scrolls when long.
 */
@Composable
fun BreadcrumbBar(
    chain: List<BookmarkFolder>,
    onSegmentClick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootLabel = stringResource(R.string.bookmarks_breadcrumb_root_label)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rootLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onSegmentClick(null) }
                .padding(horizontal = Spacing.xs),
        )
        chain.forEach { folder ->
            Text(
                text = " / ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onSegmentClick(folder.id) }
                    .padding(horizontal = Spacing.xs),
            )
        }
    }
}
