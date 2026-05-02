# Data Model: Search Engine Google (Spec 010)

**Branch**: `010-search-engine-google`
**Date**: 2026-05-03

This spec introduces no persistent storage and no Room schema changes. It modifies one domain enum, adds two URL constants, and introduces three new domain/data Kotlin types (interface + impl + use case). All other state surfaces (`BrowserUiState`, `UserSettings`, DataStore keys) are unchanged.

## Entity 1 — `SearchEngine` (extended)

**Layer**: `domain/model/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt) (existing — Spec 006)

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `Google` | enum entry | Spec 006 (existing) | `storageValue = "google"`. Default. Unchanged. |
| `DuckDuckGo` | enum entry | Spec 010 (NEW) | `storageValue = "duckduckgo"`. Stable, lowercase, matches the persistence-key convention from Spec 006. |
| `Bing` | enum entry | Spec 010 (NEW) | `storageValue = "bing"`. |

**Companion API (unchanged)**:
- `fromStorageValueOrDefault(value: String?): SearchEngine` — iterates `entries`; falls back to `AppDefaults.SEARCH_ENGINE` (= `Google`) when `value == null` or value is unknown. Adding two entries is a non-breaking change because the iterator picks them up automatically; no migration required.

**Forward / downgrade compatibility**:
- A user upgrading from a Spec 009 build (with `"google"` already on disk) sees Google after upgrade — round-trip preserved.
- A user who selects DuckDuckGo or Bing under Spec 010 then downgrades to a future build that drops one of those entries (hypothetical) — `fromStorageValueOrDefault(...)` falls back to Google with no crash. Same semantics as Spec 006 FR-009.

**Validation**: None (enum is closed).

**State transitions**: Engine choice transitions are owned by `SettingsRepository.setSearchEngine(...)` from Spec 006. This spec adds zero new transitions.

## Entity 2 — `UrlConstants` (extended)

**Layer**: `core/constants/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt)

| Constant | Value | Source | Notes |
|----------|-------|--------|-------|
| `DEFAULT_HOME_URL` | `"https://www.google.com/"` | Spec 008 (existing) | Unchanged. |
| `GOOGLE_SEARCH_URL_TEMPLATE` | `"https://www.google.com/search?q=%s"` | Spec 009 (existing) | Unchanged. Single `%s` substitution slot. |
| `HTTPS_SCHEME_PREFIX` | `"https://"` | Spec 009 (existing) | Unchanged. Used by `AddressBarInputClassifier` for the URL branch — not by this spec. |
| `DUCKDUCKGO_SEARCH_URL_TEMPLATE` | `"https://duckduckgo.com/?q=%s"` | Spec 010 (NEW) | Single `%s`. Locked via 2026-05-03 clarification Q1. |
| `BING_SEARCH_URL_TEMPLATE` | `"https://www.bing.com/search?q=%s"` | Spec 010 (NEW) | Single `%s`. Locked via 2026-05-03 clarification Q1. |

**Why these live here**: Constitution §III No-Hardcode Rule, "URLs (default home, search)" row → `core/constants/UrlConstants.kt`. See research.md R9 for rejected alternatives.

## Entity 3 — `SearchEngineRepository` (NEW interface)

**Layer**: `domain/repository/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SearchEngineRepository.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SearchEngineRepository.kt) (Spec 010 NEW)

| Member | Signature | Notes |
|--------|-----------|-------|
| `buildSearchUrl` | `suspend fun buildSearchUrl(query: String): String` | Reads the user's currently-persisted `SearchEngine` from `SettingsRepository.observeSettings().first().searchEngine`, URL-encodes `query` via `URLEncoder.encode(query, "UTF-8")`, and substitutes the encoded value into the corresponding template from `UrlConstants`. Returns the fully-formed search URL. Caller is responsible for ensuring `query` is non-empty and trimmed (Spec 009 already guarantees this via `AddressBarInputClassifier` before reaching the Query branch). |

**Lifecycle**: Hilt `@Singleton` (matches `SettingsRepository`).

**Dependencies**: `SettingsRepository` (read-only — only `observeSettings()` is called). See plan.md R5 for the documented Repository → Repository exception.

**Pure-Kotlin layer compliance**: `domain/repository/SearchEngineRepository.kt` imports zero Android SDK types (only Kotlin stdlib + the project's own `domain/repository/SettingsRepository`).

## Entity 4 — `SearchEngineRepositoryImpl` (NEW class)

**Layer**: `data/repository/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/SearchEngineRepositoryImpl.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/SearchEngineRepositoryImpl.kt) (Spec 010 NEW)

