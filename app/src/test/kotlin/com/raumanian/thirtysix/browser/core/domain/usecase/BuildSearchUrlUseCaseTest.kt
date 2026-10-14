package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.SearchEngineRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BuildSearchUrlUseCase] only delegates to [SearchEngineRepository]; per-engine and per-query
 * coverage lives in `SearchEngineRepositoryImplTest`.
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
