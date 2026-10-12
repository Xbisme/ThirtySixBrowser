package com.raumanian.thirtysix.browser.presentation.browser.protection

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.domain.protection.NavigationPreparer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 023 T037, T062 — blocking, counting and the HTTPS-upgrade lifecycle of one WebView. */
@RunWith(AndroidJUnit4::class)
class PageProtectionMonitorTest {
    private val test = ProtectionTestEngine()
    private val monitor = test.engine.newMonitor(isIncognito = false)

    @Test
    fun `listed third-party hosts are blocked and counted once each`() {
        monitor.onMainFrameRequest("https://news.com/a")
        monitor.onPageStarted("https://news.com/a")

        val response = monitor.onSubresource(Uri.parse("https://tracker.example/p.gif"))
        monitor.onSubresource(Uri.parse("https://cdn.tracker.example/p.js"))
        monitor.onSubresource(Uri.parse("https://tracker.example/again.gif"))

        assertNotNull(response)
        assertEquals(403, response!!.statusCode)
        assertEquals(setOf("tracker.example", "cdn.tracker.example"), monitor.blockedHosts())
        assertEquals(2, monitor.status.value.blockedTrackerCount)
    }

    @Test
    fun `first-party and unlisted requests load`() {
        monitor.onMainFrameRequest("https://www.tracker.example/")
        assertNull(monitor.onSubresource(Uri.parse("https://cdn.tracker.example/app.js")))
        assertNull(monitor.onSubresource(Uri.parse("https://images.org/a.png")))
        assertEquals(0, monitor.status.value.blockedTrackerCount)
    }

    @Test
    fun `an excepted site blocks nothing`() = runTest {
        test.exceptions.add("news.com")
        monitor.onMainFrameRequest("https://www.news.com/")

        assertNull(monitor.onSubresource(Uri.parse("https://tracker.example/p.gif")))
        assertFalse(monitor.status.value.protectionOn)
        assertEquals("news.com", monitor.status.value.site)
    }

    @Test
    fun `a new page resets the count but a redirect keeps it`() {
        monitor.onMainFrameRequest("https://news.com/")
        monitor.onSubresource(Uri.parse("https://ads.net/x"))
        assertEquals(1, monitor.status.value.blockedTrackerCount)

        monitor.onMainFrameRequest("https://t.org/r")
        monitor.onPageStarted("https://news.com/after-redirect")
        assertEquals(0, monitor.status.value.blockedTrackerCount)
        monitor.onSubresource(Uri.parse("https://ads.net/y"))
        assertEquals(1, monitor.status.value.blockedTrackerCount)
    }

    @Test
    fun `a history restore with no request starts a fresh count`() {
        monitor.onMainFrameRequest("https://news.com/")
        monitor.onPageStarted("https://news.com/")
        monitor.onSubresource(Uri.parse("https://ads.net/x"))

        monitor.onPageStarted("https://other.org/")

        assertEquals(0, monitor.status.value.blockedTrackerCount)
    }

    @Test
    fun `nothing is blocked while the lists are unavailable`() {
        val cold = ProtectionTestEngine(loaded = false).engine.newMonitor(isIncognito = false)
        cold.onMainFrameRequest("https://news.com/")
        assertNull(cold.onSubresource(Uri.parse("https://tracker.example/p.gif")))
    }

    @Test
    fun `status reports insecure and non-web pages`() {
        monitor.onMainFrameRequest("http://old.org/")
        monitor.onPageStarted("http://old.org/")
        assertFalse(monitor.status.value.connectionSecure)
        assertTrue(monitor.status.value.isWebPage)

        monitor.onPageStarted("about:blank")
        assertFalse(monitor.status.value.isWebPage)
    }

    @Test
    fun `a failed pending upgrade falls back once and is remembered`() {
        monitor.beginUpgrade(NavigationPreparer.Prepared("https://old.org/p", upgradedFrom = "http://old.org/p"))

        assertNull("another host is not the pending attempt", monitor.onMainFrameFailure("https://else.org/"))
        assertEquals("http://old.org/p", monitor.onMainFrameFailure("https://old.org/p"))
        assertTrue("old.org" in test.failures.snapshot())
        assertNull("only once", monitor.onMainFrameFailure("https://old.org/p"))
    }

    @Test
    fun `the http fallback waits for the error page to finish and is handed out once`() {
        // Regression (Galaxy A55, 2 of 6 runs): a load started from the error callback, even
        // posted, could be replaced by the error page's commit.
        monitor.beginUpgrade(NavigationPreparer.Prepared("https://old.org/x", upgradedFrom = "http://old.org/x"))
        assertNull("nothing due before a failure", monitor.takeFallbackDue())

        monitor.onMainFrameFailure("https://old.org/x")

        assertEquals("http://old.org/x", monitor.takeFallbackDue())
        assertNull("handed out once", monitor.takeFallbackDue())
    }

    @Test
    fun `a redirect back to http is detected`() {
        monitor.beginUpgrade(NavigationPreparer.Prepared("https://old.org/", upgradedFrom = "http://old.org/"))

        assertFalse(monitor.onRedirect("https://old.org/home"))
        assertTrue(monitor.onRedirect("http://old.org/"))
        assertTrue("old.org" in test.failures.snapshot())
    }

    @Test
    fun `finishing the upgraded page clears the pending attempt`() {
        monitor.beginUpgrade(NavigationPreparer.Prepared("https://new.org/", upgradedFrom = "http://new.org/"))
        monitor.onPageStarted("https://new.org/")
        monitor.onPageFinished("https://new.org/")

        assertNull(monitor.onMainFrameFailure("https://new.org/"))
    }

    @Test
    fun `a cancelled load that never started does not end the attempt`() {
        // Regression (16 KB AVD, neverssl.com): two loads in a row; the first, cancelled, still
        // reports onPageFinished before the real attempt fails.
        monitor.beginUpgrade(NavigationPreparer.Prepared("https://old.org/", upgradedFrom = "http://old.org/"))
        monitor.onPageFinished("https://old.org/")
        monitor.onPageStarted("https://old.org/")

        assertEquals("http://old.org/", monitor.onMainFrameFailure("https://old.org/"))
    }
}
