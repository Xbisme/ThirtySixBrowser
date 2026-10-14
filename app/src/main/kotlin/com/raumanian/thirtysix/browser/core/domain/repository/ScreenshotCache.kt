package com.raumanian.thirtysix.browser.core.domain.repository

import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Spec 011 Q4 amendment (2026-05-03) — disk-backed cache of WebView page
 * screenshots keyed by **tab id** (not URL).
 *
 * Tab-id-keyed (vs the URL-keyed first draft) for predictable lifecycle:
 *  - Exactly ONE screenshot file per tab (the most recent capture).
 *  - Bounded by `BrowserLimits.MAX_TABS` × ~5 KB ≈ 250 KB disk worst-case.
 *  - On tab close → [delete] removes the file deterministically (no
 *    URL-tracking bookkeeping needed).
 *  - On `closeAllTabs` → [clearAll] wipes the directory.
 *  - When the user navigates within a tab, the new screenshot overwrites
 *    the old one for that same tab id.
 *
 * Lookups are synchronous (file-existence check) so Composables can call
 * [fileFor] inside `remember(...)` without spinning a coroutine. Writes are
 * suspending and run on `dispatchers.io`. Storage uses WebP for ~5× smaller
 * files than PNG at acceptable quality for the tab card preview.
 */
interface ScreenshotCache {
    /** Bumps on every successful [save] / [delete] / [clearAll]. */
    val version: StateFlow<Long>

    /** Persist [bitmap] for [tabId]. Idempotent; overwrites the prior file. */
    suspend fun save(tabId: Long, writeImage: (OutputStream) -> Unit)

    /** Synchronous lookup. Returns the cached file or `null`. */
    fun fileFor(tabId: Long): File?

    /**
     * Delete the cached screenshot for [tabId] (called from `closeTab`).
     * No-op if the file does not exist. Bumps [version] so any visible
     * card composable re-evaluates and falls back to the placeholder.
     */
    suspend fun delete(tabId: Long)

    /**
     * Wipe every cached screenshot (called from `closeAllTabs`). Idempotent.
     */
    suspend fun clearAll()
}
