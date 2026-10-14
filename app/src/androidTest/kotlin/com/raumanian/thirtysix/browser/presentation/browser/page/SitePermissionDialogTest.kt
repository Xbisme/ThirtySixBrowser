package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.data.local.permission.OneTimeGrantStore
import com.raumanian.thirtysix.browser.domain.usecase.ApplySitePermissionChoiceUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveSitePermissionUseCase
import com.raumanian.thirtysix.browser.presentation.settings.InstrumentedNoopSitePermissionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 021 T026 / C2.1 — the permission dialog's choices in normal and incognito tabs. */
@RunWith(AndroidJUnit4::class)
class SitePermissionDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = InstrumentedNoopSitePermissionRepository
    private val gate = SitePermissionGate(
        ResolveSitePermissionUseCase(repository),
        ApplySitePermissionChoiceUseCase(repository),
        OneTimeGrantStore(),
        context,
    )
    private var answers = mutableListOf<String>()
    private var doneCount = 0

    private fun render(incognito: Boolean) {
        val request = PageRequest.Permission(
            tabId = 1L,
            generation = 1L,
            origin = "https://meet.example",
            capabilities = setOf(SiteCapability.Camera, SiteCapability.Microphone),
            isIncognito = incognito,
            onGrant = { answers += "grant" },
            onDeny = { answers += "deny" },
        )
        composeRule.setContent { ThirtySixTheme { SitePermissionDialog(request, gate) { doneCount++ } } }
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun normalTab_offersThreeChoices_andNamesSiteAndCapabilities() {
        render(incognito = false)

        composeRule.onNodeWithText(text(R.string.site_permission_always_allow)).assertExists()
        composeRule.onNodeWithText(text(R.string.site_permission_allow_once)).assertExists()
        composeRule.onNodeWithText(text(R.string.site_permission_deny)).assertExists()
        val capabilities = context.getString(
            R.string.site_capability_pair,
            text(R.string.site_capability_camera),
            text(R.string.site_capability_microphone),
        )
        composeRule.onNodeWithText(context.getString(R.string.site_permission_title, "meet.example", capabilities))
            .assertExists()
    }

    @Test
    fun incognitoTab_neverOffersAlwaysAllow() {
        render(incognito = true)

        composeRule.onAllNodesWithText(text(R.string.site_permission_always_allow)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.site_permission_allow_once)).assertExists()
        composeRule.onNodeWithText(text(R.string.site_permission_deny)).assertExists()
    }

    @Test
    fun dontAllow_refusesThePageOnce() {
        render(incognito = false)

        composeRule.onNodeWithText(text(R.string.site_permission_deny)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("deny"), answers)
        assertTrue(doneCount == 1)
    }
}
