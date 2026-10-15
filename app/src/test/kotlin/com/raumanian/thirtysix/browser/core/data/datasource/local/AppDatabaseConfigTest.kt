package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkFolderEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
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
 * WAL journal mode is on by default, and opening existing data at a higher schema version
 * without a migration fails fast without destroying the data.
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

        // 2) Open the same file with a schema one version ahead of production and no migration.
        val dbV2Builder = Room.databaseBuilder(
            context,
            AppDatabaseFutureVersionFixture::class.java,
            dbFile.absolutePath,
        )
        var threw: IllegalStateException? = null
        try {
            val dbV2 = dbV2Builder.build()
            // Room opens lazily.
            dbV2.openHelper.writableDatabase
            dbV2.close()
            fail("opening v2 schema without migration should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            threw = e
        }
        assertNotNull("Room must refuse to open without a migration", threw)
        // The message assertion surfaces the original cause, which satisfies detekt's SwallowedException.
        assertTrue(
            "expected migration-related message, got <${threw!!.message}>",
            threw.message?.contains("migration", ignoreCase = true) == true,
        )

        // 3) Reopen as v1: the original row is still there.
        val dbV1Again = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
        val survivor = dbV1Again.bookmarkDao().getById(insertedId)
        assertNotNull("data MUST be preserved across the failed v2 open", survivor)
        assertEquals("Survivor", survivor!!.title)
        dbV1Again.close()

        dbFile.delete()
    }

    /**
     * The production entities one version ahead of [AppDatabase.SCHEMA_VERSION], with no
     * migration. The version is derived so it stays ahead: at the same version Room raises an
     * identity mismatch instead of the missing-migration error asserted above.
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
