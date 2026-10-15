package com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Every folder, flat; pickers compute indentation from the parent chain. */
class ObserveAllFoldersUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<BookmarkFolder>> = repository.observeAllFolders()
}
