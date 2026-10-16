@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing

/** The arrow on the side the swipe comes from; it grows with the swipe and fills at the threshold. */
@Composable
internal fun BoxScope.SwipeNavigationIndicator(state: SwipeNavigationState) {
    val direction = state.direction ?: return
    val isBack = direction == SwipeDirection.Back
    val ready by remember(state) { derivedStateOf { state.progress >= 1f && state.available } }
    val scheme = MaterialTheme.colorScheme
    val background = when {
        !state.available -> scheme.surfaceContainerHigh
        ready -> scheme.primary
        else -> scheme.surfaceContainerHighest
    }
    val tint = when {
        !state.available -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
        ready -> scheme.onPrimary
        else -> scheme.onSurface
    }
    val label = stringResource(if (isBack) R.string.browser_action_back else R.string.browser_action_forward)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (isBack) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = Spacing.md)
            .size(Dimens.swipeIndicator)
            .graphicsLayer {
                val progress = state.progress
                alpha = progress
                scaleX = MIN_SCALE + (1f - MIN_SCALE) * progress
                scaleY = scaleX
            }
            .background(background, CircleShape)
            .semantics { contentDescription = label },
    ) {
        Icon(
            painter = painterResource(if (isBack) AppIcons.ArrowBack else AppIcons.ArrowForward),
            contentDescription = null,
            tint = tint,
        )
    }
}

private const val MIN_SCALE = 0.6f
private const val DISABLED_ALPHA = 0.38f
