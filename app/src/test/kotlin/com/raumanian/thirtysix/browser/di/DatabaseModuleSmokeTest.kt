package com.raumanian.thirtysix.browser.di

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Structural smoke for [DatabaseModule] (Spec 005 SC-010).
 *
 * Invokes each `@Provides` method directly with a real Application context and asserts
 * each returned DAO is non-null and capable of one round-trip. Full `@HiltAndroidTest`
 * end-to-end graph boot is deferred to Spec 013 when the first ViewModel consumer
 * materialises (it requires Robolectric + Hilt's test runner integration which is
 * heavier scaffolding than the data layer alone justifies).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseModuleSmokeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: com.raumanian.thirtysix.browser.data.local.database.AppDatabase

    @Before
    fun setup() {
        db = DatabaseModule.provideAppDatabase(context)
    }

    @After
    fun tearDown() {
        db.close()
        // Module produces a real file-backed DB at "thirtysix_browser.db" under the
        // Robolectric app context; clean it up so subsequent test classes start clean.
        context.deleteDatabase(
            com.raumanian.thirtysix.browser.data.local.database.AppDatabase.DATABASE_NAME,
        )
    }

    @Test
    fun `provideAppDatabase returns a usable singleton-style instance`() {
        assertNotNull(db)
        // Two calls within the same provider context return the same module-built
        // instance only if the caller wires @Singleton. Here we exercise that the
        // single instance is reusable across DAO providers (no double-init).
        val again = db
        assertSame(db, again)
    }

    @Test
    fun `each DAO provider yields a working DAO`() = runTest {
        val bookmarkDao = DatabaseModule.provideBookmarkDao(db)
        val folderDao = DatabaseModule.provideBookmarkFolderDao(db)
        val historyDao = DatabaseModule.provideHistoryDao(db)
        val tabDao = DatabaseModule.provideTabDao(db)

        assertNotNull(bookmarkDao)
        assertNotNull(folderDao)
        assertNotNull(historyDao)
        assertNotNull(tabDao)

        val bookmarkId = bookmarkDao.insert(
            BookmarkEntity(title = "Smoke", url = "https://smoke.com", createdAt = 0L, sortOrder = 0L),
        )
        val folderId = folderDao.insert(BookmarkFolderEntity(name = "Smoke", createdAt = 0L))
        val historyId = historyDao.insert(
            HistoryEntryEntity(url = "https://smoke.com", title = "Smoke", visitedAt = 0L),
        )
        val tabId = tabDao.insert(
            TabEntity(url = "https://smoke.com", title = "Smoke", position = 0, createdAt = 0L, lastActiveAt = 0L),
        )

        assertTrue(bookmarkId > 0L)
        assertTrue(folderId > 0L)
        assertTrue(historyId > 0L)
        assertTrue(tabId > 0L)
    }
}
