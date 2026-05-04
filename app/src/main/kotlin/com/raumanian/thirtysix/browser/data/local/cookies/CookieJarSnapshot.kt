package com.raumanian.thirtysix.browser.data.local.cookies

/**
 * Spec 012 — in-memory snapshot of the platform cookie jar at the moment
 * the first incognito tab opens.
 *
 * Restored when the last incognito tab closes (per FR-011a).
 *
 * @property capturedAt Epoch millis at capture time. For diagnostic
 *                      logs only — never compared, never used for
 *                      invalidation.
 * @property entries Map of `origin → "name=value; name2=value2 …"`
 *                   header. Keys are origins (e.g., `https://example.com`),
 *                   values are the concatenated `Cookie:` header that
 *                   `CookieManager.getCookie(origin)` returned.
 *
 * **Documented limitation** (research.md R1): cookie attributes
 * (`Domain`, `Path`, `Expires`, `Secure`, `HttpOnly`, `SameSite`) are NOT
 * preserved by the `getCookie → setCookie` round-trip — restored cookies
 * revert to default attributes (session-scoped, `Path=/`, no explicit
 * `Domain`).
 */
data class CookieJarSnapshot(
    val capturedAt: Long,
    val entries: Map<String, String>,
) {
    companion object {
        /** Empty snapshot — used as a marker for "captured nothing." */
        val EMPTY: CookieJarSnapshot = CookieJarSnapshot(
            capturedAt = 0L,
            entries = emptyMap(),
        )
    }
}
