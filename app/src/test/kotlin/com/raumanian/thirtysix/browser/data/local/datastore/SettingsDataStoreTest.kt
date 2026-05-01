package com.raumanian.thirtysix.browser.data.local.datastore

import androidx.datastore.preferences.core.edit
import com.raumanian.thirtysix.browser.core.constants.AppDefaults
import com.raumanian.thirtysix.browser.core.constants.StorageKeys
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.mapper.SettingsMapper
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [SettingsDataStore] — pure JVM (no Robolectric per Spec 006 R8).
 *
 * Each test creates a fresh DataStore in a new temp folder (via @Rule).
 * Tests that simulate "process restart" cancel the producing scope, then create
 * a second DataStore over the SAME file — DataStore guarantees readability of
 * previously persisted data across instances.
 *
 * Story coverage:
 *  - US1 (theme persist): writeThemeMode_then_freshReadReturnsThemeMode
 *  - US2 (first-launch defaults): defaultRead_returnsDocumentedDefaults
 *  - US3 (language persist + clear): writeLanguageOverrideExplicit_*, writeFollowSystem_*
 *  - US4 (search engine persist + corrupted fallback):
 *        writeSearchEngine_*, corruptedSearchEngineDisk_fallsBackToDefault
 *  - US5 (onboarding flag): writeOnboardingCompleted_then_freshReadReturnsTrue
 *  - US6 (concurrent writes): concurrentWrites_differentKeys_bothPersist (100 reps),
 *        concurrentWrites_sameKey_lastWriterWins
 */
class SettingsDataStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val mapper = SettingsMapper()

    // ----------------------------- US2 -------------------------------------

    @Test
    fun defaultRead_returnsDocumentedDefaults() = runBlocking {
        val ds = SettingsDataStore(createTestSettingsDataStore(tempFolder.newFolder()))
        val snapshot = mapper.toDomain(ds.data.first())

        assertEquals(AppDefaults.THEME_MODE, snapshot.themeMode)
        assertEquals(AppDefaults.LANGUAGE_OVERRIDE, snapshot.languageOverride)
        assertEquals(AppDefaults.SEARCH_ENGINE, snapshot.searchEngine)
        assertEquals(AppDefaults.IS_ONBOARDING_COMPLETED, snapshot.isOnboardingCompleted)
    }

    // ----------------------------- US1 -------------------------------------

    @Test
    fun writeThemeMode_then_freshReadReturnsThemeMode() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "session.preferences_pb"

        val firstScope = CoroutineScope(SupervisorJob())
        val first = SettingsDataStore(createTestSettingsDataStore(folder, fileName, firstScope))
        val writeResult = first.setThemeMode(ThemeMode.Dark)
        assertTrue("write should succeed", writeResult is Result.Success)
        firstScope.cancel("simulate process death")

        val secondScope = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, secondScope))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(ThemeMode.Dark, snapshot.themeMode)
        secondScope.cancel()
    }

    // ----------------------------- US3 -------------------------------------

    @Test
    fun writeLanguageOverrideExplicit_then_freshReadReturnsExplicit() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "lang.preferences_pb"

        val s1 = CoroutineScope(SupervisorJob())
        val first = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s1))
        assertTrue(first.setLanguageOverride(LanguageOverride.Explicit("vi")) is Result.Success)
        s1.cancel()

        val s2 = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s2))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(LanguageOverride.Explicit("vi"), snapshot.languageOverride)
        s2.cancel()
    }

    @Test
    fun writeFollowSystem_after_explicit_clears() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "lang2.preferences_pb"

        val s1 = CoroutineScope(SupervisorJob())
        val first = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s1))
        first.setLanguageOverride(LanguageOverride.Explicit("vi"))
        first.setLanguageOverride(LanguageOverride.FollowSystem)
        s1.cancel()

        val s2 = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s2))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(LanguageOverride.FollowSystem, snapshot.languageOverride)
        s2.cancel()
    }

    // ----------------------------- US4 -------------------------------------

    @Test
    fun writeSearchEngine_then_freshReadReturnsSearchEngine() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "engine.preferences_pb"

        val s1 = CoroutineScope(SupervisorJob())
        val first = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s1))
        assertTrue(first.setSearchEngine(SearchEngine.Google) is Result.Success)
        s1.cancel()

        val s2 = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s2))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(SearchEngine.Google, snapshot.searchEngine)
        s2.cancel()
    }

    @Test
    fun corruptedSearchEngineDisk_fallsBackToDefault() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "corrupt.preferences_pb"

        val s1 = CoroutineScope(SupervisorJob())
        val raw1 = createTestSettingsDataStore(folder, fileName, s1)
        // Write an unrecognized engine value directly via raw edit (simulating
        // a future build that wrote "bing" then user downgraded).
        raw1.edit { it[StorageKeys.SEARCH_ENGINE] = "bing" }
        s1.cancel()

        val s2 = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s2))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(SearchEngine.Google, snapshot.searchEngine)
        s2.cancel()
    }

    // ----------------------------- US5 -------------------------------------

    @Test
    fun writeOnboardingCompleted_then_freshReadReturnsTrue() = runBlocking {
        val folder = tempFolder.newFolder()
        val fileName = "ob.preferences_pb"

        val s1 = CoroutineScope(SupervisorJob())
        val first = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s1))
        assertTrue(first.setOnboardingCompleted(true) is Result.Success)
        s1.cancel()

        val s2 = CoroutineScope(SupervisorJob())
        val second = SettingsDataStore(createTestSettingsDataStore(folder, fileName, s2))
        val snapshot = mapper.toDomain(second.data.first())
        assertEquals(true, snapshot.isOnboardingCompleted)
        s2.cancel()
    }

    // ----------------------------- US6 -------------------------------------

    /**
     * SC-004: 100 iterations of two-coroutine concurrent writes to two DIFFERENT
     * keys MUST both persist. Uses runBlocking + real coroutines (not virtual time)
     * to exercise the actual DataStore actor-style serializer.
     */
    @Test
    fun concurrentWrites_differentKeys_bothPersist() = runBlocking {
        repeat(REPEAT_COUNT) { iteration ->
            val folder = tempFolder.newFolder()
            val scope = CoroutineScope(SupervisorJob())
            val ds = SettingsDataStore(createTestSettingsDataStore(folder, "iter.preferences_pb", scope))

            val jobs = listOf(
                launch { ds.setThemeMode(ThemeMode.Dark) },
                launch { ds.setSearchEngine(SearchEngine.Google) },
            )
            jobs.joinAll()

            val snapshot = mapper.toDomain(ds.data.first())
            assertEquals(
                "iteration $iteration: themeMode lost",
                ThemeMode.Dark,
                snapshot.themeMode,
            )
            assertEquals(
                "iteration $iteration: searchEngine lost",
                SearchEngine.Google,
                snapshot.searchEngine,
            )
            scope.cancel()
        }
    }

    @Test
    fun concurrentWrites_sameKey_lastWriterWins() = runBlocking {
        val folder = tempFolder.newFolder()
        val scope = CoroutineScope(SupervisorJob())
        val ds = SettingsDataStore(createTestSettingsDataStore(folder, "same.preferences_pb", scope))

        val jobs = listOf(
            launch { ds.setThemeMode(ThemeMode.Dark) },
            launch { ds.setThemeMode(ThemeMode.Light) },
        )
        jobs.joinAll()

        val snapshot = mapper.toDomain(ds.data.first())
        // Either Dark or Light persisted — both are valid; corruption / null is not.
        assertNotNull(snapshot.themeMode)
        assertTrue(
            "themeMode must be Dark or Light, got ${snapshot.themeMode}",
            snapshot.themeMode == ThemeMode.Dark || snapshot.themeMode == ThemeMode.Light,
        )
        scope.cancel()
    }

    private companion object {
        // SC-004 demands 100% pass across at least 100 reps. Keep at 100; if
        // the test becomes a CI bottleneck reduce to 50 with a corresponding
        // SC clarification.
        const val REPEAT_COUNT = 100
    }
}
