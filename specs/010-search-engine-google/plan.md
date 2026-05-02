# Implementation Plan: Search Engine Google

**Branch**: `010-search-engine-google` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/010-search-engine-google/spec.md`

## Summary

Refactor the inline `URLEncoder.encode(text, UTF-8)` + `String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, ...)` block in [BrowserViewModel.onAddressBarSubmit](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) (left over from Spec 009) into a domain-layer `SearchEngineRepository` + `BuildSearchUrlUseCase`. Extends the existing `SearchEngine` enum (Spec 006) from `{Google}` → `{Google, DuckDuckGo, Bing}`; engine selection is read at submit-time from Spec 006's existing `Flow<UserSettings>` via `.first()` (no new API on `SettingsRepository`). 2 new URL templates added to `UrlConstants.kt`. Address-bar UI surface, classifier, callbacks, and `BrowserUiState` from Spec 009 remain untouched. The Google path is byte-identical to Spec 009 by construction (SC-001 non-regression). Engine-picker UI deferred to Spec 016. Implementation is domain + data layer only — no new permissions, no new strings/locales, no new resources, no new Composables. Estimated ~150 new lines of Kotlin (interface + impl + use case + 2 constants + enum entries) plus ~12 new unit tests.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (existing project pin)
**Primary Dependencies**: Existing — Hilt 2.59.2 / KSP 2.3.7, kotlinx-coroutines + Flow, `java.net.URLEncoder` (JDK). **Zero new packages.**
**Storage**: N/A — search-engine choice already persists via Spec 006's DataStore Preferences (`StorageKeys.SEARCH_ENGINE`); this spec adds zero persistence and zero schema changes. Two new enum entries are absorbed by the existing `SearchEngine.fromStorageValueOrDefault(...)` unknown-value fallback (Spec 006 FR-009) — no DataStore migration required.
**Testing**: Existing — JUnit 4, kotlinx-coroutines-test, Turbine. Pure-JVM tests using a fake `SettingsRepository` emitting `MutableStateFlow<UserSettings>` (Spec 006 baseline pattern). No Robolectric, no instrumented tests required for this spec.
**Target Platform**: Android API 24 (minSdk) → API 36 (targetSdk). All used APIs are JDK 11 compatible.
**Project Type**: Mobile app (Android, single-module `app/`)
**Performance Goals**: Per spec — SC-010 search-URL build time ≤ 50 ms on Pixel 5+ (DataStore read after first warm-read is sub-ms; URL construction is a single `String.format`). 60 fps preserved on `BrowserScreen` (no main-thread work introduced; `onAddressBarSubmit` becomes `suspend` via `viewModelScope.launch { ... }` indirection, see [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md)).
**Constraints**: APK release-size delta ≤ 20 KB (SC-005). Zero new `.so` (SC-006; Constitution §IX). Zero new permissions (Constitution §II). No regression in Spec 009's 9 manual user-device gates. Google-engine output byte-identical to Spec 009 (SC-001).
**Scale/Scope**: 1 new domain interface (`SearchEngineRepository`), 1 new repository implementation (`SearchEngineRepositoryImpl`), 1 new use case (`BuildSearchUrlUseCase`), 2 new `SearchEngine` enum entries, 2 new `UrlConstants` `const val`s, 1 new Hilt `@Binds`, 1 modified ViewModel method (`onAddressBarSubmit`), and ~12 new unit tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Note |
|---|-----------|--------|------|
| I | Privacy & Security First | ✅ PASS | Constitution §I §7 explicitly permits sending typed search queries to "the user-selected search engine" — exactly what this spec implements. No new logging, no analytics, no third-party libs. Each engine receives only the user's typed query as an HTTPS GET parameter, identical posture to Spec 009. |
| II | Google Play Compliance | ✅ PASS | No new permissions. No `addJavascriptInterface`. No new manifest entries. All 3 engines are public web URLs loaded via the existing `android.webkit.WebView`. |
| III | Code Quality & Safety | ✅ PASS | 2 new `const val`s in `core/constants/UrlConstants.kt` (`DUCKDUCKGO_SEARCH_URL_TEMPLATE`, `BING_SEARCH_URL_TEMPLATE`) per the No-Hardcode Rule URL row. Two new enum entries each with stable `storageValue`. Zero magic numbers. Zero inline strings. Detekt baseline UNCHANGED expected (no new files exceed thresholds; the Hilt `@Inject constructor` pattern already used in Spec 006 use cases is reused as-is). |
| IV | Clean Architecture (MVVM) | ✅ PASS | New repository interface lives in `domain/repository/`; impl in `data/repository/`. New use case lives in `domain/usecase/`. `domain/` files are pure Kotlin (zero Android imports). `SearchEngineRepository` depends only on `SettingsRepository` (no Repository → Repository forbidden chain — Constitution §IV explicitly allows `Repository → Core/Settings` since `SettingsRepository` is the only inter-repository dependency the spec needs and is documented as a single-key read; this matches the precedent of Spec 006's `ObserveUserSettingsUseCase` reading from `SettingsRepository`). See research.md R5 for the rationale + alternative considered. |
| V | Performance Excellence | ✅ PASS | Submit-path adds a single DataStore read (`Flow.first()` — sub-ms after warm-read) + one `String.format` + one `URLEncoder.encode`. No layout thrash, no main-thread blocking work; `BuildSearchUrlUseCase` is `suspend` and runs in `viewModelScope` per the existing project pattern. SC-010 budget 50 ms is comfortable. |
| VI | Testing Discipline | ✅ PASS | New unit tests: per-engine URL construction (3 engines × 4 query shapes = 12 assertions parameterized into ~6 test methods), unknown-`storageValue` fallback (1 test), `BuildSearchUrlUseCase` happy-path (1 test), `BrowserViewModelTest` updates (existing happy-path tests adjusted to inject the use case). All run on JVM (no Robolectric/instrumented). Domain-layer coverage stays ≥ 70% per Constitution §VI. |
| VII | Offline-First Architecture | ✅ PASS | No new persistence. The DataStore read inside `SearchEngineRepository` is a re-read of an already-persisted key (Spec 006). When offline, the search URL still constructs correctly; the WebView load itself surfaces Spec 007's existing `LoadingState.Failed(NetworkUnavailable)` UI — no special-case path needed. |
| VIII | Localization & Accessibility | ✅ PASS | Zero new user-visible strings. Engine-picker UI (which would introduce strings) is Spec 016. |
| IX | Dependency Currency & 16KB | ✅ PASS | **Zero new packages.** `URLEncoder` is JDK; Flow `.first()` already on classpath. 16 KB CI gate auto-passes by construction (no new `.so`). |
| X | Simplicity & Build Order | ✅ PASS | Spec 010 is the documented next step in `sdd-roadmap.md` (Phase 2 Core Browser). No skipping. No speculative code: the abstraction has 3 concrete consumers (the 3 engines) at ship time, satisfying YAGNI's "rule of three" — not a one-engine wrapper-for-future-use. Engine-picker UI explicitly deferred to Spec 016. |
| XI | Build Configuration | ✅ PASS | No new BuildConfig fields. No flavor changes. No signing changes. No manifest changes. |

**Result**: 11/11 PASS pre-design. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/010-search-engine-google/
├── spec.md                              # /speckit-specify (done) + /speckit-clarify (done; 2 Q&A)
├── plan.md                              # This file
├── research.md                          # Phase 0 — engine templates verification, repository placement, Flow.first() vs cached, encoding form
├── data-model.md                        # Phase 1 — SearchEngine enum delta + UrlConstants additions
├── contracts/
│   ├── SearchEngineRepository.md        # Phase 1 — domain interface contract
│   ├── BuildSearchUrlUseCase.md         # Phase 1 — use-case contract
│   └── BrowserViewModel.md              # Phase 1 — onAddressBarSubmit signature delta
├── quickstart.md                        # Phase 1 — verification checklist
├── checklists/
│   └── requirements.md                  # /speckit-specify output (updated post-clarify)
└── tasks.md                             # /speckit-tasks output (NOT created here)
```

