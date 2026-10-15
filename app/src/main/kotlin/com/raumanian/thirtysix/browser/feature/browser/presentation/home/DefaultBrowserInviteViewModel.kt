package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SyncDefaultBrowserInviteUseCase
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
 * Shown only when not dismissed, launched at least 3 times, not the default browser and not in
 * incognito. Becoming the default retires the card for good; a declined confirmation leaves it.
 */
@HiltViewModel
class DefaultBrowserInviteViewModel @Inject constructor(
    private val invites: DefaultBrowserInviteRepository,
    observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase,
    private val defaultBrowser: DefaultBrowserController,
    private val syncInvite: SyncDefaultBrowserInviteUseCase,
    dispatchers: DispatcherProvider,
) : ViewModel() {

    private val isDefault = MutableStateFlow(defaultBrowser.isDefaultBrowser())

    val showInvite: StateFlow<Boolean> = combine(
        invites.observeInviteState(),
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

    fun onAction(action: DefaultBrowserInviteAction) {
        when (action) {
            DefaultBrowserInviteAction.Resumed -> onResume()
            DefaultBrowserInviteAction.Dismiss -> viewModelScope.launch { invites.markInviteDismissed() }
        }
    }

    /** The person may have just changed the default browser; re-read and record it. */
    private fun onResume() {
        val nowDefault = defaultBrowser.isDefaultBrowser()
        isDefault.value = nowDefault
        if (nowDefault) viewModelScope.launch { syncInvite() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
