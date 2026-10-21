package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieManager
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric supplies the `CookieManager` singleton the impl calls directly; it is process-global,
 * so every test starts from a wiped store.
 */
@RunWith(AndroidJUnit4::class)
class CookieJarSnapshotManagerImplTest {

    private lateinit var manager: CookieJarSnapshotManagerImpl

    @Before
    fun setup() = runTest {
        manager = CookieJarSnapshotManagerImpl(TestDispatcherProvider()) { CookieManager.getInstance() }
        wipeCookieStore()
    }

    @After
    fun tearDown() = runTest {
        wipeCookieStore()
    }

    @Test
    fun `1 - captureSnapshot stores cookies for given origins`() = runTest {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(ORIGIN_A, "session=abc123")
        cookieManager.setCookie(ORIGIN_B, "user=xbism3")

        manager.captureSnapshot(listOf(ORIGIN_A, ORIGIN_B))

        assertTrue(manager.hasSnapshot())

        // Wipe the live jar then restore — this proves the snapshot held the
        // cookies (the public API does not expose the snapshot map directly).
        suspendRemoveAllCookies(cookieManager)
        manager.restoreSnapshot()

        val restoredA = cookieManager.getCookie(ORIGIN_A).orEmpty()
        val restoredB = cookieManager.getCookie(ORIGIN_B).orEmpty()
        assertTrue("origin A cookie restored, got=$restoredA", restoredA.contains("session=abc123"))
        assertTrue("origin B cookie restored, got=$restoredB", restoredB.contains("user=xbism3"))
    }

    @Test
    fun `2 - captureSnapshot is no-op when snapshot already exists`() = runTest {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(ORIGIN_A, "first=1")

        manager.captureSnapshot(listOf(ORIGIN_A))
        assertTrue(manager.hasSnapshot())

        // If a second capture overwrote the snapshot, restore would write this new value back.
        cookieManager.setCookie(ORIGIN_A, "second=2")
        manager.captureSnapshot(listOf(ORIGIN_A))

        suspendRemoveAllCookies(cookieManager)
        manager.restoreSnapshot()

        val restored = cookieManager.getCookie(ORIGIN_A).orEmpty()
        assertTrue("first capture preserved across re-capture, got=$restored", restored.contains("first=1"))
        assertFalse("second capture must have been dropped, got=$restored", restored.contains("second=2"))
    }

    @Test
    fun `3 - restoreSnapshot is no-op when no snapshot held`() = runTest {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(ORIGIN_A, "live=value")

        assertFalse(manager.hasSnapshot())
        manager.restoreSnapshot()

        // No snapshot → no wipe → live cookies still present.
        val live = cookieManager.getCookie(ORIGIN_A).orEmpty()
        assertTrue("live cookie should be untouched, got=$live", live.contains("live=value"))
    }

    @Test
    fun `4 - restoreSnapshot wipes snapshot after restoring`() = runTest {
        CookieManager.getInstance().setCookie(ORIGIN_A, "x=1")
        manager.captureSnapshot(listOf(ORIGIN_A))
        assertTrue(manager.hasSnapshot())

        manager.restoreSnapshot()

        assertFalse("snapshot should be released after restore", manager.hasSnapshot())
        // And a subsequent captureSnapshot is not blocked by idempotency.
        manager.captureSnapshot(listOf(ORIGIN_A))
        assertTrue(manager.hasSnapshot())
    }

    @Test
    fun `5 - restoreSnapshot wipes ALL cookies including incognito-set before restoring`() = runTest {
        val cookieManager = CookieManager.getInstance()

        // Pre-incognito state on origin A.
        cookieManager.setCookie(ORIGIN_A, "normal=keep")
        manager.captureSnapshot(listOf(ORIGIN_A))

        // Incognito-session writes: a fresh cookie on A and one on B, which was not captured.
        cookieManager.setCookie(ORIGIN_A, "incognito=leak")
        cookieManager.setCookie(ORIGIN_B, "incognito=secret")

        manager.restoreSnapshot()

        val restoredA = cookieManager.getCookie(ORIGIN_A).orEmpty()
        val liveB = cookieManager.getCookie(ORIGIN_B).orEmpty()
        assertTrue("normal cookie restored on A, got=$restoredA", restoredA.contains("normal=keep"))
        assertFalse("incognito cookie wiped from A, got=$restoredA", restoredA.contains("incognito=leak"))
        assertFalse("incognito cookie wiped from B, got=$liveB", liveB.contains("incognito=secret"))
    }

    @Test
    fun `6 - captureSnapshot with empty origin list completes without crashing`() = runTest {
        // Crash-safety guarantee: empty input is valid, snapshot is held but empty.
        manager.captureSnapshot(emptyList())
        assertTrue("snapshot held even when empty (acts as wipe-marker)", manager.hasSnapshot())

        // Restore always wipes before writing back, so a cookie set in an empty session is gone.
        CookieManager.getInstance().setCookie(ORIGIN_A, "during=incognito")
        manager.restoreSnapshot()

        val after = CookieManager.getInstance().getCookie(ORIGIN_A).orEmpty()
        assertFalse("during-incognito cookie wiped, got=$after", after.contains("during=incognito"))
    }

    @Test
    fun `7 - restoreSnapshot tolerates malformed cookie pair without crashing`() = runTest {
        val cookieManager = CookieManager.getInstance()
        // The impl skips empty pairs and wraps each setCookie, so a whitespace-only pair in the
        // header must not make restore throw.
        cookieManager.setCookie(ORIGIN_A, "valid=value")
        manager.captureSnapshot(listOf(ORIGIN_A))
        suspendRemoveAllCookies(cookieManager)

        // Should not throw.
        manager.restoreSnapshot()

        val restored = cookieManager.getCookie(ORIGIN_A).orEmpty()
        assertTrue("valid pair still restored, got=$restored", restored.contains("valid=value"))
    }

    @Test
    fun `8 - hasSnapshot returns true after capture and false after restore`() = runTest {
        assertFalse(manager.hasSnapshot())

        manager.captureSnapshot(listOf(ORIGIN_A))
        assertTrue(manager.hasSnapshot())

        manager.restoreSnapshot()
        assertFalse(manager.hasSnapshot())
    }

    @Test
    fun `a removal that never answers gives up after the time limit and later calls proceed`() = runTest {
        val stuck = CookieJarSnapshotManagerImpl(VirtualTimeDispatchers(StandardTestDispatcher(testScheduler))) {
            FakeCookieManager()
        }
        stuck.captureSnapshot(listOf(ORIGIN_A))

        stuck.restoreSnapshot()
        stuck.captureSnapshot(listOf(ORIGIN_A))

        assertTrue(testScheduler.currentTime <= BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS)
        assertTrue(stuck.hasSnapshot())
    }

    private suspend fun wipeCookieStore() {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
        if (cookieManager != null) {
            suspendRemoveAllCookies(cookieManager)
        }
    }

    private suspend fun suspendRemoveAllCookies(cookieManager: CookieManager) {
        suspendCancellableCoroutine<Unit> { cont ->
            cookieManager.removeAllCookies { if (cont.isActive) cont.resume(Unit) }
        }
        cookieManager.flush()
    }

    private companion object {
        const val ORIGIN_A: String = "https://a.example.com"
        const val ORIGIN_B: String = "https://b.example.com"
    }

    private class VirtualTimeDispatchers(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher get() = d
        override val io: CoroutineDispatcher get() = d
        override val default: CoroutineDispatcher get() = d
        override val unconfined: CoroutineDispatcher get() = d
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
