package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Desktop mode sends a desktop user agent and client hints and lays out viewport-less pages at
 * desktop width; mobile mode restores the WebView's defaults. Created before the first load.
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
