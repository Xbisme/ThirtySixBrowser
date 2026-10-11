@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.AppConstants
import com.raumanian.thirtysix.browser.domain.model.LongPressAction
import kotlinx.coroutines.launch

/** Shows the menu and carries out the chosen action (FR-018 – FR-024). */
@Composable
fun LongPressHost(request: PageRequest.LongPress, hooks: LongPressHooks, done: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.long_press_share_link)
    val menu = request.menu
    LongPressSheet(
        menu = menu,
        onAction = { action ->
            request.choose(action)
            done()
            val link = menu.linkUrl.orEmpty()
            val image = menu.imageUrl.orEmpty()
            when (action) {
                LongPressAction.OpenInNewTab -> hooks.actions.openInBackground(link, hooks.isIncognito())
                LongPressAction.OpenImageInNewTab -> hooks.actions.openInBackground(image, hooks.isIncognito())
                LongPressAction.OpenInIncognitoTab -> hooks.actions.openInBackground(link, incognito = true)
                LongPressAction.CopyLink -> hooks.actions.copy(link)
                LongPressAction.CopyImageAddress -> hooks.actions.copy(image)
                LongPressAction.ShareLink, LongPressAction.ShareImageLink -> {
                    val text = if (action == LongPressAction.ShareLink) link else image
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(AppConstants.MIME_TEXT_PLAIN)
                        .putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        // No app can share text: nothing to do.
                    }
                }
                LongPressAction.DownloadLink -> hooks.onDownload(link)
                LongPressAction.DownloadImage -> hooks.onDownload(image)
            }
        },
        onDismiss = {
            request.cancel()
            done()
        },
    )
}

/** The notices after a long-press action: opened (with Switch), copied, tab limit. */
@Composable
fun LongPressEffects(hooks: LongPressHooks) {
    val opened = stringResource(R.string.long_press_opened_background)
    val openedIncognito = stringResource(R.string.long_press_opened_incognito_background)
    val switch = stringResource(R.string.long_press_switch)
    val copied = stringResource(R.string.long_press_copied)
    val tabLimit = stringResource(R.string.browser_max_tabs_reached)
    LaunchedEffect(hooks.actions) {
        hooks.actions.events.collect { event ->
            when (event) {
                is LongPressEvent.OpenedInBackground -> launch {
                    val result = hooks.snackbarHostState.showSnackbar(
                        message = if (event.incognito) openedIncognito else opened,
                        actionLabel = switch,
                    )
                    if (result == SnackbarResult.ActionPerformed) hooks.actions.switchTo(event.tabId)
                }
                LongPressEvent.Copied -> launch { hooks.snackbarHostState.showSnackbar(copied) }
                LongPressEvent.TabLimitReached -> launch { hooks.snackbarHostState.showSnackbar(tabLimit) }
            }
        }
    }
}
