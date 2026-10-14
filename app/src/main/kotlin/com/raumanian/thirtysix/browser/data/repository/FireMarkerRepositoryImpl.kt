package com.raumanian.thirtysix.browser.data.repository

import android.util.Log
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.repository.FireMarkerRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Spec 024 — [FireMarkerRepository] over the existing settings DataStore file. */
class FireMarkerRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
) : FireMarkerRepository {

    override suspend fun isFireInProgress(): Boolean = try {
        dataStore.data.first()[StorageKeys.FIRE_IN_PROGRESS] ?: false
    } catch (e: IOException) {
        // Spec 006 posture: an unreadable file reads as the default.
        Log.w(LOG_TAG, "Reading the Fire marker failed", e)
        false
    }

    override suspend fun setFireInProgress(value: Boolean): Result<Unit> = dataStore.setFireInProgress(value)

    private companion object {
        const val LOG_TAG = "FireMarker"
    }
}
