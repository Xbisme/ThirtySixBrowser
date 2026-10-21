package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.MainActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
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
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A failed page in one tab does not cost another tab its back and forward, through the real `MainActivity`. */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class ErrorRecoveryTest {

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
    val composeRule = createEmptyComposeRule()

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

    @OptIn(ExperimentalTestApi::class)
    @Test(timeout = TEST_TIMEOUT_MS)
    fun backAfterSwitchingAwayFromAFailedTab_showsThePreviousPage() {
        ActivityScenario.launch<MainActivity>(
            Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${server.localPort}$FIRST"))
                .setClass(context, MainActivity::class.java),
        ).use {
            awaitRequests(FIRST, 1)
            Thread.sleep(RENDER_MS)
            onView(isAssignableFrom(WebView::class.java)).perform(click())
            awaitRequests(SECOND, 1)
            Thread.sleep(RENDER_MS)
            val tabA = runBlocking { tabRepository.observeTabs().first().maxBy { it.lastActiveAt }.id }

            val closedPort = ServerSocket(0).use { socket -> socket.localPort }
            runBlocking { tabRepository.createTab("http://127.0.0.1:$closedPort/") }
            composeRule.waitUntilExactlyOneExists(hasTestTag(TEST_TAG_BROWSER_ERROR_STATE), WAIT_MS)

            runBlocking { tabRepository.switchActiveTab(tabA) }
            composeRule.waitUntilDoesNotExist(hasTestTag(TEST_TAG_BROWSER_ERROR_STATE), WAIT_MS)
            awaitRequests(SECOND, 2)
            Thread.sleep(RENDER_MS)

            pressBackUnconditionally()
            awaitRequests(FIRST, 2)
        }
    }

    private fun awaitRequests(path: String, count: Int) {
        composeRule.waitUntil(WAIT_MS) { requested.count { it == path } >= count }
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
        const val TEST_TIMEOUT_MS = 90_000L
        const val WAIT_MS = 15_000L
        const val RENDER_MS = 1_500L

        const val FIRST = "/first"
        const val SECOND = "/second"
        const val HEAD = "<html><head><meta name='viewport' content='width=device-width'></head><body style='margin:0'>"
        const val FULL = "style='position:fixed;inset:0;display:block;width:100%;height:100%'"
        const val PAGE_EMPTY = "$HEAD</body></html>"
        val PAGES = mapOf(
            FIRST to "$HEAD<a href='$SECOND' $FULL>next</a></body></html>",
            SECOND to "$HEAD<p>second</p></body></html>",
        )
    }
}
