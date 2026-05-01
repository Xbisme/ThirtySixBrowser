# Implementation Plan: DataStore Settings — Persistent User Preferences Foundation

**Branch**: `006-datastore-settings` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-datastore-settings/spec.md`

## Summary

Spec 006 introduces the on-device user-preferences persistence layer using **DataStore Preferences 1.2.1** (verified `developer.android.com/jetpack/androidx/releases/datastore` 2026-05-01) wired into a **full Clean Architecture data slice** — `data/local/datastore` source + `data/mapper` + `data/repository` impl + `domain/model` + `domain/repository` interface + `domain/usecase` (5 use cases) + Hilt `SettingsModule`. This is the project's first complete repository-pattern implementation per Constitution §IV.

Four persistent settings ship in v1.0:

| Key | Domain type | DataStore type | Default | Driver spec |
|---|---|---|---|---|
| `theme_mode` | `ThemeMode` (sealed enum) | `String` | `ThemeMode.System` | Spec 003 cleanup (FR-020) |
| `language_override` | `LanguageOverride` (sealed) | `String?` (null = `FollowSystem`) | `LanguageOverride.FollowSystem` | Spec 016 in-app picker |
| `search_engine` | `SearchEngine` (enum, 1 entry) | `String` | `SearchEngine.Google` | Spec 010 / 016 |
| `is_onboarding_completed` | `Boolean` | `Boolean` | `false` | Spec 017 / 018 |

Two clarifications resolved (see [spec.md § Clarifications](spec.md#clarifications)):

- **Q1** — Setters return `Result<Unit>` from existing `core/result/` wrapper (Spec 002). Disk-write exceptions mapped to `AppError.Database` via existing `AppError.from()`.
- **Q2** — Language "no override" represented as sealed type `LanguageOverride { FollowSystem; Explicit(bcp47) }` in `domain/model/`, never raw `String?` at the repository boundary.

Eleven concrete deliverables:

1. **Add** `androidx.datastore:datastore-preferences:1.2.1` to `gradle/libs.versions.toml` + wire `implementation(...)` in `app/build.gradle.kts`. Pure Kotlin/Java, zero `.so` → 16 KB CI gate auto-passes.
2. **Add** 4 new files under `core/constants/` per Constitution §III No-Hardcode Rule:
   - `StorageKeys.kt` — DataStore preference key strings
   - `AppDefaults.kt` — default values for all 4 settings
   - Extend `AppConstants.kt` with `SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"`
3. **Move** `presentation/theme/ThemeMode.kt` → `domain/model/ThemeMode.kt` (FR-021). Keep enum surface identical; update the single import in `MainActivity.kt`.
4. **Add** 3 new domain model files under `domain/model/`:
   - `LanguageOverride.kt` (sealed interface + `FollowSystem` object + `Explicit(bcp47: String)` data class)
   - `SearchEngine.kt` (enum, single entry `Google`)
   - `UserSettings.kt` (data class — 4 fields)
5. **Add** `domain/repository/SettingsRepository.kt` interface — `observeSettings(): Flow<UserSettings>` + 4 setters returning `Result<Unit>`.
6. **Add** `data/local/datastore/SettingsDataStore.kt` — thin DataStore wrapper exposing `Flow<Preferences>` + 4 typed `suspend` setters that return `Result<Unit>` (catches `IOException`, maps via `AppError.from()`).
7. **Add** `data/mapper/SettingsMapper.kt` — `Preferences → UserSettings` + per-field encode/decode helpers (handles missing-key fallback to `AppDefaults`, unknown-enum fallback per FR-009).
8. **Add** `data/repository/SettingsRepositoryImpl.kt` — bind to `SettingsDataStore` via constructor injection, expose mapped `Flow<UserSettings>` via `.map { mapper.toDomain(it) }.distinctUntilChanged()`.
9. **Add** 5 use case files under `domain/usecase/`:
   - `ObserveUserSettingsUseCase.kt` (single-method observer)
   - `SetThemeModeUseCase.kt`, `SetLanguageOverrideUseCase.kt`, `SetSearchEngineUseCase.kt`, `SetOnboardingCompletedUseCase.kt`
10. **Add** top-level `app/.../di/SettingsModule.kt` — Hilt `@InstallIn(SingletonComponent::class)` providing `DataStore<Preferences>` singleton (file `thirtysix_settings`, scope tied to app lifetime), binding `SettingsRepository → SettingsRepositoryImpl`.
11. **Modify** `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` per FR-013/014 — explicitly **INCLUDE** the DataStore Preferences file in cloud backup + device-to-device transfer (contrast: Spec 005 excludes the database). Add inline comment cross-referencing the policy difference.
12. **Modify** `MainActivity.kt` (FR-020) — replace in-memory `MutableState<ThemeMode>` with `ObserveUserSettingsUseCase` injection + `collectAsStateWithLifecycle()`; pass `themeMode` from the snapshot to `ThirtySixTheme`.

