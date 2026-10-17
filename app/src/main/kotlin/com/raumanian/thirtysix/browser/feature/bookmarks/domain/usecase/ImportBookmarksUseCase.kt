package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkFileReadResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportNames
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.BookmarkImportResult
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.model.ParsedBookmarkFile
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.repository.BookmarkFileRepository
import javax.inject.Inject

/** Imports a bookmark file under one new dated folder, all or nothing, within the app's bookmark limits. */
class ImportBookmarksUseCase @Inject constructor(
    private val files: BookmarkFileRepository,
    private val bookmarks: BookmarkRepository,
) {
    suspend operator fun invoke(uri: String, names: BookmarkImportNames): BookmarkImportResult =
        when (val read = files.read(uri, names.untitledFolderName)) {
            is BookmarkFileReadResult.Parsed -> import(read.file, names.folderBaseName)
            BookmarkFileReadResult.NotABookmarkFile -> BookmarkImportResult.NotABookmarkFile
            BookmarkFileReadResult.TooLarge -> BookmarkImportResult.TooLarge
            BookmarkFileReadResult.ReadFailed -> BookmarkImportResult.ReadFailed
        }

    private suspend fun import(file: ParsedBookmarkFile, folderBaseName: String): BookmarkImportResult =
        when {
            file.linkCount == 0 -> BookmarkImportResult.NothingToImport
            exceedsLimits(file) -> BookmarkImportResult.TooLarge
            else -> write(file, folderBaseName)
        }

    // The import adds its own top-level folder on top of the file's folders.
    private suspend fun exceedsLimits(file: ParsedBookmarkFile): Boolean =
        bookmarks.countBookmarks() + file.linkCount > BrowserLimits.MAX_BOOKMARKS ||
            bookmarks.countFolders() + file.folderCount + 1 > BrowserLimits.MAX_FOLDERS

    private suspend fun write(file: ParsedBookmarkFile, folderBaseName: String): BookmarkImportResult =
        when (val written = bookmarks.importTree(folderBaseName, file.root)) {
            is Result.Success -> BookmarkImportResult.Imported(
                folderId = written.data.folderId,
                folderName = written.data.folderName,
                imported = written.data.bookmarksAdded,
                duplicatesSkipped = file.duplicatesSkipped,
                unsupportedSkipped = file.unsupportedSkipped,
            )
            is Result.Error -> BookmarkImportResult.WriteFailed
        }
}
