package com.raumanian.thirtysix.browser.domain.validator

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.error.BookmarkException
import com.raumanian.thirtysix.browser.domain.error.BookmarkUrlValidationError
import java.net.URI
import java.net.URISyntaxException

/**
 * Spec 013 R5 — pure-Kotlin validator used by manual-add / edit flows
 * (FR-006 / FR-022). Lives in `domain/validator/` so it is callable from
 * use cases without crossing into the data layer.
 *
 * Algorithm:
 * 1. Trim leading / trailing whitespace.
 * 2. If the trimmed value does not start with `http://` / `https://`
 *    (case-insensitive), prepend `https://`.
 * 3. Parse via `java.net.URI` (JDK only — no Android imports).
 * 4. Reject when host is null/blank or scheme is not http(s).
 *
 * Returns the normalized URL on success.
 */
object BookmarkUrlValidator {

    private const val HTTPS_PREFIX: String = "https://"

    /**
     * Matches a URI scheme prefix (`scheme://`). Used to detect whether the user
     * already typed a scheme so we can either accept it (http / https) or reject
     * it (anything else) BEFORE auto-prepending `https://`.
     */
    private val SCHEME_REGEX: Regex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")

    @Suppress("ReturnCount") // Each return models a distinct validation guard branch (see KDoc).
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
