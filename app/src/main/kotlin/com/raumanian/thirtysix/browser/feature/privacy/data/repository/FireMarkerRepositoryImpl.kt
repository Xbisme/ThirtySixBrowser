package com.raumanian.thirtysix.browser.feature.privacy.data.repository

import android.util.Log
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.StorageKeys
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.privacy.domain.repository.FireMarkerRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class FireMarkerRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
) : FireMarkerRepository {

    override suspend fun isFireInProgress(): Boolean = try {
        dataStore.data.first()[StorageKeys.FIRE_IN_PROGRESS] ?: false
    } catch (e: IOException) {
        // An unreadable file reads as the default.
        Log.w(LOG_TAG, "Reading the Fire marker failed", e)
        false
    }

    override suspend fun setFireInProgress(value: Boolean): Result<Unit> = dataStore.setFireInProgress(value)

    private companion object {
        const val LOG_TAG = "FireMarker"
    }
}
