package com.raumanian.thirtysix.browser.core.domain.model

/** A tab's saved back and forward, as the web engine wrote it; the app never reads inside [state]. */
class TabSession(
    val tabId: Long,
    val state: ByteArray,
    val savedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is TabSession && other.tabId == tabId && other.savedAt == savedAt

    override fun hashCode(): Int = 31 * tabId.hashCode() + savedAt.hashCode()
}
