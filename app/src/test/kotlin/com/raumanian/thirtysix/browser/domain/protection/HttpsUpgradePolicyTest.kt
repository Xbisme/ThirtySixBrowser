package com.raumanian.thirtysix.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpsUpgradePolicyTest {

    @Test
    fun `keeps port path query and fragment`() {
        assertEquals(
            "https://example.org:8080/p/q?x=1#f",
            HttpsUpgradePolicy.upgrade("http://example.org:8080/p/q?x=1#f", emptySet()),
        )
    }

    @Test
    fun `scheme comparison ignores case`() {
        assertEquals("https://example.org/", HttpsUpgradePolicy.upgrade("HTTP://example.org/", emptySet()))
    }

    @Test
    fun `local and literal hosts are not upgraded`() {
        listOf(
            "http://localhost:8023/",
            "http://192.168.1.1/",
            "http://[::1]:80/",
            "http://router/",
        ).forEach { assertNull(it, HttpsUpgradePolicy.upgrade(it, emptySet())) }
    }

    @Test
    fun `failed hosts are not upgraded again`() {
        assertNull(HttpsUpgradePolicy.upgrade("http://old.example.org/", setOf("old.example.org")))
    }

    @Test
    fun `non-http addresses are not upgraded`() {
        assertNull(HttpsUpgradePolicy.upgrade("https://example.org/", emptySet()))
        assertNull(HttpsUpgradePolicy.upgrade("ftp://example.org/", emptySet()))
        assertNull(HttpsUpgradePolicy.upgrade("example.org", emptySet()))
    }

    @Test
    fun `host extraction handles user info and case`() {
        assertEquals("example.org", HttpsUpgradePolicy.hostOf("http://user:pw@Example.ORG:81/x"))
    }
}
