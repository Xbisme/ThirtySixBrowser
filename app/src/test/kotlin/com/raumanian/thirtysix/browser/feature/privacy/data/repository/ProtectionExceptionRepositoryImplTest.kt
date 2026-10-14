package com.raumanian.thirtysix.browser.feature.privacy.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 023 T034 — sites without protection over Room, plus the in-memory snapshot. */
@RunWith(AndroidJUnit4::class)
class ProtectionExceptionRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProtectionExceptionRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        repository = ProtectionExceptionRepositoryImpl(db.siteProtectionExceptionDao(), TestDispatchers)
    }

    @After
    fun tearDown() = db.close()

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

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
