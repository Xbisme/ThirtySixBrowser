package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import com.raumanian.thirtysix.browser.core.testing.SelfSignedServer
import com.raumanian.thirtysix.browser.feature.browser.fixture.BrowserScreenHolders
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_BROWSER_ERROR_STATE
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_BROWSER_RELOAD_STOP
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_PAGE_LOAD_PROGRESS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.net.ServerSocket
import java.net.URLEncoder
import javax.inject.Singleton
import kotlin.concurrent.thread
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The error UI replaces the WebView, both for a state seeded to Failed and for a real load that
 * fails, including on a redirect to a bad certificate, and stays there once the engine finishes
 * its own error page. Retry shows the loading line before the server answers.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenOfflineErrorTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_rendersWhenInitialStateIsFailed() {
        show(
            BrowserScreenHolders(SAMPLE_PAGE_URL).apply {
                // Seeded before setContent: the WebView never loads, so nothing can flip the state.
                page.onAction(PageAction.LoadStarted(SAMPLE_PAGE_URL))
                page.onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
            },
        )
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = ERROR_STATE_TIMEOUT_MS,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_staysAfterARealLoadFails() {
        // Nothing listens on a port just released, so the load is refused at once, offline too.
        val closedPort = ServerSocket(0).use { it.localPort }
        show(BrowserScreenHolders("http://127.0.0.1:$closedPort/"))
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = REAL_LOAD_TIMEOUT_MS,
        )
        composeRule.waitForIdle()
        Thread.sleep(ENGINE_ERROR_PAGE_SETTLE_MS)
        composeRule.onNodeWithTag(TEST_TAG_BROWSER_ERROR_STATE).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_showsForARedirectToABadCertificate() {
        AssetPageServer().use { server ->
            SelfSignedServer().use { https ->
                val target = URLEncoder.encode(https.url, Charsets.UTF_8.name())
                show(BrowserScreenHolders(server.url("redirect?to=$target")))
                composeRule.waitUntilExactlyOneExists(
                    matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
                    timeoutMillis = REAL_LOAD_TIMEOUT_MS,
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun retry_showsTheIndicatorBeforeAStalledServerAnswers() {
        val port = ServerSocket(0).use { it.localPort }
        show(BrowserScreenHolders("http://127.0.0.1:$port/"))
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = REAL_LOAD_TIMEOUT_MS,
        )
        composeRule.waitForIdle()
        Thread.sleep(ENGINE_ERROR_PAGE_SETTLE_MS)

        ServerSocket(port).use { stalled ->
            thread(isDaemon = true) { runCatching { stalled.accept().use { Thread.sleep(STALL_MS) } } }
            composeRule.onNodeWithTag(TEST_TAG_BROWSER_RELOAD_STOP).performClick()
            composeRule.waitUntilExactlyOneExists(
                matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
                timeoutMillis = RETRY_INDICATOR_TIMEOUT_MS,
            )
        }
    }

    private fun show(holders: BrowserScreenHolders) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { holders.Route() }
        }
    }

    private companion object {
        // Pure recomposition with the state pre-seeded; no network involved.
        const val ERROR_STATE_TIMEOUT_MS: Long = 5_000L
        const val REAL_LOAD_TIMEOUT_MS: Long = 10_000L

        // Time for the engine's own error page to report progress and finish.
        const val ENGINE_ERROR_PAGE_SETTLE_MS: Long = 2_000L

        const val STALL_MS: Long = 3_000L
        const val RETRY_INDICATOR_TIMEOUT_MS: Long = 1_000L
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
