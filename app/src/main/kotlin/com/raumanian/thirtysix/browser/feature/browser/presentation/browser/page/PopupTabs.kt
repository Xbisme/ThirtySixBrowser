package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseIncognitoTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CloseTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateIncognitoTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.DecidePopupUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.PopupDecision
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SwitchToTabUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the browser screen tells the person about a window a page tried to open (C2.5). */
sealed interface PopupEvent {
    /** Blocked; [openUrl] non-null → the notice offers Open (FR-026a). */
    data class Blocked(val openUrl: String?, val incognito: Boolean) : PopupEvent

    data object TabLimitReached : PopupEvent
}

/**
 * Spec 021 US5 / research R5 — new windows. A window the person asked for becomes a new active
 * tab that adopts the page-created WebView (so `window.opener` keeps working for sign-in
 * pop-ups); one the page opened on its own is blocked with a notice; `window.close()` from a
 * page-opened tab closes it and returns to its opener (FR-025 – FR-028).
 */
@HiltViewModel
@Suppress("LongParameterList") // One collaborator per tab operation.
class PopupTabs @Inject constructor(
    observeAllTabs: ObserveAllTabsUseCase,
    private val decidePopup: DecidePopupUseCase,
    private val createTab: CreateTabUseCase,
    private val createIncognitoTab: CreateIncognitoTabUseCase,
    private val closeTab: CloseTabUseCase,
    private val closeIncognitoTab: CloseIncognitoTabUseCase,
    private val switchToTab: SwitchToTabUseCase,
    private val registry: PopupWebViewRegistry,
) : ViewModel() {
    private val tabs: StateFlow<List<Tab>> =
        observeAllTabs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<PopupEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<PopupEvent> = _events.asSharedFlow()

    /**
     * Decides a window request from the page in [opener]. Returns the decision; for
     * [PopupDecision.OpenInNewTab] the caller hands the page [popupWebView]'s result through the
     * transport, and the tab is created here.
     */
    fun decide(isUserGesture: Boolean, opener: PageContext): PopupDecision {
        val decision = decidePopup(isUserGesture, opener.isIncognito, atCap(opener.isIncognito))
        if (decision == PopupDecision.TabLimit) _events.tryEmit(PopupEvent.TabLimitReached)
        return decision
    }

    /** A tab for the page-created [popup], made active; it adopts [popup] when composed. */
    fun adopt(popup: WebView, opener: PageContext) {
        val placeholder = registry.hold(popup, opener.tabId)
        viewModelScope.launch {
            val created = if (opener.isIncognito) createIncognitoTab(placeholder) else createTab(placeholder)
            when (created) {
                is Result.Success -> {
                    registry.onTabCreated(popup, created.data.id)
                    if (registry.wasClosedEarly(popup)) onCloseWindow(popup)
                }
                is Result.Error -> {
                    registry.forget(popup)
                    popup.destroy()
                    _events.tryEmit(PopupEvent.TabLimitReached)
                }
            }
        }
    }

    /** A blocked window's address arrived (or not): the notice. */
    fun onBlocked(target: String?, opener: PageContext) {
        _events.tryEmit(PopupEvent.Blocked(decidePopup.openUrlFor(target), opener.isIncognito))
    }

    /** Open from the blocked notice: once, as a new active tab (FR-026a). */
    fun openBlocked(url: String, incognito: Boolean) {
        viewModelScope.launch {
            val created = if (incognito) createIncognitoTab(url) else createTab(url)
            if (created is Result.Error) _events.tryEmit(PopupEvent.TabLimitReached)
        }
    }

    /** The WebView waiting for the tab at [tabUrl], if a page opened that tab. */
    fun take(tabUrl: String): WebView? = registry.take(tabUrl)

    /** `window.close()` — only a page-opened tab closes; its opener becomes active (FR-027). */
    fun onCloseWindow(window: WebView) {
        val popup = registry.popupOf(window)
        if (popup == null) {
            // Closed before its tab was created (a fast page): closed as soon as it is.
            registry.closeWhenCreated(window)
            return
        }
        val (tabId, openerTabId) = popup
        registry.forget(window)
        viewModelScope.launch {
            if (tabs.value.any { it.id == openerTabId }) switchToTab(openerTabId)
            if (tabId < 0L) closeIncognitoTab(tabId) else closeTab(tabId)
        }
    }

    private fun atCap(incognito: Boolean): Boolean {
        val count = tabs.value.count { it.isIncognito == incognito }
        return count >= if (incognito) BrowserLimits.MAX_INCOGNITO_TABS else BrowserLimits.MAX_TABS
    }
}