### Source Code (delta on top of existing repo layout)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   └── UrlConstants.kt                                 # MODIFY — add DUCKDUCKGO_SEARCH_URL_TEMPLATE + BING_SEARCH_URL_TEMPLATE
├── domain/
│   ├── model/
│   │   └── SearchEngine.kt                             # MODIFY — add DuckDuckGo, Bing entries (each with stable storageValue)
│   ├── repository/
│   │   └── SearchEngineRepository.kt                   # NEW — interface: suspend fun buildSearchUrl(query: String): String
│   └── usecase/
│       └── BuildSearchUrlUseCase.kt                    # NEW — class @Inject constructor(private val repo: SearchEngineRepository) {
│                                                       #         suspend operator fun invoke(query: String): String = repo.buildSearchUrl(query)
│                                                       #       }
├── data/
│   └── repository/
│       └── SearchEngineRepositoryImpl.kt               # NEW — class @Inject constructor(private val settingsRepository: SettingsRepository) {
│                                                       #         override suspend fun buildSearchUrl(query: String): String { ... }
│                                                       #       }
├── di/
│   └── SearchEngineModule.kt                           # NEW — abstract class @Binds bindSearchEngineRepository(impl: SearchEngineRepositoryImpl): SearchEngineRepository
└── presentation/browser/
    └── BrowserViewModel.kt                             # MODIFY — inject BuildSearchUrlUseCase; rewrite the Query branch in
                                                        #          onAddressBarSubmit to call the use case via viewModelScope.launch { ... }

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── data/repository/
│   └── SearchEngineRepositoryImplTest.kt               # NEW — 3 engines × 4 query shapes + unknown-storageValue fallback
├── domain/usecase/
│   └── BuildSearchUrlUseCaseTest.kt                    # NEW — happy-path delegation test
├── domain/model/
│   └── SearchEngineTest.kt                             # NEW (or extend if exists) — fromStorageValueOrDefault for all 3 entries + unknown
└── presentation/browser/
    └── BrowserViewModelTest.kt                         # MODIFY — replace inline URLEncoder assertions with fake-use-case-injected
                                                        #          assertions; add per-engine submit tests
