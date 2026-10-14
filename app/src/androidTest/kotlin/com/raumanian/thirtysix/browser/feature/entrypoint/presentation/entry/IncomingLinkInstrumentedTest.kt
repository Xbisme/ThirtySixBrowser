package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.MainActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
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
 * Spec 020 — incoming content delivered to the real `MainActivity` (FR-003, FR-004, FR-008, FR-010).
 *
 * Asserts on the tab store rather than on page content, so the tests do not depend on the
 * network: a link from another app must create exactly one tab, a second delivery to the running
 * instance exactly one more, and a recreation none.
 *
 * Each test gets its own Hilt singleton component, so the production DataStore module would open
 * a second DataStore on the same file while the previous test's is still alive ("multiple
 * DataStores active"). It is replaced here by one backed by a fresh file per component; the real
 * settings repository still runs on top of it.
 */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class IncomingLinkInstrumentedTest {

    @Module
    @InstallIn(SingletonComponent::class)
    object PerTestSettingsDataStoreModule {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { File(context.cacheDir, "spec020-settings-${System.nanoTime()}.preferences_pb") },
            )
    }

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var tabRepository: TabRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        settingsRepository.setOnboardingCompleted(true)
        tabRepository.closeAllTabs()
        tabRepository.observeTabs().first()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        tabRepository.closeAllTabs()
    }

    private fun viewIntent(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(context, MainActivity::class.java)

    private suspend fun tabCount(): Int = tabRepository.getTabCount()

    /** Waits until the tab count reaches [expected]; returns the last count seen. */
    private suspend fun awaitTabCount(expected: Int): Int {
        withTimeoutOrNull(WAIT_MS) {
            while (tabCount() != expected) delay(POLL_MS)
        }
        return tabCount()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aLinkFromAnotherApp_opensOneNewTab_andRecreationAddsNone() = runBlocking {
        val before = tabCount()
        ActivityScenario.launch<MainActivity>(viewIntent("https://example.com/")).use { scenario ->
            assertEquals(before + 1, awaitTabCount(before + 1))

            scenario.recreate()
            delay(SETTLE_MS)
            assertEquals("recreation must not reopen the link (FR-008)", before + 1, tabCount())
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun aRepeatedLinkToTheRunningInstance_isADuplicateInsideTwoSeconds_andANewTabAfter() = runBlocking {
        val before = tabCount()
        val link = viewIntent("https://example.com/")
        ActivityScenario.launch<MainActivity>(link).use { scenario ->
            assertEquals(before + 1, awaitTabCount(before + 1))

            // singleTask: the running activity receives it in onNewIntent (FR-004). Driven through
            // the instrumentation API, which calls exactly that callback. The SAME intent is reused
            // because ActivityScenario finds its activity by comparing getIntent() to the launch
            // intent; onNewIntent's setIntent with a different address would lose track of it.
            val deliver = {
                scenario.onActivity { activity ->
                    InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, Intent(link))
                }
            }
            deliver()
            delay(SETTLE_MS)
            assertEquals("same link within 2 s is one tab (FR-007)", before + 1, tabCount())

            delay(DUPLICATE_WINDOW_PASSED_MS)
            deliver()
            assertEquals(before + 2, awaitTabCount(before + 2))
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun sharedTextWithAnAddress_opensThatAddress() = runBlocking {
        val shared = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Read this https://example.net/a — so good")
            .setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(shared).use {
            withTimeoutOrNull(WAIT_MS) {
                while (tabRepository.observeTabs().first().none { it.url.startsWith("https://example.net/a") }) {
                    delay(POLL_MS)
                }
            }
            assertTrue(tabRepository.observeTabs().first().any { it.url.startsWith("https://example.net/a") })
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 60_000L
        const val WAIT_MS = 10_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 500L
        const val DUPLICATE_WINDOW_PASSED_MS = 2_100L
    }
}
