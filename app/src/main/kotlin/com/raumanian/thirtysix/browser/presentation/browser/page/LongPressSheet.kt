@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.domain.model.LongPressMenu
import com.raumanian.thirtysix.browser.presentation.bookmarks.components.SheetActionRow
/**
 * Spec 021 US4 / C2.4 — the long-press menu: the address on one line (middle-ellipsized) and one
 * row per action. Back or a swipe closes it without acting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressSheet(menu: LongPressMenu, onAction: (LongPressAction) -> Unit, onDismiss: () -> Unit) {
    SheetFrame(onDismiss = onDismiss, modifier = Modifier.testTag(TEST_TAG_LONG_PRESS_SHEET)) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            Text(
                text = menu.header,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
            menu.actions.forEach { action ->
                SheetActionRow(stringResource(action.labelRes()), action.iconRes(), { onAction(action) })
            }
        }
    }
}

@StringRes
private fun LongPressAction.labelRes(): Int = when (this) {
    LongPressAction.OpenInNewTab -> R.string.long_press_open_new_tab
    LongPressAction.OpenInIncognitoTab -> R.string.long_press_open_incognito_tab
    LongPressAction.CopyLink -> R.string.long_press_copy_link
    LongPressAction.ShareLink -> R.string.long_press_share_link
    LongPressAction.DownloadLink -> R.string.long_press_download_link
    LongPressAction.OpenImageInNewTab -> R.string.long_press_open_image
    LongPressAction.DownloadImage -> R.string.long_press_download_image
    LongPressAction.CopyImageAddress -> R.string.long_press_copy_image
    LongPressAction.ShareImageLink -> R.string.long_press_share_image
}

@DrawableRes
private fun LongPressAction.iconRes(): Int = when (this) {
    LongPressAction.OpenInNewTab -> AppIcons.Tab
    LongPressAction.OpenInIncognitoTab -> AppIcons.Incognito
    LongPressAction.CopyLink, LongPressAction.CopyImageAddress -> AppIcons.ContentCopy
    LongPressAction.ShareLink, LongPressAction.ShareImageLink -> AppIcons.Share
    LongPressAction.DownloadLink, LongPressAction.DownloadImage -> AppIcons.Download
    LongPressAction.OpenImageInNewTab -> AppIcons.Image
}

const val TEST_TAG_LONG_PRESS_SHEET: String = "long_press_sheet"
