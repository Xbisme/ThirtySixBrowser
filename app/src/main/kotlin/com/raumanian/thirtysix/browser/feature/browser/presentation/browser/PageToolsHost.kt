package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageCallbacks
/**
 * Spec 022 — what each hub page tool does on the browser screen (FR-001). The hub has already
 * closed when this runs (FR-004).
 */
@Composable
internal fun rememberPageToolHandler(
    viewModel: BrowserViewModel,
    chrome: ChromeScrollState,
    actions: PageToolActions,
): (PageTool) -> Unit {
    val current = rememberUpdatedState(actions)
    return remember(viewModel, chrome) {
        { tool ->
            when (tool) {
                PageTool.Find -> {
                    // FR-005 — the find bar replaces a top bar that is showing, not one slid away.
                    chrome.show()
                    viewModel.onFindOpened()
                }
                PageTool.Share -> current.value.share()
                PageTool.DesktopSite -> current.value.toggleDesktopSite()
                PageTool.TextSize -> current.value.openTextSize()
                PageTool.Print -> current.value.print()
            }
        }
    }
}

/**
 * Spec 022 US3 — the system share sheet with the page's address, and its title as the subject
 * when it has one (FR-023). Incognito is not consulted: sharing is the person's explicit action,
 * and nothing about it is recorded (FR-024).
 */
@Composable
internal fun rememberShareAction(viewModel: BrowserViewModel): () -> Unit {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_page_title)
    return remember(viewModel, context, chooserTitle) {
        {
            val content = ShareContent.of(viewModel.uiState.value.currentUrl, viewModel.activeTab.value?.title)
            val send = Intent(Intent.ACTION_SEND)
                .setType(AppConstants.MIME_TEXT_PLAIN)
                .putExtra(Intent.EXTRA_TEXT, content.text)
            content.subject?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
            try {
                context.startActivity(Intent.createChooser(send, chooserTitle))
            } catch (_: ActivityNotFoundException) {
                // No app can receive text: the system has nothing to offer, and neither do we.
            }
        }
    }
}

/**
 * Spec 022 US1 — the find bar's actions. Typing updates the ViewModel and searches the page with
 * the text as the ViewModel stored it (cut at the length limit); Close clears the highlights
 * before closing (I-5). A navigation or tab switch closes the bar from the ViewModel instead:
 * the page whose matches were highlighted is gone by then.
 */
@Composable
internal fun rememberFindCallbacks(
    viewModel: BrowserViewModel,
    webViewActions: WebViewActionsHandle,
): FindInPageCallbacks =
    remember(viewModel, webViewActions) {
        FindInPageCallbacks(
            onQueryChange = { text ->
                viewModel.onFindQueryChanged(text)
                val query = viewModel.findInPage.value.query
                if (query.isEmpty()) webViewActions.clearMatches() else webViewActions.findAll(query)
            },
            onPrevious = { webViewActions.findNext(false) },
            onNext = { webViewActions.findNext(true) },
            onClose = {
                webViewActions.clearMatches()
                viewModel.onFindClosed()
            },
        )
    }

/**
 * Spec 019 R7 / Spec 022 FR-005 — page scrolling slides the bars away, except while the find bar
 * is open: it stays put, so the page must not move the chrome underneath it.
 */
@Composable
internal fun rememberScrollDeltaHandler(chrome: ChromeScrollState, isFinding: () -> Boolean): (Float) -> Unit {
    val finding = rememberUpdatedState(isFinding)
    return remember(chrome) { { delta -> if (!finding.value()) chrome.onScrollDelta(delta) } }
}

/** Spec 022 FR-005 — the find bar takes the top bar's place while open; Home shows neither. */
@Composable
internal fun FindOrTopBar(
    findOpen: Boolean,
    showTopBar: Boolean,
    findBar: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
) {
    when {
        findOpen -> findBar()
        showTopBar -> topBar()
    }
}

/** Spec 022 FR-005 / I-7 — composes [content], the bottom bar, only while the find bar is closed. */
@Composable
internal fun UnlessFinding(findOpen: Boolean, content: @Composable () -> Unit) {
    if (!findOpen) content()
}

/** Spec 022 FR-005 — while finding the bars stay put, so the page is shortened by the find bar's height. */
internal fun findBarPagePadding(findOpen: Boolean, findBarHeight: Dp): Dp = if (findOpen) findBarHeight else 0.dp
