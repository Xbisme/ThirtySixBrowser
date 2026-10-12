package com.raumanian.thirtysix.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 022 FR-033 — the job name is the title, else the hostname, else the address. */
class PrintJobNameTest {

    @Test
    fun `the title is used when there is one`() {
        assertEquals("Receipt", PrintJobName.of("  Receipt ", "https://shop.example/r/1"))
    }

    @Test
    fun `the hostname is used when the title is missing`() {
        assertEquals("shop.example", PrintJobName.of("", "https://shop.example/r/1"))
        assertEquals("shop.example", PrintJobName.of(null, "https://shop.example/r/1"))
    }

    @Test
    fun `the address itself is used when it has no hostname`() {
        assertEquals("not a url", PrintJobName.of(null, "not a url"))
    }
}
