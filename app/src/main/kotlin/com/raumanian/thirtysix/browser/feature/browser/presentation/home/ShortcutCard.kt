@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SeededMeshArt
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.rememberFileImage
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.seedColorFor
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.softShadow
import com.raumanian.thirtysix.browser.feature.browser.domain.model.ShortcutKind
private val CardPadding = 10.dp
private val TagPadding = 8.dp
private val TagGap = 4.dp
private const val SCRIM_ALPHA = 0.45f
private const val SHADOW_BLUR = 8f

/** The tab's screenshot when there is one, otherwise a mesh seeded from the host. */
@Composable
fun ShortcutCard(
    item: HomeShortcutUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    val shortcut = item.shortcut
    val screenshot by rememberFileImage(item.screenshot)
    Box(
        modifier = modifier
            .size(width = Dimens.shortcutWidth, height = Dimens.shortcutHeight)
            .softShadow(shape, colors.shadow)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val image = screenshot
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SeededMeshArt(
                seed = seedColorFor(shortcut.host),
                letter = shortcut.host.firstOrNull()?.uppercase(),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, colors.heroDeep.copy(alpha = SCRIM_ALPHA))),
                ),
        )
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(CardPadding),
        ) {
            KindTag(kind = shortcut.kind)
            CardCaption(title = shortcut.title, host = shortcut.host)
        }
    }
}

@Composable
private fun CardCaption(title: String, host: String) {
    val colors = ThirtySixTheme.colors
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                shadow = Shadow(color = colors.heroDeep, blurRadius = SHADOW_BLUR),
            ),
            color = colors.onGlass,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = host,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onGlassMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KindTag(kind: ShortcutKind) {
    val colors = ThirtySixTheme.colors
    val shape = ThirtySixTheme.shapes.full
    val (icon, label) = when (kind) {
        ShortcutKind.Tab -> AppIcons.Tab to R.string.home_shortcut_tag_tab
        ShortcutKind.Saved -> AppIcons.Bookmark to R.string.home_shortcut_tag_saved
        ShortcutKind.Recent -> AppIcons.History to R.string.home_shortcut_tag_recent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TagGap),
        modifier = Modifier
            .height(Dimens.shortcutTag)
            .clip(shape)
            .background(colors.glass)
            .border(Dimens.hairline, colors.glassStroke, shape)
            .padding(horizontal = TagPadding),
    ) {
        Icon(painterResource(icon), null, tint = colors.onGlass, modifier = Modifier.size(Dimens.iconSmall - TagGap))
        Text(text = stringResource(label), style = MaterialTheme.typography.labelSmall, color = colors.onGlass)
    }
}
