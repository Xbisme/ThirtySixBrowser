@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Spec 021 — the browser screen's page session: created once, told about every tab switch, and
 * disposed with the screen so nothing pending is left unanswered (INV-3, spec edge case "leaving
 * the browser screen with a request pending").
 */
@Composable
fun rememberPageSession(
    activeTabId: Long?,
    isIncognito: Boolean,
    permissionGate: SitePermissionGate = hiltViewModel(),
    popups: PopupTabs? = null,
): PageSession {
    val scope = rememberCoroutineScope()
    val session = remember(permissionGate, popups) { PageSession(PageRequestQueue(), scope, permissionGate, popups) }
    LaunchedEffect(activeTabId, isIncognito) { session.onTabChanged(activeTabId, isIncognito) }
    val context = LocalContext.current
    DisposableEffect(session) {
        onDispose {
            session.queue.dispose()
            session.fullscreen.exit()
            CaptureFiles.clear(context)
        }
    }
    return session
}

/** Spec 021 — renders the session's pending requests with every story's renderer. */
@Composable
fun BrowserPageRequests(
    session: PageSession,
    permissionGate: SitePermissionGate = hiltViewModel(),
    longPress: LongPressHooks? = null,
) {
    longPress?.let { LongPressEffects(it) }
    PageRequestHost(queue = session.queue, renderers = rememberPageRequestRenderers(permissionGate, longPress))
}

@Composable
private fun rememberPageRequestRenderers(
    permissionGate: SitePermissionGate,
    longPress: LongPressHooks?,
): PageRequestRenderers =
    remember(permissionGate, longPress) {
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
                is PageRequest.LongPress -> longPress?.let {
                    LongPressHost(request, it, done)
                    true
                } ?: false
                else -> false
            }
        }
    }
