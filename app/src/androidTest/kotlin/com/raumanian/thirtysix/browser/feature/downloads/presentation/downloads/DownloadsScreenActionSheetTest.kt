package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadFailureCause
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_ACTION_COPY_LINK
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_ACTION_DELETE_FILE
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_ACTION_OPEN
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_ACTION_REMOVE
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_DELETE_FILE_DIALOG
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component.TEST_TAG_DOWNLOAD_ACTION_SHEET
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The rendered counterpart to `DownloadActionAvailabilityTest`: the sheet honours the matrix.
@RunWith(AndroidJUnit4::class)
class DownloadsScreenActionSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val completeRecord = instrumentedRecord(1L, "done.pdf")

    // `ConfirmDialog` exposes no button modifier, so the button is found by label inside the tagged dialog.
    private fun deleteConfirmButton(): SemanticsNodeInteraction {
        val label = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.downloads_delete_dialog_confirm)
        return composeRule.onNode(
            hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(TEST_TAG_DELETE_FILE_DIALOG)),
        )
    }

    private fun show(status: DownloadStatus, filePresent: Boolean) {
        val vm = instrumentedViewModel(
            records = listOf(completeRecord),
            statuses = mapOf(completeRecord.transferHandle to status),
            existingFiles = if (filePresent) setOf(completeRecord.localUri!!) else emptySet(),
        )
        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()
        vm.onItemLongPressed(vm.uiState.value.items.single())
        composeRule.waitForIdle()
    }

    @Test
    fun completedEntry_offersAllFourActions() {
        show(DownloadStatus.Complete(), filePresent = true)

        composeRule.onNodeWithTag(TEST_TAG_DOWNLOAD_ACTION_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_OPEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_COPY_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_REMOVE).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_DELETE_FILE).assertIsDisplayed()
    }

    @Test
    fun inFlightEntry_offersOnlyCopyLink_soNoTransferCanBeOrphaned() {
        show(DownloadStatus.Running(bytesSoFar = 1L, totalBytes = 10L), filePresent = false)

        composeRule.onNodeWithTag(TEST_TAG_ACTION_COPY_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_OPEN).assertDoesNotExist()
        // Remove is withheld while in flight: the load-bearing cell of the matrix.
        composeRule.onNodeWithTag(TEST_TAG_ACTION_REMOVE).assertDoesNotExist()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_DELETE_FILE).assertDoesNotExist()
    }

    @Test
    fun failedEntry_offersCopyLinkAndRemoveOnly() {
        show(DownloadStatus.Failed(DownloadFailureCause.NetworkFailure), filePresent = false)

        composeRule.onNodeWithTag(TEST_TAG_ACTION_COPY_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_REMOVE).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_OPEN).assertDoesNotExist()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_DELETE_FILE).assertDoesNotExist()
    }

    @Test
    fun missingEntry_offersCopyLinkAndRemoveOnly() {
        show(DownloadStatus.Missing, filePresent = false)

        composeRule.onNodeWithTag(TEST_TAG_ACTION_COPY_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_REMOVE).assertIsDisplayed()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_OPEN).assertDoesNotExist()
    }

    @Test
    fun deleteFile_requiresConfirmationBeforeAnythingIsRemoved() {
        val vm = instrumentedViewModel(
            records = listOf(completeRecord),
            statuses = mapOf(completeRecord.transferHandle to DownloadStatus.Complete()),
            existingFiles = setOf(completeRecord.localUri!!),
        )
        composeRule.setContent { ThirtySixTheme { DownloadsScreen(viewModel = vm) } }
        composeRule.waitForIdle()

        vm.onItemLongPressed(vm.uiState.value.items.single())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TEST_TAG_ACTION_DELETE_FILE).performClick()
        composeRule.waitForIdle()

        // The sheet has closed and the dialog is up, but nothing is gone yet.
        deleteConfirmButton().assertIsDisplayed()
        assertEquals(1, vm.uiState.value.items.size)

        deleteConfirmButton().performClick()
        composeRule.waitForIdle()
        assertEquals(0, vm.uiState.value.items.size)
    }
}
