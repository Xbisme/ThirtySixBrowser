package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Spec 013 — flat snapshot of every folder. Powers the folder-picker UI for
 * Add bookmark / Move bookmark / Move folder. The UI computes depth-based
 * indentation client-side from the parent chain.
 */
class ObserveAllFoldersUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<BookmarkFolder>> = repository.observeAllFolders()
}
