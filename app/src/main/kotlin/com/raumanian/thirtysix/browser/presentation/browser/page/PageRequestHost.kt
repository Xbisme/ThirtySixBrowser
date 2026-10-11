@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Spec 021 research R1 — shows the page's oldest pending request, one at a time (FR-035).
 *
 * Each variant is rendered by the user story that owns it. A variant with no renderer yet is
 * answered as cancelled straight away, so a page is never left waiting on the browser.
 */
@Composable
fun PageRequestHost(queue: PageRequestQueue, renderers: PageRequestRenderers) {
    val head by queue.head.collectAsStateWithLifecycle()
    val request = head ?: return
    val done: () -> Unit = { queue.complete(request) }
    val rendered = renderers.render(request, done)
    if (!rendered) {
        LaunchedEffect(request) {
            request.cancel()
            done()
        }
    }
}

/**
 * Spec 021 — the per-story renderers. [render] draws [request] and returns `true`, or returns
 * `false` when nothing renders that variant. Each renderer must call `done` once the request is
 * answered.
 */
fun interface PageRequestRenderers {
    @Composable
    fun render(request: PageRequest, done: () -> Unit): Boolean
}
