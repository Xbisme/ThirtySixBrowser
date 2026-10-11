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
): PageSession {
    val scope = rememberCoroutineScope()
    val session = remember(permissionGate) { PageSession(PageRequestQueue(), scope, permissionGate) }
    LaunchedEffect(activeTabId, isIncognito) { session.onTabChanged(activeTabId, isIncognito) }
    val context = LocalContext.current
    DisposableEffect(session) {
        onDispose {
            session.queue.dispose()
            CaptureFiles.clear(context)
        }
    }
    return session
}

/** Spec 021 — renders the session's pending requests with every story's renderer. */
@Composable
fun BrowserPageRequests(session: PageSession, permissionGate: SitePermissionGate = hiltViewModel()) {
    PageRequestHost(queue = session.queue, renderers = rememberPageRequestRenderers(permissionGate))
}

@Composable
private fun rememberPageRequestRenderers(permissionGate: SitePermissionGate): PageRequestRenderers =
    remember(permissionGate) {
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
                else -> false
            }
        }
    }
