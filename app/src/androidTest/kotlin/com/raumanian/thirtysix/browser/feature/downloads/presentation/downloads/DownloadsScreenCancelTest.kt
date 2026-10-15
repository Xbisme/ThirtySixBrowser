package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_DOWNLOAD_CANCEL
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The cancel control is one tap with no menu, and gone once the entry reaches any terminal state.
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
        composeRule.setContent { ThirtySixTheme { DownloadsRoute(viewModel = vm) } }
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
     * All terminal states render in one composition, since `setContent` may be called only once per
     * [composeRule]. The row count guards against "no cancel controls" passing on an empty list.
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
        composeRule.setContent { ThirtySixTheme { DownloadsRoute(viewModel = vm) } }
        composeRule.waitForIdle()

        assertEquals("every terminal row must be on screen", statuses.size, vm.uiState.value.items.size)
        assertEquals(
            "no terminal state may offer cancellation: $statuses",
            0,
            composeRule.onAllNodesWithTag(TEST_TAG_DOWNLOAD_CANCEL).fetchSemanticsNodes().size,
        )
    }
}
