package com.raumanian.thirtysix.browser.presentation.browser.page

import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Spec 021 T005 — queue order, staleness, merging and disposal (FR-034, FR-035, INV-3). */
class PageRequestQueueTest {

    private val answers = mutableListOf<String>()

    private fun permission(
        id: String,
        tab: Long = 1,
        gen: Long = 1,
        caps: Set<SiteCapability> = setOf(SiteCapability.Camera),
    ) =
        PageRequest.Permission(
            tabId = tab,
            generation = gen,
            origin = "https://a.org",
            capabilities = caps,
            isIncognito = false,
            onGrant = { answers += "$id:grant" },
            onDeny = { answers += "$id:deny" },
        )

    private fun leave(id: String, tab: Long = 1, gen: Long = 1) =
        PageRequest.LeavePage(
            tab,
            gen,
            "https://a.org",
            onLeave = { answers += "$id:leave" },
            onStay = { answers += "$id:stay" },
        )

    @Test
    fun `requests are shown one at a time in arrival order`() {
        val queue = PageRequestQueue()
        val first = leave("a")
        val second = leave("b")
        queue.enqueue(first)
        queue.enqueue(second)

        assertSame(first, queue.head.value)
        first.leave()
        queue.complete(first)
        assertSame(second, queue.head.value)
    }

    @Test
    fun `stale requests are cancelled once and removed`() {
        val queue = PageRequestQueue()
        val old = permission("old", gen = 1)
        val current = permission("cur", gen = 2, caps = setOf(SiteCapability.Location))
        queue.enqueue(old)
        queue.enqueue(current)

        queue.dropStale(tabId = 1, generation = 2)
        queue.dropStale(tabId = 1, generation = 2)

        assertEquals(listOf("old:deny"), answers)
        assertSame(current, queue.head.value)
    }

    @Test
    fun `another tab's requests are stale too`() {
        val queue = PageRequestQueue()
        queue.enqueue(leave("x", tab = 7))
        queue.dropStale(tabId = 1, generation = 1)
        assertEquals(listOf("x:stay"), answers)
        assertNull(queue.head.value)
    }

    @Test
    fun `an identical permission request joins the waiting one and gets the same answer`() {
        val queue = PageRequestQueue()
        val first = permission("a")
        queue.enqueue(first)
        queue.enqueue(permission("b"))

        first.grant()
        queue.complete(first)

        assertEquals(listOf("a:grant", "b:grant"), answers)
        assertNull(queue.head.value)
    }

    @Test
    fun `an answered request cannot be answered again`() {
        val request = permission("a")
        request.grant()
        request.deny()
        request.cancel()
        assertEquals(listOf("a:grant"), answers)
    }

    @Test
    fun `dispose cancels everything pending`() {
        val queue = PageRequestQueue()
        queue.enqueue(permission("a"))
        queue.enqueue(leave("b"))
        queue.dispose()
        assertEquals(listOf("a:deny", "b:stay"), answers)
        assertNull(queue.head.value)
    }
}
