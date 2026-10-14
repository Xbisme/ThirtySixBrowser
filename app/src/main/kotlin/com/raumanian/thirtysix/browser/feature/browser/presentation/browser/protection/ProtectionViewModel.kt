package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SetSiteFireproofUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SetSiteProtectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The hub's protection section: the shown page's status and the per-site switch. Only one WebView,
 * the active tab's, reports its monitor through [hooks] at a time.
 */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    private val engine: ProtectionEngine,
    private val setSiteProtection: SetSiteProtectionUseCase,
    observeFireproofSites: ObserveFireproofSitesUseCase,
    private val setSiteFireproof: SetSiteFireproofUseCase,
    scriptInputs: PrivacyScriptInputs,
) : ViewModel() {
    private val monitor = MutableStateFlow<PageProtectionMonitor?>(null)

    val hooks: ProtectionHooks = ProtectionHooks(
        engine = engine,
        scripts = ScriptHooks(scriptInputs, viewModelScope),
        attach = { monitor.value = it },
        // A tab switch builds the new WebView before the old one is released: only the
        // monitor still shown may clear the slot.
        detach = { released -> monitor.compareAndSet(released, null) },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: StateFlow<PageProtectionStatus> = monitor
        .flatMapLatest { it?.status ?: flowOf(PageProtectionStatus.NOT_A_WEB_PAGE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PageProtectionStatus.NOT_A_WEB_PAGE)

    /** The dashboard for the page on screen, or `null`; it follows the shown monitor while open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboard: StateFlow<PageDashboardState?> = monitor
        .flatMapLatest { shown ->
            if (shown == null) {
                flowOf(null)
            } else {
                combine(shown.status, shown.blockedHostsFlow, observeFireproofSites()) { status, hosts, fireproof ->
                    PageDashboardState.of(
                        status = status,
                        blockedHosts = hosts,
                        fireproofSites = fireproof.mapTo(HashSet()) { it.site },
                        isIncognito = shown.isIncognito,
                        registrableDomain = ::registrableDomain,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun registrableDomain(host: String): String =
        engine.matchers.matchersOrNull()?.suffixes?.registrableDomain(host) ?: host

    /** Normal tabs only: incognito never offers the fireproof switch. */
    fun onFireproofSwitched(fireproof: Boolean) {
        val current = monitor.value?.takeUnless { it.isIncognito } ?: return
        current.currentSite?.let { site -> viewModelScope.launch { setSiteFireproof(site, fireproof) } }
    }

    private val _reloadEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val reloadEvents: SharedFlow<Unit> = _reloadEvents.asSharedFlow()

    fun onProtectionSwitched(on: Boolean) {
        val current = monitor.value ?: return
        val site = current.currentSite ?: return
        viewModelScope.launch {
            setSiteProtection(site, on = on, isIncognito = current.isIncognito)
            // The reload must already run the scripts' new rule for this site.
            hooks.reinstallScripts()
            _reloadEvents.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** What the browser screen hands to `BrowserWebView` so its WebView is protected. */
class ProtectionHooks(
    val engine: ProtectionEngine,
    val attach: (PageProtectionMonitor) -> Unit,
    val detach: (PageProtectionMonitor) -> Unit,
    /** `null` in tests that exercise the network protections alone. */
    private val scripts: ScriptHooks? = null,
) {
    private val installers = mutableMapOf<WebViewProtection, PrivacyScriptInstaller>()

    fun protect(webView: WebView, isIncognito: Boolean): WebViewProtection {
        GpcHeaderInstaller.install(webView)
        val protection = WebViewProtection(engine, engine.newMonitor(isIncognito))
        scripts?.let { hooks ->
            installers[protection] = PrivacyScriptInstaller(webView, isIncognito, hooks.inputs, hooks.scope)
        }
        return protection.also { attach(it.monitor) }
    }

    fun unprotect(protection: WebViewProtection) {
        installers.remove(protection)?.detach()
        protection.release()
        detach(protection.monitor)
    }

    /** Every live WebView re-installs its scripts from the current exceptions. Main thread. */
    fun reinstallScripts() {
        installers.values.forEach(PrivacyScriptInstaller::reinstall)
    }
}

/** What [ProtectionHooks] needs to give each WebView its page-start scripts. */
class ScriptHooks(val inputs: PrivacyScriptInputs, val scope: CoroutineScope)
