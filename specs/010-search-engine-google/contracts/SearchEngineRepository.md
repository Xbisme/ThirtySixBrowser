# Contract: `SearchEngineRepository` (Spec 010)

**Layer**: `domain/repository/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SearchEngineRepository.kt` (NEW)

## Interface

```kotlin
package com.raumanian.thirtysix.browser.domain.repository

/**
 * Translates a free-form search query into a fully-formed search-results URL
 * for the user's currently-selected search engine (Spec 010).
 *
 * Engine selection is read at submit-time from [SettingsRepository] (Spec 006)
 * via the existing `observeSettings()` flow + `.first()`. There is no internal
 * cache; every call re-reads the snapshot. See spec.md edge case "Engine
 * preference changes mid-typing" for the read-on-submit semantics rationale.
 *
 * Per Constitution §IV, this interface lives in `domain/repository/` and uses
 * only Kotlin / project domain types. The implementation lives in
 * `data/repository/SearchEngineRepositoryImpl.kt`.
 */
interface SearchEngineRepository {

    /**
     * Build a fully-formed search-results URL for [query] under the user's
     * currently-persisted [com.raumanian.thirtysix.browser.domain.model.SearchEngine].
     *
     * @param query the trimmed, non-empty search query as typed by the user.
     *              The caller (`BuildSearchUrlUseCase` via `BrowserViewModel`)
     *              is responsible for ensuring non-emptiness — Spec 009's
     *              `AddressBarInputClassifier` returns `Empty` before reaching
     *              the Query branch, so this method is never called with an
     *              empty input under normal operation.
     * @return a fully-formed absolute HTTPS URL with the URL-encoded query
     *         substituted into the engine's documented template. Always starts
     *         with `https://`.
     */
    suspend fun buildSearchUrl(query: String): String
}
```

## Behavior contract

| Behavior | Specification |
|----------|---------------|
| **Engine resolution** | `settingsRepository.observeSettings().first().searchEngine` is read once per call. Result is a snapshot; subsequent persistence-layer changes do not affect the URL returned by this call. |
| **Encoding** | `URLEncoder.encode(query, Charsets.UTF_8.name())` — produces form-encoding (`hello+world` for space). Same call form Spec 009 uses inline. |
| **URL substitution** | `String.format(template, encoded)`. Template selection is an exhaustive `when (engine)` over the closed `SearchEngine` enum. |
| **Return value** | A non-empty string starting with `https://`. |
| **Failure modes** | None expected. `URLEncoder.encode(..., "UTF-8")` does not throw under any documented input. `Flow.first()` cancels on coroutine cancellation but does not raise IO errors (DataStore Preferences serves from in-process state after warm-read). If a future implementation changes this, it must throw a domain-typed error or default to Google rather than crash the address-bar submit. |
| **Threading** | `suspend`. Caller invokes from `viewModelScope` or any other `CoroutineScope`. Implementation does no main-thread work. |
| **Idempotency** | Pure function of `(persisted_engine, query)` at call time. Two calls with identical state return identical strings (I3 / I4 in data-model.md). |

## Postconditions

- The returned string is a syntactically valid absolute HTTPS URL.
- For `engine = Google`, the returned string is byte-identical to Spec 009's inline output for the same input. (SC-001 — verified by parameterized unit test.)
- For `engine = DuckDuckGo`, the returned string starts with `"https://duckduckgo.com/?q="` followed by the URL-encoded query.
- For `engine = Bing`, the returned string starts with `"https://www.bing.com/search?q="` followed by the URL-encoded query.

## Out of scope (NOT this contract)

- Suggestions / autocomplete: Spec 010 FR-020.
- Region-specific engine domains: Spec 010 FR-021.
- Custom user-defined engines: Spec 010 FR-022.
- Incognito-aware behavior: Spec 010 FR-023 / Spec 012.
- Exposing the current engine as `Flow<SearchEngine>` for UI consumers: research.md R4 (UI consumers read directly from `SettingsRepository`).

## Hilt binding

```kotlin
// app/.../di/SearchEngineModule.kt — NEW

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchEngineModule {

    @Binds
    @Singleton
    abstract fun bindSearchEngineRepository(
        impl: SearchEngineRepositoryImpl,
    ): SearchEngineRepository
}
```

Singleton scope matches `SettingsRepository` and avoids per-ViewModel allocation. See research.md R6.
