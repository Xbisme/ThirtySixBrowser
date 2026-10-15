package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Turns what the server offered into a safe filename: derive (Content-Disposition, then the last
 * path segment, then a fallback), then always sanitise, since every candidate is attacker-influenced.
 * Pure Kotlin rather than the platform helper, so the hostile-name tests run on the JVM.
 */
class SanitizeDownloadFileNameUseCase @Inject constructor() {

    /** Returns a single-segment filename: never blank, no separator, no parent reference. */
    operator fun invoke(contentDisposition: String?, url: String): String {
        val candidate = deriveFromDisposition(contentDisposition)
            ?: deriveFromUrl(url)
            ?: FALLBACK_NAME
        return sanitize(candidate)
    }

    /** RFC 5987's `filename*=` wins over the plain form; lazily, so a malformed one falls through. */
    private fun deriveFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val candidates = sequenceOf(
            { EXTENDED_FILENAME.find(header)?.groupValues?.get(1)?.let(::decodePercent) },
            { QUOTED_FILENAME.find(header)?.groupValues?.get(1) },
            { BARE_FILENAME.find(header)?.groupValues?.get(1)?.trim() },
        )
        return candidates.mapNotNull { it() }.firstOrNull { it.isNotBlank() }
    }

    /** Only an address with a path names the file; `https://example.com` would name it after the host. */
    private fun deriveFromUrl(url: String): String? {
        val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
        if (!withoutScheme.contains('/')) return null
        return withoutScheme
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?.let { decodePercent(it) }
    }

    /**
     * Separators are stripped before dot handling, so `../../etc/passwd` becomes `passwd`
     * instead of an error: the person gets their file whenever a safe name exists.
     */
    private fun sanitize(raw: String): String {
        val lastSegment = raw
            .replace('\\', '/')
            .substringAfterLast('/')
            .filterNot { it.isISOControl() }
            .replace(RESERVED_CHARS, "_")
            .trim()

        val withoutLeadingDots = lastSegment.trimStart('.')
        val safe = if (withoutLeadingDots.isBlank()) FALLBACK_NAME else withoutLeadingDots
        return bound(safe)
    }

    /** Bounds the length but keeps the extension; the cap is every Android filesystem's limit. */
    private fun bound(name: String): String {
        val max = BrowserLimits.MAX_DOWNLOAD_FILENAME_LENGTH
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        return when {
            name.length <= max -> name
            extension.isEmpty() || extension.length >= max -> name.take(max)
            else -> name.substring(0, name.length - extension.length - 1)
                .take(max - extension.length - 1) + "." + extension
        }
    }

    private fun decodePercent(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    private companion object {
        const val FALLBACK_NAME = "download"

        /** Characters a filename must not carry, whatever the filesystem. */
        val RESERVED_CHARS = Regex("[<>:\"|?*\\x00-\\x1F]")
        val EXTENDED_FILENAME = Regex("filename\\*\\s*=\\s*[^']*'[^']*'([^;\\s]+)", RegexOption.IGNORE_CASE)
        val QUOTED_FILENAME = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        val BARE_FILENAME = Regex("filename\\s*=\\s*([^;\"]+)", RegexOption.IGNORE_CASE)
    }
}
