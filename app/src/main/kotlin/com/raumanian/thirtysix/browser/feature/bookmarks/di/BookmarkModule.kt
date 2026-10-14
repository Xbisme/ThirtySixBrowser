package com.raumanian.thirtysix.browser.feature.bookmarks.di

import com.raumanian.thirtysix.browser.core.domain.repository.BookmarkRepository
import com.raumanian.thirtysix.browser.feature.bookmarks.data.repository.BookmarkRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Spec 013 — binds [BookmarkRepository] to [BookmarkRepositoryImpl] at the
 * application graph. `AppDatabase` and DAOs are already provided by the
 * existing Spec 005 `DatabaseModule`; no DI changes there.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarkModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
}
