package com.raumanian.thirtysix.browser.core.domain.repository

import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Spec 011 favicon amendment (2026-05-03) — disk-backed cache of website
 * favicons keyed by SHA-1 of the URL hostname.
 *
 * Lookups are synchronous (file-existence check) so Composables can call
 * [fileFor] inside `remember(...)` without spinning a coroutine. Writes are
 * suspending and run on `dispatchers.io`. Hostname-keyed (not full-URL-keyed)
 * because favicons are origin-scoped — every page on `example.com` shares
 * the same favicon. SHA-1 keeps file names safe characters and bounded length.
 *
 * Interface + impl split (vs. a concrete class) so JVM unit tests can wire
 * a [TempDirFaviconCache] over a `@TempFolder` without needing Android
 * `Context` / Robolectric.
 *
 * NO Room schema migration — favicons live entirely on disk; the
 * `TabEntity` schema (Spec 005) is unchanged. NO third-party dependency —
 * uses [Bitmap.compress] for write + (consumer-side)
 * [android.graphics.BitmapFactory] for read. NO LRU eviction in v1.0 —
 * favicon files are tiny (~1–10 KB each) and bounded by the number of
 * unique hosts the user visits; explicit eviction can be added later if
 * disk usage becomes a concern.
 */
interface FaviconCache {
    /**
     * Monotonically-increasing counter that bumps on every successful
     * [save]. Composables collect this to re-trigger composition after a
     * new favicon arrives.
     */
    val version: StateFlow<Long>

    /**
     * Persist [bitmap] as the favicon for the host derived from [url].
     * Caller supplies the URL string (full URL or just hostname); the
     * implementation extracts the hostname internally. Idempotent;
     * subsequent calls overwrite the existing file.
     */
    suspend fun save(url: String, writeImage: (OutputStream) -> Unit)

    /**
     * Synchronous lookup: returns the [File] if a favicon for [url]'s host
     * is cached, else `null`. Composable-safe — at most a stat() syscall.
     */
    fun fileFor(url: String): File?

    /**
     * Spec 016 FR-029 — delete every cached site icon.
     *
     * Contract (mirrors `ScreenshotCache.clearAll`):
     *  - Runs on the IO dispatcher. Idempotent; a missing directory is not an error.
     *  - Bumps [version] afterwards, so every composable reading an icon re-evaluates and
     *    falls back to its placeholder until the icon is fetched again.
     *  - A file that cannot be deleted is logged and skipped rather than aborting the rest;
     *    the call returns normally, and the caller treats a thrown exception — not a partial
     *    deletion — as the category's failure signal.
     */
    suspend fun clearAll()

    /**
     * Spec 024 FR-008 — delete every cached site icon except those of [hosts] (bookmarked hosts and
     * hosts of fireproof sites). Same contract as [clearAll]: IO dispatcher, idempotent, a file that
     * cannot be deleted is skipped, and [version] is bumped afterwards.
     */
    suspend fun retainOnly(hosts: Set<String>)
}
