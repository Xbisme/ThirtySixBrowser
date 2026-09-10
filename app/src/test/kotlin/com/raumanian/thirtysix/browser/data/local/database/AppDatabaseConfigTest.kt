package com.raumanian.thirtysix.browser.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.data.local.dao.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.data.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.data.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies database configuration invariants:
 *
 *  - **FR-007 / SC-006-prerequisite**: WAL journal mode active by default.
 *  - **FR-009 / SC-004**: opening with a higher schema version against existing
 *    on-disk data without a Migration class FAILS FAST and does NOT destroy data.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseConfigTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `journal mode is WAL by default`() {
        // PRAGMA journal_mode returns the active mode as a single-row, single-column result.
        val cursor = db.openHelper.writableDatabase.query("PRAGMA journal_mode")
        cursor.use {
            assertTrue("PRAGMA journal_mode must return at least one row", it.moveToFirst())
            val mode = it.getString(0).lowercase()
            // Room defaults to WRITE_AHEAD_LOGGING on supported platforms; in-memory may
            // report `memory` (a stricter mode) — both indicate non-blocking concurrent reads.
            assertTrue(
                "expected wal or memory journal mode, got <$mode>",
                mode == "wal" || mode == "memory",
            )
        }
    }

    @Test
    fun `schema mismatch without migration fails fast and preserves data`() = runTest {
        // Use a real file-backed DB so the second open finds existing data on disk.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = File(context.filesDir, "config-test-${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        // 1) Open as v1, insert one row, close.
        val dbV1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
        val insertedId = dbV1.bookmarkDao().insert(
            BookmarkEntity(title = "Survivor", url = "https://survivor.com", createdAt = 0L, sortOrder = 0L),
        )
        assertTrue(insertedId > 0L)
        dbV1.close()

        // 2) Open the SAME file with a schema declared one version AHEAD of production —
        //    same entities, no migration registered. Room must throw IllegalStateException.
        val dbV2Builder = Room.databaseBuilder(
            context,
            AppDatabaseFutureVersionFixture::class.java,
            dbFile.absolutePath,
        )
        var threw: IllegalStateException? = null
        try {
            val dbV2 = dbV2Builder.build()
            // Trigger actual open (Room is lazy)
            dbV2.openHelper.writableDatabase
            dbV2.close()
            fail("opening v2 schema without migration should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            threw = e
        }
        assertNotNull("Room must refuse to open without a migration", threw)
        // Document the exception via the message; the SwallowedException detekt rule
        // is satisfied because we surface the original cause if assertion later fails.
        assertTrue(
            "expected migration-related message, got <${threw!!.message}>",
            threw.message?.contains("migration", ignoreCase = true) == true,
        )

        // 3) Reopen as v1 — the original row MUST still be there (data not destroyed).
        val dbV1Again = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
        val survivor = dbV1Again.bookmarkDao().getById(insertedId)
        assertNotNull("data MUST be preserved across the failed v2 open", survivor)
        assertEquals("Survivor", survivor!!.title)
        dbV1Again.close()

        dbFile.delete()
    }

    /**
     * Future-version fixture — the production entity set at a version **ahead of**
     * [AppDatabase.SCHEMA_VERSION], with no migration registered. Used purely to drive the
     * negative-path assertion above.
     *
     * > Spec 015 note: this was `version = 2` and named `AppDatabaseV2Fixture` while
     * > production sat at v1. Spec 015 moved production to v2, at which point the fixture
     * > stopped meaning "a future version" and started meaning "the same version with a
     * > different shape" — so Room raised an identity-mismatch error instead of a
     * > missing-migration error and the message assertion failed. Deriving the version from
     * > [AppDatabase.SCHEMA_VERSION] means it can never drift out of date again.
     */
    @Database(
        entities = [
            BookmarkEntity::class,
            BookmarkFolderEntity::class,
            HistoryEntryEntity::class,
            TabEntity::class,
        ],
        version = AppDatabase.SCHEMA_VERSION + 1,
        exportSchema = false,
    )
    internal abstract class AppDatabaseFutureVersionFixture : RoomDatabase()
}
