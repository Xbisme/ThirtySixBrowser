package com.raumanian.thirtysix.browser.core.testing.fake

import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView

/**
 * A cookie engine whose [removeAllCookies] never calls back. [jar] answers reads by exact URL and
 * [written] records every `setCookie`.
 */
class FakeCookieManager(private val jar: Map<String, String> = emptyMap()) : CookieManager() {
    val written = mutableListOf<Pair<String, String>>()

    override fun getCookie(url: String): String? = jar[url]
    override fun setCookie(url: String, value: String) {
        written += url to value
    }
    override fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?) = setCookie(url, value)
    override fun removeAllCookies(callback: ValueCallback<Boolean>?) = Unit
    override fun setAcceptCookie(accept: Boolean) = Unit
    override fun acceptCookie(): Boolean = true
    override fun setAcceptThirdPartyCookies(webview: WebView, accept: Boolean) = Unit
    override fun acceptThirdPartyCookies(webview: WebView): Boolean = false
    override fun removeSessionCookie() = Unit
    override fun removeSessionCookies(callback: ValueCallback<Boolean>?) = Unit
    override fun removeAllCookie() = Unit
    override fun hasCookies(): Boolean = jar.isNotEmpty()
    override fun removeExpiredCookie() = Unit
    override fun flush() = Unit
}
