package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Spec 015 FR-004 / FR-005 — turn whatever the server offered into a filename that is
 * safe to write.
 *
 * Two stages, deliberately separate:
 *  1. **Derive** a candidate — the `Content-Disposition` filename, else the address's last
 *     path segment, else a generated fallback (FR-004).
 *  2. **Sanitise unconditionally** (FR-005). Stage 2 does not trust stage 1, whichever
 *     branch produced the value. Every candidate is attacker-influenced: a server controls
 *     the header outright and largely controls the URL.
 *
 * **Why pure Kotlin rather than the platform's filename helper.** research.md R8 proposed
 * deriving with the platform helper and sanitising afterwards, noting the helper "is not a
 * security boundary". Deriving here instead keeps this use case free of Android imports,
 * which matters twice over: Constitution §IV requires a pure domain layer, and SC-015
 * demands a six-case hostile-input proof that should run on the plain JVM rather than
 * behind a device. The header grammar involved is small and stable, so the platform helper
 * was buying convenience, not correctness.
 */
class SanitizeDownloadFileNameUseCase @Inject constructor() {

    /**
     * @param contentDisposition the raw header value, or null when absent.
     * @param url the address being downloaded.
     * @return a plain single-segment filename: never blank, never containing a path
     *   separator, never a parent-directory reference.
     */
    operator fun invoke(contentDisposition: String?, url: String): String {
        val candidate = deriveFromDisposition(contentDisposition)
            ?: deriveFromUrl(url)
            ?: FALLBACK_NAME
        return sanitize(candidate)
    }

    /**
     * RFC 5987's `filename*=UTF-8''name` wins over the plain form when both appear, which
     * is what the RFC requires and what servers assume. Evaluated lazily so a malformed
     * extended form still falls through to the quoted one.
     */
    private fun deriveFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val candidates = sequenceOf(
            { EXTENDED_FILENAME.find(header)?.groupValues?.get(1)?.let(::decodePercent) },
            { QUOTED_FILENAME.find(header)?.groupValues?.get(1) },
            { BARE_FILENAME.find(header)?.groupValues?.get(1)?.trim() },
        )
        return candidates.mapNotNull { it() }.firstOrNull { it.isNotBlank() }
    }

    /**
     * The address contributes a name only when it actually has a path. Without this guard
     * `https://example.com` would yield `example.com`, naming the file after the host —
     * legal, but misleading enough that the generated fallback is the better answer.
     */
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
     * Reduce any candidate to a single, harmless path component.
     *
     * Order matters: separators are stripped *before* the dot handling, so a value like
     * `../../etc/passwd` collapses to `passwd` rather than being rejected outright. A user
     * who asked for a download should get their file whenever a safe name is derivable,
     * not an error message.
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

    /**
     * Bound the length while keeping the extension, so a truncated name still opens with
     * the right app. The cap matches the per-component limit of every filesystem Android
     * ships on, so the sanitiser never emits a name the platform will reject.
     */
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
