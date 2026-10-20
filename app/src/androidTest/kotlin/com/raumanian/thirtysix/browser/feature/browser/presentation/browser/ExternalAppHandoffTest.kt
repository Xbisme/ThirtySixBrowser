@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ApplySitePermissionChoiceUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ResolveSitePermissionUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.BrowserPageRequests
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.ExternalAppEffects
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.PageSession
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.SitePermissionGate
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page.rememberPageSession
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.InstrumentedNoopSitePermissionRepository
import java.util.concurrent.CopyOnWriteArrayList
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pages opening other apps, through the real [BrowserWebView], page session, snackbar offer and
 * confirmation dialog. Every outgoing intent is stubbed, so no other app ever starts.
 */
@RunWith(AndroidJUnit4::class)
class ExternalAppHandoffTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = AssetPageServer()
    private val snackbars = SnackbarHostState()
    private val actions = WebViewActionsHandle()
    private val titles = CopyOnWriteArrayList<String>()
    private var pageCompositions = 0

    @Before
    fun setUp() {
        Intents.init()
        intending(not(isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        server.close()
    }

    private fun start(attempt: String, incognito: Boolean = false) {
        val gate = SitePermissionGate(
            ResolveSitePermissionUseCase(InstrumentedNoopSitePermissionRepository),
            ApplySitePermissionChoiceUseCase(InstrumentedNoopSitePermissionRepository),
            OneTimeGrantStore(),
            context,
        )
        val url = pageUrl(attempt)
        composeRule.setContent {
            val session = rememberPageSession(activeTabId = 1L, isIncognito = incognito, permissionGate = gate)
            Box(Modifier.fillMaxSize()) {
                PageHost(url, incognito, session)
                SnackbarHost(snackbars)
            }
            BrowserPageRequests(session, gate)
            ExternalAppEffects(session, snackbars, pageUrl = { url }, loadFallback = { actions.loadUrl(it) })
        }
    }

    @Composable
    private fun PageHost(url: String, incognito: Boolean, session: PageSession) {
        SideEffect { pageCompositions++ }
        BrowserWebView(
            incognitoProfile = IncognitoProfiles,
            state = remember {
                BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle, isIncognito = incognito)
            },
            actions = actions,
            callbacks = BrowserWebViewCallbacks(
                onLoadStarted = {},
                onProgressChanged = {},
                onLoadFinished = {},
                onLoadFailed = {},
                onDownloadRequested = { _, _, _, _ -> },
                pageRequests = session,
            ),
            navigationCallbacks = BrowserNavigationCallbacks(
                onUrlChange = {},
                onCanGoBackChange = {},
                onCanGoForwardChange = {},
                onTitleChange = { titles += it },
                onIconReceived = { _, _ -> },
                onScreenshotReady = {},
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun pageUrl(attempt: String) = server.url("$PAGE?$attempt")

    private fun offerShown(): Boolean =
        snackbars.currentSnackbarData?.visuals?.message == context.getString(R.string.external_app_offer)

    private fun startedIntents(): List<Intent> =
        Intents.getIntents().filter { it.component?.packageName != context.packageName }

    @Test
    fun untappedAttempts_openNothing_andOfferTheApp() {
        start(UNTAPPED.first())
        UNTAPPED.forEachIndexed { index, attempt ->
            if (index > 0) composeRule.runOnUiThread { actions.loadUrl(pageUrl(attempt)) }
            composeRule.waitUntil(TIMEOUT_MS) { offerShown() }
            if (index == 0) composeRule.onNodeWithText(context.getString(R.string.external_app_offer)).assertExists()
            assertEquals("$attempt started an activity", emptyList<Intent>(), startedIntents())
            composeRule.runOnUiThread { snackbars.currentSnackbarData?.dismiss() }
            composeRule.waitUntil(TIMEOUT_MS) { snackbars.currentSnackbarData == null }
        }
    }

    @Test
    fun anIframeRedirect_isDroppedSilently() {
        start("iframe")
        composeRule.waitUntil(TIMEOUT_MS) { server.paths().any { it.contains("iframe") } }
        Thread.sleep(SETTLE_MS)
        composeRule.waitForIdle()

        assertNull(snackbars.currentSnackbarData)
        assertEquals(emptyList<Intent>(), startedIntents())
    }

    @Test
    fun aTappedLinkInIncognito_asksFirst_cancelOpensNothing_openOpensBrowsable() {
        start("link", incognito = true)
        composeRule.waitUntil(TIMEOUT_MS) { READY in titles }
        Thread.sleep(RENDER_MS)

        val before = pageCompositions
        tapPage()
        composeRule.waitUntil(TIMEOUT_MS) { dialogShown() }
        composeRule.waitForIdle()
        val recompositions = pageCompositions - before
        assertTrue("page host recomposed $recompositions times", recompositions <= MAX_RECOMPOSITIONS)
        composeRule.onNodeWithText(context.getString(R.string.site_dialog_cancel)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { !dialogShown() }
        assertEquals(emptyList<Intent>(), startedIntents())

        // The dialog's window must be gone, or the tap lands on it instead of the page.
        Thread.sleep(RENDER_MS)
        tapPage()
        composeRule.waitUntil(TIMEOUT_MS) { dialogShown() }
        composeRule.onNodeWithText(context.getString(R.string.popup_open)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { startedIntents().isNotEmpty() }

        val intent = startedIntents().single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(TAPPED_TARGET), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(intent.component)
    }

    private fun dialogShown(): Boolean =
        composeRule.onAllNodes(hasText(context.getString(R.string.external_app_confirm_title)))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun tapPage() {
        onView(isAssignableFrom(WebView::class.java)).perform(click())
    }

    private companion object {
        const val PAGE = "external-untapped.html"
        const val READY = "ready"
        const val TAPPED_TARGET = "market://details?id=tapped"
        const val TIMEOUT_MS = 15_000L
        const val SETTLE_MS = 3_000L
        const val RENDER_MS = 1_000L
        const val MAX_RECOMPOSITIONS = 2
        val UNTAPPED = listOf(
            "assignProperty",
            "replace",
            "assign",
            "metaRefresh",
            "timeout",
            "windowOpenSelf",
            "formSubmit",
            "scriptClick",
            "historyThenAssign",
            "serverRedirect",
        )
    }
}
