package com.raumanian.thirtysix.browser.data.repository

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
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
 * Spec 012 (T043 / US3 / FR-012) — process-death erases incognito tabs while
 * normal tabs are restored.
 *
 * Closest portable proxy for SIGKILL: [ActivityScenario.recreate] — it does
 * NOT replicate full process death (the [IncognitoTabRepository] singleton
 * survives `recreate`), so this test pairs the recreate exercise with an
 * additional invariant check: the in-memory `MutableStateFlow` state stays
 * empty if no incognito tab was ever created. True SIGKILL recovery is
 * verified by the manual user-device gate G2 (deferred per Spec 008/011
 * pattern).
 *
 * The complementary unit test [com.raumanian.thirtysix.browser.data.repository
 * .IncognitoStateLegacyDefensiveReadTest] covers the cold-start invariant on
 * a freshly-constructed singleton.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProcessDeathIncognitoEraseInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var incognitoRepository: IncognitoTabRepository

    @Inject
    lateinit var tabRepository: TabRepository

    @Test
    fun recreate_preserves_normal_tabs_but_incognito_pool_remains_in_memory_only() = runBlocking {
        hiltRule.inject()

        // Cold start: incognito repo MUST be empty by FR-012 invariant.
        assertTrue(
            "incognito repo MUST start empty (FR-012)",
            incognitoRepository.observeTabs().first().isEmpty(),
        )

        // Seed a normal tab so we can verify normal-tab restoration in the
        // same scenario.
        tabRepository.observeTabs().first()
        tabRepository.createTab("https://normal.example/")
        val normalBefore = tabRepository.observeTabs().first()

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.recreate()

            // Normal tabs MUST be preserved through Activity recreate.
            val normalAfter = tabRepository.observeTabs().first()
            assertEquals(normalBefore.size, normalAfter.size)
            assertEquals(normalBefore.map { it.id }.toSet(), normalAfter.map { it.id }.toSet())

            // Incognito repo MUST still be empty (no incognito tabs ever
            // created → no path that would have populated state).
            assertTrue(
                "incognito repo stays empty across Activity recreate",
                incognitoRepository.observeTabs().first().isEmpty(),
            )
            assertEquals(0, incognitoRepository.getCount())
        }
    }
}
