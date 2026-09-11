package com.raumanian.thirtysix.browser.presentation.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.presentation.downloads.components.TEST_TAG_DOWNLOAD_CANCEL
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 015 T100 (US6 / FR-036, FR-036a).
 *
 * The control's *presence* is the contract here: FR-036 requires one tap with no menu, and
 * FR-036a requires it gone the moment the entry reaches any terminal state.
 */
@RunWith(AndroidJUnit4::class)
class DownloadsScreenCancelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val record = instrumentedRecord(1L, "big.iso")

    private fun render(status: DownloadStatus): DownloadsViewModel {
        val vm = instrumentedViewModel(
            records = listOf(record),
            statuses = mapOf(record.transferHandle to status),
        )
        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()
        return vm
    }

    @Test
    fun inFlightRow_showsAOneTapCancelControl() {
        render(DownloadStatus.Running(bytesSoFar = 1L, totalBytes = 100L))

        composeRule.onNodeWithTag(TEST_TAG_DOWNLOAD_CANCEL).assertIsDisplayed()
    }

    @Test
    fun tappingCancel_removesTheEntry() {
        val vm = render(DownloadStatus.Running(bytesSoFar = 1L, totalBytes = 100L))

        composeRule.onNodeWithTag(TEST_TAG_DOWNLOAD_CANCEL).performClick()
        composeRule.waitForIdle()

        assertEquals(
            "a cancelled row would otherwise resolve to Missing forever",
            0,
            vm.uiState.value.items.size,
        )
    }

    /**
     * All four terminal states are rendered **together, in one composition**, rather than a
     * state per iteration: `setContent` may be called only once per [composeRule], so the
     * loop this started as threw `has already set content` on its second pass and only ever
     * really checked `Complete`.
     *
     * The row-count assertion is not decoration. Without it "no cancel controls" would pass
     * just as happily against an empty list, which is the failure mode this test exists to
     * rule out.
     */
    @Test
    fun terminalRows_carryNoCancelControl() {
        val statuses = listOf(
            DownloadStatus.Complete(),
            DownloadStatus.Failed(DownloadFailureCause.Generic),
            DownloadStatus.Cancelled,
            DownloadStatus.Missing,
        )
        val records = statuses.indices.map { index ->
            instrumentedRecord(id = index + 1L, fileName = "terminal-$index.bin")
        }
        val vm = instrumentedViewModel(
            records = records,
            statuses = records.map { it.transferHandle }.zip(statuses).toMap(),
            existingFiles = records.mapNotNull { it.localUri }.toSet(),
        )
        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        assertEquals("every terminal row must be on screen", statuses.size, vm.uiState.value.items.size)
        assertEquals(
            "no terminal state may offer cancellation: $statuses",
            0,
            composeRule.onAllNodesWithTag(TEST_TAG_DOWNLOAD_CANCEL).fetchSemanticsNodes().size,
        )
    }
}
