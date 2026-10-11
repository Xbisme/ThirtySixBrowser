package com.raumanian.thirtysix.browser.presentation.browser

import java.net.URI

/** Spec 022 — the five page tools the hub offers (contracts C12). */
enum class PageTool { Find, Share, DesktopSite, TextSize, Print }

/** Spec 022 — the page tools other than Find, each supplied by the browser screen. */
internal data class PageToolActions(
    val share: () -> Unit = {},
    val toggleDesktopSite: () -> Unit = {},
    val openTextSize: () -> Unit = {},
    val print: () -> Unit = {},
)

/**
 * Spec 022 research R9 — which page tools the hub offers for the page on screen.
 *
 * Text size is an app-wide setting, so it is always offered. The other four need a web page:
 * none of them is offered on Home or the incognito start page (FR-002). A failed load still has
 * a meaningful address, so Share and Desktop site stay; there is nothing to search or print.
 * Print waits for the load to finish (FR-033), so a half-rendered page is never printed.
 */
object PageToolsAvailability {
    private val home: Set<PageTool> = setOf(PageTool.TextSize)
    private val loading: Set<PageTool> = setOf(PageTool.Find, PageTool.Share, PageTool.DesktopSite, PageTool.TextSize)
    private val loaded: Set<PageTool> = loading + PageTool.Print
    private val failed: Set<PageTool> = setOf(PageTool.Share, PageTool.DesktopSite, PageTool.TextSize)

    fun of(isHomeContent: Boolean, loadingState: LoadingState): Set<PageTool> = when {
        isHomeContent -> home
        loadingState is LoadingState.Failed -> failed
        loadingState == LoadingState.Loaded -> loaded
        else -> loading
    }
}

/**
 * Spec 022 FR-023 — what Share hands to the system share sheet: the address as the text, and
 * the title as the subject only when there is one.
 */
data class ShareContent(val text: String, val subject: String?) {
    companion object {
        fun of(url: String, title: String?): ShareContent =
            ShareContent(text = url, subject = title?.takeIf { it.isNotBlank() })
    }
}

/**
 * Spec 022 FR-033 — the print job's name, which the system also suggests as the PDF file name:
 * the page title, else its hostname, else the address itself.
 */
object PrintJobName {
    fun of(title: String?, url: String): String {
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host.isNullOrBlank()) url else host
    }
}
