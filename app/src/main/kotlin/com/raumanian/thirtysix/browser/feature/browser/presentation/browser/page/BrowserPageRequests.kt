@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

/**
 * The browser screen's page session: created once, told about every tab switch, and disposed with
 * the screen so no pending request is left unanswered.
 */
@Composable
fun rememberPageSession(
    activeTabId: Long?,
    isIncognito: Boolean,
    permissionGate: SitePermissionGate,
    popups: PopupTabs? = null,
): PageSession {
    val scope = rememberCoroutineScope()
    val session = remember(permissionGate, popups) { PageSession(PageRequestQueue(), scope, permissionGate, popups) }
    LaunchedEffect(activeTabId, isIncognito) { session.onTabChanged(activeTabId, isIncognito) }
    val context = LocalContext.current
    DisposableEffect(session) {
        onDispose {
            session.queue.dispose()
            popups?.releaseWaiting()
            session.fullscreen.exit()
            CaptureFiles.clear(context)
        }
    }
    return session
}

/** Renders the session's pending requests. */
@Composable
fun BrowserPageRequests(
    session: PageSession,
    permissionGate: SitePermissionGate,
    longPress: LongPressHooks? = null,
) {
    longPress?.let { LongPressEffects(it) }
    val renderers = rememberPageRequestRenderers(session, permissionGate, longPress)
    PageRequestHost(queue = session.queue, renderers = renderers)
}

@Composable
private fun rememberPageRequestRenderers(
    session: PageSession,
    permissionGate: SitePermissionGate,
    longPress: LongPressHooks?,
): PageRequestRenderers =
    remember(session, permissionGate, longPress) {
        PageRequestRenderers { request, done ->
            when (request) {
                is PageRequest.Permission -> {
                    SitePermissionDialog(request, permissionGate, done)
                    true
                }
                is PageRequest.FileChooser -> {
                    FileChooserLauncher(request, done)
                    true
                }
                is PageRequest.JsDialog -> {
                    val suppress = { session.dialogs.suppress(request.tabId, request.generation) }
                    SiteJsDialog(request, onSuppress = suppress, done = done)
                    true
                }
                is PageRequest.LeavePage -> {
                    SiteLeaveDialog(request, done)
                    true
                }
                is PageRequest.HttpAuth -> {
                    HttpAuthDialog(request, done = done)
                    true
                }
                is PageRequest.OpenExternalApp -> {
                    ExternalAppDialog(request, done)
                    true
                }
                is PageRequest.LongPress -> longPress?.let {
                    LongPressHost(request, it, done)
                    true
                } ?: false
            }
        }
    }
