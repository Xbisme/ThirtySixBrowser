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
import javax.inject.Singleton
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The loading indicator shows while `Loading` and not otherwise. The view model is seeded to Failed
 * before `setContent`, so the WebView never loads or fires a callback and the state is driven
 * deterministically; against a live load, a warm cache or a late re-fire made the check flaky.
 */
@HiltAndroidTest
// The browser screen reads settings (text size), so each test gets its own DataStore file:
// a fresh Hilt component per test would otherwise open a second one ("multiple DataStores active").
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenLoadingIndicatorTest {

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

    private lateinit var viewModel: PageViewModel

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            holders = BrowserScreenHolders(SAMPLE_PAGE_URL)
            viewModel = holders.page.apply {
                // Seeded before setContent so `BrowserWebView` skips its initial `loadUrl`.
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
    fun loadingIndicator_appearsWhileLoadingAndHidesWhenLoaded() {
        composeRule.activity.runOnUiThread {
            viewModel.onAction(PageAction.LoadStarted(SAMPLE_PAGE_URL))
            viewModel.onAction(PageAction.ProgressChanged(PROGRESS_MIDWAY))
        }
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )

        composeRule.activity.runOnUiThread {
            viewModel.onAction(PageAction.ProgressChanged(PROGRESS_COMPLETE))
            viewModel.onAction(PageAction.LoadFinished(SAMPLE_PAGE_URL))
        }
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun loadingIndicator_isAbsentWhileTheLoadHasFailed() {
        // Failed is neither Loading nor Loaded, so no indicator.
        composeRule.waitUntilDoesNotExist(
            matcher = hasTestTag(TEST_TAG_PAGE_LOAD_PROGRESS),
            timeoutMillis = INDICATOR_TIMEOUT_MS,
        )
    }

    private companion object {
        /** Pure recomposition against pre-seeded state — no network in the loop. */
        const val INDICATOR_TIMEOUT_MS: Long = 5_000L

        /** Any value below 100 maps to `LoadingState.Loading`. */
        const val PROGRESS_MIDWAY: Int = 40

        /** 100 maps to `LoadingState.Loaded`. */
        const val PROGRESS_COMPLETE: Int = 100
    }
}

/** A real page address for tests; the app's default is the Home page. */
private const val SAMPLE_PAGE_URL: String = "https://www.google.com/"
