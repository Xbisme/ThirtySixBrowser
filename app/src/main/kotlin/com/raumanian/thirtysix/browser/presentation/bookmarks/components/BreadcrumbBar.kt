@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/**
 * Spec 013 FR-014 — breadcrumb. Tappable segments: "Root › A › B › C". Tap a segment to
 * navigate to that level. Horizontal-scrolls when long. Spec 019 — chevron separators, the
 * current level in `onSurface`, ancestors in `primary`.
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
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BreadcrumbSegment(label = rootLabel, current = chain.isEmpty(), onClick = { onSegmentClick(null) })
        chain.forEachIndexed { index, folder ->
            Icon(
                painter = painterResource(AppIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSmall),
            )
            BreadcrumbSegment(
                label = folder.name,
                current = index == chain.lastIndex,
                onClick = { onSegmentClick(folder.id) },
            )
        }
    }
}

@Composable
private fun BreadcrumbSegment(label: String, current: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(ThirtySixTheme.shapes.full)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
