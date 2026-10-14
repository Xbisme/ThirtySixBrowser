package com.raumanian.thirtysix.browser.presentation.browser.page

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.domain.usecase.ApplySitePermissionChoiceUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveSitePermissionUseCase
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.InstrumentedNoopSitePermissionRepository
import com.raumanian.thirtysix.browser.presentation.browser.BrowserNavigationCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.browser.BrowserWebView
import com.raumanian.thirtysix.browser.presentation.browser.BrowserWebViewCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.LoadingState
import com.raumanian.thirtysix.browser.presentation.browser.WebViewActionsHandle
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 021 T032 / FR-011 – FR-012 — a page's file input, end to end through the real
 * [BrowserWebView], page session and [FileChooserLauncher]. The system picker is stubbed with
 * espresso-intents; the page reports what it received through its title.
 */
@RunWith(AndroidJUnit4::class)
class FileChooserInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val titles = CopyOnWriteArrayList<String>()
    private lateinit var server: ServerSocket

    @Before
    fun setUp() {
        server = ServerSocket(0)
        thread(isDaemon = true) { serve() }
        Intents.init()
        val gate = SitePermissionGate(
            ResolveSitePermissionUseCase(InstrumentedNoopSitePermissionRepository),
            ApplySitePermissionChoiceUseCase(InstrumentedNoopSitePermissionRepository),
            OneTimeGrantStore(),
            context,
        )
        val url = "http://127.0.0.1:${server.localPort}/"
        composeRule.setContent {
            val session = rememberPageSession(activeTabId = 1L, isIncognito = false, permissionGate = gate)
            BrowserWebView(
                incognitoProfile = IncognitoProfiles,
                state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle) },
                actions = remember { WebViewActionsHandle() },
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
            BrowserPageRequests(session, gate)
        }
        composeRule.waitUntil(TIMEOUT_MS) { READY in titles }
    }

    @After
    fun tearDown() {
        Intents.release()
        server.close()
        CaptureFiles.clear(context)
    }

    private fun shareable(name: String): Uri {
        val dir = File(context.cacheDir, AppConstants.CAPTURE_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, name).apply { writeText(name) }
        return FileProvider.getUriForFile(context, context.packageName + AppConstants.FILE_PROVIDER_AUTHORITY_SUFFIX, file)
    }

    private fun stubPicker(resultCode: Int, data: Intent?) {
        intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(Instrumentation.ActivityResult(resultCode, data))
    }

    private fun tapFileInput() = onView(isAssignableFrom(WebView::class.java)).perform(click())

    @Test
    fun oneFile_reachesThePage() {
        stubPicker(Activity.RESULT_OK, Intent().setData(shareable("a.txt")))
        tapFileInput()
        composeRule.waitUntil(TIMEOUT_MS) { titles.last().startsWith("files:") }
        assertEquals("files:1", titles.last())
    }

    @Test
    fun severalFiles_reachThePage() {
        val data = Intent().apply {
            clipData = ClipData.newRawUri("", shareable("a.txt")).apply { addItem(ClipData.Item(shareable("b.txt"))) }
        }
        stubPicker(Activity.RESULT_OK, data)
        tapFileInput()
        composeRule.waitUntil(TIMEOUT_MS) { titles.last().startsWith("files:") }
        assertEquals("files:2", titles.last())
    }

    @Test
    fun cancel_answersThePage_soTheInputWorksAgain() {
        stubPicker(Activity.RESULT_CANCELED, null)
        tapFileInput()
        composeRule.waitForIdle()
        assertEquals(READY, titles.last())

        // An unanswered chooser would block this second one.
        stubPicker(Activity.RESULT_OK, Intent().setData(shareable("c.txt")))
        tapFileInput()
        composeRule.waitUntil(TIMEOUT_MS) { titles.last().startsWith("files:") }
        assertEquals("files:1", titles.last())
    }

    private fun serve() {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            runCatching {
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    val body = PAGE.toByteArray()
                    it.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray() + body,
                    )
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val READY = "ready"

        // The input covers the whole page, so a tap anywhere on the WebView is a user gesture on it.
        const val PAGE = "<html><head><title>ready</title>" +
            "<meta name='viewport' content='width=device-width'></head>" +
            "<body style='margin:0'><input id='f' type='file' multiple " +
            "style='position:fixed;inset:0;width:100%;height:100%;opacity:0.01'>" +
            "<script>document.getElementById('f').addEventListener('change', function (e) {" +
            "document.title = 'files:' + e.target.files.length; e.target.value = ''; });</script>" +
            "</body></html>"
    }
}
