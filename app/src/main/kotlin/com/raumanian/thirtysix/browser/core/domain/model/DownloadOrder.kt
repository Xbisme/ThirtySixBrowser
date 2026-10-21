package com.raumanian.thirtysix.browser.core.domain.model

data class DownloadOrder(
    val sourceUrl: String,
    /**
     * Already sanitised to a single path segment; this type is where a server-supplied name starts
     * Being trusted.
     */
    val fileName: String,
    /** Empty when nothing trustworthy was available. */
    val mimeType: String,
    /** The web engine's, so the transfer uses the same identity as the request. */
    val userAgent: String,
    val referer: String?,
    /** Never logged or stored. */
    val cookies: String?,
)
