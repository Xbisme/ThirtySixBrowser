@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SeededMeshArt
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.rememberFileImage
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.seedColorFor
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabPreview
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabScreenshots
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.entrySign

internal const val TEST_TAG_TAB_SWIPE_PREVIEW: String = "tab_swipe_preview"

/**
 * The incoming tab beside the sliding page: its saved picture, the Tabs card's mesh, the Home
 * surface, or the incognito surface, which never shows a picture.
 */
@Composable
internal fun TabSwipePreview(
    state: TabSwipeState,
    screenshots: TabScreenshots,
    screenshotVersion: () -> Long,
    topInset: Dp,
    modifier: Modifier = Modifier,
) {
    val preview = state.incoming ?: return
    val side = state.side ?: return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = if (state.holding) 0f else (state.fraction + side.entrySign(rtl)) * size.width
            }
            .testTag(TEST_TAG_TAB_SWIPE_PREVIEW),
    ) {
        when {
            preview.isIncognito -> IncognitoSurface()
            preview.isHome -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            else -> PagePicture(preview, screenshots, screenshotVersion(), topInset)
        }
    }
}

@Composable
private fun PagePicture(preview: TabPreview, screenshots: TabScreenshots, version: Long, topInset: Dp) {
    val file = remember(preview.tabId, version) { screenshots.fileFor(preview) }
    val image by rememberFileImage(file)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val bitmap = image
        if (bitmap == null) {
            SeededMeshArt(seed = seedColorFor(preview.host), modifier = Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                // The picture is the top of the page at a fraction of the screen's width.
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().padding(top = topInset),
            )
        }
    }
}

@Composable
private fun IncognitoSurface() {
    val colors = ThirtySixTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(colors.surfaceIncognito),
    ) {
        Icon(
            painter = painterResource(AppIcons.IncognitoFilled),
            contentDescription = null,
            tint = colors.onIncognito,
            modifier = Modifier.size(Dimens.iconLarge),
        )
    }
}
