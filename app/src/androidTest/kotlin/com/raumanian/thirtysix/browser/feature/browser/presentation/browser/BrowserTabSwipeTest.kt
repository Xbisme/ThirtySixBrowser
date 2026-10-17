package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.di.SettingsDataStoreProviderModule
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_HARNESS_FIELD
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_TAB_SWIPE_LANE
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TabSwipeHarness
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real holder over the real tab store: a swipe or an accessibility action changes the active tab. */
@HiltAndroidTest
@UninstallModules(SettingsDataStoreProviderModule::class)
@RunWith(AndroidJUnit4::class)
class BrowserTabSwipeTest {

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

    @Inject
    lateinit var tabs: TabRepository

    @Inject
    lateinit var settings: SettingsRepository

    private val ids = mutableListOf<Long>()
    private val swipeState = TabSwipeState()

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        tabs.closeAllTabs()
        ids += tabs.observeTabs().first().single().id
        repeat(2) { i -> ids += (tabs.createTab("https://t$i.example/") as Result.Success).data.id }
    }

    private fun show() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                val holder: TabSwipeViewModel = hiltViewModel()
                val swipe by holder.uiState.collectAsStateWithLifecycle()
                TabSwipeHarness(swipe = swipe, onAction = holder::onAction, state = swipeState)
            }
        }
        composeRule.waitUntil(WAIT_MS) { activeId() == ids.last() && customActions().isNotEmpty() }
        composeRule.waitForIdle()
    }

    private fun activeId(): Long = runBlocking { tabs.observeTabs().first().first().id }

    @Test
    fun swipingWalksTheTabsInOpeningOrder() {
        show()

        swipeAndAwait(towardStart = false, active = ids[1], actions = 2)
        swipeAndAwait(towardStart = false, active = ids[0], actions = 1)
        swipeAndAwait(towardStart = true, active = ids[1], actions = 2)
        swipeAndAwait(towardStart = true, active = ids[2], actions = 1)
    }

    /** Waits for the holder to show the new tab's neighbours too: until then a swipe is held back. */
    private fun swipeAndAwait(towardStart: Boolean, active: Long, actions: Int) {
        swipe(towardStart)
        runCatching {
            composeRule.waitUntil(WAIT_MS) {
                activeId() == active && customActions().size == actions && !swipeState.holding
            }
        }.onFailure {
            val seen = "${activeId()}/${customActions().size}, holding=${swipeState.holding}"
            throw AssertionError("wanted $active/$actions, got $seen in $ids", it)
        }
    }

    @Test
    fun theAccessibilityActionSwitchesToo() {
        show()
        val previous = customActions().single()

        composeRule.runOnUiThread { previous.action() }

        composeRule.waitUntil(WAIT_MS) { activeId() == ids[1] }
    }

    @Test
    fun withTheSettingOffASwipeChangesNothing() {
        runBlocking { settings.setTabSwipeEnabled(false) }
        show()

        swipe(towardStart = false)
        composeRule.waitForIdle()

        assertEquals(ids.last(), activeId())
    }

    private fun swipe(towardStart: Boolean) {
        composeRule.waitUntil(WAIT_MS) { !swipeState.holding }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TEST_TAG_TAB_SWIPE_LANE).performTouchInput {
            if (towardStart) swipeLeft() else swipeRight()
        }
    }

    private fun customActions() = composeRule.onNodeWithTag(TEST_TAG_HARNESS_FIELD).fetchSemanticsNode()
        .config.getOrElse(SemanticsActions.CustomActions) { emptyList() }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
