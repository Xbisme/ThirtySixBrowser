package com.raumanian.thirtysix.browser.core.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SitePermissionDao

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        dao = db.sitePermissionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed(origin: String, capability: String = "camera") =
        dao.upsert(SitePermissionEntity(origin, capability, "allow", 0L))

    @Test
    fun `deleteOrigins removes every row of exactly those origins`() = runTest {
        seed("https://a.test")
        seed("https://a.test", "microphone")
        seed("https://b.test")
        seed("https://keep.test")

        val deleted = dao.deleteOrigins(listOf("https://a.test", "https://b.test", "https://absent.test"))

        assertEquals(3, deleted)
        assertEquals(listOf("https://keep.test"), dao.observeAll().first().map { it.origin })
    }

    @Test
    fun `deleteOrigins handles more origins than SQLite binds in one statement`() = runTest {
        val origins = (0 until MANY).map { "https://site$it.test" }
        origins.forEach { seed(it) }
        seed("https://keep.test")

        assertEquals(MANY, dao.deleteOrigins(origins))
        assertEquals(listOf("https://keep.test"), dao.observeAll().first().map { it.origin })
    }

    private companion object {
        const val MANY = 1_200
    }
}
