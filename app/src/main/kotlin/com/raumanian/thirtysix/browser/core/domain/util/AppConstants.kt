package com.raumanian.thirtysix.browser.core.domain.util

object AppConstants {
    const val APP_NAME = "ThirtySix Browser"

    // `preferencesDataStoreFile` appends `.preferences_pb`.
    const val SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"

    const val FAVICON_CACHE_DIR_NAME = "favicons"

    const val SCREENSHOT_CACHE_DIR_NAME = "screenshots"

    // 16:9 like the tab card preview; crisp at card widths without bloating disk.
    const val SCREENSHOT_TARGET_WIDTH_PX = 480
    const val SCREENSHOT_TARGET_HEIGHT_PX = 270

    // ClipData labels identify the clip's origin in the system clipboard preview; not translated.
    const val CLIPBOARD_URL_LABEL = "ThirtySix Browser URL"
    const val CLIPBOARD_DOWNLOAD_LINK_LABEL = "ThirtySix Browser download link"

    // Injected rather than read from `Build`/`BuildConfig` so tests cover both branches on the JVM.
    const val QUALIFIER_SUPPORTS_DYNAMIC_COLOR = "supports_dynamic_color"
    const val QUALIFIER_APP_VERSION_NAME = "app_version_name"

    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    /** `cacheDir` sub-folder for photos/videos captured for an upload. */
    const val CAPTURE_CACHE_DIR = "capture"

    const val MIME_TEXT_PLAIN = "text/plain"
}
