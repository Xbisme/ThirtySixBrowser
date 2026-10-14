package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/**
 * Spec 022 research R2 — the user agent a tab sends in desktop mode, derived from the WebView's
 * own default so the Chrome version stays truthful.
 *
 * The platform token `(Linux; Android …; …; wv)` becomes [PLATFORM_TOKEN]; the WebView's
 * `Version/4.0` token and the ` Mobile` token are removed. The result is the shape desktop Chrome
 * on Linux sends, which is what Chrome for Android itself sends in its desktop mode.
 */
object DesktopUserAgent {
    /** The platform a desktop request claims, in the user-agent string. */
    const val PLATFORM_TOKEN: String = "(X11; Linux x86_64)"

    /** The platform a desktop request claims, in user-agent client hints (`Sec-CH-UA-Platform`). */
    const val METADATA_PLATFORM: String = "Linux"

    /** The first parenthesised group: `(Linux; Android 16; <model> Build/<id>; wv)`. */
    private val PLATFORM_GROUP = Regex("""\([^)]*\)""")

    /** The WebView marker `Version/4.0`, which desktop Chrome never sends. */
    private val WEBVIEW_VERSION = Regex("""\s*Version/\d+(\.\d+)*""")

    /** The token that marks a mobile browser. */
    private const val MOBILE_TOKEN: String = " Mobile"

    fun from(defaultUserAgent: String): String = defaultUserAgent
        .replaceFirst(PLATFORM_GROUP, PLATFORM_TOKEN)
        .replace(WEBVIEW_VERSION, "")
        .replace(MOBILE_TOKEN, "")
}
