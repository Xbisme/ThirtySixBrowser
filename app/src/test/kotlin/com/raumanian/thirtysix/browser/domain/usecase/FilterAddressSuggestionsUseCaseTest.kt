package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.AddressSuggestion
import com.raumanian.thirtysix.browser.domain.model.AddressSuggestionSnapshot
import com.raumanian.thirtysix.browser.domain.model.SuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 022 FR-014 / FR-017 — literal, case-insensitive matching on title or address, capped. */
class FilterAddressSuggestionsUseCaseTest {

    private val filter = FilterAddressSuggestionsUseCase()

    private fun snapshot(vararg pairs: Pair<String, String>) = AddressSuggestionSnapshot(
        pairs.map { (title, url) -> AddressSuggestion(url, title, SuggestionSource.History) },
    )

    private fun urls(query: String, snapshot: AddressSuggestionSnapshot, limit: Int = LIMIT) =
        filter(snapshot, query, limit).map { it.url }

    @Test
    fun `an empty or blank query suggests nothing`() {
        val all = snapshot("Kotlin" to "https://kotlinlang.org/")
        assertTrue(filter(all, "", LIMIT).isEmpty())
        assertTrue(filter(all, "   ", LIMIT).isEmpty())
    }

    @Test
    fun `matches on the title or on the address`() {
        val all = snapshot("Kotlin docs" to "https://kotlinlang.org/", "News" to "https://news.example/kotlin")
        assertEquals(listOf("https://kotlinlang.org/", "https://news.example/kotlin"), urls("kot", all))
        assertEquals(listOf("https://kotlinlang.org/"), urls("docs", all))
    }

    @Test
    fun `case is ignored in every script`() {
        val all = snapshot(
            "Tiếng Việt" to "https://vi.example/",
            "Привет мир" to "https://ru.example/",
        )
        assertEquals(listOf("https://vi.example/"), urls("TIẾNG", all))
        assertEquals(listOf("https://ru.example/"), urls("ПРИВЕТ", all))
    }

    @Test
    fun `wildcard and quote characters match only themselves`() {
        val all = snapshot(
            "50%_off sale" to "https://shop.example/a",
            "Plain page" to "https://plain.example/abc",
            "it's here" to "https://quote.example/",
            "back\\slash" to "https://slash.example/",
        )
        assertEquals(listOf("https://shop.example/a"), urls("%", all))
        assertEquals(listOf("https://shop.example/a"), urls("_", all))
        assertEquals(listOf("https://shop.example/a"), urls("0%_", all))
        assertEquals(listOf("https://quote.example/"), urls("'", all))
        assertEquals(listOf("https://slash.example/"), urls("\\", all))
    }

    @Test
    fun `snapshot order is kept and the result stops at the limit`() {
        val all = AddressSuggestionSnapshot(
            (1..10).map { AddressSuggestion("https://e.example/$it", "Page $it", SuggestionSource.History) },
        )
        assertEquals((1..LIMIT).map { "https://e.example/$it" }, urls("page", all))
        assertEquals(listOf("https://e.example/1", "https://e.example/2"), urls("page", all, limit = 2))
    }

    private companion object {
        const val LIMIT = 6
    }
}
