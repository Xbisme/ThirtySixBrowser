package com.raumanian.thirtysix.browser.presentation.browser.protection

import android.os.Handler
import android.os.Looper
import com.raumanian.thirtysix.browser.core.constants.NetworkTimeouts
import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.protection.NavigationPreparer
import com.raumanian.thirtysix.browser.domain.protection.ProtectionMatchersHolder
import com.raumanian.thirtysix.browser.domain.usecase.IsSiteProtectedUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Spec 023 — the browser screen's single entry point to the protections: it builds one
 * [PageProtectionMonitor] per WebView and prepares every address the app loads (research R7).
 */
@Singleton
class ProtectionEngine @Inject constructor(
    internal val matchers: ProtectionMatchersHolder,
    internal val isSiteProtected: IsSiteProtectedUseCase,
    internal val failures: HttpsUpgradeFailures,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mainThread = Handler(Looper.getMainLooper())

    fun newMonitor(isIncognito: Boolean): PageProtectionMonitor = PageProtectionMonitor(this, isIncognito)

    /** Main thread. `null` while the lists are still loading. */
    fun prepareNow(url: String, isIncognito: Boolean): NavigationPreparer.Prepared? =
        matchers.matchersOrNull()?.preparer?.prepare(url, isIncognito)

    /**
     * Main thread. Prepares [url] and hands the result to [load] on the main thread — at once when
     * the lists are ready, otherwise after waiting for them on an IO thread (a cold start straight
     * into a link). If they never arrive the address is loaded as given (fail open).
     */
    fun prepare(url: String, isIncognito: Boolean, load: (NavigationPreparer.Prepared) -> Unit) {
        prepareNow(url, isIncognito)?.let {
            load(it)
            return
        }
        scope.launch {
            val prepared = matchers.awaitMatchers(NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS)
                ?.preparer?.prepare(url, isIncognito)
                ?: NavigationPreparer.Prepared(url, upgradedFrom = null)
            mainThread.post { load(prepared) }
        }
    }
}
