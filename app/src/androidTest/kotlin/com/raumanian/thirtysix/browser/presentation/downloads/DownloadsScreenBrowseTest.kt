package com.raumanian.thirtysix.browser.presentation.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.presentation.downloads.components.TEST_TAG_DOWNLOADS_EMPTY
import com.raumanian.thirtysix.browser.presentation.downloads.components.TEST_TAG_DOWNLOAD_PROGRESS
import com.raumanian.thirtysix.browser.presentation.downloads.components.TEST_TAG_DOWNLOAD_ROW
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 015 T065 (US2 / FR-019 – FR-022, and US7 / FR-040). */
@RunWith(AndroidJUnit4::class)
class DownloadsScreenBrowseTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyRecordRendersARow() {
        val records = listOf(
            instrumentedRecord(1L, "first.pdf"),
            instrumentedRecord(2L, "second.zip"),
            instrumentedRecord(3L, "third.png"),
        )
        val vm = instrumentedViewModel(
            records = records,
            statuses = records.associate { it.transferHandle to DownloadStatus.Complete() },
            existingFiles = records.mapNotNull { it.localUri }.toSet(),
        )

        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        assertEquals(3, composeRule.onAllNodesWithTag(TEST_TAG_DOWNLOAD_ROW).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("first.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("second.zip").assertIsDisplayed()
    }

    @Test
    fun anInFlightRowShowsProgress_andASettledRowDoesNot() {
        val running = instrumentedRecord(1L, "running.bin")
        val done = instrumentedRecord(2L, "done.bin")
        val vm = instrumentedViewModel(
            records = listOf(running, done),
            statuses = mapOf(
                running.transferHandle to DownloadStatus.Running(bytesSoFar = 5L, totalBytes = 10L),
                done.transferHandle to DownloadStatus.Complete(),
            ),
            existingFiles = setOf(done.localUri!!),
        )

        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        assertEquals(
            "exactly one row is transferring, so exactly one progress bar belongs on screen",
            1,
            composeRule.onAllNodesWithTag(TEST_TAG_DOWNLOAD_PROGRESS).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun noRecords_showsTheEmptyState() {
        val vm = instrumentedViewModel(records = emptyList(), statuses = emptyMap())

        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TEST_TAG_DOWNLOADS_EMPTY).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TEST_TAG_DOWNLOAD_ROW).fetchSemanticsNodes().also {
            assertEquals(0, it.size)
        }
    }
}
