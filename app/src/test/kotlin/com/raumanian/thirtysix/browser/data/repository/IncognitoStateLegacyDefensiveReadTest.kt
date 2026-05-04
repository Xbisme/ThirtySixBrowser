package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
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
 * Spec 012 — T042 (US3, FR-021).
 *
 * Forward-compatibility guard: the cold-start path for incognito state MUST
 * silently emit `emptyList()` and never propagate an exception, even when:
 *  - the singleton was just constructed (no in-memory state),
 *  - the injected [TabRepository] dependency would throw on its first read,
 *  - or origin enumeration on the 0→1 transition fails for any reason.
 *
 * The first assertion mirrors the FR-012 process-death guarantee (singleton
 * dies → state resets to empty); the second pair guard against future builds
 * that persist incognito state to disk (currently forbidden by FR-002 but
 * worth a defensive smoke test in case the contract changes).
 */
class IncognitoStateLegacyDefensiveReadTest {

    @Test
    fun `fresh repository observeTabs emits empty on cold start`() = runTest {
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            tabRepository = FakeTabRepository(),
            dispatchers = TestDispatcherProvider(),
        )
        val first = repository.observeTabs().first()
        assertEquals("singleton constructed → empty state per FR-012", emptyList<Tab>(), first)
        assertEquals(0, repository.getCount())
    }

    @Test
    fun `createTab still succeeds when TabRepository observe would throw - FR-021`() = runTest {
        val poisoned = ThrowingTabRepository()
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            tabRepository = poisoned,
            dispatchers = TestDispatcherProvider(),
        )

        // Snapshot capture on 0→1 transition reads tabRepository.observeTabs().
        // The impl wraps that read in runCatching so a thrown Flow degrades
        // to an empty origins list — createTab MUST still succeed.
        val result = repository.createTab("https://incognito.example.com/")

        assertTrue("createTab should succeed despite poisoned TabRepository", result is Result.Success)
        val tab = (result as Result.Success).data
        assertEquals(1, repository.getCount())
        assertNotNull(tab)
        assertTrue("incognito ids must be negative", tab.id < 0L)
    }

    @Test
    fun `closeAll on a fresh repository is a safe no-op`() = runTest {
        val repository = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            tabRepository = FakeTabRepository(),
            dispatchers = TestDispatcherProvider(),
        )

        // Should NOT throw; state stays empty.
        repository.closeAll()
        assertEquals(0, repository.getCount())
    }

    private class ThrowingTabRepository : TabRepository {
        override fun observeTabs(): Flow<List<Tab>> = flow { error("simulated cold-start read failure") }
        override suspend fun createTab(url: String): Result<Tab> = error("not used")
        override suspend fun switchActiveTab(tabId: Long) = error("not used")
        override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = error("not used")
        override suspend fun closeTab(tabId: Long) = error("not used")
        override suspend fun closeAllTabs() = error("not used")
        override suspend fun getTabCount(): Int = error("not used")
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
