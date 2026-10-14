@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
/**
 * Spec 016 FR-003 — a setting whose current value is shown under its title. The whole row is
 * the touch target, at least the platform's minimum interactive size tall (FR-039), and
 * TalkBack reads title and value together because they share one clickable node.
 * Spec 019 adds the pastel icon lead and a trailing chevron (FR-044).
 */
@Composable
@Suppress("LongParameterList")
fun SettingsValueRow(
    title: String,
    value: String,
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowLayout(
        icon = icon,
        tone = tone,
        title = title,
        summary = value,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(AppIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.icon),
        )
    }
}

/**
 * Spec 020 FR-021 — a setting shown for information only, such as "ThirtySix is your default
 * browser": no click, no chevron. Title and summary are merged into one node so TalkBack reads
 * them together.
 */
@Composable
fun SettingsInfoRow(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    modifier: Modifier = Modifier,
) {
    SettingsRowLayout(
        icon = icon,
        tone = tone,
        title = title,
        summary = summary,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {}
}

/**
 * Spec 016 — a row that starts an action instead of showing a value, such as clearing browsing
 * data. The whole row is the touch target, at least the minimum interactive size tall (FR-039),
 * and announced as a button.
 */
@Composable
fun SettingsActionRow(
    title: String,
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowLayout(
        icon = icon,
        tone = tone,
        title = title,
        summary = null,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            painter = painterResource(AppIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.icon),
        )
    }
}

/**
 * Spec 016 — an on/off setting. The row itself is `toggleable` with [Role.Switch], so TalkBack
 * announces the on/off state and a tap anywhere on the row flips it; the [Switch] is visual
 * only (`onCheckedChange = null`) to avoid a second, smaller focus target (FR-039).
 */
@Composable
@Suppress("LongParameterList")
fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsRowLayout(
        icon = icon,
        tone = tone,
        title = title,
        summary = summary,
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The shared row anatomy: icon square, title over an optional summary, trailing slot. */
@Composable
@Suppress("LongParameterList")
private fun SettingsRowLayout(
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .then(modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        SettingsIcon(icon = icon, tone = tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** A Settings row's 40dp rounded icon square in its [tone] (FR-044). */
@Composable
fun SettingsIcon(
    @DrawableRes icon: Int,
    tone: SettingsIconTone,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (fill, ink) = when (tone) {
        SettingsIconTone.Sky -> colors.tileSky to colors.tileSkyInk
        SettingsIconTone.Sun -> colors.tileSun to colors.tileSunInk
        SettingsIconTone.Mint -> colors.tileMint to colors.tileMintInk
        SettingsIconTone.Peach -> colors.tilePeach to colors.tilePeachInk
        SettingsIconTone.Primary -> scheme.primaryContainer to scheme.primary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(Dimens.settingIcon)
            .clip(MaterialTheme.shapes.medium)
            .background(fill),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(Dimens.iconSmall),
        )
    }
}