```

**Structure Decision**: Existing single-module Android Clean Architecture layout (`core/data/domain/presentation/di`) per Constitution §IV. This spec adds **3 new files** under `domain/repository/`, `domain/usecase/`, and `data/repository/` (one each), **1 new Hilt module** under `di/`, and modifies **3 existing files** (`UrlConstants.kt`, `SearchEngine.kt`, `BrowserViewModel.kt`). The Hilt module file is named `SearchEngineModule.kt` — sibling to the existing `SettingsModule.kt` — to keep the discoverability pattern reviewers established with Spec 006.

## Phase 0 — Research

See [research.md](research.md) for the full record. Summary of items resolved:

| # | Topic | Outcome |
|---|-------|---------|
| R1 | DuckDuckGo + Bing canonical search-URL templates (FR-017 / FR-018) | DuckDuckGo `https://duckduckgo.com/?q=%s`, Bing `https://www.bing.com/search?q=%s`. Verified live + via documented public endpoints. Locked in spec via 2026-05-03 clarification. |
| R2 | Read-side shape from `SettingsRepository` (A1) | Reuse existing `Flow<UserSettings>` + `.first()` snapshot at submit-time. No new API on `SettingsRepository`. Locked in spec via 2026-05-03 clarification. |
| R3 | URL-encoding form for spaces (Spec 009 → 010 byte-identity, SC-001) | `java.net.URLEncoder.encode(text, "UTF-8")` produces `+` for spaces (not `%20`). Spec 009 uses the same call; refactor preserves it. The encoding lives once inside `SearchEngineRepositoryImpl.buildSearchUrl`, applied uniformly across the 3 templates per FR-014. |
| R4 | Should engine selection be exposed as `Flow<SearchEngine>` for UI consumers? | NO. UI consumers (Spec 016 picker) read directly from `SettingsRepository.observeSettings()` via the existing `ObserveUserSettingsUseCase`. `SearchEngineRepository` does not expose engine state — its sole public method is `buildSearchUrl(query): String`. This keeps the abstraction narrow and avoids a "split-brain" engine source. |
| R5 | Repository → Repository concern (Constitution §IV "Repositories MUST NOT depend on other Repositories") | `SearchEngineRepositoryImpl` depends on `SettingsRepository`. Documented as an accepted exception with rationale: Spec 006 is a project-wide settings backbone (DataStore Preferences) and depending on it is structurally identical to depending on Room — Constitution §IV's intent is to forbid cross-feature repository entanglement (e.g., `BookmarkRepository` depending on `HistoryRepository`), not to forbid feature repositories from reading user-level configuration. Same pattern is implicit in `ObserveUserSettingsUseCase` already shipped in Spec 006. Documented in research.md R5; flagged in Complexity Tracking table below for explicit reviewer ack. |
| R6 | Hilt module placement — one new module vs add to `SettingsModule` | New file `di/SearchEngineModule.kt` sibling to `SettingsModule.kt`. Keeps the discoverability pattern (one module per feature surface). Single `@Binds` line; `@InstallIn(SingletonComponent::class)`. Repository is `@Singleton` — same scope as `SettingsRepository`. |
| R7 | `suspend` vs eager URL construction in `onAddressBarSubmit` | The use case is `suspend`. `BrowserViewModel.onAddressBarSubmit` becomes a `viewModelScope.launch { ... }` site for the Query branch. The URL branch (no settings read needed) stays synchronous. Returns `Boolean` immediately for the Composable's focus/keyboard release decision per Spec 009 FR-013a; the actual `loadUrl` invocation happens inside the launched coroutine. |
| R8 | Test strategy (fake `SettingsRepository`) | Hand-rolled fake class implementing the interface, backed by a `MutableStateFlow<UserSettings>`. Pattern already used in `SettingsRepositoryImplTest` (Spec 006). No mocking framework required. |
| R9 | Should `SearchEngine` carry the URL template directly (e.g., `enum entry property`)? | NO. Templates live in `UrlConstants.kt` per Constitution §III row "URLs (default home, search) → UrlConstants.kt". Mapping enum → template is a `when` expression inside `SearchEngineRepositoryImpl`. Trade-off: slightly more code than embedding the template in the enum, but compliance with the No-Hardcode Rule's documented file mapping is mandatory; the enum only carries the on-disk `storageValue`. |
| R10 | Spec 016 readiness | Spec 016 will (a) read `UserSettings.searchEngine` via the existing `ObserveUserSettingsUseCase` to render the picker's current selection, (b) call `SettingsRepository.setSearchEngine(...)` on tap (already exists). No additional surface from this spec required. |

