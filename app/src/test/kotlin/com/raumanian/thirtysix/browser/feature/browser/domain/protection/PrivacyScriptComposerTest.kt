package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 024 T054 — the composed page-start script (research R5, Constitution §II-1). */
class PrivacyScriptComposerTest {

    @Test
    fun `each bundle is called with the same frozen settings, in order`() {
        val script = PrivacyScriptComposer.compose(
            secret = "0a1b",
            exceptedSites = setOf("shop.example", "a.example"),
            bundles = listOf("(function(cfg){A})", "/*! notice */\n(function(cfg){B})\n"),
        )

        val settings = "Object.freeze({\"v\":1,\"s\":\"0a1b\",\"x\":[\"a.example\",\"shop.example\"]})"
        assertEquals(
            "((function(cfg){A}))($settings);\n(/*! notice */\n(function(cfg){B}))($settings);",
            script,
        )
    }

    @Test
    fun `sites that are not plain host names are dropped and the rest lower-cased`() {
        val script = PrivacyScriptComposer.compose(
            secret = "ff",
            exceptedSites = setOf("Shop.Example", "evil\"});alert(1)//", "a b.test", "</script>", "ok-1.test"),
            bundles = listOf("(function(cfg){})"),
        )

        assertTrue(script.contains("\"x\":[\"ok-1.test\",\"shop.example\"]"))
        assertFalse(script.contains("alert"))
        assertFalse(script.contains("</script>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a secret that is not lower-case hex is rejected`() {
        PrivacyScriptComposer.compose(secret = "\"; alert(1); \"", exceptedSites = emptySet(), bundles = emptyList())
    }

    @Test
    fun `json strings escape quotes, backslashes, angle brackets and line separators`() {
        assertEquals("\"a\\\"b\\\\c\\u003c\\u003e\\u2028\\n\"", PrivacyScriptComposer.jsonString("a\"b\\c<> \n"))
    }

    @Test
    fun `the settings are never assigned to a global`() {
        val script = PrivacyScriptComposer.compose("01", setOf("a.test"), listOf("(function(cfg){})"))

        assertFalse(script.contains("window."))
        assertFalse(script.contains("var "))
    }
}
