package com.raumanian.thirtysix.browser.core.domain.repository

/** Seam over handing a file to another app, keeping `Intent` out of the domain. */
interface ExternalFileOpener {

    /** False when no app can open it or the platform refused; `ActivityNotFoundException` never escapes. */
    fun open(contentUri: String, mimeType: String): Boolean
}
