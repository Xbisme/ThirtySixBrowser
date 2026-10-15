package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.feature.browser.fixture.BrowserScreenHolders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Browser screen assertions that need a real page load: the page renders, and a configuration
 * change keeps `currentUrl`. Render branches that must not race a live load are tested with a
 * seeded view model in `BrowserScreenLoadingIndicatorTest` and `BrowserScreenOfflineErrorTest`.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenInstrumentedTest {

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

    private lateinit var holders: BrowserScreenHolders

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            // Class-scoped, so the rotation test re-attaches the same holders after the activity is recreated.
            holders = BrowserScreenHolders(TEST_PAGE_URL)
            composeRule.activity.setContent {
                holders.Route()
            }
        }
    }

    @Test
    fun pageRenders_assertsDomContainsExampleDomain() {
        // The Espresso-Web atom waits for the JS-ready state, then checks the page text.
        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "body"))
            .check(webMatches(getText(), containsString(PAGE_TEXT)))
    }

    @Test
    fun rotation_preservesCurrentUrlInUiState() {
        // Configuration change: the same holders are re-attached, so the same URL renders again.
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                holders.Route()
            }
        }
        composeRule.waitForIdle()

        onWebView(isAssignableFrom(WebView::class.java))
            .withElement(findElement(Locator.TAG_NAME, "body"))
            .check(webMatches(getText(), containsString(PAGE_TEXT)))
    }

    private companion object {
        // Pinned to example.com for the page-text assertion, independent of the app's defaults.
        const val TEST_PAGE_URL: String = "https://example.com"

        // example.com has no <h1>Example Domain</h1>, so the check reads body text.
        const val PAGE_TEXT: String = "documentation examples"

        // A CI emulator budget, not the production loading target.
    }
}
