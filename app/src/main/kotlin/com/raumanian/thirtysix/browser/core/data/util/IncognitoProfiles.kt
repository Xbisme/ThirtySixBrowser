package com.raumanian.thirtysix.browser.core.data.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile

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

    override fun sessionProfileName(): String =
        sessionName ?: (NAME_PREFIX + System.nanoTime()).also { sessionName = it }

    override fun collectLeftovers() {
        collectLeftovers(current = sessionName)
    }

    override fun isSessionProfile(name: String): Boolean = name.startsWith(NAME_PREFIX)

    override fun existingProfileNames(): List<String> =
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
