package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import java.net.URI

enum class PageTool { Find, Share, DesktopSite, TextSize, Print }

internal data class PageToolActions(
    val share: () -> Unit = {},
    val toggleDesktopSite: () -> Unit = {},
    val openTextSize: () -> Unit = {},
    val print: () -> Unit = {},
)

/**
 * Text size is app-wide, so it is always offered; the rest need a web page, never Home. A failed
 * load keeps Share and Desktop site; Print waits for the load to finish.
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

data class ShareContent(val text: String, val subject: String?) {
    companion object {
        fun of(url: String, title: String?): ShareContent =
            ShareContent(text = url, subject = title?.takeIf { it.isNotBlank() })
    }
}

/** Also the suggested PDF file name: the page title, else its hostname, else the address. */
object PrintJobName {
    fun of(title: String?, url: String): String {
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val host = runCatching { URI(url).host }.getOrNull()
        return if (host.isNullOrBlank()) url else host
    }
}
