@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
private val RowVerticalPadding = 10.dp
private val RowGap = 14.dp
private val RowMinHeight = 56.dp

/**
 * Spec 019 — the one list row for History, Bookmarks, Downloads and search results (design
 * system ListRow): a 44dp leading square, one-line title, secondary line in `onSurfaceVariant`,
 * optional trailing slot. Open on the surface, no card, no divider. Long-press opens the item's
 * action sheet when [onLongClick] is given.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongParameterList")
fun ListRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(role = Role.Button, onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .then(clickable)
            .padding(horizontal = Spacing.md, vertical = RowVerticalPadding),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            below?.invoke()
        }
        trailing?.invoke()
    }
}

/** A row's leading square holding a single letter (no favicon yet). */
@Composable
fun ListRowLetter(letter: String, modifier: Modifier = Modifier) {
    ListRowLead(modifier = modifier, fill = MaterialTheme.colorScheme.surfaceContainer) {
        Text(text = letter, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A row's leading square holding an icon; [accent] is the folder look (`primaryContainer`). */
@Composable
fun ListRowIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, accent: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    ListRowLead(modifier = modifier, fill = if (accent) scheme.primaryContainer else scheme.surfaceContainer) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (accent) scheme.primary else scheme.onSurface,
            modifier = Modifier.size(Dimens.icon),
        )
    }
}

/** The bare 44dp leading square, for custom content such as a favicon image. */
@Composable
fun ListRowLead(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimens.listLead)
            .clip(MaterialTheme.shapes.medium)
            .background(fill),
    ) { content() }
}
