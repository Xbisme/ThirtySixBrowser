package com.raumanian.thirtysix.browser.data.local.webview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * The incognito session's WebView profile, where the engine has profiles (`MULTI_PROFILE`).
 *
 * Spec 012 kept incognito apart by snapshotting the shared cookie jar, wiping it at the end of
 * the session and writing the snapshot back. Only the open normal tabs' sites were snapshotted,
 * so ending an incognito session signed the person out of every other site, and site storage
 * written in incognito stayed in normal browsing. With a profile of its own, incognito has its own
 * cookies, storage, cache and service workers; normal browsing's are never touched, and ending
 * the session deletes the profile.
 */
interface IncognitoWebProfile {
    /** Whether incognito WebViews get a profile of their own on this engine. */
    fun isIsolated(): Boolean

    /** The first incognito tab opens: later incognito WebViews use a fresh profile. */
    fun beginSession()

    /** The last incognito tab closed: everything the session's profile holds is wiped. */
    fun endSession()
}

/**
 * Process-wide, because `BrowserWebView` gives every new WebView its profile before anything
 * else touches it, and that composable has no injection. Hilt binds this object for the incognito
 * repository. Profile calls run on the main thread, as the platform requires.
 *
 * A session's profile has a unique name, so the next session never sees its data, and a WebView
 * still being torn down never shares a profile with it. The engine keeps a profile "in use" for
 * the rest of the process once a WebView has used it, so it cannot be deleted then: at the end of
 * the session everything inside it is wiped (cookies, storage, cache, service workers), and the
 * emptied profile itself is deleted in a later process, when its first incognito WebView is made.
 */
object IncognitoProfiles : IncognitoWebProfile {
    private const val TAG = "IncognitoProfiles"
    private const val NAME_PREFIX = "thirtysix-incognito-"

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isolated: Boolean by lazy {
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
    }

    // Read and written on the main thread only; @Volatile for the begin/end calls from IO.
    @Volatile
    private var sessionName: String? = null
    private var leftoversCollected = false

    override fun isIsolated(): Boolean = isolated

    override fun beginSession() {
        sessionName = NAME_PREFIX + System.nanoTime()
    }

    override fun endSession() {
        val ended = sessionName ?: return
        sessionName = null
        mainHandler.post { wipe(ended) }
    }

    /**
     * Gives a new WebView the current incognito profile. Call on the main thread straight after
     * constructing it, before any other use. A normal WebView, or an engine without profiles,
     * is returned untouched (Spec 012's snapshot path then keeps incognito apart). The first
     * WebView of a process, normal or not, also deletes the profiles earlier processes left.
     */
    fun prepare(webView: WebView, incognito: Boolean): WebView {
        if (isolated) {
            collectLeftovers(current = sessionName)
            if (incognito) attachSessionProfile(webView)
        }
        return webView
    }

    private fun attachSessionProfile(webView: WebView) {
        val name = sessionName ?: (NAME_PREFIX + System.nanoTime()).also { sessionName = it }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching {
                ProfileStore.getInstance().getOrCreateProfile(name)
                WebViewCompat.setProfile(webView, name)
            }.onFailure { e -> Log.w(TAG, "could not give the WebView its incognito profile", e) }
        }
    }

    /**
     * A throw-away WebView handed to a page as a new window shares its opener's profile, so an
     * incognito page never reaches normal browsing's profile through it. Main thread, before use.
     */
    fun prepareLike(webView: WebView, opener: WebView): WebView {
        if (!isolated || !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return webView
        runCatching {
            val name = WebViewCompat.getProfile(opener).name
            if (name.startsWith(NAME_PREFIX)) WebViewCompat.setProfile(webView, name)
        }.onFailure { e -> Log.w(TAG, "could not match the opener's profile", e) }
        return webView
    }

    /** Names of incognito profiles that still exist on disk; for tests and diagnostics. Main thread. */
    fun existingProfileNames(): List<String> =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { ProfileStore.getInstance().allProfileNames.filter { it.startsWith(NAME_PREFIX) } }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

    /** Profiles of earlier processes are not in use in this one, so they can be deleted now. */
    @Suppress("TooGenericExceptionCaught") // A profile the engine still reports in use stays for next time.
    private fun collectLeftovers(current: String?) {
        if (leftoversCollected) return
        leftoversCollected = true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        existingProfileNames().filterNot { it == current }.forEach { name ->
            try {
                ProfileStore.getInstance().deleteProfile(name)
            } catch (e: RuntimeException) {
                Log.w(TAG, "could not delete leftover profile $name", e)
            }
        }
    }

    /** Empties an ended session's profile. Main thread. */
    private fun wipe(name: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val profile = runCatching { ProfileStore.getInstance().getProfile(name) }.getOrNull() ?: return
        runCatching {
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                WebStorageCompat.deleteBrowsingData(profile.webStorage) {}
            } else {
                profile.webStorage.deleteAllData()
            }
        }.onFailure { e -> Log.w(TAG, "could not wipe ended profile $name", e) }
    }
}
