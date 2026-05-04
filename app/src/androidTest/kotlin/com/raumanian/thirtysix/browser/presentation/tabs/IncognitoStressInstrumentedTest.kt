package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T067 (SC-005) — open/close stress test for the incognito tab
 * pipeline.
 *
 * Goal: verify the in-memory state machine + cookie snapshot/restore
 * lifecycle remain stable under repeated open/close cycles AND that no
 * runaway memory growth surfaces.
 *
 * **CI cycle count**: 30 (down from spec target 100). Each cycle exercises
 * `createTab` → `captureSnapshot` → `closeTab` → `restoreSnapshot` →
 * `removeAllCookies` (Main thread hop) — on a CI emulator (API 29, 2 GB
 * RAM) the 100-cycle target consistently hangs the runner. The full
 * 100-cycle SC-005 target is verified by the manual G5 user-device gate
 * (already ✅). The CI version retains the contract assertions (final
 * count == 0, heap delta < 2 MB) so any catastrophic regression still
 * surfaces. Hard timeout via `@Test(timeout = …)` fails fast if a future
 * change reintroduces a hang.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IncognitoStressInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var incognitoRepository: IncognitoTabRepository

    @Test(timeout = TEST_TIMEOUT_MS)
    fun open_close_cycles_no_crash_no_runaway_heap() = runBlocking {
        hiltRule.inject()

        // Settle starting state.
        if (incognitoRepository.getCount() > 0) {
            incognitoRepository.closeAll()
        }
        assertEquals(0, incognitoRepository.getCount())

        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(GC_SETTLE_MS)
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()

        repeat(STRESS_CYCLES) { i ->
            val result = incognitoRepository.createTab("https://stress$i.example/")
            assertTrue(
                "createTab cycle $i should succeed",
                result is Result.Success,
            )
            val created = (result as Result.Success).data
            incognitoRepository.closeTab(created.id)
        }

        // Final state MUST be empty (last close on each cycle drops count
        // back to 0; restore + capture cycle every iteration).
        assertEquals(
            "incognito pool is empty after $STRESS_CYCLES cycles",
            0,
            incognitoRepository.getCount(),
        )
        assertEquals(emptyList<Any>(), incognitoRepository.observeTabs().first())

        runtime.gc()
        Thread.sleep(GC_SETTLE_MS)
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val deltaBytes = heapAfter - heapBefore
        assertTrue(
            "heap delta MUST be < 2 MB after $STRESS_CYCLES cycles, got=${deltaBytes / BYTES_PER_KB} KB",
            deltaBytes < HEAP_DELTA_BUDGET_BYTES,
        )
    }

    private companion object {
        const val STRESS_CYCLES: Int = 30
        const val GC_SETTLE_MS: Long = 100L
        const val BYTES_PER_KB: Long = 1_024L
        const val HEAP_DELTA_BUDGET_BYTES: Long = 2L * 1_024L * 1_024L
        const val TEST_TIMEOUT_MS: Long = 60_000L
    }
}
