package com.raumanian.thirtysix.browser.core.constants

/**
 * App-wide constants stub for Spec 001.
 *
 * Future specs extend the `core/constants/` namespace via separate files
 * (UrlConstants, StorageKeys, BrowserLimits, ...) per CLAUDE.md project structure.
 */
object AppConstants {
    const val APP_NAME = "ThirtySix Browser"

    // Spec 006 — DataStore Preferences file name (extension `.preferences_pb` is
    // appended automatically by `Context.preferencesDataStoreFile(name)`).
    // Single source of truth per Constitution §III FR-016.
    const val SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"

    // Spec 011 (favicon amendment 2026-05-03) — sub-directory under
    // `Context.cacheDir` where favicon bitmaps are persisted as PNG files
    // keyed by SHA-1 of the URL hostname. Disk-cached because favicons
    // rarely change per origin; reusing across sessions saves a re-fetch.
    const val FAVICON_CACHE_DIR_NAME = "favicons"

    // Spec 011 (Q4 screenshot amendment 2026-05-03) — sub-directory under
    // `Context.cacheDir` where WebView screenshot bitmaps are persisted as
    // WebP files keyed by SHA-1 of the full URL. URL-keyed (not hostname-
    // keyed like favicons) because screenshots are page-specific —
    // different URLs on the same host show different content.
    const val SCREENSHOT_CACHE_DIR_NAME = "screenshots"

    // Spec 011 (Q4 screenshot amendment 2026-05-03) — target dimensions
    // for the captured WebView screenshot. 16:9 ratio matches the tab
    // card preview area. 480×270 is large enough to render crisp at
    // typical card sizes (~160–240dp wide) without bloating disk usage.
    const val SCREENSHOT_TARGET_WIDTH_PX = 480
    const val SCREENSHOT_TARGET_HEIGHT_PX = 270
}
