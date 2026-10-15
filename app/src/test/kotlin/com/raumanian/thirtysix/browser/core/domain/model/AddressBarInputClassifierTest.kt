package com.raumanian.thirtysix.browser.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** URL/query heuristic, scheme prepend, trimming, newline stripping and the accepted trade-offs. */
class AddressBarInputClassifierTest {

    // URL classification

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
        assertEquals(
            AddressBarSubmitResult.Url("https://developer.android.com"),
            classifyAddressBarInput("developer.android.com\n"),
        )
    }

    @Test
    fun `package-name-shaped input is classified as URL false-positive`() {
        // Accepted trade-off: any input with a dot is a URL, though "kotlin.coroutines" may be a query.
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

    // Empty / whitespace

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

    // Query classification

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
        // `localhost` has no dot, so it is a query, not a URL.

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
