package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.LanguageOverride
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.repository.SettingsRepository
import java.net.URLEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 010 — Tests for [SearchEngineRepositoryImpl].
 *
 * Covers:
 * - SC-001 byte-identical Google output (US1, T009): the repo's URL for any
 *   query under Google MUST equal `String.format(GOOGLE_TEMPLATE, URLEncoder.encode(q, UTF-8))`.
 * - I1 unknown-storageValue fallback (US1, T010): when the persisted value is
 *   unknown, the enum falls back to Google and the URL is built accordingly.
 * - DuckDuckGo + Bing routes (US2, T018/T019): per-engine canonical URL.
 * - I4 read-on-submit semantics (US2, T020): mid-stream engine change is
 *   reflected on the next call.
 * - FR-014 encoding-engine-independence (US3, T024): the same encoded query
 *   substring appears across all 3 engine templates.
 *
 * Test double: hand-rolled [FakeSettingsRepository] backed by a
 * [MutableStateFlow] of [UserSettings]. Pattern reused from
 * `SettingsRepositoryImplTest` setup convention (no MockK).
 */
class SearchEngineRepositoryImplTest {

    private fun newRepo(
        initialEngine: SearchEngine = SearchEngine.Google,
    ): Pair<SearchEngineRepositoryImpl, FakeSettingsRepository> {
        val fake = FakeSettingsRepository(initialEngine)
        val repo = SearchEngineRepositoryImpl(fake)
        return repo to fake
    }

    // --- US1 / T009: Google byte-identity for canonical inputs (SC-001) ---

    @Test
    fun google_byteIdenticalToInlineFormula_forCanonicalInputs() = runTest {
        val (repo, _) = newRepo()
        listOf(
            "android",
            "kotlin coroutines",
            "c++ tutorial",
            "cà phê sữa",
            "東京タワー",
            "красная площадь",
            "weather & forecast",
            "?",
            "🍕 pizza",
            "검색",
        ).forEach { input ->
            val expected = String.format(
                UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE,
                URLEncoder.encode(input, Charsets.UTF_8.name()),
            )
            val actual = repo.buildSearchUrl(input)
            assertEquals("byte-identity failed for input: $input", expected, actual)
        }
    }

    // --- US1 / T010: unknown storageValue falls back to Google ---

    @Test
    fun unknownStorageValue_fallsBackToGoogle() = runTest {
        val unknownDefault = SearchEngine.fromStorageValueOrDefault("yandex")
        val (repo, fake) = newRepo()
        fake.set(unknownDefault)
        val url = repo.buildSearchUrl("foo")
        assertEquals(SearchEngine.Google, unknownDefault)
        assertTrue(
            "expected Google template substring, got: $url",
            url.startsWith("https://www.google.com/search?q="),
        )
    }

    // --- US2 / T018: DuckDuckGo route ---

    @Test
    fun duckDuckGo_producesCanonicalUrl() = runTest {
        val (repo, _) = newRepo(initialEngine = SearchEngine.DuckDuckGo)
        listOf("android compose", "cà phê sữa", "weather & forecast").forEach { input ->
            val url = repo.buildSearchUrl(input)
            val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
            assertTrue(
                "DuckDuckGo URL should start with the documented host: $url",
                url.startsWith("https://duckduckgo.com/?q="),
            )
            assertTrue(
                "DuckDuckGo URL should contain encoded query for '$input': $url",
                url.contains(encoded),
            )
        }
    }

    // --- US2 / T019: Bing route ---

    @Test
    fun bing_producesCanonicalUrl() = runTest {
        val (repo, _) = newRepo(initialEngine = SearchEngine.Bing)
        listOf("android compose", "cà phê sữa", "weather & forecast").forEach { input ->
            val url = repo.buildSearchUrl(input)
            val encoded = URLEncoder.encode(input, Charsets.UTF_8.name())
            assertTrue(
                "Bing URL should start with the documented host: $url",
                url.startsWith("https://www.bing.com/search?q="),
            )
            assertTrue(
                "Bing URL should contain encoded query for '$input': $url",
                url.contains(encoded),
            )
        }
    }

    // --- US2 / T020: read-on-submit semantics — change between calls ---

    @Test
    fun engineChangeBetweenCalls_switchesTemplate() = runTest {
        val (repo, fake) = newRepo(initialEngine = SearchEngine.Google)
        val urlGoogle = repo.buildSearchUrl("foo")
        assertTrue(urlGoogle.startsWith("https://www.google.com/search?q="))

        fake.set(SearchEngine.DuckDuckGo)
        val urlDuck = repo.buildSearchUrl("foo")
        assertTrue(urlDuck.startsWith("https://duckduckgo.com/?q="))

        fake.set(SearchEngine.Bing)
        val urlBing = repo.buildSearchUrl("foo")
        assertTrue(urlBing.startsWith("https://www.bing.com/search?q="))
    }

    // --- US3 / T024: same encoding produced for every engine ---

    @Test
    fun encodingIsEngineIndependent_forUnicodeAndSpecialChars() = runTest {
        listOf(
            "cà phê sữa",
            "東京タワー",
            "красная площадь",
            "weather & forecast",
            "🍕 pizza",
            "검색",
        ).forEach { input ->
            val expectedEncoded = URLEncoder.encode(input, Charsets.UTF_8.name())
            SearchEngine.entries.forEach { engine ->
                val (repo, _) = newRepo(initialEngine = engine)
                val url = repo.buildSearchUrl(input)
                assertTrue(
                    "engine $engine produced a URL not containing the canonical encoded form for input '$input': $url",
                    url.contains(expectedEncoded),
                )
            }
        }
    }
}

/**
 * Minimal in-memory [SettingsRepository] for repository-level tests. Only the
 * `searchEngine` field is varied; other fields stay at [UserSettings.DEFAULT].
 * Setters that aren't exercised throw to surface accidental misuse from tests.
 */
private class FakeSettingsRepository(initialEngine: SearchEngine) : SettingsRepository {

    private val state = MutableStateFlow(UserSettings.DEFAULT.copy(searchEngine = initialEngine))

    fun set(engine: SearchEngine) {
        state.value = state.value.copy(searchEngine = engine)
    }

    override fun observeSettings(): Flow<UserSettings> = state.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = error("not used in this test")

    override suspend fun setLanguageOverride(override: LanguageOverride): Result<Unit> = error("not used in this test")

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> {
        set(engine)
        return Result.Success(Unit)
    }

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = error("not used in this test")
}
