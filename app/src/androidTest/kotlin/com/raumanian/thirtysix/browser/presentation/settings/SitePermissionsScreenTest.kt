package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
import com.raumanian.thirtysix.browser.core.domain.repository.SitePermissionRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.usecase.ObserveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveSitePermissionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSitePermissionUseCase
import com.raumanian.thirtysix.browser.presentation.settings.sitepermissions.SitePermissionsScreen
import com.raumanian.thirtysix.browser.presentation.settings.sitepermissions.SitePermissionsViewModel
import com.raumanian.thirtysix.browser.presentation.settings.sitepermissions.TEST_TAG_SITE_PERMISSIONS_REMOVE_ALL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 021 T026 / C2.7 — the Site permissions screen over an in-memory store. */
@RunWith(AndroidJUnit4::class)
class SitePermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = InMemorySitePermissions()

    private fun render() {
        val viewModel = SitePermissionsViewModel(
            ObserveSitePermissionsUseCase(repository),
            SetSitePermissionUseCase(repository),
            RemoveSitePermissionsUseCase(repository),
        )
        composeRule.setContent { ThirtySixTheme { SitePermissionsScreen(viewModel = viewModel) } }
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun empty_showsEmptyStateAndNoRemoveAll() {
        render()

        composeRule.onNodeWithText(context.getString(R.string.site_permissions_empty)).assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.site_permissions_remove_all)).assertCountEquals(0)
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun site_isListed_andRemoveAllAfterConfirmEmptiesIt() {
        repository.seed("https://meet.example", SiteCapability.Camera, SiteDecision.Allow)
        render()

        composeRule.onNodeWithText("meet.example").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.site_decision_allowed)).assertExists()

        composeRule.onNodeWithTag(TEST_TAG_SITE_PERMISSIONS_REMOVE_ALL).performClick()
        composeRule.onNodeWithText(context.getString(R.string.site_permissions_remove_all_confirm_title)).assertExists()
        // The top bar's "Remove all" is the first match; the dialog's confirm button is the last.
        composeRule.onAllNodesWithText(context.getString(R.string.site_permissions_remove_all_confirm_action))
            .onLast()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.site_permissions_empty)).assertExists()
    }

    @Test(timeout = SETTINGS_TEST_TIMEOUT_MS)
    fun decision_canBeChangedToNotAllowed() {
        repository.seed("https://meet.example", SiteCapability.Camera, SiteDecision.Allow)
        render()

        composeRule.onNodeWithText(context.getString(R.string.site_decision_allowed)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.site_decision_denied)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.site_decision_denied)).assertExists()
    }
}

private class InMemorySitePermissions : SitePermissionRepository {
    private val rows = MutableStateFlow<List<SitePermissionDecision>>(emptyList())

    fun seed(origin: String, capability: SiteCapability, decision: SiteDecision) =
        rows.update { it + SitePermissionDecision(origin, capability, decision, 0L) }

    override fun observeAll(): Flow<List<SitePermissionDecision>> = rows

    override suspend fun decisionsFor(origin: String): Map<SiteCapability, SiteDecision> =
        rows.value.filter { it.origin == origin }.associate { it.capability to it.decision }

    override suspend fun save(origin: String, capability: SiteCapability, decision: SiteDecision): Result<Unit> {
        rows.update { list ->
            list.filterNot { it.origin == origin && it.capability == capability } +
                SitePermissionDecision(origin, capability, decision, 0L)
        }
        return Result.Success(Unit)
    }

    override suspend fun removeSite(origin: String): Result<Unit> {
        rows.update { list -> list.filterNot { it.origin == origin } }
        return Result.Success(Unit)
    }

    override suspend fun clearAll(): Result<Unit> {
        rows.value = emptyList()
        return Result.Success(Unit)
    }
}
