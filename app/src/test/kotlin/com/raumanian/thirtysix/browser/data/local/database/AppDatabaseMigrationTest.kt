package com.raumanian.thirtysix.browser.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.data.local.database.migrations.Migration1To2
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 015 FR-017 — the project's **first** migration test.
 *
 * Spec 005 wrote down a strict no-destructive-migration policy and then never had to
 * exercise it, because no schema ever changed. This turns that policy from a comment into
 * something enforced: a database created at v1 and populated in all four original tables
 * must arrive at v2 with every row intact.
 *
 * **Why not `MigrationTestHelper`?** tasks.md T018 called for it, and `room-testing` is
 * already on the test classpath. It cannot work here: `MigrationTestHelper` loads the
 * exported schema from the instrumentation context's *assets*, and AGP does not merge an
 * assets source set for JVM unit tests (only `debug`/`androidTest` get a merge task), so
 * it raises `FileNotFoundException` for `…AppDatabase/1.json` no matter where the schema
 * directory is wired. The options were to move this test to `androidTest` — putting the
 * single most important test in this spec behind a device — or to drive the real
 * production open path instead. This does the latter, and loses nothing that matters:
 * opening a v1 file with the real [Room] builder runs the registered migration and then
 * validates the resulting schema against the compiled entity identity, so a
 * [Migration1To2] that drifts from [com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity]
 * still fails here rather than on a user's device. It arguably tests *more* than the
 * helper would, because it exercises the same builder configuration `DatabaseModule` ships.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migrating v1 to v2 preserves every existing row and adds download_records`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtVersionTwo()

        assertEquals("bookmark folders lost", 1, db.countOf("bookmark_folders"))
        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals("download_records should start empty", 0, db.downloadRecordDao().count())

        // Values, not just row counts — a migration that recreated the tables empty would
        // still pass a count check if it also re-seeded them.
        db.openHelper.readableDatabase.query("SELECT title, url FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Example", c.getString(0))
            assertEquals("https://example.com", c.getString(1))
        }
        assertEquals(2, db.historyDao().count())
        assertNotNull(db.historyDao().getById(2L))
        assertEquals("https://kotlinlang.org", db.historyDao().getById(2L)?.url)
    }

    @Test
    fun `v2 database carries the download_records created_at index`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtVersionTwo()

        db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'download_records'",
        ).use { cursor ->
            val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertTrue(
                "expected the created_at index, found $names",
                names.contains("index_download_records_created_at"),
            )
        }
    }

    @Test
    fun `the new table accepts writes immediately after migration`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtVersionTwo()
        val id = db.downloadRecordDao().insert(
            com.raumanian.thirtysix.browser.data.local.entity.DownloadRecordEntity(
                id = 0L,
                sourceUrl = "https://example.com/file.pdf",
                fileName = "file.pdf",
                mimeType = "application/pdf",
                createdAt = 9000L,
                transferHandle = 42L,
                localUri = null,
            ),
        )

        assertTrue(id > 0)
        assertEquals(1, db.downloadRecordDao().count())
    }

    /**
     * Opening with the same builder configuration `DatabaseModule` uses. Room sees
     * `user_version = 1`, runs [Migration1To2], then validates the result against the
     * compiled v2 identity — which is where a drifted migration is caught.
     */
    private fun openAtVersionTwo(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(Migration1To2.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    private fun createVersionOneDatabaseWithData() {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                VERSION_ONE_DDL.forEach(db::execSQL)
                seedVersionOneData(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            // Touching the writable database forces onCreate to run and the file to exist.
            helper.writableDatabase.version = 1
        }
    }

    private fun seedVersionOneData(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO bookmark_folders (id, name, parent_id, created_at) VALUES (1, 'Reading', NULL, 1000)",
        )
        db.execSQL(
            "INSERT INTO bookmarks (id, title, url, parent_folder_id, created_at, sort_order) " +
                "VALUES (1, 'Example', 'https://example.com', 1, 1100, 0)",
        )
        db.execSQL(
            "INSERT INTO history_entries (id, url, title, visited_at) " +
                "VALUES (1, 'https://example.com', 'Example Domain', 2000)",
        )
        db.execSQL(
            "INSERT INTO history_entries (id, url, title, visited_at) " +
                "VALUES (2, 'https://kotlinlang.org', 'Kotlin', 3000)",
        )
        db.execSQL(
            "INSERT INTO tabs (id, url, title, position, created_at, last_active_at) " +
                "VALUES (1, 'https://example.com', 'Example', 0, 4000, 4000)",
        )
        db.execSQL(
            "INSERT INTO tabs (id, url, title, position, created_at, last_active_at) " +
                "VALUES (2, 'https://kotlinlang.org', 'Kotlin', 1, 4100, 4100)",
        )
    }

    private fun AppDatabase.countOf(table: String): Int =
        openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /**
         * Verbatim from the committed `app/schemas/…/1.json`. Reproducing the shipped v1
         * schema exactly — indices included — is what makes the v2 validation meaningful;
         * a sloppy approximation would fail validation for reasons unrelated to the
         * migration under test.
         */
        val VERSION_ONE_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `bookmark_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `parent_id` INTEGER, `created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`parent_id`) REFERENCES `bookmark_folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_bookmark_folders_parent_id` ON `bookmark_folders` (`parent_id`)",
            "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `parent_folder_id` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, " +
                "FOREIGN KEY(`parent_folder_id`) REFERENCES `bookmark_folders`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_bookmarks_parent_folder_id` ON `bookmarks` (`parent_folder_id`)",
            "CREATE TABLE IF NOT EXISTS `history_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `visited_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_history_entries_visited_at` ON `history_entries` (`visited_at`)",
            "CREATE TABLE IF NOT EXISTS `tabs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `last_active_at` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_tabs_position` ON `tabs` (`position`)",
        )
    }
}
