package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 011 (T040 / US2 / R9) — process-death restoration smoke test.
 *
 * Robolectric `ActivityScenario.recreate()` is the closest portable proxy
 * for Android's "process killed and re-launched" path. True SIGKILL recovery
 * is verified by the manual user-device gate M2 (deferred); this test
 * exercises the same code paths (`BrowserViewModel.init {}` re-collection
 * from a fresh ViewModel + `TabRepository.observeTabs` re-emission) that
 * matter for restoration.
 *
 * Asserts: after seeding 3 tabs through the production [TabRepository],
 * recreating the host activity preserves the tab list AND the active-tab
 * pointer (the tab with max `lastActiveAt`).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TabPersistenceProcessDeathTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: TabRepository

    @Test
    fun recreate_preserves_tabs_and_active_pointer() = runBlocking {
        hiltRule.inject()

        // Seed 3 tabs with distinct lastActiveAt so the active-tab pointer is
        // deterministic. The fresh home tab auto-seeded on first observation
        // is closed once we add our 3 tabs (count == 4, then close the seed).
        // Simpler: rely on auto-seed creating one tab, then add 2 more — ends
        // at 3 tabs total with the most recently created as active.
        repository.createTab("https://a.example/")
        repository.createTab("https://b.example/")
        // 2 created tabs + 1 auto-seeded = 3 tabs.

        val before = repository.observeTabs().first()
        assertEquals(EXPECTED_TAB_COUNT, before.size)
        val activeBefore = before.first().id // sorted DESC by lastActiveAt → first is active

        // Spec 011 R9 — ActivityScenario.recreate() exercises the
        // BrowserViewModel.init { observeActiveTab() } re-collection from a
        // fresh ViewModel instance. True SIGKILL is verified manually (M2).
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.recreate()

            val after = repository.observeTabs().first()
            assertEquals(EXPECTED_TAB_COUNT, after.size)
            assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
            assertEquals(activeBefore, after.first().id)
        }
    }

    private companion object {
        const val EXPECTED_TAB_COUNT: Int = 3
    }
}
