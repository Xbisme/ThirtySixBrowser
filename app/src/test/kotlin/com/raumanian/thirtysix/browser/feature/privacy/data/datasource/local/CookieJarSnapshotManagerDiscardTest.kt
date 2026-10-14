package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `hasSnapshot()` staying `true` after a discard shows the snapshot became `EMPTY`, not `null`: the
 * difference between "restore wipes and writes nothing back" and "restore skips its wipe and leaks
 * incognito cookies". `CookieRestoreInstrumentedTest` checks the resulting jar on a device.
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
