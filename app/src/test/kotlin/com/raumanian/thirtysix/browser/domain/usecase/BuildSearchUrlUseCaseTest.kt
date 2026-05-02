package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec 010 — Verifies that [BuildSearchUrlUseCase] is a thin pass-through
 * over [SearchEngineRepository]. Per-engine + per-query coverage lives in
 * `SearchEngineRepositoryImplTest`; this file only enforces the delegation
 * contract.
 */
class BuildSearchUrlUseCaseTest {

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val expectedUrl = "https://www.google.com/search?q=android"
        val fakeRepo = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                require(query == "android") { "unexpected query: $query" }
                return expectedUrl
            }
        }
        val useCase = BuildSearchUrlUseCase(fakeRepo)

        val actual = useCase("android")

        assertEquals(expectedUrl, actual)
    }
}
