package com.raumanian.thirtysix.browser.presentation.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 009 (T010 + T019) — pure JVM unit tests for [classifyAddressBarInput].
 * Table-driven coverage of the URL/query heuristic, scheme prepend, trim,
 * newline strip, and explicitly-accepted v1 trade-offs.
 */
class AddressBarInputClassifierTest {

    // ----------------- URL classification (T010 — US1) -----------------

    @Test
    fun `url with https scheme stays as-is`() {
        assertEquals(
            AddressBarSubmitResult.Url("https://example.com"),
            classifyAddressBarInput("https://example.com"),
        )
    }

    @Test
    fun `url with http scheme stays as-is`() {
        assertEquals(
            AddressBarSubmitResult.Url("http://example.com"),
            classifyAddressBarInput("http://example.com"),
        )
    }

    @Test
    fun `bare host without scheme gets https prepended`() {
        assertEquals(
            AddressBarSubmitResult.Url("https://developer.android.com"),
            classifyAddressBarInput("developer.android.com"),
        )
    }

    @Test
    fun `uppercase scheme is preserved`() {
        assertEquals(
            AddressBarSubmitResult.Url("HTTPS://example.com"),
            classifyAddressBarInput("HTTPS://example.com"),
        )
    }

    @Test
    fun `IPv4 address is classified as URL with https prepended`() {
        assertEquals(
            AddressBarSubmitResult.Url("https://192.168.1.1"),
            classifyAddressBarInput("192.168.1.1"),
        )
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals(
            AddressBarSubmitResult.Url("https://developer.android.com"),
            classifyAddressBarInput("   developer.android.com   "),
        )
    }

    @Test
    fun `embedded newline is replaced with space then trimmed`() {
        // "developer.android.com\n" becomes "developer.android.com " after
        // newline replacement, then trimmed to "developer.android.com".
        assertEquals(
            AddressBarSubmitResult.Url("https://developer.android.com"),
            classifyAddressBarInput("developer.android.com\n"),
        )
    }

    @Test
    fun `package-name-shaped input is classified as URL false-positive`() {
        // Spec edge case — accepted v1 trade-off. The string contains a dot
        // so the heuristic classifies it as a URL even though a user typing
        // "kotlin.coroutines" might have meant a search query.
        assertEquals(
            AddressBarSubmitResult.Url("https://kotlin.coroutines"),
            classifyAddressBarInput("kotlin.coroutines"),
        )
    }

    @Test
    fun `url with path and query is classified intact`() {
        assertEquals(
            AddressBarSubmitResult.Url("https://developer.android.com/jetpack/compose?utm=test"),
            classifyAddressBarInput("https://developer.android.com/jetpack/compose?utm=test"),
        )
    }

    // ----------------- Empty / whitespace (T010 — US1) -----------------

    @Test
    fun `empty string is classified as Empty`() {
        assertEquals(AddressBarSubmitResult.Empty, classifyAddressBarInput(""))
    }

    @Test
    fun `whitespace-only string is classified as Empty after trim`() {
        assertEquals(AddressBarSubmitResult.Empty, classifyAddressBarInput("   "))
    }

    @Test
    fun `newline-only string is classified as Empty after newline-strip-and-trim`() {
        assertEquals(AddressBarSubmitResult.Empty, classifyAddressBarInput("\n\n"))
    }

    // ----------------- Query classification (T019 — US2) -----------------

    @Test
    fun `single-word non-dotted input is classified as Query`() {
        assertEquals(
            AddressBarSubmitResult.Query("hello"),
            classifyAddressBarInput("hello"),
        )
    }

    @Test
    fun `multi-word input is classified as Query preserving spaces`() {
        assertEquals(
            AddressBarSubmitResult.Query("kotlin coroutines"),
            classifyAddressBarInput("kotlin coroutines"),
        )
    }

    @Test
    fun `localhost without dot is classified as Query (accepted v1 trade-off)`() {
        // Spec edge case: `localhost` has no dot → query, not URL.
        assertEquals(
            AddressBarSubmitResult.Query("localhost"),
            classifyAddressBarInput("localhost"),
        )
    }

    @Test
    fun `query with leading and trailing whitespace is trimmed`() {
        assertEquals(
            AddressBarSubmitResult.Query("hello world"),
            classifyAddressBarInput("   hello world   "),
        )
    }

    @Test
    fun `query with embedded newline gets newline replaced with space`() {
        assertEquals(
            AddressBarSubmitResult.Query("hello world"),
            classifyAddressBarInput("hello\nworld"),
        )
    }

    @Test
    fun `query with special chars is classified as Query (encoding deferred to ViewModel)`() {
        assertEquals(
            AddressBarSubmitResult.Query("c++ vs rust"),
            classifyAddressBarInput("c++ vs rust"),
        )
    }
}
