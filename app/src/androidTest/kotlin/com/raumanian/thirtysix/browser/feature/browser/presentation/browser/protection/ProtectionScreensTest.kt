package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.HubProtectionSection
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_HUB_NOT_SECURE
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_HUB_PROTECTION_SWITCH
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.ObserveProtectionExceptionsUseCase
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.RemoveProtectionExceptionsUseCase
import com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions.ProtectionExceptionsScreen
import com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions.ProtectionExceptionsViewModel
import com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions.TEST_TAG_PROTECTION_EXCEPTIONS_EMPTY
import com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions.TEST_TAG_PROTECTION_EXCEPTIONS_TURN_ALL_ON
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.AboutSection
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.TEST_TAG_SETTINGS_ABOUT_NOTICE_PSL
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component.TEST_TAG_SETTINGS_ABOUT_NOTICE_TRACKERS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The protection hub section, the sites-without-protection list and the About notices. */
@RunWith(AndroidJUnit4::class)
class ProtectionScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun hub(status: PageProtectionStatus, switched: MutableList<Boolean> = mutableListOf()) {
        composeRule.setContent {
            ThirtySixTheme {
                HubProtectionSection(status = status, onBanner = {}, onProtectionSwitch = { switched += it })
            }
        }
    }

    @Test
    fun hubShowsTheCountAndTheSwitchOnAWebPage() {
        val switched = mutableListOf<Boolean>()
        hub(
            PageProtectionStatus(12, protectionOn = true, connectionSecure = true, isWebPage = true, site = "news.com"),
            switched,
        )

        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.hub_protection_trackers_blocked, 12, 12))
            .assertExists()
        composeRule.onNodeWithTag(TEST_TAG_HUB_PROTECTION_SWITCH).assertIsOn().performClick()
        composeRule.onNodeWithTag(TEST_TAG_HUB_NOT_SECURE).assertDoesNotExist()
        assertEquals(listOf(false), switched)
    }

    @Test
    fun hubShowsNotSecureAndAnOffSwitch() {
        hub(PageProtectionStatus(0, protectionOn = false, connectionSecure = false, isWebPage = true, site = "old.org"))

        composeRule.onNodeWithTag(TEST_TAG_HUB_PROTECTION_SWITCH).assertIsOff()
        composeRule.onNodeWithTag(TEST_TAG_HUB_NOT_SECURE).assertExists()
    }

    @Test
    fun hubShowsOnlyTheBannerOffAWebPage() {
        hub(PageProtectionStatus.NOT_A_WEB_PAGE)

        composeRule.onNodeWithText(resources.getString(R.string.hub_privacy_banner)).assertExists()
        composeRule.onNodeWithTag(TEST_TAG_HUB_PROTECTION_SWITCH).assertDoesNotExist()
    }

    @Test
    fun sitesWithoutProtectionCanBeTurnedBackOn() {
        val repository = TestProtection.InMemoryExceptions()
        runBlocking {
            repository.add("a.com")
            repository.add("b.com")
        }
        val viewModel = ProtectionExceptionsViewModel(
            ObserveProtectionExceptionsUseCase(repository),
            RemoveProtectionExceptionsUseCase(repository),
        )
        composeRule.setContent { ThirtySixTheme { ProtectionExceptionsScreen(viewModel = viewModel) } }

        composeRule.onNodeWithText("a.com").assertExists()
        composeRule.onNodeWithTag(TEST_TAG_PROTECTION_EXCEPTIONS_TURN_ALL_ON).performClick()
        val turnOn = resources.getString(R.string.protection_exceptions_turn_on)
        composeRule.onNode(hasText(turnOn) and hasAnyAncestor(isDialog()))
            .performClick()

        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { repository.currentSites().isEmpty() }
        composeRule.onNodeWithTag(TEST_TAG_PROTECTION_EXCEPTIONS_EMPTY).assertExists()
        assertTrue(repository.currentSites().isEmpty())
    }

    @Test
    fun aboutShowsBothOpenSourceNotices() {
        composeRule.setContent { ThirtySixTheme { AboutSection(appVersionName = "1.0") } }

        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_ABOUT_NOTICE_TRACKERS).assertExists()
        composeRule.onNodeWithTag(TEST_TAG_SETTINGS_ABOUT_NOTICE_PSL).assertExists()
    }
}
