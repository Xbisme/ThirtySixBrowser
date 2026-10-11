package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.MainActivity
import com.raumanian.thirtysix.browser.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
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
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 021 T052 / FR-025 – FR-027 — windows a page opens, through the real `MainActivity`, tab
 * store and WebView. Pages come from a server inside the test; assertions are on the tab store.
 */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class PopupInstrumentedTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "spec021-settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var tabRepository: TabRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val requested = CopyOnWriteArrayList<String>()
    private lateinit var server: ServerSocket

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        server = ServerSocket(0)
        thread(isDaemon = true) { serve() }
        settingsRepository.setOnboardingCompleted(true)
        tabRepository.closeAllTabs()
        tabRepository.observeTabs().first()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        server.close()
        tabRepository.closeAllTabs()
    }

    private fun open(path: String) = ActivityScenario.launch<MainActivity>(
        Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${server.localPort}$path"))
            .setClass(context, MainActivity::class.java),
    )

    private suspend fun awaitTabCount(expected: Int): Int {
        withTimeoutOrNull(WAIT_MS) { while (tabRepository.getTabCount() != expected) delay(POLL_MS) }
        return tabRepository.getTabCount()
    }

    private suspend fun awaitRequest(path: String): Boolean =
        withTimeoutOrNull(WAIT_MS) { while (requested.none { it == path }) delay(POLL_MS) } != null

    private suspend fun tapPage() {
        delay(RENDER_MS) // The request arriving is not the page being drawn.
        onView(isAssignableFrom(WebView::class.java)).perform(click())
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aTappedTargetBlankLink_opensOneNewTab() = runBlocking {
        val before = tabRepository.getTabCount()
        open("/link").use {
            assertEquals(before + 1, awaitTabCount(before + 1))
            assertTrue(awaitRequest("/link"))

            tapPage()

            assertEquals(before + 2, awaitTabCount(before + 2))
            assertTrue("the new tab loads the link", awaitRequest("/child"))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aWindowOpenedOnLoad_isBlocked_andNeverLoaded() = runBlocking {
        val before = tabRepository.getTabCount()
        open("/auto").use {
            assertEquals(before + 1, awaitTabCount(before + 1))
            assertTrue(awaitRequest("/auto"))

            delay(SETTLE_MS)

            assertEquals("no tab for a pop-up the page opened itself (FR-026)", before + 1, tabRepository.getTabCount())
            assertTrue("the blocked address is never loaded", requested.none { it == "/child" })
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aPageOpenedTab_closingItself_returnsToItsOpener() = runBlocking {
        val before = tabRepository.getTabCount()
        open("/opener").use {
            assertEquals(before + 1, awaitTabCount(before + 1))
            assertTrue(awaitRequest("/opener"))

            tapPage()

            assertEquals(before + 2, awaitTabCount(before + 2))
            assertTrue(awaitRequest("/closer"))
            assertEquals("window.close() closes the page-opened tab (FR-027)", before + 1, awaitTabCount(before + 1))
            val active = tabRepository.observeTabs().first().maxBy { it.lastActiveAt }
            assertTrue("the opener is active again", active.url.endsWith("/opener"))
        }
    }

    private fun serve() {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            runCatching {
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine()?.split(" ")?.getOrNull(1).orEmpty()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    requested += path
                    val body = (PAGES[path] ?: PAGE_EMPTY).toByteArray()
                    it.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\n" +
                                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
                            ).toByteArray() + body,
                    )
                }
            }
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 60_000L
        const val WAIT_MS = 15_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 3_000L
        const val RENDER_MS = 1_500L

        const val HEAD = "<html><head><meta name='viewport' content='width=device-width'></head><body style='margin:0'>"
        const val FULL = "style='position:fixed;inset:0;display:block;width:100%;height:100%'"
        const val PAGE_EMPTY = "$HEAD</body></html>"
        val PAGES = mapOf(
            "/link" to "$HEAD<a target='_blank' href='/child' $FULL>open</a></body></html>",
            "/auto" to "$HEAD<p>auto</p><script>window.open('/child');</script></body></html>",
            "/opener" to "$HEAD<a href='#' $FULL onclick=\"window.open('/closer'); return false;\">open</a></body></html>",
            "/closer" to "$HEAD<p>closing</p><script>setTimeout(function () { window.close(); }, 1000);</script></body></html>",
            "/child" to "$HEAD<p>child</p></body></html>",
        )
    }
}