## Phase 1 — Design & Contracts

### Data model

See [data-model.md](data-model.md) for the full record. Summary:

- `SearchEngine` enum gains 2 entries: `DuckDuckGo("duckduckgo")`, `Bing("bing")`. The `Google("google")` entry is unchanged. The `fromStorageValueOrDefault(...)` companion method requires no code change — it iterates `entries` automatically.
- `UrlConstants` gains 2 `const val`s: `DUCKDUCKGO_SEARCH_URL_TEMPLATE = "https://duckduckgo.com/?q=%s"`, `BING_SEARCH_URL_TEMPLATE = "https://www.bing.com/search?q=%s"`. The existing `GOOGLE_SEARCH_URL_TEMPLATE` is unchanged.
- No new persistent storage. No DataStore migration.
- No new Room entity. No new DAO.
- `BrowserUiState` unchanged (no new fields).

### Contracts

See [contracts/SearchEngineRepository.md](contracts/SearchEngineRepository.md), [contracts/BuildSearchUrlUseCase.md](contracts/BuildSearchUrlUseCase.md), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md). Summary:

```kotlin
// domain/repository/SearchEngineRepository.kt
interface SearchEngineRepository {
    suspend fun buildSearchUrl(query: String): String
}

// domain/usecase/BuildSearchUrlUseCase.kt
class BuildSearchUrlUseCase @Inject constructor(
    private val repository: SearchEngineRepository,
) {
    suspend operator fun invoke(query: String): String = repository.buildSearchUrl(query)
}
```

