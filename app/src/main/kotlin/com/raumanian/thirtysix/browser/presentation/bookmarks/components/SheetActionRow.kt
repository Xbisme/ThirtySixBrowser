@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLead
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
/**
 * Spec 019 — one action in a bookmark/folder action sheet: a 44dp lead holding the action's
 * glyph and the label. [destructive] paints both in the error colours (Delete).
 *
 * Kept local because the shared `ListRow` fixes its title colour to `onSurface`, so it cannot
 * show a destructive label.
 */
@Composable
internal fun SheetActionRow(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = ThirtySixTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        ListRowLead(fill = if (destructive) colors.tilePeach else scheme.surfaceContainer) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (destructive) colors.tilePeachInk else scheme.onSurface,
                modifier = Modifier.size(Dimens.icon),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (destructive) scheme.error else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Spec 019 — the title line at the top of an action sheet (the item's name). */
@Composable
internal fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
