package com.raumanian.thirtysix.browser.core.domain.repository

import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Disk cache of site icons keyed by SHA-1 of the host: icons are origin-scoped, and the hash keeps
 * file names safe and bounded. Lookups are synchronous so composables can call [fileFor] in
 * `remember`. No eviction: icon files are small and bounded by the hosts visited.
 */
interface FaviconCache {
    /** Bumps on every [save], so composables re-read after a new icon arrives. */
    val version: StateFlow<Long>

    /** Accepts a full URL or a host; overwrites any existing icon. */
    suspend fun save(url: String, writeImage: (OutputStream) -> Unit)

    /** At most a stat() call, so safe in composition. */
    fun fileFor(url: String): File?

    /**
     * Runs on IO and bumps [version] so icons fall back to placeholders. An undeletable file is
     * logged and skipped; only a thrown exception signals failure.
     */
    suspend fun clearAll()

    /** Like [clearAll], but keeps the icons of [hosts] (bookmarked and fireproof sites). */
    suspend fun retainOnly(hosts: Set<String>)
}
