package com.raumanian.thirtysix.browser.presentation.browser.protection

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.domain.usecase.SetSiteProtectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spec 023 US2 — the hub's protection section: the shown page's status and the per-site switch.
 *
 * A view model of its own (Spec 022 `PageTextSizeViewModel` precedent) so `BrowserViewModel`
 * does not grow. The WebView reports its [PageProtectionMonitor] through [hooks]; only one
 * WebView — the active tab's — exists at a time.
 */
@HiltViewModel
class ProtectionViewModel @Inject constructor(
    engine: ProtectionEngine,
    private val setSiteProtection: SetSiteProtectionUseCase,
) : ViewModel() {
    private val monitor = MutableStateFlow<PageProtectionMonitor?>(null)

    val hooks: ProtectionHooks = ProtectionHooks(
        engine = engine,
        attach = { monitor.value = it },
        // A tab switch builds the new WebView before the old one is released: only the
        // monitor still shown may clear the slot.
        detach = { released -> monitor.compareAndSet(released, null) },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val status: StateFlow<PageProtectionStatus> = monitor
        .flatMapLatest { it?.status ?: flowOf(PageProtectionStatus.NOT_A_WEB_PAGE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PageProtectionStatus.NOT_A_WEB_PAGE)

    private val _reloadEvents = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One event per switch: the page reloads with the new setting (FR-012). */
    val reloadEvents: SharedFlow<Unit> = _reloadEvents.asSharedFlow()

    fun onProtectionSwitched(on: Boolean) {
        val current = monitor.value ?: return
        val site = current.currentSite ?: return
        viewModelScope.launch {
            setSiteProtection(site, on = on, isIncognito = current.isIncognito)
            _reloadEvents.tryEmit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Spec 023 — what the browser screen hands to `BrowserWebView` so its WebView is protected. */
class ProtectionHooks(
    val engine: ProtectionEngine,
    val attach: (PageProtectionMonitor) -> Unit,
    val detach: (PageProtectionMonitor) -> Unit,
) {
    /** A new WebView: GPC installed, a monitor attached for the hub. Main thread. */
    fun protect(webView: WebView, isIncognito: Boolean): WebViewProtection {
        GpcHeaderInstaller.install(webView)
        return WebViewProtection(engine, engine.newMonitor(isIncognito)).also { attach(it.monitor) }
    }

    /** The WebView is going away: no late load may reach it, and the hub lets it go. */
    fun unprotect(protection: WebViewProtection) {
        protection.release()
        detach(protection.monitor)
    }
}
