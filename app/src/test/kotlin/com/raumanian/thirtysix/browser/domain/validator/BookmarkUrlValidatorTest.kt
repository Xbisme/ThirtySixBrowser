package com.raumanian.thirtysix.browser.domain.validator

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.error.BookmarkUrlValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkUrlValidatorTest {

    @Test
    fun `https url passes validation unchanged`() {
        val result = BookmarkUrlValidator.validate("https://example.com")
        assertEquals(Result.Success("https://example.com"), result)
    }

    @Test
    fun `http url passes validation unchanged`() {
        val result = BookmarkUrlValidator.validate("http://example.com")
        assertEquals(Result.Success("http://example.com"), result)
    }

    @Test
    fun `bare host gets https prepended`() {
        val result = BookmarkUrlValidator.validate("example.com")
        assertEquals(Result.Success("https://example.com"), result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        val result = BookmarkUrlValidator.validate("   example.com   ")
        assertEquals(Result.Success("https://example.com"), result)
    }

    @Test
    fun `empty input is rejected as Empty`() {
        val result = BookmarkUrlValidator.validate("")
        assertReason(result, BookmarkUrlValidationError.Empty)
    }

    @Test
    fun `whitespace-only input is rejected as Empty`() {
        val result = BookmarkUrlValidator.validate("   \t  ")
        assertReason(result, BookmarkUrlValidationError.Empty)
    }

    @Test
    fun `ftp scheme is rejected as InvalidScheme`() {
        val result = BookmarkUrlValidator.validate("ftp://example.com")
        assertReason(result, BookmarkUrlValidationError.InvalidScheme)
    }

    @Test
    fun `https without host is rejected as MissingHost`() {
        val result = BookmarkUrlValidator.validate("https://")
        assertReason(result, BookmarkUrlValidationError.MissingHost)
    }

    @Test
    fun `case-insensitive scheme prefix recognition`() {
        val result = BookmarkUrlValidator.validate("HTTPS://example.com/path")
        assertEquals(Result.Success("HTTPS://example.com/path"), result)
    }

    @Test
    fun `path with query string is preserved`() {
        val result = BookmarkUrlValidator.validate("https://example.com/path?q=test")
        assertEquals(Result.Success("https://example.com/path?q=test"), result)
    }

    private fun assertReason(result: Result<String>, expected: BookmarkUrlValidationError) {
        assertTrue("Expected Result.Error but got $result", result is Result.Error)
        val error = (result as Result.Error).throwable
        assertTrue(
            "Expected BookmarkException.UrlValidation but got $error",
            error is BookmarkException.UrlValidation,
        )
        assertEquals(expected, (error as BookmarkException.UrlValidation).reason)
    }
}
