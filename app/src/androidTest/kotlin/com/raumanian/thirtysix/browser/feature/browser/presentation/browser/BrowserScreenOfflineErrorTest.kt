package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.feature.browser.fixture.BrowserScreenHolders
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_BROWSER_ERROR_STATE
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With the state seeded to Failed, the screen shows the error UI instead of the WebView. A real
 * failing load was unreliable on the emulator; the error mapping itself is covered by JVM tests.
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

    private lateinit var holders: BrowserScreenHolders

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            // Seeded to Failed before setContent: the WebView never loads, so nothing can flip the
            // state back to Loaded before the assertion.
            holders = BrowserScreenHolders(SAMPLE_PAGE_URL)
            holders.page.apply {
                onAction(PageAction.LoadStarted(SAMPLE_PAGE_URL))
                onAction(PageAction.LoadFailed(ErrorReason.NetworkUnavailable))
            }
            composeRule.activity.setContent {
                holders.Route()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun errorState_rendersWhenInitialStateIsFailed() {
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = ERROR_STATE_TIMEOUT_MS,
        )
    }

    private companion object {
        // Pure recomposition with the state pre-seeded; no network involved.
        const val ERROR_STATE_TIMEOUT_MS: Long = 5_000L
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
