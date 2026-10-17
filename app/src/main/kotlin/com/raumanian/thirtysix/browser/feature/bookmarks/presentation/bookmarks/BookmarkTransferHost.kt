@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.EventEffect
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkExportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkTransferLimits
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkImportSummaryDialog
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkTransferProgressDialog
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.BookmarkTransferSheet
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.flow.Flow

// Providers label .html files inconsistently, so the picker offers every type a bookmark file arrives as.
private val IMPORT_MIME_TYPES = arrayOf("text/html", "application/xhtml+xml", "text/plain", "application/octet-stream")
private const val EXPORT_MIME_TYPE = "text/html"

/** The import and export sheet, the system file pickers, progress and results for the Bookmarks screen. */
@Composable
fun BookmarkTransferHost(
    state: BookmarkTransferUiState,
    events: Flow<BookmarkTransferEvent>,
    onAction: (BookmarkTransferAction) -> Unit,
    onOpenFolder: (Long) -> Unit,
    showMessage: (String) -> Unit,
) {
    val resources = LocalResources.current
    val locale = LocalLocale.current.platformLocale
    val importNames by rememberUpdatedState(
        BookmarkImportNames(
            folderBaseName = resources.getString(
                R.string.bookmarks_import_folder_name,
                DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date()),
            ),
            untitledFolderName = resources.getString(R.string.bookmarks_import_untitled_folder),
        ),
    )

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onAction(BookmarkTransferAction.ImportFilePicked(it.toString(), importNames)) }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE),
    ) { uri ->
        uri?.let { onAction(BookmarkTransferAction.ExportFilePicked(it.toString())) }
    }

    EventEffect(events) { event ->
        when (event) {
            BookmarkTransferEvent.LaunchImportPicker -> importPicker.launch(IMPORT_MIME_TYPES)
            is BookmarkTransferEvent.LaunchExportPicker -> exportPicker.launch(event.suggestedFileName)
            is BookmarkTransferEvent.OpenFolder -> onOpenFolder(event.folderId)
            is BookmarkTransferEvent.ImportFailed -> showMessage(resources.importMessage(event.result))
            is BookmarkTransferEvent.ExportFinished -> showMessage(resources.exportMessage(event.result))
        }
    }

    BookmarkTransferContent(state = state, onAction = onAction)
}

@Composable
private fun BookmarkTransferContent(state: BookmarkTransferUiState, onAction: (BookmarkTransferAction) -> Unit) {
    if (state.isSheetOpen) {
        BookmarkTransferSheet(
            onImport = { onAction(BookmarkTransferAction.ImportClick) },
            onExport = {
                val name = BookmarkTransferLimits.EXPORT_FILE_NAME_PATTERN.format(LocalDate.now().toString())
                onAction(BookmarkTransferAction.ExportClick(name))
            },
            onDismiss = { onAction(BookmarkTransferAction.SheetDismiss) },
        )
    }
    when (val progress = state.progress) {
        BookmarkTransferProgress.Idle -> Unit
        BookmarkTransferProgress.Importing -> BookmarkTransferProgressDialog(isImport = true)
        BookmarkTransferProgress.Exporting -> BookmarkTransferProgressDialog(isImport = false)
        is BookmarkTransferProgress.Imported -> BookmarkImportSummaryDialog(
            result = progress.result,
            onOpenFolder = { onAction(BookmarkTransferAction.OpenImportedFolder(progress.result.folderId)) },
            onDismiss = { onAction(BookmarkTransferAction.SummaryDismiss) },
        )
    }
}

private fun Resources.importMessage(result: BookmarkImportResult): String = getString(
    when (result) {
        BookmarkImportResult.NotABookmarkFile -> R.string.bookmarks_import_error_not_bookmarks
        BookmarkImportResult.NothingToImport -> R.string.bookmarks_import_error_nothing
        BookmarkImportResult.TooLarge -> R.string.bookmarks_import_error_too_large
        BookmarkImportResult.ReadFailed -> R.string.bookmarks_import_error_read
        BookmarkImportResult.WriteFailed, is BookmarkImportResult.Imported -> R.string.bookmarks_import_error_write
    },
)

private fun Resources.exportMessage(result: BookmarkExportResult): String = when (result) {
    is BookmarkExportResult.Exported ->
        getQuantityString(R.plurals.bookmarks_export_done, result.bookmarks, result.bookmarks)
    BookmarkExportResult.NothingToExport -> getString(R.string.bookmarks_export_nothing)
    BookmarkExportResult.WriteFailed -> getString(R.string.bookmarks_export_error_write)
}
