package com.raumanian.thirtysix.browser.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raumanian.thirtysix.browser.data.local.database.AppDatabase

/**
 * Shared test fixture — builds an in-memory [AppDatabase] without touching the device's
 * default storage path. Pattern documented in `specs/005-room-database-schema/research.md` R10.
 *
 * `allowMainThreadQueries()` is permissible here because the JVM unit-test environment
 * has no Looper / main thread; production code paths still use `suspend fun` to enforce
 * off-main-thread execution (FR-006).
 */
internal fun inMemoryAppDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
