# Contract: `BuildSearchUrlUseCase` (Spec 010)

**Layer**: `domain/usecase/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCase.kt` (NEW)

## Class

```kotlin
package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.SearchEngineRepository
import javax.inject.Inject

/**
 * Build the search-results URL for a free-form query under the user's
 * currently-selected search engine (Spec 010).
 *
 * Operator-invocable so call sites read as `buildSearchUrl(query)` rather
 * than `buildSearchUrl.invoke(query)`. Pure delegation to repository — exists
 * per Constitution §IV "all business logic in domain/usecase/" so the
 * consumer-facing API is stable as Spec 016 (settings screen) potentially
 * grows it (e.g., per-engine query rewriting, opt-out parameters).
 *
 * Mirrors the pattern established by Spec 006's `ObserveUserSettingsUseCase`.
 */
class BuildSearchUrlUseCase @Inject constructor(
    private val repository: SearchEngineRepository,
) {
    /**
     * @param query the trimmed, non-empty user-typed search query.
     * @return a fully-formed absolute HTTPS URL for the user's selected engine.
     *         See [SearchEngineRepository.buildSearchUrl] for the full contract.
     */
    suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query)
}
```

## Behavior contract

Identical to [SearchEngineRepository.buildSearchUrl](SearchEngineRepository.md). This use case is a pure pass-through; it adds no validation, no transformation, no side effects.

## Why it exists

Constitution §IV: "All business logic in `domain/usecase/`; ViewModels MUST go through use cases." `BrowserViewModel` cannot inject `SearchEngineRepository` directly — that would couple the presentation layer to a repository interface. The use case provides the stable boundary even when the wrapping is a one-liner today, matching the project pattern from Spec 006 (5 setter use cases, each a one-line wrapper).

## Hilt

No new module entry. `class @Inject constructor(...)` is auto-discovered by Hilt's constructor-injection mechanism — same as `ObserveUserSettingsUseCase` (Spec 006), which has zero `@Module` lines.

## Test surface

A single unit test in `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCaseTest.kt`:

```kotlin
class BuildSearchUrlUseCaseTest {

    @Test
    fun `invoke delegates to repository`() = runTest {
        val expectedUrl = "https://www.google.com/search?q=android"
        val fakeRepo = object : SearchEngineRepository {
            override suspend fun buildSearchUrl(query: String): String {
                require(query == "android")
                return expectedUrl
            }
        }
        val useCase = BuildSearchUrlUseCase(fakeRepo)

        val actual = useCase("android")

        assertEquals(expectedUrl, actual)
    }
}
```

The richer per-engine + per-query-shape tests live in `SearchEngineRepositoryImplTest` per the data-model layer pyramid (test the implementation, not the wrapper).
