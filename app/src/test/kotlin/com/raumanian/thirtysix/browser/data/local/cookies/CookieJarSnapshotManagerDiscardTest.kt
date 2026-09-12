package com.raumanian.thirtysix.browser.data.local.cookies

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 016 T069 (Robolectric part) — ⚠️ non-negotiable privacy test for
 * [CookieJarSnapshotManagerImpl.discardSetAsideCookies].
 *
 * `hasSnapshot()` staying `true` after a discard is the observable sign that the snapshot became
 * `EMPTY` rather than `null` — the difference between "restore wipes and writes nothing back"
 * and "restore skips its wipe and leaks incognito cookies" (research R7). The instrumented half
 * in `CookieRestoreInstrumentedTest` proves the resulting cookie jar on a real device.
 */
@RunWith(AndroidJUnit4::class)
class CookieJarSnapshotManagerDiscardTest {

    private lateinit var manager: CookieJarSnapshotManagerImpl

    @Before
    fun setUp() {
        manager = CookieJarSnapshotManagerImpl(UnconfinedDispatchers)
    }

    @Test
    fun `discarding with nothing held leaves nothing held`() = runTest {
        manager.discardSetAsideCookies()

        assertFalse(manager.hasSnapshot())
    }

    @Test
    fun `discarding a held snapshot keeps a snapshot held because it becomes EMPTY, not null`() = runTest {
        manager.captureSnapshot(listOf(ORIGIN))
        assertTrue(manager.hasSnapshot())

        manager.discardSetAsideCookies()

        assertTrue("R7 — null would make restore skip its wipe", manager.hasSnapshot())
    }

    private object UnconfinedDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
    }

    private companion object {
        const val ORIGIN: String = "https://discard.test.example"
    }
}
