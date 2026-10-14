package com.raumanian.thirtysix.browser.presentation.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.domain.usecase.ObserveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RemoveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.presentation.settings.fireproof.FireproofSitesScreen
import com.raumanian.thirtysix.browser.presentation.settings.fireproof.FireproofSitesViewModel
import com.raumanian.thirtysix.browser.presentation.settings.fireproof.TEST_TAG_FIREPROOF_SITES_EMPTY
import com.raumanian.thirtysix.browser.presentation.settings.fireproof.TEST_TAG_FIREPROOF_SITES_REMOVE_ALL
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 024 T046 — Settings → Privacy → Fireproof sites (FR-012). */
@RunWith(AndroidJUnit4::class)
class FireproofSitesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val text = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = InMemoryFireproof(listOf("new.example", "old.example"))

    private fun show() {
        val viewModel = FireproofSitesViewModel(
            ObserveFireproofSitesUseCase(repository),
            RemoveFireproofSitesUseCase(repository),
        )
        composeRule.setContent { ThirtySixTheme { FireproofSitesScreen(viewModel = viewModel) } }
    }

    @Test
    fun sitesAreListedNewestFirstAndOneCanBeRemoved() {
        show()
        composeRule.onNodeWithText("new.example").assertExists()
        composeRule.onNodeWithText("old.example").assertExists()

        composeRule.onNodeWithContentDescriptionOf("old.example").performClick()

        composeRule.waitUntil(TIMEOUT_MS) { repository.currentSites() == setOf("new.example") }
    }

    @Test
    fun removeAllAsksFirstThenEmptiesTheList() {
        show()
        composeRule.onNodeWithTag(TEST_TAG_FIREPROOF_SITES_REMOVE_ALL).performClick()
        assertEquals(2, repository.currentSites().size)

        composeRule.onNodeWithText(text.getString(R.string.fireproof_sites_remove_all_confirm_title)).assertExists()
        composeRule.onAllNodesWithTextLast(text.getString(R.string.fireproof_sites_remove_all)).performClick()

        composeRule.onNodeWithTag(TEST_TAG_FIREPROOF_SITES_EMPTY).assertExists()
        assertEquals(emptySet<String>(), repository.currentSites())
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionOf(site: String) =
        onNode(
            androidx.compose.ui.test.hasContentDescription(text.getString(R.string.fireproof_sites_remove_cd, site)),
        )

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextLast(label: String) =
        onAllNodes(androidx.compose.ui.test.hasText(label)).let { nodes ->
            nodes[nodes.fetchSemanticsNodes().size - 1]
        }

    private class InMemoryFireproof(initial: List<String>) : FireproofSiteRepository {
        private val rows = MutableStateFlow(initial.map { FireproofSite(it, Instant.EPOCH) })
        override fun observeAll(): Flow<List<FireproofSite>> = rows
        override fun currentSites(): Set<String> = rows.value.mapTo(HashSet()) { it.site }
        override suspend fun add(site: String): Result<Unit> = Result.Success(Unit)
        override suspend fun remove(site: String): Result<Unit> {
            rows.value = rows.value.filterNot { it.site == site }
            return Result.Success(Unit)
        }
        override suspend fun clearAll(): Result<Unit> {
            rows.value = emptyList()
            return Result.Success(Unit)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
