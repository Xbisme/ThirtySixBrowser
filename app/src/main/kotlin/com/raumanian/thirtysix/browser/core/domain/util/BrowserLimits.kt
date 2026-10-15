package com.raumanian.thirtysix.browser.core.domain.util

object BrowserLimits {
    const val MAX_TABS: Int = 50

    // Incognito tabs have their own ceiling and do not count against [MAX_TABS].
    const val MAX_INCOGNITO_TABS: Int = 50

    const val MAX_BOOKMARK_TITLE_LENGTH: Int = 200

    const val MAX_BOOKMARK_URL_LENGTH: Int = 2048

    const val MAX_FOLDER_NAME_LENGTH: Int = 100

    const val MAX_BOOKMARKS: Int = 10_000

    const val MAX_FOLDERS: Int = 1_000

    const val SEARCH_MIN_CHARS: Int = 2

    const val MAX_HISTORY_QUERY_LENGTH: Int = 200

    // No "keep forever": the history list loads the whole table (~300 B/row), so its size must stay bounded.
    const val HISTORY_RETENTION_DAYS_7: Int = 7

    const val HISTORY_RETENTION_DAYS_30: Int = 30

    const val HISTORY_RETENTION_DAYS_90: Int = 90

    const val HISTORY_RETENTION_DAYS_180: Int = 180

    // The clear dialog cannot be dismissed, so an engine callback that never arrives must not trap the user.
    const val WEB_DATA_CLEAR_TIMEOUT_MS: Long = 15_000L

    const val LANGUAGE_APPLY_TIMEOUT_MS: Long = 600L

    // The name comes from an untrusted Content-Disposition header; 255 is every Android filesystem's limit.
    const val MAX_DOWNLOAD_FILENAME_LENGTH: Int = 255

    // Progress must visibly advance once a second; polling runs only while something is in flight.
    const val DOWNLOAD_STATUS_POLL_INTERVAL_MS: Long = 1_000L

    const val HOME_SHORTCUT_LIMIT: Int = 8

    const val HOME_RECENT_LIMIT: Int = 5

    const val TAB_SWIPE_CLOSE_FRACTION: Float = 0.5f

    // Incoming text is cut to this length; a longer address is refused, since a cut URL is a different page.
    const val MAX_INCOMING_TEXT_LENGTH: Int = 2_048

    const val INCOMING_DUPLICATE_WINDOW_MS: Long = 2_000L

    const val DEFAULT_BROWSER_INVITE_MIN_LAUNCHES: Int = 3

    const val DIALOG_SUPPRESS_FROM_COUNT: Int = 2

    const val BLOCKED_POPUP_ADDRESS_WAIT_MS: Long = 1_000L

    const val TEXT_SIZE_PERCENT_50: Int = 50

    const val TEXT_SIZE_PERCENT_75: Int = 75

    const val TEXT_SIZE_PERCENT_90: Int = 90

    const val TEXT_SIZE_PERCENT_100: Int = 100

    const val TEXT_SIZE_PERCENT_110: Int = 110

    const val TEXT_SIZE_PERCENT_125: Int = 125

    const val TEXT_SIZE_PERCENT_150: Int = 150

    const val TEXT_SIZE_PERCENT_175: Int = 175

    const val TEXT_SIZE_PERCENT_200: Int = 200

    const val MAX_ADDRESS_SUGGESTIONS: Int = 6

    const val MAX_FIND_QUERY_LENGTH: Int = 200

    const val REGISTRABLE_DOMAIN_CACHE_SIZE: Int = 2_048

    const val FIRE_OVERLAY_MS: Int = 900

    const val FIRE_OVERLAY_MIN_MS: Long = 600L

    const val SITE_DATA_DELETE_TIMEOUT_MS: Long = 5_000L

    const val FIRE_SECRET_BYTES: Int = 16
}
