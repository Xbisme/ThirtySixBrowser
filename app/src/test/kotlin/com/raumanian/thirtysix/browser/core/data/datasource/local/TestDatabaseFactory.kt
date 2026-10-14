package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
/**
 * Builds an in-memory [AppDatabase] without touching the device's default storage path.
 * `allowMainThreadQueries()` is fine here: JVM unit tests have no Looper or main thread.
 */

internal fun inMemoryAppDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
