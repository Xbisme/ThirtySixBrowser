package com.raumanian.thirtysix.browser.core.domain.repository

import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Page screenshots keyed by tab id, one file per tab, so closing a tab deletes its file and the
 * cache stays bounded by the tab cap. Lookups are synchronous so composables can call [fileFor]
 * in `remember`. WebP keeps files about five times smaller than PNG.
 */
interface ScreenshotCache {
    /** Bumps on every successful [save] / [delete] / [clearAll]. */
    val version: StateFlow<Long>

    /** Overwrites the tab's previous screenshot. */
    suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit)

    fun fileFor(tabId: Long): File?

    /** Bumps [version] so a visible card falls back to its placeholder. */
    suspend fun delete(tabId: Long)

    suspend fun clearAll()
}
