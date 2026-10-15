@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shows the page's oldest pending request, one at a time. A variant with no renderer is answered
 * as cancelled at once, so a page is never left waiting.
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
 * The per-kind renderers. [render] draws [request] and returns `true`, or `false` when nothing
 * renders that variant. Each renderer must call `done` once the request is answered.
 */
fun interface PageRequestRenderers {
    @Composable
    fun render(request: PageRequest, done: () -> Unit): Boolean
}
