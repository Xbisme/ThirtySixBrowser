package com.raumanian.thirtysix.browser.feature.history.presentation.history

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.RecordHistoryEntryUseCase
import com.raumanian.thirtysix.browser.feature.history.data.repository.HistoryRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recorder path against real SQLite, use case → repository → mapper → DAO → Room. The
 * ViewModel gating above it is covered by JVM tests; loading a real page here would only add
 * network flake.
 */
@RunWith(AndroidJUnit4::class)
class HistoryRecorderIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: HistoryRepositoryImpl

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = HistoryRepositoryImpl(database.historyDao(), testDispatchers)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun normalTabVisit_isPersistedToSqlite() = runBlocking {
        RecordHistoryEntryUseCase(repository)(
            url = "https://example.com/",
            title = "Example Domain",
            isIncognito = false,
        )

        val entries = repository.observeAll().first()
        assertEquals(1, entries.size)
        assertEquals("https://example.com/", entries.single().url)
        assertEquals("Example Domain", entries.single().title)
        assertTrue(entries.single().id > 0L)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun incognitoVisit_isNeverPersisted() = runBlocking {
        val useCase = RecordHistoryEntryUseCase(repository)
        useCase(url = "https://example.com/", title = "Example Domain", isIncognito = false)

        useCase(url = "https://secret.example.com/", title = "Secret", isIncognito = true)

        val entries = repository.observeAll().first()
        assertEquals(1, entries.size)
        assertEquals("https://example.com/", entries.single().url)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun repeatVisitsToTheSameUrl_produceSeparateRows() = runBlocking {
        val useCase = RecordHistoryEntryUseCase(repository)
        useCase(url = "https://example.com/", title = "Example Domain", isIncognito = false)
        useCase(url = "https://example.com/", title = "Example Domain", isIncognito = false)

        val entries = repository.observeAll().first()
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.id }.distinct().size)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun deleteById_removesOnlyTheTargetedRow() = runBlocking {
        val useCase = RecordHistoryEntryUseCase(repository)
        useCase(url = "https://example.com/", title = "Example Domain", isIncognito = false)
        useCase(url = "https://example.com/", title = "Example Domain", isIncognito = false)
        val target = repository.observeAll().first().first()

        assertEquals(1, repository.deleteById(target.id))

        val remaining = repository.observeAll().first()
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().id != target.id)
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun clearAll_emptiesTheTable() = runBlocking {
        val useCase = RecordHistoryEntryUseCase(repository)
        repeat(3) { useCase("https://example.com/$it", "Page $it", isIncognito = false) }

        assertEquals(3, repository.clearAll())

        assertTrue(repository.observeAll().first().isEmpty())
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 30_000L
    }
}
