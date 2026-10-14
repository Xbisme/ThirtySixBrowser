package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import android.util.Log
import android.webkit.WebView
import androidx.webkit.CustomHeader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
/**
 * Global Privacy Control on every request, set once per WebView profile where profile headers
 * exist, so page resources and service workers get it too. Ignores per-site exceptions.
 */
internal object GpcHeaderInstaller {
    private val outcomes = mutableMapOf<String, Boolean>()

    /** Every profile seen so far carries the header, so app-issued loads need not add it. */
    val appWide: Boolean
        get() = outcomes.isNotEmpty() && outcomes.values.all { it }

    fun install(webView: WebView): Boolean {
        val profileName = profileNameOf(webView)
        if (profileName !in outcomes) {
            outcomes[profileName] = ORIGIN_RULE_SETS.any { rules -> tryAdd(webView, rules) }
        }
        return appWide
    }

    private fun profileNameOf(webView: WebView): String =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).name }.getOrDefault(NO_PROFILE)
        } else {
            NO_PROFILE
        }

    /** Extra headers for an app-issued `loadUrl` when the header is not app-wide. */
    fun headersForLoad(): Map<String, String> =
        if (appWide) emptyMap() else mapOf(UrlConstants.GPC_HEADER_NAME to UrlConstants.GPC_HEADER_VALUE)

    @Suppress("TooGenericExceptionCaught") // An engine that rejects the rule falls back, never crashes.
    private fun tryAdd(webView: WebView, rules: Set<String>): Boolean = try {
        // Older WebViews lack profile headers: GPC then rides on the app's own loads only.
        val profile = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.getProfile(webView)
        } else {
            null
        }
        if (profile != null && WebViewFeature.isFeatureSupported(WebViewFeature.CUSTOM_REQUEST_HEADERS)) {
            profile.addCustomHeader(CustomHeader(UrlConstants.GPC_HEADER_NAME, UrlConstants.GPC_HEADER_VALUE, rules))
            true
        } else {
            false
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "profile header rejected for rules $rules", e)
        false
    }

    private const val TAG = "GpcHeader"
    private const val NO_PROFILE = ""

    /** `*` first; if the engine rejects it, every http and https origin. */
    private val ORIGIN_RULE_SETS: List<Set<String>> = listOf(setOf("*"), setOf("https://*", "http://*"))
}
