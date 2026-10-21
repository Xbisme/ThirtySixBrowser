package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoWebProfile
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The incognito cold-start path emits `emptyList()` and never throws: on a fresh singleton, when
 * [TabRepository] throws on first read, or when origin enumeration at `0 → 1` fails.
 */
class IncognitoStateLegacyDefensiveReadTest {

    private val incognitoProfile = FakeIncognitoWebProfile()

    @Test
    fun `fresh repository observeTabs emits empty on cold start`() = runTest {
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            snapshotOrigins = IncognitoSnapshotOrigins(
                FakeTabRepository(),
                FakeHistoryRepository(),
                FakeFireproofSiteRepository(),
            ),
            dispatchers = TestDispatcherProvider(),
            incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
            incognitoProfile = incognitoProfile,
        )
        val first = repository.observeTabs().first()
        assertEquals("singleton constructed → empty state", emptyList<Tab>(), first)
        assertEquals(0, repository.observeTabs().first().size)
    }

    @Test
    fun `createTab still succeeds when TabRepository observe would throw`() = runTest {
        val poisoned = ThrowingTabRepository()
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            snapshotOrigins = IncognitoSnapshotOrigins(
                poisoned,
                FakeHistoryRepository(),
                FakeFireproofSiteRepository(),
            ),
            dispatchers = TestDispatcherProvider(),
            incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
            incognitoProfile = incognitoProfile,
        )

        // Snapshot capture on 0→1 transition reads tabRepository.observeTabs().
        // The impl wraps that read in runCatching so a thrown Flow degrades
        // to an empty origins list — createTab still succeeds.
        val result = repository.createTab("https://incognito.example.com/")

        assertTrue("createTab should succeed despite poisoned TabRepository", result is Result.Success)
        val tab = (result as Result.Success).data
        assertEquals(1, repository.observeTabs().first().size)
        assertNotNull(tab)
        assertTrue("incognito ids must be negative", tab.id < 0L)
    }

    @Test
    fun `closeAll on a fresh repository is a safe no-op`() = runTest {
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            snapshotOrigins = IncognitoSnapshotOrigins(
                FakeTabRepository(),
                FakeHistoryRepository(),
                FakeFireproofSiteRepository(),
            ),
            dispatchers = TestDispatcherProvider(),
            incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
            incognitoProfile = incognitoProfile,
        )

        // Should NOT throw; state stays empty.
        repository.closeAll()
        assertEquals(0, repository.observeTabs().first().size)
    }

    private class ThrowingTabRepository : TabRepository {
        override fun observeTabs(): Flow<List<Tab>> = flow { error("simulated cold-start read failure") }
        override suspend fun createTab(url: String): Result<Tab> = error("not used")
        override suspend fun switchActiveTab(tabId: Long) = error("not used")
        override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = error("not used")
        override suspend fun closeTab(tabId: Long) = error("not used")
        override suspend fun closeAllTabs() = error("not used")
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
