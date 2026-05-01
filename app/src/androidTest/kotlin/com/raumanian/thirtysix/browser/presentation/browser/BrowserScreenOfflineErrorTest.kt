package com.raumanian.thirtysix.browser.presentation.browser

import androidx.activity.compose.setContent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.di.UrlConfigModule
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_BROWSER_ERROR_STATE
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Named
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 007 US3 — offline-error scenario (T036 / SC-003).
 *
 * Uninstalls the production [UrlConfigModule] and reinstalls a replacement
 * binding pointing to an unresolvable domain, so the WebView reliably fires
 * `WebViewClient.onReceivedError(ERROR_HOST_LOOKUP)` → `ErrorReason.DnsFailure`
 * → `LoadingState.Failed` without disabling the emulator's network.
 *
 * The replacement scope is THIS test class only — `UninstallModules` is
 * per-test, not global, so the page-render and rotation tests in
 * [BrowserScreenInstrumentedTest] continue to load `example.com`.
 */
@HiltAndroidTest
@UninstallModules(UrlConfigModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserScreenOfflineErrorTest {

    @Module
    @InstallIn(ViewModelComponent::class)
    object FakeUrlConfigModule {
        @Provides
        @Named("default_home_url")
        fun provideFakeDefaultHomeUrl(): String =
            "https://this-domain-does-not-exist-thirtysix.invalid"
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { BrowserScreen() }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unresolvableHost_showsLocalizedErrorState() {
        composeRule.waitUntilExactlyOneExists(
            matcher = hasTestTag(TEST_TAG_BROWSER_ERROR_STATE),
            timeoutMillis = ERROR_STATE_TIMEOUT_MS,
        )
    }

    private companion object {
        // SC-003 budget for the *production* "no internet" path is ≤ 5 s — TCP
        // connect fails instantly when the network adapter is offline. THIS test
        // uses an unresolvable `.invalid` host instead (Hilt-deterministic; no
        // emulator-network-toggle hack), which forces Chromium's DNS NXDOMAIN
        // path. That path is artificially slower (5–10 s on emulator API 29).
        // The 15 s budget here covers the slowest observed CI run with margin;
        // the production SC-003 budget remains 5 s and is verified manually
        // (Gate 7 step 4 in quickstart.md).
        const val ERROR_STATE_TIMEOUT_MS: Long = 15_000L
    }
}