| Member | Signature | Notes |
|--------|-----------|-------|
| Primary constructor | `@Inject constructor(private val settingsRepository: SettingsRepository)` | Single dependency. Hilt provides via the existing Spec 006 binding. |
| `buildSearchUrl` | `override suspend fun buildSearchUrl(query: String): String` | Implementation: (1) `engine = settingsRepository.observeSettings().first().searchEngine`, (2) `encoded = URLEncoder.encode(query, Charsets.UTF_8.name())` — same form Spec 009 uses to preserve byte-identity per SC-001, (3) `template = when (engine) { Google -> GOOGLE_SEARCH_URL_TEMPLATE; DuckDuckGo -> DUCKDUCKGO_SEARCH_URL_TEMPLATE; Bing -> BING_SEARCH_URL_TEMPLATE }`, (4) return `String.format(template, encoded)`. |

**Defensive behavior**: None — the `when` is exhaustive over a closed enum, so the compiler enforces coverage. The `query` argument is trusted to be non-empty per the contract.

## Entity 5 — `BuildSearchUrlUseCase` (NEW class)

**Layer**: `domain/usecase/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/BuildSearchUrlUseCase.kt) (Spec 010 NEW)

| Member | Signature | Notes |
|--------|-----------|-------|
| Primary constructor | `@Inject constructor(private val repository: SearchEngineRepository)` | Single dependency. |
| `invoke` | `suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query)` | Operator-invocable so call sites read as `buildSearchUrl(query)` rather than `buildSearchUrl.invoke(query)`. Matches the `ObserveUserSettingsUseCase` precedent from Spec 006. |

**Why this exists despite being a thin wrapper**: Constitution §IV "all business logic in domain/usecase/". ViewModels MUST go through use cases per the project's established pattern. Spec 016 may grow this use case (e.g., to inject query rewriting or analytics-free safe-search handling) without altering the consumer.

## Entity 6 — `BrowserViewModel` Query branch (MODIFIED)

**Layer**: `presentation/browser/`
**File**: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) (Spec 010 MODIFY)

| Aspect | Before (Spec 009) | After (Spec 010) |
|--------|-------------------|-------------------|
| Constructor | `@Inject constructor(@param:Named("default_home_url") defaultHomeUrl: String)` | `@Inject constructor(@param:Named("default_home_url") defaultHomeUrl: String, private val buildSearchUrl: BuildSearchUrlUseCase)` |
| Imports | `import java.net.URLEncoder` | Removed. |
| Query branch | Synchronous: `val encoded = URLEncoder.encode(...); val searchUrl = String.format(...); loadUrl(searchUrl); true` | `viewModelScope.launch { val searchUrl = buildSearchUrl(classified.text); loadUrl(searchUrl) }; true` (returns `true` synchronously) |
| URL branch | Unchanged | Unchanged |
| Return type | `Boolean` | `Boolean` (preserved per Spec 009 FR-013a — focus/keyboard release decision happens synchronously) |

See [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md) for the full delta.

## Persistence diagram

```text
                         User taps address bar Go on "android compose"
                                        │
                                        ▼
                       BrowserViewModel.onAddressBarSubmit
                                        │
                                  classifier
                                        │
                            ┌───────────┴───────────┐
                            │                       │
                          Url(...)              Query(text)
                            │                       │
                            ▼                       ▼
                       loadUrl(target)    viewModelScope.launch {
                                            url = buildSearchUrl(text)   ◄── Spec 010 NEW
                                            loadUrl(url)                  ◄── Spec 008 WebViewActionsHandle
                                          }
                                                    │
                                  buildSearchUrl(text) suspend
                                                    │
                          BuildSearchUrlUseCase.invoke(text)               ◄── domain/usecase/
                                                    │
                          SearchEngineRepository.buildSearchUrl(text)      ◄── domain/repository/
                                                    │
                          SearchEngineRepositoryImpl                       ◄── data/repository/
                                                    │
                            (1) settingsRepository.observeSettings().first().searchEngine
                            (2) URLEncoder.encode(text, "UTF-8")
                            (3) when (engine) → template from UrlConstants
                            (4) String.format(template, encoded)
                                                    │
                                                    ▼
                              "https://duckduckgo.com/?q=android+compose"  (example)
```

## Invariants

- **I1**: `SearchEngine.fromStorageValueOrDefault(value)` returns a non-null `SearchEngine` for every input, including `null` and unknown strings. Falls back to `Google`. *(Inherited from Spec 006.)*
- **I2**: For any non-empty `query`, `buildSearchUrl(query)` returns a string starting with `"https://"`. *(SC-006 enforced via FR-015.)*
- **I3**: For any non-empty `query` and `engine = Google`, `buildSearchUrl(query)` is byte-identical to Spec 009's inline output. *(SC-001 — explicit non-regression test.)*
- **I4**: A change to the persisted engine becomes visible to the next `buildSearchUrl(...)` call without app restart. *(SC-002 — `Flow.first()` re-reads on every invocation.)*
- **I5**: No DataStore migration is required by Spec 010. A user upgrading from Spec 009 sees no behavior change. *(SC-009.)*
