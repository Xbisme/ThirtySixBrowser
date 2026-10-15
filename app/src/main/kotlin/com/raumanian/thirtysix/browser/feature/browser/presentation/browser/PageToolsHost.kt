package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageCallbacks
import kotlinx.coroutines.flow.StateFlow
/** What each hub page tool does; the hub has already closed when this runs. */
@Composable
internal fun rememberPageToolHandler(
    onFindAction: (FindInPageAction) -> Unit,
    chrome: ChromeScrollState,
    actions: PageToolActions,
): (PageTool) -> Unit {
    val current = rememberUpdatedState(actions)
    return remember(onFindAction, chrome) {
        { tool ->
            when (tool) {
                PageTool.Find -> {
                    // The find bar replaces a showing top bar, not one slid away.
                    chrome.show()
                    onFindAction(FindInPageAction.Opened)
                }
                PageTool.Share -> current.value.share()
                PageTool.DesktopSite -> current.value.toggleDesktopSite()
                PageTool.TextSize -> current.value.openTextSize()
                PageTool.Print -> current.value.print()
            }
        }
    }
}

/** Incognito is not consulted: sharing is the person's explicit action, and nothing about it is recorded. */
@Composable
internal fun rememberShareAction(currentUrl: () -> String, title: () -> String?): () -> Unit {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_page_title)
    val page by rememberUpdatedState(currentUrl to title)
    return remember(context, chooserTitle) {
        {
            val content = ShareContent.of(page.first(), page.second())
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
 * Searches with the text as the view model stored it (cut at the limit). A navigation or tab
 * switch closes the bar from the view model instead, since the highlighted page is gone.
 */
@Composable
internal fun rememberFindCallbacks(
    onFindAction: (FindInPageAction) -> Unit,
    findInPage: StateFlow<FindInPageState>,
    webViewActions: WebViewActionsHandle,
): FindInPageCallbacks =
    remember(onFindAction, findInPage, webViewActions) {
        FindInPageCallbacks(
            onQueryChange = { text ->
                onFindAction(FindInPageAction.QueryChanged(text))
                val query = findInPage.value.query
                if (query.isEmpty()) webViewActions.clearMatches() else webViewActions.findAll(query)
            },
            onPrevious = { webViewActions.findNext(false) },
            onNext = { webViewActions.findNext(true) },
            onClose = {
                webViewActions.clearMatches()
                onFindAction(FindInPageAction.Closed)
            },
        )
    }

/** Page scrolling slides the bars away, except while the find bar is open above them. */
@Composable
internal fun rememberScrollDeltaHandler(chrome: ChromeScrollState, isFinding: () -> Boolean): (Float) -> Unit {
    val finding = rememberUpdatedState(isFinding)
    return remember(chrome) { { delta -> if (!finding.value()) chrome.onScrollDelta(delta) } }
}

/** The find bar takes the top bar's place while open; Home shows neither. */
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

@Composable
internal fun UnlessFinding(findOpen: Boolean, content: @Composable () -> Unit) {
    if (!findOpen) content()
}

internal fun findBarPagePadding(findOpen: Boolean, findBarHeight: Dp): Dp = if (findOpen) findBarHeight else 0.dp
