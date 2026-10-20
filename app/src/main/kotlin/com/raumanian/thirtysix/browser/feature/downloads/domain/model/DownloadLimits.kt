package com.raumanian.thirtysix.browser.feature.downloads.domain.model

object DownloadLimits {
    // Below SQLite's 999 bound-argument limit on older platform versions.
    const val STATUS_QUERY_CHUNK: Int = 500
}
