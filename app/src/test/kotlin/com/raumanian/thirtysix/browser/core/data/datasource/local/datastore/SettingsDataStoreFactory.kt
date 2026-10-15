package com.raumanian.thirtysix.browser.core.data.datasource.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Builds a fresh file-backed DataStore<Preferences> under [parentDir] on a real-time IO scope.
 * Tests using `runTest` must pass a scope built from the test dispatcher: mixing virtual and
 * real schedulers hangs the DataStore actor.
 */

fun createTestSettingsDataStore(
    parentDir: File,
    fileName: String = "test.preferences_pb",
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { File(parentDir, fileName) },
)