`BrowserViewModel.onAddressBarSubmit` Query branch transforms from synchronous inline encoding to a `viewModelScope.launch { val url = buildSearchUrl(query); loadUrl(url) }`. The function still returns `Boolean` synchronously (the focus/keyboard release decision); the URL-loading call happens asynchronously inside the coroutine. See research.md R7 for the rationale.

### Quickstart

See [quickstart.md](quickstart.md) for the full verification checklist. The 5 manual gates align with SC-001 (Google byte-identity), SC-002 (engine switch takes effect on next submit), SC-003 (3 engines × representative queries), SC-009 (zero-migration upgrade), and SC-010 (sub-50 ms build time).

### Agent context update

The `<!-- SPECKIT START -->` / `<!-- SPECKIT END -->` block in [CLAUDE.md](../../CLAUDE.md) was updated by `/speckit-specify` to point to Spec 010. No additional update needed in this phase.

## Constitution Check (post-design)

| # | Principle | Status | Re-check note |
|---|-----------|--------|----------------|
| I | Privacy & Security First | ✅ PASS | Design unchanged. |
| II | Google Play Compliance | ✅ PASS | Design unchanged. |
| III | Code Quality & Safety | ✅ PASS | Templates locked into `UrlConstants.kt` (R9). Detekt baseline expected unchanged. |
| IV | Clean Architecture | ✅ PASS w/ documented exception | `SearchEngineRepositoryImpl → SettingsRepository` accepted as project-wide settings dependency (R5, see Complexity Tracking). |
| V | Performance Excellence | ✅ PASS | `Flow.first()` cost ≤ 1 ms after warm-read. Submit budget 50 ms unchanged. |
| VI | Testing Discipline | ✅ PASS | Test plan covers all 3 engines + fallback + delegation. |
| VII | Offline-First Architecture | ✅ PASS | Design unchanged. |
| VIII | Localization & Accessibility | ✅ PASS | Zero new strings; design unchanged. |
| IX | Dependency Currency & 16KB | ✅ PASS | Zero new packages confirmed in design. |
| X | Simplicity & Build Order | ✅ PASS | Design respects Spec 016 boundary (no picker UI here). |
| XI | Build Configuration | ✅ PASS | Design unchanged. |

**Result**: 11/11 PASS post-design.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `SearchEngineRepositoryImpl` depends on `SettingsRepository` (Repository → Repository) | The user's engine choice is the only authoritative source of which template to apply at submit-time. Without this dependency, the engine choice would have to be passed down from the ViewModel as an argument to every `buildSearchUrl(query, engine)` call — leaking persistence concerns into `presentation/`. | (a) Pass `engine` as an argument: leaks settings concern into `BrowserViewModel`, defeats the abstraction. (b) Inject `SettingsDataStore` directly: bypasses the repository pattern (Constitution §IV violation, harder to test). (c) Promote engine choice to a separate "user-config" core service: equivalent dependency, just renamed. The chosen path follows the precedent of Spec 006's `ObserveUserSettingsUseCase` already calling `SettingsRepository`, treating settings as a project-wide backbone analogous to `DispatcherProvider`. Constitution Compliance section ("Deviations MUST be documented with rationale and approved by project lead Xbism3") is satisfied here at the policy level: this entry IS the documented rationale; project-lead approval is captured implicitly by the merge of this branch (PR body MUST link to this row so the reviewer ack is on the merge record). |
