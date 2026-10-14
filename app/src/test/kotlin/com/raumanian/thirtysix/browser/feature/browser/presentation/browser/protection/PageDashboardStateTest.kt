package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 024 T051 — what the dashboard shows (FR-015, FR-016, FR-011). */
class PageDashboardStateTest {
    private val site: (String) -> String = { host -> host.split('.').takeLast(2).joinToString(".") }
    private val web = PageProtectionStatus(
        blockedTrackerCount = 3,
        protectionOn = true,
        connectionSecure = true,
        isWebPage = true,
        site = "news.test",
    )
    private val hosts = listOf("a.tracker.test", "b.tracker.test", "ads.example")

    @Test
    fun `a web page shows its blocked hosts grouped, the total and the fireproof state`() {
        val state = PageDashboardState.of(web, hosts, setOf("news.test"), isIncognito = false, site)!!

        assertEquals("news.test", state.site)
        assertEquals(listOf("tracker.test", "ads.example"), state.blockedGroups.map { it.site })
        assertEquals(3, state.totalBlockedHosts)
        assertEquals(true, state.fireproof)
    }

    @Test
    fun `protection off shows no hosts`() {
        val state = PageDashboardState.of(web.copy(protectionOn = false), hosts, emptySet(), false, site)!!

        assertTrue(state.blockedGroups.isEmpty())
        assertEquals(0, state.totalBlockedHosts)
        assertEquals(false, state.fireproof)
    }

    @Test
    fun `incognito offers no fireproof switch`() {
        val state = PageDashboardState.of(web, hosts, setOf("news.test"), isIncognito = true, site)!!

        assertNull(state.fireproof)
    }

    @Test
    fun `no dashboard when no web page is shown`() {
        assertNull(PageDashboardState.of(PageProtectionStatus.NOT_A_WEB_PAGE, hosts, emptySet(), false, site))
    }
}
