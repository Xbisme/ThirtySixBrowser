package com.raumanian.thirtysix.browser.incognito

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.local.webview.IncognitoProfiles
import com.raumanian.thirtysix.browser.protection.TestProtection
import com.raumanian.thirtysix.browser.testing.AssetPageServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ending an incognito session must not sign the person out of sites (the Spec 012 limitation found
 * by Spec 024), and nothing written in incognito may reach normal browsing.
 *
 * `storage.html` (Spec 024 fixture) stores five kinds of site data, and the server sets the
 * HttpOnly cookie `fp_persist`; `storage.html?check` reports what the page can still see.
 */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class IncognitoIsolationTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "incognito-settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Inject lateinit var incognitoTabs: IncognitoTabRepository

    @Inject lateinit var history: HistoryRepository

    private val server = AssetPageServer()
    private val protection = TestProtection(blockResolvableTracker = false)

    /** What the hosted WebView shows; a new value builds a new WebView (normal or incognito). */
    private var page by mutableStateOf<Page?>(null)
    private var started = false

    private data class Page(val url: String, val incognito: Boolean, val serial: Long = System.nanoTime())

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { incognitoTabs.closeAll() }
        composeRule.runOnUiThread { WebStorage.getInstance().deleteAllData() }
        val wiped = CountDownLatch(1)
        composeRule.runOnUiThread { CookieManager.getInstance().removeAllCookies { wiped.countDown() } }
        wiped.await(LATCH_SECONDS, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        runBlocking { incognitoTabs.closeAll() }
        server.close()
    }

    private fun show(url: String, incognito: Boolean, done: (String) -> Boolean) {
        protection.title.set("")
        if (!started) {
            started = true
            page = Page(url, incognito)
            composeRule.setContent {
                page?.let { shown -> key(shown.serial) { protection.Content(shown.url, shown.incognito) } }
            }
        } else {
            composeRule.runOnUiThread { page = Page(url, incognito) }
        }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { done(protection.title.get()) }
    }

    private fun store(host: String, incognito: Boolean) =
        show(server.url(STORAGE, host), incognito) { it == "stored" }

    private fun check(host: String): String {
        show(server.url("$STORAGE?check", host), incognito = false) { it.startsWith(CHECK) }
        return protection.title.get()
    }

    private fun normalCookie(host: String): String =
        composeRule.runOnUiThread { CookieManager.getInstance().getCookie(server.url(STORAGE, host)).orEmpty() }

    /** Cookies for [host] in every incognito profile that exists. */
    private fun incognitoProfileCookies(host: String): String = composeRule.runOnUiThread {
        IncognitoProfiles.existingProfileNames().joinToString(";") { name ->
            ProfileStore.getInstance().getProfile(name)?.cookieManager?.getCookie(server.url(STORAGE, host)).orEmpty()
        }.trim(';')
    }

    /** Removes the hosted WebView, so an ended session's profile is free to delete. */
    private fun hide() = composeRule.runOnUiThread { page = null }.also { composeRule.waitForIdle() }

    @Test
    fun endingAnIncognitoSession_keepsTheCookiesOfSitesNotOpenInATab() {
        store(LOOPBACK, incognito = false)
        hide()
        runBlocking {
            history.recordVisit(server.url(STORAGE, LOOPBACK), "storage", System.currentTimeMillis())
            val tab = incognitoTabs.createTab("about:blank")
            incognitoTabs.closeAll()
            assertTrue(tab is Result.Success)
        }

        assertTrue("signed out of a site visited before: ${normalCookie(LOOPBACK)}", PERSISTENT in normalCookie(LOOPBACK))
    }

    @Test
    fun incognitoHasItsOwnProfile_andNormalBrowsingIsUntouched() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))
        // Normal data on a site that is in no tab and not in history.
        store(LOOPBACK, incognito = false)
        runBlocking { incognitoTabs.createTab(server.url(STORAGE, LOCAL)) }

        store(LOCAL, incognito = true)
        assertTrue("the incognito page kept its own cookie", incognitoProfileCookies(LOCAL).isNotEmpty())
        assertFalse("incognito cookie reached normal browsing: ${normalCookie(LOCAL)}", PERSISTENT in normalCookie(LOCAL))
        hide()
        runBlocking { incognitoTabs.closeAll() }

        // The ended session's profile is emptied (it is deleted in a later process).
        composeRule.waitUntil(PROFILE_WIPE_TIMEOUT_MS) { incognitoProfileCookies(LOCAL).isEmpty() }
        assertEquals("incognito storage reached normal browsing", NOTHING, check(LOCAL))
        assertEquals("normal data lost", EVERYTHING, check(LOOPBACK))
    }

    private companion object {
        const val STORAGE = "fixtures024/storage.html"
        const val CHECK = "check:"
        const val NOTHING = "check:js=0,ls=0,idb=0,cache=0,sw=0"
        const val EVERYTHING = "check:js=1,ls=1,idb=1,cache=1,sw=1"
        const val PERSISTENT = "fp_persist="
        const val LOCAL = TestProtection.PAGE_HOST
        const val LOOPBACK = "127.0.0.1"
        const val LATCH_SECONDS = 5L
        const val PROFILE_WIPE_TIMEOUT_MS = 10_000L
    }
}
