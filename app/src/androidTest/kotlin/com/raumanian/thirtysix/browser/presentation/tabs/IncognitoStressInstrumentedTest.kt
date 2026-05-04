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
 * Spec 012 — T067 (SC-005) — 100-cycle open/close stress test for the
 * incognito tab pipeline.
 *
 * Goal: verify the in-memory state machine + cookie snapshot/restore
 * lifecycle remain stable under repeated open/close cycles AND that no
 * runaway memory growth surfaces. Mirrors the manual G5 device gate that the
 * user runs on a 2 GB emulator.
 *
 * Heap delta sanity check: < 2 MB across 100 cycles. The bound is
 * deliberately loose because Robolectric/instrumented runtimes carry GC
 * jitter; the test's primary value is asserting that NO exception
 * propagates to the runner across 100 iterations.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class IncognitoStressInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var incognitoRepository: IncognitoTabRepository

    @Test
    fun open_close_100_cycles_no_crash_no_runaway_heap() = runBlocking {
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
            "incognito pool is empty after 100 cycles",
            0,
            incognitoRepository.getCount(),
        )
        assertEquals(emptyList<Any>(), incognitoRepository.observeTabs().first())

        runtime.gc()
        Thread.sleep(GC_SETTLE_MS)
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val deltaBytes = heapAfter - heapBefore
        assertTrue(
            "heap delta MUST be < 2 MB after 100 cycles, got=${deltaBytes / BYTES_PER_KB} KB",
            deltaBytes < HEAP_DELTA_BUDGET_BYTES,
        )
    }

    private companion object {
        const val STRESS_CYCLES: Int = 100
        const val GC_SETTLE_MS: Long = 100L
        const val BYTES_PER_KB: Long = 1_024L
        const val HEAP_DELTA_BUDGET_BYTES: Long = 2L * 1_024L * 1_024L
    }
}
