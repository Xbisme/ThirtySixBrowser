package com.raumanian.thirtysix.browser.fire

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.CookieManagerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import com.raumanian.thirtysix.browser.domain.fire.FireCoordinator
import com.raumanian.thirtysix.browser.presentation.browser.WebViewProfiles
import com.raumanian.thirtysix.browser.protection.TestProtection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
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
 * Spec 024 T035 / T040 — Fire against real web-engine data (SC-002, FR-003, FR-010, FR-011).
 *
 * `storage.html` stores five kinds of site data (and the server sets the HttpOnly cookie
 * `fp_persist`) on two sites, `localhost` and `127.0.0.1`. Fire runs through the real coordinator,
 * use case and `AndroidWebDataCleaner`; the result is read back with `storage.html?check` and the
 * cookie store. The WebView is hosted directly (TestProtection, no scripts), as in Spec 023.
 */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class FireDataTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "spec024-settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Inject lateinit var fire: FireCoordinator

    @Inject lateinit var fireproof: FireproofSiteRepository

    @Inject lateinit var history: HistoryRepository

    @Inject lateinit var tabs: TabRepository

    @Inject lateinit var incognitoTabs: IncognitoTabRepository

    private val server = AssetPageServer()
    private val protection = TestProtection(blockResolvableTracker = false)
    private var started = false

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { fireproof.clearAll() }
    }

    @After
    fun tearDown() {
        runBlocking { fireproof.clearAll() }
        server.close()
    }

    private fun open(url: String, done: (String) -> Boolean) {
        protection.title.set("")
        if (!started) {
            composeRule.setContent { protection.Content(url) }
            started = true
        } else {
            composeRule.runOnUiThread { protection.actions.loadUrl(url) }
        }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { done(protection.title.get()) }
    }

    private fun store(host: String) = open(server.url(STORAGE, host)) { it == "stored" }

    /** The `check:` title of `storage.html` for [host]. */
    private fun check(host: String): String {
        open(server.url("$STORAGE?check", host)) { it.startsWith(CHECK) }
        return protection.title.get()
    }

    private fun cookieInfo(host: String): List<String> = composeRule.runOnUiThread {
        val url = server.url(STORAGE, host)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            CookieManagerCompat.getCookieInfo(CookieManager.getInstance(), url)
        } else {
            CookieManager.getInstance().getCookie(url)?.split("; ").orEmpty()
        }
    }

    private fun runFire() {
        composeRule.runOnUiThread { fire.fire() }
        composeRule.waitUntil(FIRE_TIMEOUT_MS) { fire.state.value is FireState.Finished }
        composeRule.runOnUiThread { fire.acknowledge() }
    }

    /**
     * Every kind is gone on an engine with complete site-data removal. On older engines (the CI
     * emulator's WebView 74) the fallback leaves Cache Storage and service workers (Spec 016 A17),
     * so only the other kinds are checked there.
     */
    private fun assertCleared(host: String) {
        val found = check(host)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            assertEquals(NOTHING, found)
        } else {
            assertTrue("$host kept data: $found", found.startsWith(FALLBACK_CLEARED))
        }
    }

    @Test
    fun withoutFireproofSitesEverySiteIsCleared() {
        store(LOCAL)
        store(LOOPBACK)
        runBlocking { history.recordVisit(server.url(STORAGE, LOCAL), "storage", System.currentTimeMillis()) }

        runFire()

        assertCleared(LOCAL)
        assertCleared(LOOPBACK)
        assertTrue(cookieInfo(LOCAL).none { it.startsWith(PERSISTENT) })
        assertTrue("history not cleared", runBlocking { history.distinctHosts() }.isEmpty())
        assertEquals("exactly one tab after Fire", 1, runBlocking { tabs.observeTabs().first() }.size)
    }

    @Test
    fun aFireproofSiteKeepsItsDataAndCookieAttributes() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
        store(LOCAL)
        store(LOOPBACK)
        runBlocking { fireproof.add(LOCAL) }

        runFire()

        assertEquals(EVERYTHING, check(LOCAL))
        val kept = cookieInfo(LOCAL).firstOrNull { it.startsWith(PERSISTENT) }.orEmpty()
        assertTrue("fp_persist lost: $kept", kept.isNotEmpty())
        if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            assertTrue("HttpOnly lost: $kept", kept.contains("httponly", ignoreCase = true))
            assertTrue("expiry lost: $kept", kept.contains("expires", ignoreCase = true))
        }
        assertEquals(NOTHING, check(LOOPBACK))
    }

    @Test
    fun aCookieSetDuringIncognitoIsNotKeptForAFireproofSite() {
        store(LOCAL)
        runBlocking {
            fireproof.add(LOCAL)
            incognitoTabs.createTab("about:blank")
        }
        // Written the way an incognito page writes it: into the incognito session's own profile
        // where the engine has profiles, else into the shared jar (Spec 012's snapshot path).
        composeRule.runOnUiThread {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val incognitoPage =
                WebViewProfiles.prepare(WebView(context), incognito = true, profiles = IncognitoProfiles)
            val jar = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                WebViewCompat.getProfile(incognitoPage).cookieManager
            } else {
                CookieManager.getInstance()
            }
            jar.setCookie(server.url(STORAGE, LOCAL), "$INCOGNITO=1; Max-Age=31536000; Path=/")
            incognitoPage.destroy()
        }

        runFire()

        val cookies = cookieInfo(LOCAL)
        assertFalse("incognito cookie kept: $cookies", cookies.any { it.startsWith(INCOGNITO) })
        assertTrue("fireproof cookie lost: $cookies", cookies.any { it.startsWith(PERSISTENT) })
    }

    private companion object {
        const val STORAGE = "fixtures024/storage.html"
        const val CHECK = "check:"
        const val NOTHING = "check:js=0,ls=0,idb=0,cache=0,sw=0"
        const val FALLBACK_CLEARED = "check:js=0,ls=0,idb=0,"
        const val EVERYTHING = "check:js=1,ls=1,idb=1,cache=1,sw=1"
        const val PERSISTENT = "fp_persist="
        const val INCOGNITO = "fp_incognito"
        const val LOCAL = TestProtection.PAGE_HOST
        const val LOOPBACK = "127.0.0.1"
        const val FIRE_TIMEOUT_MS = 30_000L
    }
}
