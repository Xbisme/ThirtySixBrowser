package com.raumanian.thirtysix.browser.feature.settings.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.HistoryRetention
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import java.net.URLEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineRepositoryImplTest {

    private fun newRepo(
        initialEngine: SearchEngine = SearchEngine.Google,
    ): Pair<SearchEngineRepositoryImpl, FakeSettingsRepository> {
        val fake = FakeSettingsRepository(initialEngine)
        val repo = SearchEngineRepositoryImpl(fake)
        return repo to fake
    }

    // Google output must stay byte-identical to String.format(template, URLEncoder.encode(q, UTF-8)).

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

    // The engine is read on every submit, so a change between calls applies.

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

// Setters that are not exercised throw, to surface accidental misuse.
private class FakeSettingsRepository(initialEngine: SearchEngine) : SettingsRepository {

    private val state = MutableStateFlow(UserSettings.DEFAULT.copy(searchEngine = initialEngine))

    fun set(engine: SearchEngine) {
        state.value = state.value.copy(searchEngine = engine)
    }

    override fun observeSettings(): Flow<UserSettings> = state.asStateFlow()

    override suspend fun currentSettings(): UserSettings = state.value

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = error("not used in this test")

    override suspend fun setDynamicColorEnabled(enabled: Boolean): Result<Unit> = error("not used in this test")

    override suspend fun setSearchEngine(engine: SearchEngine): Result<Unit> {
        set(engine)
        return Result.Success(Unit)
    }

    override suspend fun setHistoryRetention(retention: HistoryRetention): Result<Unit> =
        error("not used in this test")

    override suspend fun setTextSize(size: com.raumanian.thirtysix.browser.core.domain.model.TextSize) =

        com.raumanian.thirtysix.browser.core.domain.util.Result.Success(Unit)

    override suspend fun setOnboardingCompleted(value: Boolean): Result<Unit> = error("not used in this test")
}
