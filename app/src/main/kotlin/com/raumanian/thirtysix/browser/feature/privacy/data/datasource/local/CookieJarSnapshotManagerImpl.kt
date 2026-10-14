package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.util.Log
import android.webkit.CookieManager
import com.raumanian.thirtysix.browser.core.domain.repository.CookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Per-origin cookie snapshot over the public `CookieManager` API. Every platform call is wrapped,
 * so failures degrade to "cookies wiped", the privacy-stronger outcome. Held in memory only:
 * incognito tabs die with the process too, so there is nothing to restore after process death.
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
                // A snapshot is never overwritten, so opening a second incognito tab does not re-capture.
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
                // Wipe the live jar, including everything set during the incognito session.
                runCatching { removeAllCookiesAwait(cookieManager) }
                runCatching { cookieManager.flush() }
                // Write back; attributes are lost in the round-trip (see CookieJarSnapshot).
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
     * Holds [CookieJarSnapshot.EMPTY], never `null`: with `null`, [restoreSnapshot] would skip its
     * wipe and leak incognito cookies into normal browsing. `EMPTY` also keeps capture a no-op.
     */
    override suspend fun discardSetAsideCookies() {
        mutex.withLock {
            if (snapshot != null) snapshot = CookieJarSnapshot.EMPTY
        }
    }

    // The callback form of removeAllCookies throws on a thread without a Looper (API 29, older
    // WebView), and the IO dispatcher has none, so the call hops to Main.
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
