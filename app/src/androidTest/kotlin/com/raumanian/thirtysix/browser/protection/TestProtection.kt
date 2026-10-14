package com.raumanian.thirtysix.browser.protection

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.dispatcher.DefaultDispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.privacy.PrivacyScriptBundlesImpl
import com.raumanian.thirtysix.browser.data.local.privacy.PrivacyScriptSessionImpl
import com.raumanian.thirtysix.browser.data.local.protection.HttpsUpgradeFailureStore
import com.raumanian.thirtysix.browser.data.local.protection.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.domain.model.ProtectionData
import com.raumanian.thirtysix.browser.domain.model.PublicSuffixRules
import com.raumanian.thirtysix.browser.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.domain.model.TrackerHostSet
import com.raumanian.thirtysix.browser.domain.model.TrackingParamRules
import com.raumanian.thirtysix.browser.domain.protection.ProtectionMatchersHolder
import com.raumanian.thirtysix.browser.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import com.raumanian.thirtysix.browser.domain.usecase.IsSiteProtectedUseCase
import com.raumanian.thirtysix.browser.presentation.browser.BrowserNavigationCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.browser.BrowserWebView
import com.raumanian.thirtysix.browser.presentation.browser.BrowserWebViewCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.LoadingState
import com.raumanian.thirtysix.browser.presentation.browser.WebViewActionsHandle
import com.raumanian.thirtysix.browser.presentation.browser.privacy.PrivacyScriptInputs
import com.raumanian.thirtysix.browser.presentation.browser.protection.PageProtectionMonitor
import com.raumanian.thirtysix.browser.presentation.browser.protection.ProtectionEngine
import com.raumanian.thirtysix.browser.presentation.browser.protection.ProtectionHooks
import com.raumanian.thirtysix.browser.presentation.browser.protection.ScriptHooks
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Spec 023 — a real [ProtectionEngine] over a small in-memory list, for instrumented tests that
 * host [BrowserWebView] directly (Spec 022 pattern; no Hilt). The tracker list holds the 12
 * `tracker-NN.test` hosts of `trackers.html` plus `127.0.0.1`, the one tracker that resolves.
 */
internal class TestProtection(
    /** Spec 024 — install the real page-start scripts (cookie banners, fingerprinting). */
    withScripts: Boolean = false,
    /** Spec 024 — `false` keeps `127.0.0.1` off the tracker list, so cross-site frames load. */
    blockResolvableTracker: Boolean = true,
) {
    val exceptions = InMemoryExceptions()
    val incognito = IncognitoProtectionExceptionStore()
    val failures = HttpsUpgradeFailureStore()
    val monitor = AtomicReference<PageProtectionMonitor?>()
    val loadsFinished = AtomicInteger()
    val actions = WebViewActionsHandle()

    /** Spec 024 — the page title, which the fixtures use to report their results. */
    val title = AtomicReference("")

    private val data = ProtectionData(
        trackers = TrackerHostSet(if (blockResolvableTracker) TRACKER_HOSTS + RESOLVABLE_TRACKER else TRACKER_HOSTS),
        suffixes = PublicSuffixRules(normal = setOf("test", "com"), wildcard = emptySet(), exception = emptySet()),
        params = TrackingParamRules(names = setOf("fbclid", "gclid"), prefixes = listOf("utm_")),
    )

    private val dataRepository = object : ProtectionDataRepository {
        override fun preload() = Unit

        override fun currentDataOrNull(): ProtectionData = data

        override fun awaitData(timeoutMs: Long): ProtectionData = data
    }

    private val isSiteProtected = IsSiteProtectedUseCase(exceptions, incognito)

    val engine = ProtectionEngine(
        matchers = ProtectionMatchersHolder(dataRepository, isSiteProtected, failures),
        isSiteProtected = isSiteProtected,
        failures = failures,
        dispatchers = DefaultDispatcherProvider(),
    )

    /** Spec 024 — the real session and the app's bundled scripts. */
    val scriptSession = PrivacyScriptSessionImpl()

    val hooks = ProtectionHooks(
        engine = engine,
        attach = { monitor.set(it) },
        detach = { monitor.compareAndSet(it, null) },
        scripts = if (withScripts) {
            val bundles = PrivacyScriptBundlesImpl(
                InstrumentationRegistry.getInstrumentation().targetContext,
                DefaultDispatcherProvider(),
            ).also { it.preload() }
            ScriptHooks(PrivacyScriptInputs(scriptSession, bundles, exceptions, incognito), MainScope())
        } else {
            null
        },
    )

    @Composable
    fun Content(url: String, incognito: Boolean = false) {
        BrowserWebView(
            state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle, isIncognito = incognito) },
            actions = actions,
            callbacks = BrowserWebViewCallbacks(
                onLoadStarted = {},
                onProgressChanged = {},
                onLoadFinished = { loadsFinished.incrementAndGet() },
                onLoadFailed = {},
                onDownloadRequested = { _, _, _, _ -> },
            ),
            navigationCallbacks = BrowserNavigationCallbacks(
                onUrlChange = {},
                onCanGoBackChange = {},
                onCanGoForwardChange = {},
                onTitleChange = { title.set(it) },
                onIconReceived = { _, _ -> },
                onScreenshotReady = {},
            ),
            modifier = Modifier.fillMaxSize(),
            protection = hooks,
        )
    }

    /** A plain in-memory [ProtectionExceptionRepository]. */
    class InMemoryExceptions : ProtectionExceptionRepository {
        private val rows = MutableStateFlow<List<SiteProtectionException>>(emptyList())

        override fun observeAll(): Flow<List<SiteProtectionException>> = rows

        override fun currentSites(): Set<String> = rows.value.mapTo(HashSet()) { it.site }

        override suspend fun add(site: String): Result<Unit> {
            rows.value = rows.value + SiteProtectionException(site, Instant.now())
            return Result.Success(Unit)
        }

        override suspend fun remove(site: String): Result<Unit> {
            rows.value = rows.value.filterNot { it.site == site }
            return Result.Success(Unit)
        }

        override suspend fun clearAll(): Result<Unit> {
            rows.value = emptyList()
            return Result.Success(Unit)
        }
    }

    companion object {
        val TRACKER_HOSTS: Set<String> = (1..12).mapTo(LinkedHashSet()) { "tracker-%02d.test".format(it) }
        const val RESOLVABLE_TRACKER: String = "127.0.0.1"
        const val PAGE_HOST: String = "localhost"
        const val TIMEOUT_MS: Long = 20_000L
    }
}
