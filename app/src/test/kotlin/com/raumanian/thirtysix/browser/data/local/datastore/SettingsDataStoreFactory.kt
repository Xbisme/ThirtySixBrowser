package com.raumanian.thirtysix.browser.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/**
 * Test fixture: build a fresh DataStore<Preferences> backed by a real file under
 * the supplied directory. Pure JVM — no Robolectric (Spec 006 R8).
 *
 * Default scope uses Dispatchers.IO + SupervisorJob (real-time scheduling) so
 * tests can use runBlocking to assert against actual disk I/O. Tests using
 * runTest (virtual time) MUST pass their own scope built from the test
 * dispatcher; mixing virtual and real schedulers will hang the DataStore actor.
 */
fun createTestSettingsDataStore(
    parentDir: File,
    fileName: String = "test.preferences_pb",
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { File(parentDir, fileName) },
)
