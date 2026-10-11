package com.raumanian.thirtysix.browser.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.usecase.DismissDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SyncDefaultBrowserInviteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Spec 020 FR-024 – FR-027 / data-model §8 — whether the Home page shows the invitation to make
 * ThirtySix the default browser.
 *
 * Shown only when: not dismissed, launched at least 3 times, not the default browser, not on an
 * incognito tab. The default-browser state is re-read on every resume (FR-022); finding that the
 * app has become the default hides the card and retires it for good (FR-026). Using the card's
 * action does not dismiss it — a declined confirmation leaves it in place (FR-027).
 *
 * Kept apart from `HomeViewModel` so the Home content and the invitation evolve independently.
 */
@HiltViewModel
class DefaultBrowserInviteViewModel @Inject constructor(
    observeInvite: ObserveDefaultBrowserInviteUseCase,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val isDefaultBrowser: IsDefaultBrowserUseCase,
    private val dismissInvite: DismissDefaultBrowserInviteUseCase,
    private val syncInvite: SyncDefaultBrowserInviteUseCase,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val isDefault = MutableStateFlow(isDefaultBrowser())

    val showInvite: StateFlow<Boolean> = combine(
        observeInvite(),
        isDefault,
        observeActiveTabIsIncognito(),
    ) { invite, default, incognito ->
        !invite.dismissed &&
            invite.launchCount >= BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES &&
            !default &&
            !incognito
    }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = false)

    /** FR-022 / FR-026 — the person may have just changed the default; re-read and record it. */
    fun onResume() {
        val nowDefault = isDefaultBrowser()
        isDefault.value = nowDefault
        if (nowDefault) viewModelScope.launch { syncInvite() }
    }

    /** FR-025 — the close button. */
    fun onDismiss() {
        viewModelScope.launch { dismissInvite() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