Plus tests (~10 new unit tests across ~5 test files): `SettingsMapperTest`, `SettingsDataStoreTest`, `SettingsRepositoryImplTest`, `UseCaseTests` (parameterized), `SettingsModuleSmokeTest`.

No new packages with native shared libraries → 16 KB page size CI gate auto-passes.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (compiled via AGP 9.1.1's built-in Kotlin support); Java 11 target; KSP 2.3.7.
**Primary Dependencies**: DataStore Preferences 1.2.1 (released 2026-03-11, verified `developer.android.com` 2026-05-01) — single artifact `androidx.datastore:datastore-preferences:1.2.1`. Pure Kotlin/Java; transitively pulls `androidx.datastore:datastore-core:1.2.1` (also Kotlin-only) and `kotlinx-coroutines-core` (already in catalog from Spec 002 / 005). Hilt 2.59.2, KSP 2.3.7, Turbine 1.2.1, Robolectric 4.16.1, JUnit 4.13.2 — all already in catalog. `kotlinx-coroutines-test` 1.10.2 already in catalog.
**Storage**: On-device app-private DataStore Preferences file at the framework-default path (`/data/data/com.raumanian.thirtysix.browser.{debug?}/files/datastore/thirtysix_settings.preferences_pb`); single backing protobuf file managed entirely by DataStore (no schema-export equivalent — keys are loosely typed at storage boundary, strongly typed at the domain boundary via `SettingsMapper`).
**Testing**: Unit tests using `PreferenceDataStoreFactory.create(scope, file)` with JUnit `@TempFolder` rule (or programmatic `kotlin.io.path.createTempDirectory`) — pure JVM, **no Robolectric required**. `kotlinx-coroutines-test` `runTest { }` blocks with `StandardTestDispatcher` for deterministic concurrency tests. Turbine for `Flow` `awaitItem()` assertions in repository observer tests.
**Target Platform**: Android 7.0+ (`minSdk = 24`). DataStore 1.x supports `minSdk = 21` — comfortably below project floor.
**Project Type**: Mobile (single-module Android app, package `com.raumanian.thirtysix.browser`).
**Performance Goals**: SC-002 first-frame settings availability — DataStore cold-load is async; the first emission of `Flow<UserSettings>` MUST contain document defaults within ~50 ms on Pixel 5 (DataStore opens the file lazily on first collect). MainActivity uses `collectAsStateWithLifecycle()` with an initial value of `UserSettings.DEFAULT` so the first composition does NOT block on disk; the recomposition triggered by the first real emission is what FR-020 + SC-001 require to render with persisted theme. SC-003 stream emission ≤ 50 ms after write completion.
**Constraints**: Constitution §IX 16 KB page size — DataStore Preferences contains zero `.so` files; existing 16 KB CI gate continues to verify only `libandroidx.graphics.path.so` from Compose, already aligned 0x4000. Constitution §III No-Hardcode Rule — DataStore file name + 4 preference keys + 4 default values all live in `core/constants/`. Constitution §IV — Repository pattern strictly enforced; ViewModels (and `MainActivity`) never touch `DataStore<Preferences>` directly. Constitution §VII offline-first — DataStore writes are atomic via the underlying protobuf single-file write (DataStore implementation guarantees) → no partial-write corruption risk.
**Scale/Scope**: 4 settings keys (≤ 64 bytes each on disk); single-process, single-user; expected file size on disk < 1 KB even after onboarding-completed flag is set; ~17 source files net (11 production + ~5 test + 1 modified backup XML pair); APK release-size delta target < 200 KB (DataStore Preferences runtime ~150 KB dexed + R8-shrunk).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.2.0 — all 11 principles evaluated.

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Privacy & Security First | ✅ PASS | All 4 settings live on-device only; FR-003 explicitly forbids transmission. **Settings ARE included in OS Auto Backup (FR-013)** — this is policy-asymmetric vs Spec 005 (DB excluded) but does NOT violate §I. The OS backup channel is user-controlled via system-level toggles, not a ThirtySix-controlled server; backed-up settings contain no PII (theme preference, language tag, search-engine identifier, boolean flag). Per-store policy difference is documented inline in `backup_rules.xml` (FR-014) so future maintainers see the contrast. |
| II | Google Play Compliance | ✅ PASS | DataStore is an AndroidX official library — Play-approved. No new permissions. App stays in Tools/Productivity. No `addJavascriptInterface` / WebView surface affected. |
| III | Code Quality & Safety / No-Hardcode Rule | ✅ PASS | DataStore file name moves to `AppConstants.SETTINGS_DATASTORE_FILE_NAME`. 4 preference keys live in **new** `core/constants/StorageKeys.kt` (Constitution §III explicitly names this file). 4 default values live in **new** `core/constants/AppDefaults.kt` (Constitution §III explicitly names this file). No magic numbers introduced (timeout / size constants n/a — DataStore is pull-based). Detekt MagicNumber baseline UNCHANGED. |
| IV | Clean Architecture (MVVM) | ✅ PASS — **first full repository slice** | Layer separation strictly enforced: `data/local/datastore/` source + `data/mapper/` + `data/repository/Impl/` (data); `domain/model/` (4 new pure-Kotlin files) + `domain/repository/` interface + `domain/usecase/` (5 files) (domain — zero Android imports verified at lint time); `presentation/` only consumes via the use case (MainActivity). `MainActivity` (a `ComponentActivity`, not a ViewModel) injects the use case via Hilt `@AndroidEntryPoint`; per Constitution §IV the rule "ViewModels NEVER access DAOs/DataStore directly" applies to the persistence boundary, which use case + repository satisfy. Hilt `@Module` placed in top-level `app/.../di/SettingsModule.kt` (parallel to Spec 005's `DatabaseModule`). |
| V | Performance Excellence | ✅ PASS | Settings access is read-mostly + low-frequency-write — no 60 fps surface impact. Cold-start budget: the asynchronous Flow-based first emission (target < 50 ms p50) is bridged by `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)` so the first composition is non-blocking and visually correct under FR-008 (defaults). MainActivity recomposes once when persisted values arrive — within Spec 003's existing windowBackground-flash-fix budget. APK delta < 200 KB (SC-008). |
| VI | Testing Discipline | ✅ PASS | ≥ 70% coverage target on `data/` + `domain/` met by 10+ new unit tests covering: mapper round-trips (incl. unknown-enum fallback per FR-009), DataStore wrapper writes/reads (incl. concurrent writes per US6), repository observer (Turbine), use case command/query split, Hilt module smoke. **No Robolectric needed** — DataStore tests use pure JVM `PreferenceDataStoreFactory.create(scope, file)` with `@TempFolder`, faster than Spec 005's Robolectric Room tests. Lint, Detekt, ktlint gates unaffected (SC-007). Negative-path schema-rename test (US7 / FR-019) covered by a single fixture-based unit test. |
| VII | Offline-First | ✅ PASS | All 4 settings operate fully offline by definition. DataStore writes are atomic via the underlying protobuf-file write contract (single-file replace; DataStore guarantees no partial-write corruption). Process-death survival is intrinsic to DataStore (file-backed). Constitution §VII says "Database backups MAY be opt-in" — settings backup matches that opt-in baseline (default-on for AndroidManifest's `allowBackup` is the OS default; we keep it on for settings, opt-out via `<exclude>` for DB). |
| VIII | Localization & Accessibility | ✅ PASS | No new user-facing strings. No UI added. The `LanguageOverride` model **enables** Spec 016's locale switcher to honor Constitution §VIII "Locale switching MUST take effect without app restart" — the per-process recomposition triggered when `UserSettings.languageOverride` changes is the substrate Spec 016 will build the `AppCompatDelegate.setApplicationLocales` call on. Accessibility n/a at the persistence layer. |
| IX | Dependency Currency & 16KB Page Size | ✅ PASS | DataStore Preferences 1.2.1 verified latest stable on `developer.android.com/jetpack/androidx/releases/datastore` 2026-05-01 ([research.md R1](research.md)). Pure Kotlin/Java — **zero `.so` files** introduced. The existing 16 KB CI gate continues to verify only `libandroidx.graphics.path.so` from Compose, already aligned 0x4000. DataStore 1.3.0-alpha08 (2026-04-22) explicitly rejected per Constitution §IX "latest **stable**" rule. |
| X | Simplicity & Build Order | ✅ PASS | Spec 006 is in Phase 1 (Foundation), the **last** Phase 1 spec. Unblocks Phase 2 Spec 007 (`webview-compose-wrapper`) which requires both 005 + 006 complete. YAGNI: NO `default_home_url` (no Spec 016 UX driver yet); NO `tracker_blocker_enabled` (Spec 019 optional); NO encryption layer (no sensitive keys); NO multi-process support; NO Proto DataStore (Preferences variant chosen per CLAUDE.md). The 5 use cases are the minimum to give every consumer a 1-method API surface — no speculative `BatchUpdateUseCase` or generic-setter abstraction. |
| XI | Build Configuration | ✅ PASS | No flavor changes. New dep lives in `gradle/libs.versions.toml` under new `datastore` version key + new `[libraries]` entry. No `BuildConfig` field changes. R8 / minify / shrink rules unaffected (DataStore generates no Kotlin code; default DataStore ProGuard rules ship with the artifact and are auto-included by AGP). Signing config (debug-fallback per §XI v1.2.0) unaffected. |

**Result**: 11/11 PASS. No Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/006-datastore-settings/
├── plan.md                         # This file
├── spec.md                         # Feature spec + Clarifications session 2026-05-01 (Q1, Q2)
├── research.md                     # Phase 0 — DataStore version, file-name policy, backup-rules format, Q1/Q2 implementation details, schema-evolution rule
├── data-model.md                   # Phase 1 — UserSettings + 3 supporting domain types, on-disk Preferences shape, mapper rules
├── quickstart.md                   # Phase 1 — verification playbook (8 gates)
├── contracts/
│   ├── SettingsRepository.kt       # Repository interface signature
│   ├── SettingsDataStore.kt        # DataStore wrapper signature
│   ├── SettingsModule.kt           # Hilt module signature
│   ├── ObserveUserSettingsUseCase.kt
│   ├── SetThemeModeUseCase.kt
│   ├── SetLanguageOverrideUseCase.kt
│   ├── SetSearchEngineUseCase.kt
│   └── SetOnboardingCompletedUseCase.kt
├── checklists/
│   └── requirements.md             # Quality checklist (16/16 PASS)
└── tasks.md                        # Phase 2 — generated by /speckit-tasks (NOT this command)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   └── constants/
│       ├── AppConstants.kt          [MODIFY — add SETTINGS_DATASTORE_FILE_NAME]
│       ├── StorageKeys.kt           [NEW — preference key names]
│       └── AppDefaults.kt           [NEW — 4 default values]
├── data/
│   ├── local/
│   │   └── datastore/
│   │       └── SettingsDataStore.kt [NEW — DataStore wrapper]
│   ├── mapper/
│   │   └── SettingsMapper.kt        [NEW — Preferences ↔ UserSettings]
│   └── repository/
│       └── SettingsRepositoryImpl.kt [NEW]
├── domain/
│   ├── model/
│   │   ├── ThemeMode.kt             [MOVE from presentation/theme/ — FR-021]
│   │   ├── LanguageOverride.kt      [NEW — sealed interface]
│   │   ├── SearchEngine.kt          [NEW — enum, 1 entry]
│   │   └── UserSettings.kt          [NEW — data class snapshot]
│   ├── repository/
│   │   └── SettingsRepository.kt    [NEW — interface]
│   └── usecase/
│       ├── ObserveUserSettingsUseCase.kt        [NEW]
│       ├── SetThemeModeUseCase.kt               [NEW]
│       ├── SetLanguageOverrideUseCase.kt        [NEW]
│       ├── SetSearchEngineUseCase.kt            [NEW]
│       └── SetOnboardingCompletedUseCase.kt     [NEW]
├── presentation/
│   └── theme/
│       └── ThemeMode.kt             [DELETE — moved to domain/model/]
├── di/
│   └── SettingsModule.kt            [NEW — Hilt @InstallIn(SingletonComponent)]
└── MainActivity.kt                  [MODIFY — inject ObserveUserSettingsUseCase, replace MutableState]

app/src/main/res/xml/
├── backup_rules.xml                 [MODIFY — keep DB excludes; document settings INCLUDED per FR-013/014]
└── data_extraction_rules.xml        [MODIFY — same as above]

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── data/
│   ├── local/datastore/
│   │   ├── SettingsDataStoreTest.kt           [NEW — read/write/concurrent]
│   │   └── SettingsDataStoreFactory.kt        [NEW — test fixture using @TempFolder]
│   ├── mapper/
│   │   └── SettingsMapperTest.kt              [NEW — round-trip + unknown-enum fallback + missing-key]
│   └── repository/
│       └── SettingsRepositoryImplTest.kt      [NEW — Turbine observer + setter Result<Unit>]
├── domain/usecase/
│   └── SettingsUseCasesTest.kt                [NEW — parameterized over 4 setters + observe]
└── di/
    └── SettingsModuleSmokeTest.kt             [NEW — Hilt structural smoke]

gradle/
└── libs.versions.toml               [MODIFY — add datastore = "1.2.1" + library coord]

app/
└── build.gradle.kts                 [MODIFY — add implementation(libs.androidx.datastore.preferences)]
```

**Structure Decision**: Mobile (single-module Android). Strictly follows Constitution §IV layered layout already established by Specs 002/005. Spec 006 is the first spec to populate `domain/model/` + `domain/repository/` + `domain/usecase/` + `data/repository/` + `data/mapper/` + `data/local/datastore/` simultaneously — establishing the canonical pattern that Specs 011/013/014/016 will follow. Hilt module placement: top-level `app/.../di/SettingsModule.kt` parallel to Spec 005's `DatabaseModule.kt`, NOT `core/di/` (which holds Spec 002's `DispatcherModule.kt` for cross-cutting concerns).

## Complexity Tracking

> No Constitution Check violations. Section intentionally empty.
