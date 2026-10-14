package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/**
 * Derived from the WebView's own user agent so the Chrome version stays truthful: the platform
 * token becomes Linux and `Version/4.0` and ` Mobile` go, as Chrome for Android's desktop mode does.
 */
object DesktopUserAgent {
    const val PLATFORM_TOKEN: String = "(X11; Linux x86_64)"

    /** For user-agent client hints (`Sec-CH-UA-Platform`). */
    const val METADATA_PLATFORM: String = "Linux"

    private val PLATFORM_GROUP = Regex("""\([^)]*\)""")

    /** Desktop Chrome never sends it. */
    private val WEBVIEW_VERSION = Regex("""\s*Version/\d+(\.\d+)*""")

    private const val MOBILE_TOKEN: String = " Mobile"

    fun from(defaultUserAgent: String): String = defaultUserAgent
        .replaceFirst(PLATFORM_GROUP, PLATFORM_TOKEN)
        .replace(WEBVIEW_VERSION, "")
        .replace(MOBILE_TOKEN, "")
}
