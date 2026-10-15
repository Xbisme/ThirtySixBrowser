package com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
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
 * Open/close stress for incognito tabs and their cookie snapshot/restore: the final count is 0
 * and the heap grows under 2 MB. 30 cycles, because 100 hangs a 2 GB API 29 CI emulator; the
 * 100-cycle run is checked by hand, and the test timeout fails fast if a hang returns.
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

        // Each cycle's last close drops the count back to 0.
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
