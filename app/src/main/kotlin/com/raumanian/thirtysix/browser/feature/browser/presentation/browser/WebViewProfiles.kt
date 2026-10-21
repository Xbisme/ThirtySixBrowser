package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Gives new WebViews their profile. Call on the main thread straight after constructing one, before any other use. */
internal object WebViewProfiles {
    private const val TAG = "WebViewProfiles"

    /**
     * An incognito WebView gets the session's profile. A normal WebView, or an engine without profiles,
     * is returned untouched. The first WebView of a process also deletes the profiles earlier ones left.
     */
    fun prepare(webView: WebView, incognito: Boolean, profiles: IncognitoWebProfile): WebView {
        if (profiles.isIsolated()) {
            profiles.collectLeftovers()
            if (incognito) attach(webView, profiles.sessionProfileName())
        }
        return webView
    }

    /** A throw-away WebView handed to a page as a new window shares its opener's profile. */
    fun prepareLike(webView: WebView, opener: WebView, profiles: IncognitoWebProfile): WebView {
        if (!profiles.isIsolated() || !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return webView
        runCatching {
            val name = WebViewCompat.getProfile(opener).name
            if (profiles.isSessionProfile(name)) WebViewCompat.setProfile(webView, name)
        }.onFailure { e -> Log.w(TAG, "could not match the opener's profile", e) }
        return webView
    }

    /**
     * The jar a tab browses with. Without profiles the default jar holds the swapped-in incognito
     * cookies while incognito is open. Null when the incognito profile is gone. Main thread.
     */
    fun cookieManagerFor(
        incognito: Boolean,
        profiles: IncognitoWebProfile,
        profileCookies: (String) -> CookieManager? = ::profileCookieManager,
        defaultCookies: () -> CookieManager = CookieManager::getInstance,
    ): CookieManager? =
        if (incognito && profiles.isIsolated()) profileCookies(profiles.sessionProfileName()) else defaultCookies()

    private fun profileCookieManager(name: String): CookieManager? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return null
        return runCatching { ProfileStore.getInstance().getProfile(name)?.cookieManager }.getOrNull()
    }

    private fun attach(webView: WebView, name: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching {
            ProfileStore.getInstance().getOrCreateProfile(name)
            WebViewCompat.setProfile(webView, name)
        }.onFailure { e -> Log.w(TAG, "could not give the WebView its incognito profile", e) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WebViewProfilesEntryPoint {
    fun incognitoWebProfile(): IncognitoWebProfile
}

/** The process-wide incognito profile, for code outside Hilt injection. */
internal fun Context.incognitoWebProfile(): IncognitoWebProfile =
    EntryPointAccessors.fromApplication(applicationContext, WebViewProfilesEntryPoint::class.java).incognitoWebProfile()
