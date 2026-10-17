@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabPreview
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeAction
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeEnvironment
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeInputs
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.TabSwipeUiState
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.rememberTabSwipeAccessibilityActions
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.tabSwipe
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.withAccessibilityActions

internal const val TEST_TAG_HARNESS_FIELD = "harness_field"
internal const val TEST_TAG_HARNESS_PAGE = "harness_page"

/** Counts recompositions of the parts a drag must leave alone. */
internal class RecompositionCounts {
    var pill = 0
    var page = 0
}

internal fun preview(id: Long) =
    TabPreview(id, isIncognito = false, isHome = false, host = "t$id.example", title = "T$id")

/**
 * The lane around a plain text field, laid out as the browser does: the page area moves by the
 * same share of its width, read only in its layer.
 */
@Composable
internal fun TabSwipeHarness(
    swipe: TabSwipeUiState,
    onAction: (TabSwipeAction) -> Unit,
    counts: RecompositionCounts = RecompositionCounts(),
    onFocusChange: (Boolean) -> Unit = {},
    state: TabSwipeState = remember { TabSwipeState() },
) {
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val currentSwipe by rememberUpdatedState(swipe)
    val currentOnAction by rememberUpdatedState(onAction)
    val inputs = remember(state, scope) {
        TabSwipeInputs(
            state = state,
            scope = scope,
            environment = TabSwipeEnvironment(
                swipe = { currentSwipe },
                canStart = { !focused },
                isRtl = { false },
                animationsEnabled = { false },
                gestureFreeRange = { 0f..Float.MAX_VALUE },
            ),
            onCommit = { id -> currentOnAction(TabSwipeAction.SwitchTo(id, announce = false)) },
        )
    }
    // As the browser does once the switched-to tab is active: here, once the neighbours move.
    LaunchedEffect(swipe.previous?.tabId, swipe.next?.tabId) {
        if (state.holding) state.awaitPageThenRelease(waitForPage = false)
    }
    val actions = rememberTabSwipeAccessibilityActions(swipe, onAction)
    var text by remember { mutableStateOf("t2.example") }
    ThirtySixTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = state.fraction * size.width }
                    .testTag(TEST_TAG_HARNESS_PAGE),
            ) {
                SideEffect { counts.page++ }
            }
            TabSwipeLane(
                state = state,
                isIncognito = false,
                modifier = Modifier.fillMaxWidth().height(LANE_HEIGHT).tabSwipe(inputs),
            ) {
                SideEffect { counts.pill++ }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TEST_TAG_HARNESS_FIELD)
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange(it.isFocused)
                        }
                        .withAccessibilityActions(actions),
                )
            }
        }
    }
}

private val LANE_HEIGHT = 56.dp
