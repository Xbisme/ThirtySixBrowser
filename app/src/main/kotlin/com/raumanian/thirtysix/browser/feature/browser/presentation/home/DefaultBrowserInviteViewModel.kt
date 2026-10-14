package com.raumanian.thirtysix.browser.feature.browser.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.domain.usecase.IsDefaultBrowserUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.DismissDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.ObserveDefaultBrowserInviteUseCase
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

    /** The person may have just changed the default browser; re-read and record it. */
    fun onResume() {
        val nowDefault = isDefaultBrowser()
        isDefault.value = nowDefault
        if (nowDefault) viewModelScope.launch { syncInvite() }
    }

    fun onDismiss() {
        viewModelScope.launch { dismissInvite() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
