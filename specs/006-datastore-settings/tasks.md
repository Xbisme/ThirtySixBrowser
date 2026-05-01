---
description: "Task list for Spec 006 — DataStore Settings (Persistent User Preferences Foundation)"
---

# Tasks: DataStore Settings — Persistent User Preferences Foundation

**Input**: Design documents from `/specs/006-datastore-settings/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: INCLUDED — Constitution §VI mandates unit tests for repositories, ViewModels, use cases, and mappers; Spec 006 ships ~10 new unit tests across 5 test files. Test tasks appear within each user-story phase that they exercise.

**Organization**: Tasks grouped by user story (US1–US7) for independent verification. The shared persistence infrastructure is in Foundational because every user story depends on it; user-story phases add the verification (tests) plus story-specific code (only US1 has new production code beyond foundational — the MainActivity replacement).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency)
- **[Story]**: Maps to spec.md user stories (US1–US7); omitted for Setup / Foundational / Polish
- All file paths are repository-relative

## Path conventions

- App source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- App tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/...`
- Build config: `gradle/libs.versions.toml`, `app/build.gradle.kts`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the DataStore Preferences dependency to the version catalog and Gradle build, then verify the build is still green before any code change lands.

- [X] T001 Add `datastore` version key (`= "1.2.1"`) and `androidx-datastore-preferences` library coordinate to `gradle/libs.versions.toml`. See [research.md R1](research.md#r1--datastore-version--16-kb-compliance) for the exact lines (group `androidx.datastore`, name `datastore-preferences`).
- [X] T002 Add `implementation(libs.androidx.datastore.preferences)` to the `dependencies { }` block in `app/build.gradle.kts` (alphabetical position with other AndroidX `implementation(...)` lines).
- [X] T003 Run `./gradlew clean assembleDebug` to confirm the new dep resolves and the project still builds with no other code changes. Expected: ✅ green.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The full Clean Architecture data slice that every user story consumes — constants, domain models, repository interface + impl, DataStore wrapper, mapper, all 5 use cases, Hilt module, and the backup-rules XML asymmetry. After Phase 2, the wiring is complete; user-story phases verify behavior and (only US1) replace MainActivity's in-memory state.

**⚠️ CRITICAL**: No user-story phase may start until Phase 2 is complete and `./gradlew testDebugUnitTest` is still green at the existing 51-test baseline.

### 2.1 — Constants (Constitution §III No-Hardcode Rule)

- [X] T004 [P] Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt`. Add `const val SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"` to the existing `AppConstants` object. See [data-model.md § On-disk DataStore Preferences shape](data-model.md#on-disk-datastore-preferences-shape) for the file-name policy.
- [X] T005 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/StorageKeys.kt`. Declare 4 `Preferences.Key<*>` constants (`THEME_MODE`, `LANGUAGE_OVERRIDE`, `SEARCH_ENGINE`, `IS_ONBOARDING_COMPLETED`) inside an `object StorageKeys`. Prepend the schema-evolution-rules doc-comment block from [research.md R7](research.md#r7--schema-evolution-rule-fr-019--implementation) (4 numbered rules + reference to the negative-path test). See exact key names in [data-model.md § Key inventory](data-model.md#key-inventory).

### 2.2 — Domain models (pure Kotlin, ZERO Android imports)

- [X] T006 [P] [FR-021] Move `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/ThemeMode.kt` → `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/ThemeMode.kt` via `git mv` to preserve history. Update the file: change `package` to `com.raumanian.thirtysix.browser.domain.model`; add `(val storageValue: String)` constructor + per-variant values `Light("light"), Dark("dark"), System("system")`; add `companion object { fun fromStorageValueOrDefault(value: String?): ThemeMode }`. See exact shape in [data-model.md § ThemeMode](data-model.md#thememode--enum-relocated-from-presentationtheme).
- [X] T007 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/LanguageOverride.kt`. Define `sealed interface LanguageOverride { data object FollowSystem; data class Explicit(val bcp47: String) }`. See [data-model.md § LanguageOverride](data-model.md#languageoverride--sealed-interface-q2-clarification).
- [X] T008 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/SearchEngine.kt`. Define `enum class SearchEngine(val storageValue: String) { Google("google") }` + `companion object { fun fromStorageValueOrDefault(value: String?): SearchEngine }`. See [data-model.md § SearchEngine](data-model.md#searchengine--enum-extensible).
- [X] T009 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppDefaults.kt`. Declare `object AppDefaults` with 4 fields (`THEME_MODE`, `LANGUAGE_OVERRIDE`, `SEARCH_ENGINE`, `IS_ONBOARDING_COMPLETED`). Imports the 3 domain enums from T006/T007/T008. Depends on: T006, T007, T008.
- [X] T010 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/UserSettings.kt`. Declare `data class UserSettings(...)` with 4 fields + `companion object { val DEFAULT = UserSettings(...) }` reading from `AppDefaults`. Depends on: T009.

### 2.3 — Repository interface

- [X] T011 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/SettingsRepository.kt`. Match the contract from [contracts/SettingsRepository.kt](contracts/SettingsRepository.kt) verbatim: `observeSettings(): Flow<UserSettings>` + 4 `suspend ... : Result<Unit>` setters. Depends on: T010 (UserSettings type).

### 2.4 — Data layer

- [X] T012 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/datastore/SettingsDataStore.kt`. Match [contracts/SettingsDataStore.kt](contracts/SettingsDataStore.kt) verbatim: `@Singleton` class, constructor-injected `DataStore<Preferences>`, expose `val data: Flow<Preferences>`, 4 typed setters returning `Result<Unit>` via `editCatching` helper that catches `IOException` only (NOT `CancellationException`) and maps via `AppError.from(io)`. Imports `core.constants.StorageKeys`, `core.error.AppError`, `core.result.Result`, the 3 domain enums. Depends on: T005, T006, T007, T008.
- [X] T013 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/SettingsMapper.kt`. `class SettingsMapper @Inject constructor()` with `fun toDomain(prefs: Preferences): UserSettings` reading 4 keys via `prefs[StorageKeys.*]`, falling back to `AppDefaults` per key (`ThemeMode.fromStorageValueOrDefault(...)`, `String?.toLanguageOverride()`, `SearchEngine.fromStorageValueOrDefault(...)`, `?: AppDefaults.IS_ONBOARDING_COMPLETED`). Add private extension `fun String?.toLanguageOverride()`. See [data-model.md § Mapper rules](data-model.md#mapper-rules-datamappersettingsmapperkt) for the exact body. Depends on: T005, T009, T010.
- [X] T014 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/SettingsRepositoryImpl.kt`. `@Singleton class SettingsRepositoryImpl @Inject constructor(private val dataStore: SettingsDataStore, private val mapper: SettingsMapper) : SettingsRepository`. Implement `observeSettings()` as `dataStore.data.map { mapper.toDomain(it) }.distinctUntilChanged()`. Implement 4 setters as 1-line delegations to `dataStore.set*(...)`. Depends on: T011, T012, T013.

### 2.5 — Use cases (5 files, all 1-method delegations)

- [X] T015 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveUserSettingsUseCase.kt`. Match [contracts/ObserveUserSettingsUseCase.kt](contracts/ObserveUserSettingsUseCase.kt) verbatim. Depends on: T011 (SettingsRepository interface only — implementation can be wired later).
- [X] T016 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SetThemeModeUseCase.kt`. Match [contracts/SetThemeModeUseCase.kt](contracts/SetThemeModeUseCase.kt) verbatim. Depends on: T011.
- [X] T017 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SetLanguageOverrideUseCase.kt`. Match [contracts/SetLanguageOverrideUseCase.kt](contracts/SetLanguageOverrideUseCase.kt). Depends on: T011.
- [X] T018 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SetSearchEngineUseCase.kt`. Match [contracts/SetSearchEngineUseCase.kt](contracts/SetSearchEngineUseCase.kt). Depends on: T011.
- [X] T019 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SetOnboardingCompletedUseCase.kt`. Match [contracts/SetOnboardingCompletedUseCase.kt](contracts/SetOnboardingCompletedUseCase.kt). Depends on: T011.

### 2.6 — Hilt wiring

- [X] T020 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/SettingsModule.kt`. Match [contracts/SettingsModule.kt](contracts/SettingsModule.kt) verbatim — two modules in one file: `SettingsDataStoreProviderModule` (`@Provides @Singleton fun provideSettingsDataStore(...)`) + `SettingsRepositoryBindingModule` (`@Binds @Singleton abstract fun bindSettingsRepository(impl): SettingsRepository`). Use `Context.preferencesDataStoreFile(AppConstants.SETTINGS_DATASTORE_FILE_NAME)` for file location; `dispatcherProvider.io + SupervisorJob()` for scope. Depends on: T004, T011, T014.

### 2.7 — Backup-rules XML (FR-013, FR-014, SC-005)

- [X] T021 Modify `app/src/main/res/xml/backup_rules.xml`. Replace the existing comment block with the asymmetric-policy comment from [research.md R3](research.md#r3--backup-posture-for-settings-file-fr-013-fr-014). Keep the 3 existing DB excludes; add `<include domain="file" path="datastore/thirtysix_settings.preferences_pb"/>` after them inside the `<full-backup-content>` element.
- [X] T022 Modify `app/src/main/res/xml/data_extraction_rules.xml`. Same comment update + same `<include>` line added inside BOTH `<cloud-backup>` and `<device-transfer>` elements (keep existing DB excludes in each).

### 2.8 — Foundation checkpoint

- [X] T023 Run `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` to confirm Phase 2 wiring is green and Hilt graph is valid. Expected: 51/51 existing tests still pass; build succeeds with no Kotlin compile errors. **Foundation ready — user story phases may now begin.**

---

## Phase 3: User Story 1 — Theme Choice Survives App Restart (Priority: P1) 🎯 MVP

**Goal**: Replace MainActivity's in-memory `MutableState<ThemeMode>` (Spec 003) with a Hilt-injected `ObserveUserSettingsUseCase` collected via `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)`. Persisted theme survives process death and renders on first composition without flash.

**Independent Test**: Manual emulator gate (Gate 8 in [quickstart.md](quickstart.md#gate-8--manual-smoke-on-emulator-visual)) — adb-write `theme_mode = "dark"` to the DataStore file via `run-as` shell, force-stop, relaunch, observe Dark theme on first frame. Plus the unit-level repository observer test below.

- [X] T024 [US1] Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt`. Add `@Inject lateinit var observeUserSettings: ObserveUserSettingsUseCase`. Inside `setContent { }`: replace `var themeMode by remember { mutableStateOf(ThemeMode.System) }` with `val settings by observeUserSettings().collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)`. Update the `darkTheme` `when` to read `settings.themeMode`. Update imports: drop `androidx.compose.runtime.{getValue, mutableStateOf, remember, setValue}` and `presentation.theme.ThemeMode`; add `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `domain.model.{ThemeMode, UserSettings}`, `domain.usecase.ObserveUserSettingsUseCase`, `javax.inject.Inject`. See full code in [research.md R10](research.md#r10--mainactivity-integration-fr-020). Depends on: T015 (use case), T020 (Hilt module bound).
- [X] T025 [US1] Delete `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/ThemeMode.kt`. The file was moved in T006; this task removes the now-empty placeholder. Confirm via `git status` that the file is staged for deletion. Depends on: T024.
- [X] T026 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/datastore/SettingsDataStoreFactory.kt`. Test fixture: a top-level `fun createTestSettingsDataStore(folder: File): DataStore<Preferences>` using `PreferenceDataStoreFactory.create(scope = TestScope(...).backgroundScope, produceFile = { File(folder, "test.preferences_pb") })`. Used by all DataStoreTest classes. See pattern in [research.md R8](research.md#r8--test-strategy-pure-jvm-preferencedatastorefactory).
- [X] T027 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/datastore/SettingsDataStoreTest.kt`. Add 2 tests: `defaultRead_returnsDocumentedDefaults` (US2 too — see Phase 4) AND `writeThemeMode_then_freshReadReturnsThemeMode` (write `Dark`, close, recreate DataStore over same file, read, assert `Dark`). Use `@TempFolder` JUnit rule + the factory from T026. Depends on: T026, T012.
- [X] T028 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/SettingsRepositoryImplTest.kt`. Use Turbine: `repo.observeSettings().test { ... }` — write `setThemeMode(Dark)`, `awaitItem()` returns `UserSettings(themeMode = Dark, ...)`. Verify `distinctUntilChanged` (writing same value twice yields one emission). Depends on: T014.

**US1 checkpoint**: After T024–T028, MainActivity reads from DataStore; SC-001 manual gate ready; SC-011 (no MutableState in MainActivity) verifiable by `git grep`.

---

## Phase 4: User Story 2 — First-Launch Defaults Available Without Errors (Priority: P1)

**Goal**: A fresh-install caller reading any of the 4 settings keys gets the documented defaults (System / FollowSystem / Google / false) within a budget that lets the first frame render without fallback UI. No crash on missing keys.

**Independent Test**: `SettingsDataStoreTest#defaultRead_returnsDocumentedDefaults` (introduced in T027) plus `SettingsMapperTest#emptyPrefs_returnsAllDefaults`. Together they prove every settings key has a working fallback path through the entire data slice.

- [X] T029 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/SettingsMapperTest.kt`. Add 4 tests: `emptyPrefs_returnsAllDefaults`, `unknownThemeValue_fallsBackToDefault` (US4 too), `unknownSearchEngineValue_fallsBackToDefault` (US4 too), `nullLanguage_isFollowSystem`. Build a `MutablePreferences` instance via `mutablePreferencesOf()` for each fixture. Depends on: T013.
- [X] T030 [US2] In the `SettingsDataStoreTest` from T027, ensure the `defaultRead_returnsDocumentedDefaults` test asserts ALL 4 fields equal `UserSettings.DEFAULT` field-by-field (theme=System, languageOverride=FollowSystem, searchEngine=Google, isOnboardingCompleted=false). Depends on: T027.

**US2 checkpoint**: Mapper + DataStore both verified to return defaults for every never-written key — SC-002 satisfied at unit-test level.

---

## Phase 5: User Story 3 — Language Preference Persists (Priority: P2)

**Goal**: `setLanguageOverride(Explicit("vi"))` then close+reopen DataStore → read returns `Explicit("vi")`. Clear via `setLanguageOverride(FollowSystem)` → read returns `FollowSystem`.

**Independent Test**: `SettingsDataStoreTest#writeLanguageOverrideExplicit_then_freshReadReturnsExplicit` and `SettingsDataStoreTest#writeFollowSystem_after_explicit_clears`.

- [X] T031 [P] [US3] In `SettingsDataStoreTest` (T027), add `writeLanguageOverrideExplicit_then_freshReadReturnsExplicit`: write `Explicit("vi")`, close, recreate over same file, read `data.first()`, decode via mapper, assert `LanguageOverride.Explicit("vi")`. Depends on: T027, T013.
- [X] T032 [P] [US3] In `SettingsDataStoreTest`, add `writeFollowSystem_after_explicit_clears`: write `Explicit("vi")` then `FollowSystem`, fresh read returns `FollowSystem`. Depends on: T027.
- [X] T033 [P] [US3] In `SettingsMapperTest` (T029), add `explicitLanguage_isExplicit`: prefs has `language_override = "de"`, mapper produces `Explicit("de")`. Depends on: T029.

---

## Phase 6: User Story 4 — Search Engine Preference Persists (Priority: P2)

**Goal**: `setSearchEngine(Google)` persists; an unrecognized future value (e.g., `"bing"`) falls back to `Google` per FR-009 instead of crashing.

**Independent Test**: `SettingsDataStoreTest#writeSearchEngine_then_freshReadReturnsSearchEngine` plus the `unknownSearchEngineValue_fallsBackToDefault` test from T029.

- [X] T034 [P] [US4] In `SettingsDataStoreTest`, add `writeSearchEngine_then_freshReadReturnsSearchEngine`: write `Google`, fresh read returns `Google`. Depends on: T027.
- [X] T035 [P] [US4] Verify the `unknownSearchEngineValue_fallsBackToDefault` test from T029 covers SC US4 acceptance scenario 2 (corrupted/unrecognized value → default, no crash). If it does not yet write the unknown value through DataStore (just constructs a Preferences instance), add a sibling test `corruptedSearchEngineDisk_fallsBackToDefault` that writes `"unknown_engine"` via raw `dataStore.edit { it[StorageKeys.SEARCH_ENGINE] = "unknown_engine" }`, fresh read, assert `Google`. Depends on: T029.

---

## Phase 7: User Story 5 — Onboarding Completion Flag Persists (Priority: P2)

**Goal**: `setOnboardingCompleted(true)` persists across process death; default-read on fresh install returns `false`.

**Independent Test**: `SettingsDataStoreTest#writeOnboardingCompleted_then_freshReadReturnsTrue` plus the default-read coverage from T030.

- [X] T036 [P] [US5] In `SettingsDataStoreTest`, add `writeOnboardingCompleted_then_freshReadReturnsTrue`: write `true`, fresh read returns `true`. Then add `defaultOnboardingCompleted_isFalse` which is already covered by T030; explicitly assert it once more in this dedicated test for clarity. Depends on: T027.

---

## Phase 8: User Story 6 — Concurrent Writes Do Not Lose Data (Priority: P3)

**Goal**: Two coroutines writing different keys at the same instant both persist; same-key concurrent writes have last-writer-wins semantics. SC-004: 100/100 iterations green.

**Independent Test**: `SettingsDataStoreTest#concurrentWrites_bothPersist` runs 100 iterations.

- [X] T037 [P] [US6] In `SettingsDataStoreTest`, add `concurrentWrites_differentKeys_bothPersist` (run 100 iterations via `repeat(100) { ... }` inside `runTest { }`): each iteration creates a fresh DataStore, launches 2 coroutines writing `setThemeMode(Dark)` + `setSearchEngine(Google)`, joinAll, assert fresh read sees both values. Use `StandardTestDispatcher()`. Depends on: T027.
- [X] T038 [P] [US6] In `SettingsDataStoreTest`, add `concurrentWrites_sameKey_lastWriterWins`: launch 2 coroutines writing `setThemeMode(Dark)` and `setThemeMode(Light)` concurrently; assert read returns one of the two values (not null, not crashed). Depends on: T027.

---

## Phase 9: User Story 7 — Schema Evolution Stays Backward-Compatible (Priority: P3)

**Goal**: A documented schema-rules block lives at the top of `StorageKeys.kt`; the negative-path test demonstrates the rule cannot be silently broken.

**Independent Test**: Source-grep for the doc-comment + `SettingsMapperTest#renamedKey_returnsDefault_oldValueGone`.

- [X] T039 [P] [US7] Verify the doc-comment block from T005 is present at the top of `StorageKeys.kt`. Inspect via `head -25 app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/StorageKeys.kt`. Confirm it lists the 4 numbered rules verbatim from [research.md R7](research.md#r7--schema-evolution-rule-fr-019--implementation). If missing or partial, edit T005's deliverable to add it (this is a verification gate, not a code task — but if T005 was incomplete, return to it).
- [X] T040 [P] [US7] In `SettingsMapperTest`, add `renamedKey_returnsDefault_oldValueGone`: create a Preferences instance with key `stringPreferencesKey("theme_mode_OLD")` set to `"dark"` (simulating a now-renamed key). Map to domain. Assert `themeMode == ThemeMode.System` (the documented default for `theme_mode`) — proves the old value did NOT silently bleed into the new key. Depends on: T029.

---

## Phase 10: Polish & Cross-Cutting

**Purpose**: Hilt smoke test, parameterized use-case tests, and full quality-gate sweep before marking the spec complete.

- [X] T041 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/di/SettingsModuleSmokeTest.kt`. Hilt-style structural smoke test: build the Hilt graph in test, request a `SettingsRepository`, exercise `observeSettings().first()` and `setThemeMode(Light)` round-trip, assert no missing-binding exception. Use a test-DataStore replacement via Hilt `@TestInstallIn` — see Spec 005's `DatabaseModuleSmokeTest` for the precedent pattern. Depends on: T020.
- [X] T042 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SettingsUseCasesTest.kt`. Parameterized over 5 use cases: each test invokes the use case with a fixture argument, asserts the corresponding `SettingsRepository` method was called with the same argument (use a hand-rolled spy or MockK if already in classpath; otherwise a tiny `FakeSettingsRepository` recording calls). 5 tests total; verifies use cases are pure delegations (Constitution §IV — "all business logic in use cases" satisfied even for thin delegations because the consumer-facing API is stable). Depends on: T015–T019.
- [X] T043 Run automated quality gates 0–7 from [quickstart.md](quickstart.md). Specifically: `./gradlew clean assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck assembleRelease`. Plus the 16 KB CI script. Plus the 3 `git grep` checks (Gate 3). Plus inspect the 2 backup XML files (Gate 4). Plus run the dedicated test commands (Gates 5, 6, 7). Confirm all green. Document any deviations. **Then re-evaluate the Constitution Check table from [plan.md § Constitution Check](plan.md#constitution-check) row-by-row against the as-implemented code (SC-009 mandates 11/11 PASS pre AND post design + implementation).** Record the post-impl re-evaluation outcome in the implementation report (PR description or commit body); flag any row that drifted from PASS as a blocking issue.
- [X] T043b [USER] ✅ Verified by user 2026-05-01 on Android 13+ emulator/device — no theme flash on cold start, persisted theme renders on first composition. Manually verify quickstart Gate 8 — install debug APK on Android 13+ emulator/device (`./gradlew installDebug`), reproduce the no-flash-on-cold-start scenario per [quickstart.md § Gate 8](quickstart.md#gate-8--manual-smoke-on-emulator-visual). Cannot be executed by an automated agent (requires real device interaction). Record outcome (PASS/FAIL + device/API level) in the PR description. SC-001 cannot be fully closed without this gate. Mirrors Spec 004's "manual gates deferred to user verification" pattern.
- [X] T044 Update `CLAUDE.md` § Recent Changes with the Spec 006 done entry — date `2026-05-01`, list of created files, package version (DataStore Preferences 1.2.1), test count delta, APK size, key clarifications applied (Q1 Result<Unit>, Q2 LanguageOverride sealed). Mirror format used by Spec 005's entry.
- [X] T045 Update `.claude/claude-app/project-context.md` § Trạng thái dự án to mark Spec 006 ✅ + add a Key Decisions Log entry for 2026-05-01 mirroring Spec 005's structure (Versions chốt / Architecture / Quality gates result / Files created / Constitution Check).
- [X] T046 Update `.claude/claude-app/sdd-roadmap.md` Spec List table — change row 006 status from `⬜ Next` to `✅ Done 2026-05-01`. Update Phase 1 progress note: "5/6" → "6/6 Phase 1 done; Phase 2 Spec 007 unblocked".
- [X] T047 Update `CLAUDE.md` SPECKIT block (between `<!-- SPECKIT START -->` and `<!-- SPECKIT END -->`) to mark Spec 006 ✅ done with implementation summary; suggest Spec 007 as next.

---

## Dependencies Summary

```text
Phase 1 (T001–T003) — Setup
        │
        ▼
Phase 2 (T004–T023) — Foundational
        │
        ├── T004 [P]  ┐
        ├── T005 [P]  │
        ├── T006 [P]  │
        ├── T007 [P]  │  These 5 + T009 + T010 + T011 + T012 + T013 + T014 + T015–T019 + T020 + T021 + T022 form the data slice
        ├── T008 [P]  │
        ├── T009  ←── T006, T007, T008
        ├── T010  ←── T009
        ├── T011 [P] ←── T010
        ├── T012  ←── T005, T006, T007, T008
        ├── T013  ←── T005, T009, T010
        ├── T014  ←── T011, T012, T013
        ├── T015–T019 [P] ←── T011
        ├── T020  ←── T004, T011, T014
        ├── T021 [P] (XML)
        ├── T022 [P] (XML)
        └── T023 (build verify)
        │
        ▼
Phase 3 US1 (T024–T028) — Theme persist + MainActivity wiring (MVP)
        │
        ├── T024 ←── T015, T020
        ├── T025 ←── T024
        └── T026, T027, T028 [P] ←── T012/T014/T026
        │
        ▼
Phase 4 US2 (T029–T030) — First-launch defaults
        │
        └── T029, T030 [P] ←── T013, T027
        │
        ▼
Phase 5 US3 (T031–T033) — Language persist
Phase 6 US4 (T034–T035) — Search engine persist
Phase 7 US5 (T036) — Onboarding flag persist
Phase 8 US6 (T037–T038) — Concurrent writes
Phase 9 US7 (T039–T040) — Schema evolution
        │  (Phases 5–9 are all [P] — depend only on T027 / T029 from earlier phases)
        ▼
Phase 10 (T041–T047 + T043b) — Polish + docs sweep
        │
        ├── T041 ←── T020
        ├── T042 ←── T015–T019
        ├── T043 ←── ALL prior tasks (auto gates 0–7 + Constitution 11/11 re-eval post-impl)
        ├── T043b ←── T043 [USER manual Gate 8]
        └── T044–T047 ←── T043, T043b
```

## Parallel execution opportunities

- **Within Phase 2**: T004, T005, T007, T008, T015–T019 (use case stubs after interface T011 lands), T021, T022 are all `[P]` — can land in any order.
- **Within Phase 3**: T026, T027, T028 are `[P]` once their underlying impl lands.
- **Phases 4–9 user-story tests** are all `[P]` — they only add tests to existing test files (`SettingsDataStoreTest`, `SettingsMapperTest`) so they can be authored simultaneously. The only contention is the same test file getting multiple commits; that's a merge concern, not a logical dependency.

## Independent test criteria per user story

| Story | Independent test |
|---|---|
| US1 | T027 (`writeThemeMode_then_freshReadReturnsThemeMode`) + T028 (Turbine observer) + Gate 8 manual emulator |
| US2 | T029 (`emptyPrefs_returnsAllDefaults`) + T030 (DataStore default-read) |
| US3 | T031 + T032 (DataStore language persist + clear) + T033 (Mapper explicit decode) |
| US4 | T034 (DataStore engine persist) + T035 (corrupted-value fallback) |
| US5 | T036 (DataStore boolean persist) |
| US6 | T037 (`concurrentWrites_differentKeys_bothPersist` × 100) + T038 (same-key last-writer-wins) |
| US7 | T039 (doc-comment present) + T040 (`renamedKey_returnsDefault_oldValueGone`) |

## MVP scope

**MVP = Phase 1 + Phase 2 + Phase 3 (US1) + Phase 4 (US2)** — 30 tasks. Delivers: persisted theme that survives process death + working defaults on fresh install. Without this, the spec ships nothing user-visible. US3–US7 are added value and quality discipline.

## Total task count

**48 tasks** (T001–T047 + T043b) across 10 phases. ~70% are `[P]` parallelizable. T043b is a `[USER]` manual gate not executable by an automated agent.

## Format validation

All 48 tasks follow `- [ ] T### [P?|USER?] [Story?] Description with file path`. User-story phases (US1–US7) carry `[Story]` labels; Setup/Foundational/Polish do not. File paths are repository-relative and absolute within the repo tree. The single `[USER]` marker on T043b explicitly flags the manual emulator gate that must be executed by the user, not the implementing agent.
