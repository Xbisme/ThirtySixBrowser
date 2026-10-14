package com.raumanian.thirtysix.browser.presentation.browser

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.FireResult
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.core.domain.model.SiteDataPath
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.domain.privacy.BlockedSiteGroup
import com.raumanian.thirtysix.browser.presentation.browser.components.FireConfirmDialog
import com.raumanian.thirtysix.browser.presentation.browser.components.FireOverlay
import com.raumanian.thirtysix.browser.presentation.browser.components.HubCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.HubPageToolsState
import com.raumanian.thirtysix.browser.presentation.browser.components.HubSheet
import com.raumanian.thirtysix.browser.presentation.browser.components.PrivacyDashboardSheet
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_DASHBOARD_FIREPROOF_SWITCH
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_DASHBOARD_GROUP
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_DASHBOARD_PROTECTION_SWITCH
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_FIRE_OVERLAY
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_HUB_FIRE
import com.raumanian.thirtysix.browser.presentation.browser.privacy.PageDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 024 T036 / T046 / T052 — the new surfaces at component level, as Spec 019's HubSheetTest:
 * the hub's Fire tile and its blocked-tracker line, the Fire confirmation and overlay, and the
 * privacy dashboard's groups and switches. End-to-end data behaviour is FireDataTest.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyToolsScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val text = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setHub(status: PageProtectionStatus) {
        composeRule.setContent {
            ThirtySixTheme {
                HubSheet(
                    canGoBack = false,
                    canGoForward = false,
                    hasActiveDownloadsFlow = MutableStateFlow(false),
                    onDismiss = {},
                    callbacks = HubCallbacks(
                        onSettings = {}, onBack = {}, onForward = {}, onDownloadsInProgress = {},
                        onPrivacy = { calls += "privacy" },
                        onNewIncognitoTab = {}, onDownloads = {}, onHistory = {}, onBookmarks = {},
                        onQuickSetting = {}, onPageTool = {},
                        onFire = { calls += "fire" },
                        onDashboard = { calls += "dashboard" },
                    ),
                    pageTools = HubPageToolsState(available = emptySet(), isDesktopMode = false, textSizePercent = 100),
                    protectionFlow = MutableStateFlow(status),
                )
            }
        }
    }

    @Test
    fun theHubFireTileAsksForFire() {
        setHub(PageProtectionStatus.NOT_A_WEB_PAGE)
        composeRule.onNodeWithTag(TEST_TAG_HUB_FIRE).performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT_MS) { calls.isNotEmpty() }
        assertEquals(listOf("fire"), calls)
    }

    @Test
    fun onAWebPageTheBlockedLineOpensTheDashboard() {
        setHub(WEB)
        composeRule.onNodeWithText(blockedLine(3)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { calls.isNotEmpty() }
        assertEquals(listOf("dashboard"), calls)
    }

    @Test
    fun onHomeTheBannerStillOpensSettings() {
        setHub(PageProtectionStatus.NOT_A_WEB_PAGE)
        composeRule.onNodeWithText(text.getString(R.string.hub_privacy_banner)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { calls.isNotEmpty() }
        assertEquals(listOf("privacy"), calls)
    }

    @Test
    fun theConfirmationCancelsOrConfirms() {
        composeRule.setContent {
            ThirtySixTheme {
                FireConfirmDialog(onConfirm = { calls += "confirm" }, onDismiss = { calls += "cancel" })
            }
        }
        composeRule.onNodeWithText(text.getString(R.string.fire_confirm_cancel)).performClick()
        composeRule.onNodeWithText(text.getString(R.string.fire_confirm_action)).performClick()
        assertEquals(listOf("cancel", "confirm"), calls)
    }

    @Test
    fun theOverlayShowsWhileRunningAndAcknowledgesWhenFinished() {
        val state = MutableStateFlow<FireState>(FireState.Running)
        composeRule.setContent {
            ThirtySixTheme {
                val current = state.collectAsState().value
                FireOverlay(state = current, onFinished = { calls += "finished" })
            }
        }
        composeRule.onNodeWithTag(TEST_TAG_FIRE_OVERLAY).assertExists()
        assertTrue(calls.isEmpty())

        state.value = FireState.Finished(FireResult(emptySet(), SiteDataPath.Complete))
        composeRule.waitUntil(TIMEOUT_MS) { calls == listOf("finished") }
    }

    @Test
    fun theDashboardListsGroupsAndItsSwitchesReport() {
        val dashboard = MutableStateFlow<PageDashboardState?>(
            PageDashboardState(
                site = "news.test",
                connectionSecure = true,
                protectionOn = true,
                blockedGroups = listOf(
                    BlockedSiteGroup("tracker.test", listOf("a.tracker.test", "b.tracker.test")),
                    BlockedSiteGroup("ads.example", listOf("ads.example")),
                ),
                totalBlockedHosts = 3,
                fireproof = false,
            ),
        )
        composeRule.setContent {
            ThirtySixTheme {
                PrivacyDashboardSheet(
                    dashboardFlow = dashboard,
                    onDismiss = {},
                    onProtectionSwitched = { calls += "protection:$it" },
                    onFireproofSwitched = { calls += "fireproof:$it" },
                )
            }
        }

        composeRule.onNodeWithText(blockedLine(3)).assertExists()
        assertEquals(2, composeRule.onAllNodesWithTag(TEST_TAG_DASHBOARD_GROUP).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TEST_TAG_DASHBOARD_PROTECTION_SWITCH).assertIsOn().performClick()
        composeRule.onNodeWithTag(TEST_TAG_DASHBOARD_FIREPROOF_SWITCH).performScrollTo().assertIsOff().performClick()
        assertEquals(listOf("protection:false", "fireproof:true"), calls)
    }

    @Test
    fun theDashboardOffersNoFireproofSwitchInIncognito() {
        composeRule.setContent {
            ThirtySixTheme {
                PrivacyDashboardSheet(
                    dashboardFlow = MutableStateFlow(
                        PageDashboardState("news.test", true, true, emptyList(), 0, fireproof = null),
                    ),
                    onDismiss = {},
                    onProtectionSwitched = {},
                    onFireproofSwitched = {},
                )
            }
        }
        composeRule.onNodeWithText(text.getString(R.string.dashboard_no_trackers)).assertExists()
        composeRule.onNodeWithTag(TEST_TAG_DASHBOARD_FIREPROOF_SWITCH).assertDoesNotExist()
    }

    private fun blockedLine(count: Int): String =
        text.resources.getQuantityString(R.plurals.hub_protection_trackers_blocked, count, count)

    private companion object {
        const val TIMEOUT_MS = 5_000L
        val WEB = PageProtectionStatus(
            blockedTrackerCount = 3,
            protectionOn = true,
            connectionSecure = true,
            isWebPage = true,
            site = "news.test",
        )
    }
}
