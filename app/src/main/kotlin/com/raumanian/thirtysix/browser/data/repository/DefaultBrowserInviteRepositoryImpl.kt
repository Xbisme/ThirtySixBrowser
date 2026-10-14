package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.domain.repository.InviteState
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Spec 020 — [DefaultBrowserInviteRepository] over the existing settings DataStore file. */
class DefaultBrowserInviteRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
) : DefaultBrowserInviteRepository {

    override fun observeInviteState(): Flow<InviteState> = dataStore.data
        .map { prefs ->
            InviteState(
                dismissed = prefs[StorageKeys.DEFAULT_BROWSER_INVITE_DISMISSED] ?: false,
                launchCount = prefs[StorageKeys.APP_LAUNCH_COUNT] ?: 0,
            )
        }
        // An unreadable file reads as the defaults rather than ending the flow (Spec 006 posture).
        .catch { error -> if (error is IOException) emit(InviteState.DEFAULT) else throw error }
        .distinctUntilChanged()

    override suspend fun markInviteDismissed(): Result<Unit> = dataStore.markDefaultBrowserInviteDismissed()

    override suspend fun recordLaunch(): Result<Unit> =
        dataStore.incrementAppLaunchCount(cap = BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES)
}
