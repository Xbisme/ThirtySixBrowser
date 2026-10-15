package com.raumanian.thirtysix.browser.feature.privacy.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.FireproofSiteDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FireproofSiteRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FireproofSiteRepositoryImpl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        repository = FireproofSiteRepositoryImpl(db.fireproofSiteDao(), TestDispatchers, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `added sites are listed newest first and visible in the snapshot at once`() = runTest {
        repository.add("old.example")
        Thread.sleep(2)
        repository.add("new.example")

        assertEquals(listOf("new.example", "old.example"), repository.observeAll().first().map { it.site })
        assertEquals(setOf("old.example", "new.example"), repository.currentSites())
    }

    @Test
    fun `remove and clearAll update table and snapshot`() = runTest {
        repository.add("a.example")
        repository.add("b.example")

        repository.remove("a.example")
        assertEquals(setOf("b.example"), repository.currentSites())
        assertEquals(listOf("b.example"), repository.observeAll().first().map { it.site })

        repository.clearAll()
        assertTrue(repository.currentSites().isEmpty())
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `adding a site twice keeps one row`() = runTest {
        repository.add("a.example")
        repository.add("a.example")

        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `a failed write is reported as an error and leaves the snapshot unchanged`() = runTest {
        val failing = FireproofSiteRepositoryImpl(FailingDao, TestDispatchers, scope)

        val result = failing.add("a.example")

        assertTrue(result is Result.Error)
        assertTrue(failing.currentSites().isEmpty())
    }

    private object FailingDao : FireproofSiteDao {
        override suspend fun upsert(entity: FireproofSiteEntity) = throw IOException("disk full")
        override fun observeAll(): Flow<List<FireproofSiteEntity>> = emptyFlow()
        override suspend fun getAll(): List<FireproofSiteEntity> = emptyList()
        override suspend fun delete(site: String): Int = throw IOException("disk full")
        override suspend fun deleteAll(): Int = throw IOException("disk full")
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
