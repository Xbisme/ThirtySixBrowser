package com.raumanian.thirtysix.browser.di

import com.raumanian.thirtysix.browser.data.repository.BookmarkRepositoryImpl
import com.raumanian.thirtysix.browser.domain.repository.BookmarkRepository
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
