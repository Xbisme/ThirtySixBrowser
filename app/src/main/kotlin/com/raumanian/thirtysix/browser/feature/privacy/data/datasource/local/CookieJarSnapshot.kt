package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

/**
 * The cookie jar as it was when the first incognito tab opened, restored when the last one closes.
 * The `getCookie` → `setCookie` round-trip loses cookie attributes (`Domain`, `Path`, `Expires`,
 * `Secure`, `HttpOnly`, `SameSite`): restored cookies are session cookies with `Path=/`.
 *
 * @property capturedAt Epoch millis, for diagnostic logs only.
 * @property entries Origin → the `Cookie:` header `CookieManager.getCookie(origin)` returned.
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
