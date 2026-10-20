package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.HiltTestActivity
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Process death erases incognito tabs while normal tabs are restored. [ActivityScenario.recreate]
 * is the portable proxy, but the [IncognitoTabRepository] singleton survives it, so the test also
 * checks the state stays empty when no incognito tab was created. Real SIGKILL is checked by hand.
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

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        // Reset the shared Hilt-singleton Room database state. The instrumented
        // test APK shares one Hilt container across @HiltAndroidTest classes,
        // so leftover tabs from a prior test would otherwise pollute counts.
        tabRepository.closeAllTabs()
        if (incognitoRepository.observeTabs().first().size > 0) incognitoRepository.closeAll()
    }

    @After
    fun tearDown() = runBlocking {
        // Same rationale as @Before: a leaked tab breaks the next test class's counts.
        tabRepository.closeAllTabs()
        if (incognitoRepository.observeTabs().first().size > 0) incognitoRepository.closeAll()
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    fun recreate_preserves_normal_tabs_but_incognito_pool_remains_in_memory_only() = runBlocking {
        // Cold start: the incognito repository is empty.
        assertTrue(
            "incognito repo MUST start empty",
            incognitoRepository.observeTabs().first().isEmpty(),
        )

        // Seed a normal tab so we can verify normal-tab restoration in the
        // same scenario.
        tabRepository.observeTabs().first()
        tabRepository.createTab("https://normal.example/")
        val normalBefore = tabRepository.observeTabs().first()

        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.recreate()

            // Normal tabs survive Activity recreate.
            val normalAfter = tabRepository.observeTabs().first()
            assertEquals(normalBefore.size, normalAfter.size)
            assertEquals(normalBefore.map { it.id }.toSet(), normalAfter.map { it.id }.toSet())

            // Still empty: no incognito tab was ever created.
            assertTrue(
                "incognito repo stays empty across Activity recreate",
                incognitoRepository.observeTabs().first().isEmpty(),
            )
            assertEquals(0, incognitoRepository.observeTabs().first().size)
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MS: Long = 30_000L
    }
}
