package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants

/**
 * Hands an address to another app, only to activities that accept links from browsers and never to
 * a named component. Returns an http(s) address to load in the tab instead: an `intent:` link's
 * fallback when no app takes it, or its own address when it carries a web address or names this app.
 */
object ExternalAppLauncher {
    private const val TAG = "ExternalAppLauncher"
    private const val INTENT_SCHEME = "intent:"
    private const val FALLBACK_URL_EXTRA = "browser_fallback_url"

    fun launch(context: Context, url: String): String? {
        val intent = runCatching { safeIntent(url) }.getOrElse { return failed(it) }
        val inTab = webAddress(intent.dataString)
        return if (inTab != null || intent.`package` == context.packageName) {
            inTab ?: webFallback(intent)
        } else {
            runCatching { context.startActivity(intent) }.fold(
                onSuccess = { null },
                onFailure = { if (it is ActivityNotFoundException) webFallback(intent) else failed(it) },
            )
        }
    }

    private fun safeIntent(url: String): Intent {
        val parsed = if (url.startsWith(INTENT_SCHEME, ignoreCase = true)) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, url.toUri())
        }
        return parsed.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
            // Replaces any flags the link set, such as URI permission grants.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun webFallback(intent: Intent): String? = webAddress(intent.getStringExtra(FALLBACK_URL_EXTRA))

    private fun webAddress(address: String?): String? {
        val scheme = address?.toUri()?.scheme?.lowercase()
        return address.takeIf { scheme == UrlConstants.SCHEME_HTTP || scheme == UrlConstants.SCHEME_HTTPS }
    }

    private fun failed(cause: Throwable): String? {
        Log.w(TAG, "External app hand-off failed", cause)
        return null
    }
}
