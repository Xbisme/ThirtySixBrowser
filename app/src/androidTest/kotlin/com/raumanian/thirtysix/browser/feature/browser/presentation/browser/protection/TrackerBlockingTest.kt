package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 023 T044 (analyze C1), SC-001 — blocking proven two ways: the monitor saw every listed
 * host, and the one tracker that *does* resolve (`127.0.0.1`, a different site from `localhost`)
 * never reached the server while protection was on, and did once the site was excepted.
 */
@RunWith(AndroidJUnit4::class)
class TrackerBlockingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val server = AssetPageServer()
    private val protection = TestProtection()

    @After
    fun tearDown() = server.close()

    private fun load(incognito: Boolean = false) {
        val url = server.url("trackers.html?port=${server.port}", TestProtection.PAGE_HOST)
        composeRule.setContent { protection.Content(url, incognito) }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) {
            server.paths().count { it.startsWith("/ok-") } == FIRST_PARTY &&
                monitorBlocked().size == TestProtection.TRACKER_HOSTS.size + 1
        }
    }

    private fun monitorBlocked() = protection.monitor.get()?.blockedHosts().orEmpty()

    @Test
    fun listedThirdPartyRequestsAreRefusedAndFirstPartyOnesLoad() {
        load()

        assertEquals(TestProtection.TRACKER_HOSTS + TestProtection.RESOLVABLE_TRACKER, monitorBlocked())
        assertEquals(TestProtection.TRACKER_HOSTS.size + 1, protection.monitor.get()!!.status.value.blockedTrackerCount)
        assertEquals(FIRST_PARTY, server.paths().count { it.startsWith("/ok-") })
        assertFalse("a blocked tracker reached the server", server.paths().any { it.startsWith("/pixel") })
    }

    @Test
    fun anExceptedSiteLoadsItsTrackers() {
        load()
        runBlocking { protection.exceptions.add(TestProtection.PAGE_HOST) }

        composeRule.runOnIdle { protection.actions.reload() }
        composeRule.waitUntil(TestProtection.TIMEOUT_MS) { server.paths().any { it.startsWith("/pixel") } }

        assertTrue(monitorBlocked().isEmpty())
        assertFalse(protection.monitor.get()!!.status.value.protectionOn)
    }

    @Test
    fun incognitoTabsAreProtectedTheSameWay() {
        load(incognito = true)

        assertEquals(TestProtection.TRACKER_HOSTS + TestProtection.RESOLVABLE_TRACKER, monitorBlocked())
        assertFalse(server.paths().any { it.startsWith("/pixel") })
    }

    private companion object {
        const val FIRST_PARTY = 10
    }
}
