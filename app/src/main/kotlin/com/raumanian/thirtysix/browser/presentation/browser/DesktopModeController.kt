package com.raumanian.thirtysix.browser.presentation.browser

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Spec 022 research R2 — switches one WebView between the mobile and desktop page modes.
 *
 * Desktop mode sends [DesktopUserAgent]'s user agent, lays a page without a viewport tag out at
 * desktop width and zoomed out to fit, and, where the engine supports it, reports
 * `Sec-CH-UA-Mobile: ?0` with a desktop platform through user-agent client hints. Mobile mode
 * restores exactly what the WebView started with. Created with its WebView, before the first load.
 */
internal class DesktopModeController(private val webView: WebView) {
    private val mobileMetadata: UserAgentMetadata? =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.getUserAgentMetadata(webView.settings)
        } else {
            null
        }

    private fun desktopUserAgent(): String = DesktopUserAgent.from(WebSettings.getDefaultUserAgent(webView.context))

    fun apply(desktop: Boolean) {
        val settings = webView.settings
        // null restores the WebView's own default user agent.
        settings.userAgentString = if (desktop) desktopUserAgent() else null
        settings.useWideViewPort = desktop
        settings.loadWithOverviewMode = desktop
        val original = mobileMetadata ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(
                settings,
                if (desktop) {
                    UserAgentMetadata.Builder(original)
                        .setMobile(false)
                        .setPlatform(DesktopUserAgent.METADATA_PLATFORM)
                        .setModel("")
                        .build()
                } else {
                    original
                },
            )
        }
    }
}
