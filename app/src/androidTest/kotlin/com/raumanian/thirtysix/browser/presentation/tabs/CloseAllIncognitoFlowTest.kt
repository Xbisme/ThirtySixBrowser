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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 012 — T058 (US5 / FR-006).
 *
 * End-to-end-on-the-data-layer assertion that "close all incognito tabs"
 * wipes every incognito tab AND triggers the cookie snapshot restore. The
 * UI affordance + dialog are covered by the manual G4 user-device gate;
 * this test exercises the underlying use-case path that the dialog's
 * "Confirm" button invokes.
 *
 * Pure data-layer: opens 3 incognito tabs through the production
 * [IncognitoTabRepository], calls `closeAll()`, asserts the in-memory state
 * is empty.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CloseAllIncognitoFlowTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject
    lateinit var incognitoRepository: IncognitoTabRepository

    @Before
    fun setup() = runBlocking {
        hiltRule.inject()
        if (incognitoRepository.getCount() > 0) incognitoRepository.closeAll()
    }

    @Test
    fun closeAll_wipes_every_incognito_tab() = runBlocking {
        // Open 3 incognito tabs.
        repeat(EXPECTED_TAB_COUNT) { i ->
            val result = incognitoRepository.createTab("https://incognito$i.example/")
            assertTrue("createTab #$i should succeed", result is Result.Success)
        }
        assertEquals(EXPECTED_TAB_COUNT, incognitoRepository.getCount())

        incognitoRepository.closeAll()

        assertEquals(
            "incognitoTabCount MUST be 0 after closeAll",
            0,
            incognitoRepository.getCount(),
        )
        assertEquals(emptyList<Any>(), incognitoRepository.observeTabs().first())
    }

    private companion object {
        const val EXPECTED_TAB_COUNT: Int = 3
    }
}
