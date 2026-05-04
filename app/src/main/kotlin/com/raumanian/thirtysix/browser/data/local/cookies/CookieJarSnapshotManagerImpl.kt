package com.raumanian.thirtysix.browser.data.local.cookies

import android.util.Log
import android.webkit.CookieManager
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.repository.CookieJarSnapshotManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Spec 012 — public-API implementation of [CookieJarSnapshotManager].
 *
 * Strategy (research.md R1): per-origin `getCookie` / `setCookie` round-trip
 * over the public `android.webkit.CookieManager` API. Crash-safety is the
 * top-priority constraint per the user's brief — every platform call is
 * wrapped in `runCatching { … }` so the manager NEVER propagates exceptions.
 * Failures degrade to "cookies wiped" (privacy-stronger fallback).
 *
 * Concurrency:
 *  - Internal [Mutex] serializes capture + restore operations even if
 *    [IncognitoTabRepositoryImpl] (the primary caller) somehow lets two
 *    callers race. Defence-in-depth.
 *  - All platform calls hop to [DispatcherProvider.io] so the Main thread
 *    is never blocked on disk-flushing cookies.
 *
 * Bounded memory: snapshot ≤ `MAX_TABS = 50` origins × ~2 KB per cookie
 * header ≈ 100 KB worst case. Released entirely on restore.
 *
 * Process death: snapshot is held in memory only. Process termination loses
 * the snapshot — which is correct, because incognito tabs are also lost on
 * process death (FR-012), so there's no live incognito session that needs
 * its cookies preserved.
 */
@Singleton
class CookieJarSnapshotManagerImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : CookieJarSnapshotManager {

    private val mutex: Mutex = Mutex()

    @Volatile
    private var snapshot: CookieJarSnapshot? = null

    override suspend fun captureSnapshot(origins: List<String>) =
        withContext(dispatchers.io) {
            mutex.withLock {
                // Idempotency: if a snapshot already exists, do NOT overwrite.
                // Guarantees a 0→1→2→1 incognito-tab sequence does not
                // re-capture mid-session (FR-011a step 1).
                if (snapshot != null) return@withLock
                val cookieManager = runCatching { CookieManager.getInstance() }
                    .getOrNull()
                if (cookieManager == null) {
                    snapshot = CookieJarSnapshot.EMPTY
                    return@withLock
                }
                runCatching { cookieManager.flush() }
                val captured = origins
                    .distinct()
                    .associateWith { origin ->
                        runCatching { cookieManager.getCookie(origin).orEmpty() }
                            .getOrElse { e ->
                                Log.w(LOG_TAG, "getCookie failed for origin=$origin", e)
                                ""
                            }
                    }
                    .filterValues { it.isNotEmpty() }
                snapshot = CookieJarSnapshot(
                    capturedAt = System.currentTimeMillis(),
                    entries = captured,
                )
            }
        }

    override suspend fun restoreSnapshot() =
        withContext(dispatchers.io) {
            mutex.withLock {
                val held = snapshot ?: return@withLock
                val cookieManager = runCatching { CookieManager.getInstance() }
                    .getOrNull()
                if (cookieManager == null) {
                    snapshot = null
                    return@withLock
                }
                // Step 1: wipe live cookie jar (incl. any cookies set during
                // incognito session). Suspending wrapper around the
                // callback-based API.
                runCatching { removeAllCookiesAwait(cookieManager) }
                runCatching { cookieManager.flush() }
                // Step 2: write snapshot entries back. NB cookie attributes
                // (Domain/Path/Expires/Secure/HttpOnly/SameSite) are NOT
                // preserved by `getCookie → setCookie` round-trip; restored
                // cookies revert to default attributes (research.md R1
                // documented limitation).
                held.entries.forEach { (origin, header) ->
                    header.split(COOKIE_PAIR_DELIMITER).forEach { pair ->
                        val trimmed = pair.trim()
                        if (trimmed.isEmpty()) return@forEach
                        runCatching { cookieManager.setCookie(origin, trimmed) }
                            .onFailure { e ->
                                Log.w(LOG_TAG, "setCookie failed origin=$origin pair=$trimmed", e)
                            }
                    }
                }
                runCatching { cookieManager.flush() }
                snapshot = null
            }
        }

    override fun hasSnapshot(): Boolean = snapshot != null

    /**
     * Suspending wrapper around [CookieManager.removeAllCookies] callback API.
     *
     * Platform requirement (Android API 29 / older WebView builds): the
     * callback variant of `removeAllCookies` MUST be invoked on a thread that
     * carries a `Looper` — otherwise [AwCookieManager] throws
     * `IllegalStateException: removeAllCookies must be called on a thread with
     * a running Looper.` The IO dispatcher does NOT carry a Looper, so we hop
     * onto [Dispatchers.Main] (the immediate variant when already on Main, to
     * minimise dispatch overhead) for the platform call. Newer Android
     * versions are more permissive but the explicit hop is harmless and
     * portable across all supported API levels (24 .. 36).
     */
    private suspend fun removeAllCookiesAwait(cookieManager: CookieManager) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                cookieManager.removeAllCookies {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    private companion object {
        const val LOG_TAG: String = "CookieSnapshot"
        const val COOKIE_PAIR_DELIMITER: String = ";"
    }
}
