package com.raumanian.thirtysix.browser.core.data.datasource.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SiteProtectionExceptionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabSessionEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration1To2
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration2To3
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration3To4
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration4To5
import com.raumanian.thirtysix.browser.core.data.datasource.local.migration.Migration5To6
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database created at v1 and populated in all four original tables arrives at v2 with every
 * row intact. Drives the real [Room] builder rather than `MigrationTestHelper`, which cannot
 * load exported schemas in JVM unit tests; Room still validates the migrated schema on open.
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

        val db = openAtCurrentVersion()

        assertEquals("bookmark folders lost", 1, db.countOf("bookmark_folders"))
        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals("download_records should start empty", 0, db.countOf("download_records"))

        // Values, not just row counts: a migration that recreated and re-seeded the tables passes a count.
        db.openHelper.readableDatabase.query("SELECT title, url FROM bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Example", c.getString(0))
            assertEquals("https://example.com", c.getString(1))
        }
        assertEquals(2, db.historyDao().count())
        db.openHelper.readableDatabase.query("SELECT url FROM history_entries WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("https://kotlinlang.org", c.getString(0))
        }
    }

    @Test
    fun `migrating on to v3 adds an empty site_permissions table and keeps every row`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()

        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals("site_permissions should start empty", 0, db.countOf(SitePermissionEntity.TABLE_NAME))
    }

    @Test
    fun `migrating on to v4 adds an empty site_protection_exceptions table and keeps every row`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()

        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals(0, db.countOf(SiteProtectionExceptionEntity.TABLE_NAME))
    }

    @Test
    fun `migrating on to v5 adds an empty fireproof_sites table and keeps every row`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()

        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals(0, db.countOf(SiteProtectionExceptionEntity.TABLE_NAME))
        assertEquals(0, db.countOf(FireproofSiteEntity.TABLE_NAME))
    }

    @Test
    fun `migrating on to v6 adds an empty tab_sessions table and keeps every row`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()

        assertEquals("bookmarks lost", 1, db.countOf("bookmarks"))
        assertEquals("history entries lost", 2, db.countOf("history_entries"))
        assertEquals("tabs lost", 2, db.countOf("tabs"))
        assertEquals(0, db.countOf(TabSessionEntity.TABLE_NAME))
    }

    @Test
    fun `tab_sessions accepts writes and goes with its migrated tab`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()
        db.tabSessionDao().upsert(TabSessionEntity(1L, byteArrayOf(7), 1L))
        assertEquals(1, db.countOf(TabSessionEntity.TABLE_NAME))

        db.tabDao().deleteAll()

        assertEquals(0, db.countOf(TabSessionEntity.TABLE_NAME))
    }

    @Test
    fun `fireproof_sites accepts writes and replaces by site`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()
        val dao = db.fireproofSiteDao()
        dao.upsert(FireproofSiteEntity("mail.example", 1L))
        dao.upsert(FireproofSiteEntity("mail.example", 5L))
        dao.upsert(FireproofSiteEntity("bank.example", 3L))

        val rows = dao.observeAll().first().map { it.site to it.addedAt }
        assertEquals(listOf("mail.example" to 5L, "bank.example" to 3L), rows)
    }

    @Test
    fun `site_protection_exceptions accepts writes and replaces by site`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()
        val dao = db.siteProtectionExceptionDao()
        dao.upsert(SiteProtectionExceptionEntity("shop.example", 1L))
        dao.upsert(SiteProtectionExceptionEntity("shop.example", 5L))
        dao.upsert(SiteProtectionExceptionEntity("news.example", 3L))

        val rows = dao.observeAll().first().map { it.site to it.addedAt }
        assertEquals(listOf("shop.example" to 5L, "news.example" to 3L), rows)
    }

    @Test
    fun `site_permissions accepts writes and replaces by origin and capability`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()
        val dao = db.sitePermissionDao()
        dao.upsert(SitePermissionEntity("https://a.org", "camera", "allow", 1L))
        dao.upsert(SitePermissionEntity("https://a.org", "camera", "deny", 2L))
        dao.upsert(SitePermissionEntity("https://a.org", "location", "allow", 3L))

        val rows = dao.forOrigin("https://a.org").associate { it.capability to it.decision }
        assertEquals(mapOf("camera" to "deny", "location" to "allow"), rows)
    }

    @Test
    fun `v2 database carries the download_records created_at index`() = runTest {
        createVersionOneDatabaseWithData()

        val db = openAtCurrentVersion()

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

        val db = openAtCurrentVersion()
        val id = db.downloadRecordDao().insert(
            com.raumanian.thirtysix.browser.core.data.datasource.local.entity.DownloadRecordEntity(
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
        assertEquals(1, db.countOf("download_records"))
    }

    /**
     * Opening with the same builder configuration `DatabaseModule` uses. Room sees
     * `user_version = 1`, runs [Migration1To2], then validates the result against the
     * compiled v2 identity — which is where a drifted migration is caught.
     */
    private fun openAtCurrentVersion(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(
                Migration1To2.MIGRATION_1_2,
                Migration2To3.MIGRATION_2_3,
                Migration3To4.MIGRATION_3_4,
                Migration4To5.MIGRATION_4_5,
                Migration5To6.MIGRATION_5_6,
            )
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
         * Verbatim from the committed `app/schemas/…/1.json`, indices included; an approximation
         * would fail validation for reasons unrelated to the migration under test.

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
