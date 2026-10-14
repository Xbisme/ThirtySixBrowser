package com.raumanian.thirtysix.browser.core.domain.util

import com.raumanian.thirtysix.browser.core.domain.model.BookmarkException
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkUrlValidationError
import java.net.URI
import java.net.URISyntaxException

/**
 * Validates a typed bookmark address: trims it, adds `https://` when no scheme was typed,
 * and accepts only http(s) URLs with a host. Returns the normalized URL.
 */
object BookmarkUrlValidator {

    private const val HTTPS_PREFIX: String = "https://"

    // A typed scheme is checked before `https://` is prepended, so `ftp://x` is rejected, not wrapped.
    private val SCHEME_REGEX: Regex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

    @Suppress("ReturnCount") // Each return is a distinct validation failure.
    fun validate(input: String): Result<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.Error(BookmarkException.UrlValidation(BookmarkUrlValidationError.Empty))
        }

        val schemeMatch = SCHEME_REGEX.find(trimmed)
        val candidate: String = if (schemeMatch != null) {
            val explicitScheme = schemeMatch.groupValues[1].lowercase()
            if (explicitScheme != "http" && explicitScheme != "https") {
                return Result.Error(
                    BookmarkException.UrlValidation(BookmarkUrlValidationError.InvalidScheme),
                )
            }
            trimmed
        } else {
            HTTPS_PREFIX + trimmed
        }

        val uri =
            try {
                URI(candidate)
            } catch (_: URISyntaxException) {
                return Result.Error(
                    BookmarkException.UrlValidation(BookmarkUrlValidationError.MissingHost),
                )
            } catch (_: IllegalArgumentException) {
                return Result.Error(
                    BookmarkException.UrlValidation(BookmarkUrlValidationError.MissingHost),
                )
            }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.Error(
                BookmarkException.UrlValidation(BookmarkUrlValidationError.InvalidScheme),
            )
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return Result.Error(
                BookmarkException.UrlValidation(BookmarkUrlValidationError.MissingHost),
            )
        }

        return Result.Success(candidate)
    }
}
