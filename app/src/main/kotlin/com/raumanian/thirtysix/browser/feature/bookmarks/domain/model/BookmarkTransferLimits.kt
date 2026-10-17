package com.raumanian.thirtysix.browser.feature.bookmarks.domain.model

object BookmarkTransferLimits {
    const val MAX_IMPORT_FILE_BYTES: Long = 10L * 1024 * 1024

    /** Folders nested deeper than this below the import folder are flattened into their ancestor. */
    const val MAX_IMPORT_DEPTH: Int = 32

    const val SNIFF_CHARS: Int = 1024

    const val EXPORT_FILE_NAME_PATTERN: String = "thirtysix-bookmarks-%s.html"
}
