---
description: "Task list for Spec 016 — Settings Screen"
---

# Tasks: Settings Screen

**Input**: Design documents from `/specs/016-settings-screen/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/SettingsRepository.kt](contracts/SettingsRepository.kt), [contracts/AppLanguageController.kt](contracts/AppLanguageController.kt), [contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt), [contracts/ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt), [contracts/SettingsUseCases.kt](contracts/SettingsUseCases.kt), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution §VI mandates JVM tests for business logic and Compose UI tests for settings toggles. Tests are interleaved per the project's conventions, not strictly TDD-first. **Two tests are non-negotiable because they guard privacy**: `ClearBrowsingDataUseCaseTest`'s discard-before-wipe ordering (T068) and the restore-after-discard instrumented case (T069) — together they prove cleared cookies cannot be resurrected (FR-030). Constitution §VI also requires **instrumented** DataStore coverage, which T018 provides.

**Organization**: Grouped by user story, US1–US7 in the spec's priority order. MVP = Setup + Foundational + US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel with the other [P] tasks of its phase — different files, no dependency on another unfinished task in that group. Explicit "after Txxx" notes override.
- **[Story]**: US1–US7. Setup, Foundational and Polish tasks carry no story label.
- **Path tokens** — every path is from the repository root:
  - `{main}` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
  - `{test}` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
  - `{androidTest}` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
  - `{res}` = `app/src/main/res`
- **"Strings ×8"** means: add the English value to `{res}/values/strings.xml` and a translation to each of `{res}/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`. Lint enforces parity at error severity, so a key missing from any locale fails the build.

---

## Phase 1: Setup

**Purpose**: Baseline the build, add the two dependencies at their verified versions, and enable build-config generation.

- [X] T001 Confirm the branch is `016-settings-screen` and baseline a green build before any edit: `./gradlew clean assembleDebug assembleRelease testDebugUnitTest`. Record the unit-test count and the **exact** byte size of `app/build/outputs/apk/release/app-release.apk` (research.md R3 measured 2,558,178 B). SC-014 is judged against this figure.
- [X] T002 Look up the latest **stable** `androidx.appcompat:appcompat` at this moment from https://developer.android.com/jetpack/androidx/releases/appcompat, excluding alpha, beta and rc builds (plan: 1.8.0 on 2026-09-11). Add a version key `appcompat` and library alias `androidx-appcompat` to `gradle/libs.versions.toml`, with a comment block in the file's existing style recording the lookup date and "zero `.so` across its dependency graph — confirmed in a release-build spike, specs/016-settings-screen/research.md R3". If a newer stable exists, use it and note the difference for T107.
- [X] T003 Look up the latest **stable** `androidx.webkit:webkit` at this moment from https://developer.android.com/jetpack/androidx/releases/webkit (plan: 1.17.0, released 2026-08-12). The Maven metadata's `<release>` tag can point at an alpha — do not use it. Add a version key `webkit` and library alias `androidx-webkit` to `gradle/libs.versions.toml` with a comment citing research.md R5 and the zero-`.so` finding. (Same file as T002 — run after it.)
- [X] T004 In `app/build.gradle.kts`, add `implementation(libs.androidx.appcompat)` and `implementation(libs.androidx.webkit)` beside the other AndroidX implementation dependencies, each with a `// Spec 016 —` comment stating its purpose, and set `buildConfig = true` inside the existing `buildFeatures { }` block (research.md R10).
- [X] T005 Run `./gradlew assembleDebug`. Confirm both artifacts resolve and that the generated `BuildConfig` under `app/build/generated/source/buildConfig/debug/` declares `VERSION_NAME`. If it does not, stop and revisit research.md R10 before continuing.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The amended settings data slice — shared files that US3, US5 and US6 would otherwise all edit — plus the Settings screen scaffold, its entry point, and the module that supplies platform values.

**⚠️ CRITICAL**: No user story work can begin until this phase completes.

### Constants and domain models

- [X] T006 [P] In `{main}/core/constants/BrowserLimits.kt`, add `const val HISTORY_RETENTION_DAYS_7: Int = 7`, `HISTORY_RETENTION_DAYS_30 = 30`, `HISTORY_RETENTION_DAYS_90 = 90` and `HISTORY_RETENTION_DAYS_180 = 180`, with KDoc citing FR-020 and Spec 014's ~300-bytes-per-row measurement. **Keep `MAX_HISTORY_DAYS` for now**: `PruneOldHistoryUseCase` still reads it until T083 switches it over, and the build must stay green between tasks.
- [X] T007 [P] In `{main}/core/constants/StorageKeys.kt`, add `val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")` and `val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")`. Do not remove `LANGUAGE_OVERRIDE` yet — T012 removes it together with every consumer.
- [X] T008 [P] In `{main}/core/constants/AppConstants.kt`, add two `const val` Hilt qualifier names, `QUALIFIER_SUPPORTS_DYNAMIC_COLOR` and `QUALIFIER_APP_VERSION_NAME` (research.md R9, R10). `@Named` needs compile-time constants, which is why they are `const val`.
- [X] T009 Create `HistoryRetention` at `{main}/domain/model/HistoryRetention.kt` (after T006): an enum with exactly `Days7(BrowserLimits.HISTORY_RETENTION_DAYS_7)`, `Days30(…_30)`, `Days90(…_90)`, `Days180(…_180)` and `val days: Int`; `fun isShorterThan(other: HistoryRetention): Boolean = days < other.days`; and a companion `fromDaysOrDefault(value: Int?)` that returns the entry whose `days` equals `value`, else `AppDefaults.HISTORY_RETENTION`. Per data-model §2: "Exactly four entries, no unlimited value" and "Equal is not shorter". Pure Kotlin, no Android imports.
- [X] T010 In `{main}/core/constants/AppDefaults.kt` (after T009), add `const val DYNAMIC_COLOR_ENABLED: Boolean = true` and `val HISTORY_RETENTION: HistoryRetention = HistoryRetention.Days90`. Leave `LANGUAGE_OVERRIDE` for T012.
- [X] T011 Unit test `HistoryRetentionTest` at `{test}/domain/model/HistoryRetentionTest.kt` (after T010): the entries carry 7, 30, 90 and 180; `isShorterThan` is true only when strictly smaller; `fromDaysOrDefault` maps each exact value to its entry and maps `null`, `0`, `-1`, `45` and `365` to `Days90`.

### Settings data slice (data-model §1)

- [X] T012 Retire the Spec 006 language value in one compile-consistent change (research.md R4). Delete `{main}/domain/model/LanguageOverride.kt` and `{main}/domain/usecase/SetLanguageOverrideUseCase.kt`. Remove `LANGUAGE_OVERRIDE` from `{main}/core/constants/StorageKeys.kt` and `{main}/core/constants/AppDefaults.kt`, `languageOverride` from `{main}/domain/model/UserSettings.kt`, the language branch from `{main}/data/mapper/SettingsMapper.kt`, and `setLanguageOverride` from `{main}/data/local/datastore/SettingsDataStore.kt`, `{main}/domain/repository/SettingsRepository.kt` and `{main}/data/repository/SettingsRepositoryImpl.kt`. Afterwards, `grep -rn "LanguageOverride\|LANGUAGE_OVERRIDE\|setLanguageOverride" app/src/main` MUST return nothing.
- [X] T013 Amend `{main}/domain/model/UserSettings.kt` to exactly the data-model §1 fields: `themeMode`, `isDynamicColorEnabled`, `searchEngine`, `historyRetention`, `isOnboardingCompleted`. `DEFAULT` takes `AppDefaults.DYNAMIC_COLOR_ENABLED` and `AppDefaults.HISTORY_RETENTION` for the two new fields.
- [X] T014 Amend `{main}/data/mapper/SettingsMapper.kt` with the data-model §1 decoding rules, verbatim: "Missing `dynamic_color_enabled` → `true`" and "Missing `history_retention_days`, or any stored integer that is not exactly one of the four day counts → `Days90`" (via `HistoryRetention.fromDaysOrDefault`). Rewrite its KDoc — the language paragraph no longer applies.
- [X] T015 Amend `{main}/data/local/datastore/SettingsDataStore.kt`: add `setDynamicColorEnabled(enabled: Boolean): Result<Unit>` writing `StorageKeys.DYNAMIC_COLOR_ENABLED`, and `setHistoryRetention(retention: HistoryRetention): Result<Unit>` writing `retention.days` to `StorageKeys.HISTORY_RETENTION_DAYS`. Both go through the existing `editCatching`, so an `IOException` becomes `Result.Error` and `CancellationException` is never caught.
- [X] T016 Replace `{main}/domain/repository/SettingsRepository.kt` with the members and KDoc of [contracts/SettingsRepository.kt](contracts/SettingsRepository.kt) — six members — and implement the two new setters in `{main}/data/repository/SettingsRepositoryImpl.kt` as delegations to `SettingsDataStore`.
- [X] T017 Update the five settings tests for the new shape (research.md R13), then run `./gradlew testDebugUnitTest` until green:
  - `{test}/data/local/datastore/SettingsDataStoreTest.kt` — remove the language cases; add round trips for both new setters.
  - `{test}/data/mapper/SettingsMapperTest.kt` — remove the language cases; add: missing dynamic color → `true`; missing retention → `Days90`; stored `45` → `Days90`; stored `180` → `Days180`.
  - `{test}/data/repository/SettingsRepositoryImplTest.kt` — cover the two new setters.
  - `{test}/domain/usecase/SettingsUseCasesTest.kt` — remove the `SetLanguageOverrideUseCase` case and update its private fake.
  - `{test}/data/repository/SearchEngineRepositoryImplTest.kt` — update its private `FakeSettingsRepository` to the new interface.
- [X] T018 Instrumented test `SettingsDataStoreInstrumentedTest` at `{androidTest}/data/local/datastore/SettingsDataStoreInstrumentedTest.kt` (after T016). **Required by Constitution §VI** — "Instrumented tests MUST cover … DataStore read/write" — and `androidTest` contains no DataStore test today. On a real device, build `SettingsDataStore` over a `PreferenceDataStoreFactory` file in the instrumentation context's cache directory, deleted after each test, and assert through `SettingsRepositoryImpl.observeSettings()`:
  - a missing file yields `UserSettings.DEFAULT`;
  - `setDynamicColorEnabled(false)` and then `true` each read back;
  - `setHistoryRetention(HistoryRetention.Days7)` reads back as `Days7`;
  - a raw `45` written to `StorageKeys.HISTORY_RETENTION_DAYS` decodes to `Days90`.
- [X] T019 Create a shared `FakeSettingsRepository` at `{test}/testdoubles/FakeSettingsRepository.kt` (after T016): backed by a `MutableStateFlow<UserSettings>` starting at `UserSettings.DEFAULT`; each setter updates the flow and records the call; a `failNextWrite` switch returns `Result.Error(IOException())` once, for FR-043 tests.

### Screen scaffold and entry point

- [X] T020 [P] Strings ×8: `settings_screen_title`; section headers `settings_section_appearance`, `settings_section_language`, `settings_section_search`, `settings_section_privacy`, `settings_section_about`; and `settings_error_not_saved` for failed writes (FR-043). Do **not** remove `settings_screen_placeholder` here — T027 removes it together with its last use.
- [X] T021 Create `SettingsUiState` and `SettingsDialog` at `{main}/presentation/settings/SettingsUiState.kt` (after T013). `SettingsUiState` is an immutable `data class` holding the data-model §6 fields available at this point: `themeMode`, `isDynamicColorEnabled`, `isDynamicColorSupported`, `searchEngine`, `historyRetention`, `appVersionName`, and `dialog: SettingsDialog?` (null = no dialog open). `SettingsDialog` is a sealed interface; each story adds its own variants. US3 adds `appLanguage`.
- [X] T022 [P] Create `SettingsEvent` at `{main}/presentation/settings/SettingsEvent.kt` as a sealed interface containing `SettingNotSaved`. Later stories add `LanguageNotApplied` (US3), `BrowsingDataCleared` and `BrowsingDataPartiallyCleared` (US4), and `RetentionPruneDeferred` (US5), per data-model §6.
- [X] T023 Create `SettingsPlatformModule` at `{main}/di/SettingsPlatformModule.kt` (after T005, T008): an `object` `@Module @InstallIn(SingletonComponent::class)` with two `@Provides` — `@Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR)` `Boolean` computed as `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`, and `@Named(AppConstants.QUALIFIER_APP_VERSION_NAME)` `String` from `BuildConfig.VERSION_NAME`. In the same file, also declare an **empty** `abstract class SettingsPlatformBindingModule` annotated `@Module @InstallIn(SingletonComponent::class)` — the two-modules-in-one-file layout of `{main}/di/SettingsModule.kt` — so that T050 (US3) and T071 (US4) each add one `@Binds` without either story depending on the other.
- [X] T024 Create `SettingsViewModel` at `{main}/presentation/settings/SettingsViewModel.kt` (after T019, T021–T023). It is a `@HiltViewModel` whose constructor takes `ObserveUserSettingsUseCase`, `DispatcherProvider`, `@param:Named(AppConstants.QUALIFIER_SUPPORTS_DYNAMIC_COLOR) supportsDynamicColor: Boolean` and `@param:Named(AppConstants.QUALIFIER_APP_VERSION_NAME) appVersionName: String`.
  - Expose `uiState: StateFlow<SettingsUiState>`, mutated only via `update { }` and fed by the observed settings snapshot.
  - Expose `events: SharedFlow<SettingsEvent>` backed by `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`, the Spec 013 channel policy.
  - Add `dismissDialog()`.
  - Stories add use cases and handlers here, so annotate the class `@Suppress("TooManyFunctions", "LongParameterList")` with the same rationale comment `{main}/presentation/history/HistoryViewModel.kt` uses.
- [X] T025 Create the reusable row components (after T020), each via theme tokens and `Spacing.*` only (§III):
  - `{main}/presentation/settings/components/SettingsTopBar.kt` — the title plus a back icon, `Icons.AutoMirrored.Filled.ArrowBack`, reusing the back-navigation content-description string that `{main}/presentation/history/components/HistoryTopBar.kt` already uses.
  - `{main}/presentation/settings/components/SettingsSectionHeader.kt`.
  - `{main}/presentation/settings/components/SettingsRows.kt` — a value row (title plus current value, the whole row clickable, at least 48dp tall) and a switch row (title, summary and a `Switch`, with toggle semantics announced).
- [X] T026 [P] Create the internal `SingleChoiceDialog` at `{main}/presentation/settings/components/SingleChoiceDialog.kt`, generic over the option type. It takes a title, the options with a label provider, the selected option, `onSelect` and `onDismiss`. Each option is a `selectable` row with `Role.RadioButton` semantics and a height of at least 48dp (FR-039). The dialog dismisses on an outside tap and on system back.
- [X] T027 Rewrite `{main}/presentation/settings/SettingsScreen.kt` (after T024–T026):
  - The signature becomes `SettingsScreen(navController: NavHostController, viewModel: SettingsViewModel = hiltViewModel())`.
  - Layout: `Scaffold` with `SettingsTopBar`, a `SnackbarHost`, and a `LazyColumn` holding the five section headers. Stories add the rows.
  - Collect `events` into localized snackbars. The top-bar back and `BackHandler` both call `navController.popBackStack()`.
  - Keep every composable under detekt's 60-line `LongMethod` threshold by extracting per-section composables.
  - In the same change, remove `settings_screen_placeholder` from all 8 `strings.xml` files (FR-040).
- [X] T028 Wire the entry point (after T027), satisfying FR-001:
  - `{main}/presentation/navigation/AppNavGraph.kt` → `SettingsScreen(navController = navController)`.
  - `{main}/presentation/browser/components/BrowserOverflowMenuCallbacks.kt` → add `onSettingsClick: () -> Unit`, and update its KDoc: Spec 016 has now filled the reserved slot.
  - `{main}/presentation/browser/components/BrowserOverflowMenu.kt` → a fourth `DropdownMenuItem`, after Downloads, labelled `settings_screen_title` and tagged with a new `TEST_TAG_OVERFLOW_SETTINGS`.
  - `{main}/presentation/browser/BrowserScreen.kt` → the callback dismisses the menu and calls `navController.navigate(AppDestination.Settings.route)`.
- [X] T029 Update `{androidTest}/presentation/browser/BrowserOverflowMenuTest.kt` for the new callback (after T028). Assert that Settings is listed fourth, after Downloads, and that tapping it invokes `onSettingsClick`.
- [X] T030 [P] Create `{test}/presentation/settings/SettingsViewModelTest.kt` (after T024), using the project's `MainDispatcherRule` and `FakeSettingsRepository`. Build every case through a `settingsViewModel(...)` factory in `{test}/presentation/settings/SettingsViewModelTestFactory.kt` whose parameters all default to fakes; each later story that adds a constructor parameter adds a defaulted parameter there instead of editing existing tests. Cover: the initial state mirrors `UserSettings.DEFAULT`; the state follows repository emissions; the injected `supportsDynamicColor` and `appVersionName` reach the state; `dismissDialog()` clears the dialog.
- [X] T031 [P] Create `{androidTest}/presentation/settings/SettingsScreenTestDoubles.kt` (after T016): an in-memory `SettingsRepository` plus an instrumented `settingsViewModel(...)` factory with defaulted fakes, mirroring T030's — the JVM doubles in `{test}` are not visible to instrumented tests. T061, T077 and T089 extend this file with the fakes their stories need. Compose tests drive `SettingsViewModel` directly, without Hilt or a WebView — the pattern `{androidTest}/presentation/history/HistoryScreenTestDoubles.kt` established.

**Checkpoint**: the Settings screen opens from the overflow menu, shows its five section headers, and returns to the browser. Every story can now start.

---

## Phase 3: User Story 1 — Switch the theme from Settings (Priority: P1) 🎯 MVP

**Goal**: Light / Dark / System chosen from Settings, applied app-wide at once and persisted.

**Independent Test**: From a loaded page, open the overflow menu → Settings, choose each theme in turn, and confirm each applies immediately without the app's screens being rebuilt, that the same tab returns at the same address, and that the choice survives a force-stop and relaunch (quickstart G1).

- [X] T032 [P] [US1] Strings ×8: `settings_theme_title`, `settings_theme_light`, `settings_theme_dark`, `settings_theme_system`.
- [X] T033 [US1] Create the **public, stateless** `ThemeModeChooserDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit)` at `{main}/presentation/settings/components/ThemeModeChooserDialog.kt` (after T032), built on `SingleChoiceDialog`. It MUST NOT reference any view-model, so Spec 018 can reuse it (FR-037, research.md R14).
- [X] T034 [P] [US1] In `{main}/presentation/settings/SettingsViewModel.kt`, inject `SetThemeModeUseCase` and add `SettingsDialog.ThemeChooser`.
  - `onThemeRowClick()` opens the chooser.
  - `onThemeSelected(mode)` closes the dialog and returns without writing when `mode == uiState.value.themeMode` (FR-006); otherwise it writes, and emits `SettingNotSaved` on `Result.Error` (FR-043).
  - Add a defaulted `SetThemeModeUseCase` parameter to both `settingsViewModel(...)` factories (T030, T031).
- [X] T035 [US1] In `{main}/presentation/settings/SettingsScreen.kt` (after T033, T034), add the appearance-section theme value row showing the localized current mode (FR-003), and render `ThemeModeChooserDialog` while the dialog is `ThemeChooser`.
- [X] T036 [P] [US1] Extend `{test}/presentation/settings/SettingsViewModelTest.kt` (after T034): the row opens the chooser; selecting a different mode writes exactly once; selecting the current mode writes nothing; a failed write emits `SettingNotSaved`.
- [X] T037 [P] [US1] Compose test `{androidTest}/presentation/settings/SettingsScreenThemeTest.kt` (after T035): the row shows the current mode; the chooser lists three options with the current one selected; choosing Dark updates the row.
- [X] T038 [US1] Manual gate **G1** per [quickstart.md](quickstart.md) on both AVDs. Record PASS/FAIL with device and OS build.

**Checkpoint**: MVP complete — Settings is reachable and the theme can be changed.

---

## Phase 4: User Story 2 — Choose the search engine (Priority: P1)

**Goal**: Google / DuckDuckGo / Bing chosen from Settings; subsequent queries go to the chosen engine.

**Independent Test**: For each engine, submit three queries from the address bar and confirm the results page belongs to that engine; relaunch and confirm the choice held (quickstart G3).

- [X] T039 [P] [US2] Strings: `settings_search_engine_title` ×8. Engine brand names `search_engine_name_google` ("Google"), `search_engine_name_duckduckgo` ("DuckDuckGo") and `search_engine_name_bing` ("Bing"), marked `translatable="false"` in `{res}/values/strings.xml` **only**.
- [X] T040 [US2] Create the **public, stateless** `SearchEngineChooserDialog(selected: SearchEngine, onSelect, onDismiss)` at `{main}/presentation/settings/components/SearchEngineChooserDialog.kt` (after T039), built on `SingleChoiceDialog`, with no view-model reference (FR-037).
- [X] T041 [P] [US2] In `SettingsViewModel`, inject `SetSearchEngineUseCase` and add `SettingsDialog.SearchEngineChooser`, with `onSearchEngineRowClick()` and `onSearchEngineSelected(engine)`. Apply the same FR-006 no-op and FR-043 event rules as T034, and add a defaulted `SetSearchEngineUseCase` parameter to both factories (T030, T031).
- [X] T042 [US2] In `SettingsScreen`, add the search-section row showing the current engine and render the chooser (after T040, T041).
- [X] T043 [P] [US2] Extend `SettingsViewModelTest` with the T036 case set for search engines (after T041).
- [X] T044 [P] [US2] Compose test `{androidTest}/presentation/settings/SettingsScreenSearchEngineTest.kt` (after T042): the row shows the current engine; the chooser lists exactly three options; selecting one updates the row.
- [X] T045 [US2] Manual gate **G3** per quickstart.md.

---

## Phase 5: User Story 3 — Change the app language (Priority: P1)

**Goal**: Follow system plus the eight languages, switched in-app on Android 7.0–16 without a restart, with the platform as the single source of truth.

**Independent Test**: On API 24 and API 36, choose each language with three normal tabs and one incognito tab open; all tabs survive and the whole app switches. On API 36, round-trip the language through `cmd locale` (quickstart G4).

### Domain and seam

- [X] T046 [P] [US3] Create `AppLanguage` at `{main}/domain/model/AppLanguage.kt`: an enum `AppLanguage(val tag: String?)` with exactly `FollowSystem(null)`, `English("en")`, `Vietnamese("vi")`, `German("de")`, `Russian("ru")`, `Korean("ko")`, `Japanese("ja")`, `Chinese("zh")`, `French("fr")`. Add a companion `fromLanguageTagOrFollowSystem(tag: String?)` that matches on the **primary language subtag**, case-insensitively — so `zh-Hans-CN` → `Chinese` — and returns `FollowSystem` for null, blank or unsupported input (data-model §3). Pure Kotlin; `java.util.Locale` is allowed, Android imports are not.
- [X] T047 [P] [US3] Unit test `{test}/domain/model/AppLanguageTest.kt` (after T046).
  - **Mapping**: `zh-Hans-CN` → Chinese, `fr-CA` → French, `EN` → English, `es` → FollowSystem, blank and null → FollowSystem.
  - **Parity**: parse `src/main/res/xml/locales_config.xml` — JVM unit tests run with the `app/` module as their working directory — and assert that its `android:name` values equal the eight non-null tags (data-model §3 invariant).
- [X] T048 [P] [US3] Create the `AppLanguageController` interface at `{main}/domain/repository/AppLanguageController.kt` exactly per [contracts/AppLanguageController.kt](contracts/AppLanguageController.kt), KDoc included.
- [X] T049 [US3] Implement `AppCompatAppLanguageController` at `{main}/data/local/locale/AppCompatAppLanguageController.kt` (after T046, T048), a `@Singleton` with an `@Inject` constructor.
  - `current()` reads `AppCompatDelegate.getApplicationLocales()`: an empty list → `FollowSystem`; otherwise the first locale's `toLanguageTag()` through `AppLanguage.fromLanguageTagOrFollowSystem`.
  - `apply(language)` calls `AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())` for `FollowSystem`, and `LocaleListCompat.forLanguageTags(language.tag)` otherwise.
  - Wrap both in `runCatching`, logging failures under a class-private `const val` tag. Do **not** de-duplicate — the use case does that, per the contract.
- [X] T050 [US3] In `{main}/di/SettingsPlatformModule.kt` (after T049), add to `SettingsPlatformBindingModule` — created empty in T023 — a `@Binds @Singleton` binding `AppLanguageController` → `AppCompatAppLanguageController`.
- [X] T051 [US3] Create the two language use cases per [contracts/SettingsUseCases.kt](contracts/SettingsUseCases.kt) (after T048):
  - `GetAppLanguageUseCase` at `{main}/domain/usecase/GetAppLanguageUseCase.kt` delegates to `controller.current()`.
  - `SetAppLanguageUseCase` at `{main}/domain/usecase/SetAppLanguageUseCase.kt` returns `true` **without** calling `apply` when `language == controller.current()` (FR-006); otherwise it runs `withContext(dispatchers.main) { controller.apply(language) }` and returns the result.
- [X] T052 [US3] Create `{test}/testdoubles/FakeAppLanguageController.kt`, which records `apply` calls and has a configurable current value and result, and `{test}/domain/usecase/AppLanguageUseCasesTest.kt` (after T051). Cover: the same value → zero `apply` calls; a different value → exactly one call with its result propagated; transitions `FollowSystem ↔ specific` in both directions.

### Platform migration ⚠️ app-launch risk (research.md R1, R2)

- [X] T053 [US3] In `app/src/main/AndroidManifest.xml`, add inside `<application>` exactly the snippet from research.md R1: `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService" android:enabled="false" android:exported="false"><meta-data android:name="autoStoreLocales" android:value="true" /></service>`. In the same edit, correct the stale permission comment: `POST_NOTIFICATIONS` is deliberately undeclared per Spec 015 gate G9 Outcome A, and `WRITE_EXTERNAL_STORAGE` is the single documented deviation in Constitution v1.3.0 §II's table. This closes Constitution `TODO(MANIFEST_COMMENT)`.
- [X] T054 [US3] Re-parent `Theme.ThirtySix`: in `{res}/values/themes.xml` to `Theme.AppCompat.Light.NoActionBar`, and in `{res}/values-night/themes.xml` to `Theme.AppCompat.NoActionBar`. Keep both existing items, `android:windowBackground` and `android:statusBarColor`, unchanged (research.md R2). An AppCompat parent is mandatory: `AppCompatDelegateImpl` throws "You need to use a Theme.AppCompat theme (or descendant) with this activity." otherwise.
- [X] T055 [US3] In `{main}/MainActivity.kt` (after T053, T054), extend `androidx.appcompat.app.AppCompatActivity` instead of `ComponentActivity`. Keep `enableEdgeToEdge()`, the Hilt annotations and `SecureWindowEffect` unchanged, and do **not** call `AppCompatDelegate.setDefaultNightMode` (research.md R2). Then immediately install the debug build on both AVDs — **read the `adb install` output** — and confirm the app launches before continuing, since a wrong theme parent crashes at launch.

### UI

- [X] T056 [P] [US3] Strings:
  - ×8: `settings_language_title`, `settings_language_follow_system`, `settings_error_language_not_applied`.
  - Default locale only: the eight endonyms, marked `translatable="false"` in `{res}/values/strings.xml` **only** (FR-014, research.md R11) — `language_name_en` "English", `language_name_vi` "Tiếng Việt", `language_name_de` "Deutsch", `language_name_ru` "Русский", `language_name_ko` "한국어", `language_name_ja` "日本語", `language_name_zh` "中文", `language_name_fr` "Français".
- [X] T057 [US3] Create the **public, stateless** `AppLanguageChooserDialog(selected: AppLanguage, onSelect, onDismiss)` at `{main}/presentation/settings/components/AppLanguageChooserDialog.kt` (after T046, T056), built on `SingleChoiceDialog`. It lists nine options: the localized Follow system first, then the eight endonyms in `AppLanguage` order. No view-model reference (FR-037).
- [X] T058 [US3] In `SettingsViewModel` and its state types (after T051), inject `GetAppLanguageUseCase` and `SetAppLanguageUseCase`. Add `appLanguage: AppLanguage` (default `FollowSystem`) to `SettingsUiState`, `SettingsDialog.LanguageChooser`, and `SettingsEvent.LanguageNotApplied`.
  - `onScreenStarted()` re-reads the platform language into state (research.md R12).
  - `onLanguageRowClick()` opens the chooser.
  - `onLanguageSelected(language)` closes the dialog, calls `SetAppLanguageUseCase`, and emits `LanguageNotApplied` when it returns `false`.
  - Add defaulted parameters for both use cases to the JVM factory (T030), built from `FakeAppLanguageController` (T052); T061 adds them to the instrumented factory.
- [X] T059 [US3] In `SettingsScreen` (after T057, T058), add the language row showing the current selection and render the chooser. Call `viewModel.onScreenStarted()` from an ON_START lifecycle effect — `LifecycleStartEffect` from `androidx.lifecycle.compose`, already on the classpath since `MainActivity` uses `collectAsStateWithLifecycle`.
- [X] T060 [P] [US3] Extend `SettingsViewModelTest` with `FakeAppLanguageController` (after T058): `onScreenStarted()` refreshes the state from the controller; selecting the current language makes no `apply` call; selecting another makes one; a `false` result emits `LanguageNotApplied`.
- [X] T061 [P] [US3] Compose test `{androidTest}/presentation/settings/SettingsScreenLanguageTest.kt` (after T059). First add to `{androidTest}/presentation/settings/SettingsScreenTestDoubles.kt` an in-memory `AppLanguageController` that records `apply` calls and has a configurable current value — `FakeAppLanguageController` from T052 lives in `{test}` and cannot be used here — plus defaulted `GetAppLanguageUseCase` and `SetAppLanguageUseCase` parameters in the instrumented factory built from it. Then assert: the row shows the current language; the chooser shows nine options whose eight endonyms are identical whatever the test locale; selecting one reaches the fake controller.
- [X] T062 [US3] Manual gate **G4** per quickstart.md on both AVDs, including the API 36 round trip in both directions three times, plus **G10 step 1**: Spec 003's cold-start no-flash in both system themes after T054's re-parenting.

---

## Phase 6: User Story 4 — Clear browsing data (Priority: P2)

**Goal**: One dialog clears history, cookies and site data, and cached images and files — never downloads, bookmarks, tabs or settings — and cleared cookies stay cleared after an incognito session ends.

**Independent Test**: With the quickstart fixture storing all five kinds of site data, clear each category alone and then all three, on both AVDs, verifying the complete and fallback paths separately; run the incognito discard check with its control run (quickstart G5, G6).

### Domain

- [X] T063 [P] [US4] Create the three transient domain types in `{main}/domain/model/`:
  - `ClearBrowsingDataCategory.kt` — an enum `History`, `CookiesAndSiteData`, `CachedImagesAndFiles`.
  - `ClearBrowsingDataResult.kt` — `requested: Set<ClearBrowsingDataCategory>` and `failed: Set<ClearBrowsingDataCategory>`, with `init { require(requested.containsAll(failed)) }` because `failed` is "Always a subset of `requested`" (data-model §4).
  - `SiteDataClearOutcome.kt` — `Complete`, `Partial`, `Failed`, with the KDoc from [contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt).
- [X] T064 [P] [US4] Create the `WebDataCleaner` interface at `{main}/domain/repository/WebDataCleaner.kt` exactly per [contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt) (after T063).
- [X] T065 [US4] Add `discardSetAsideCookies()` per [contracts/ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt):
  - **Interface** — `{main}/domain/repository/CookieJarSnapshotManager.kt`, with the contract's KDoc.
  - **Implementation** — `{main}/data/local/cookies/CookieJarSnapshotManagerImpl.kt`: under the existing `mutex`, `if (snapshot != null) snapshot = CookieJarSnapshot.EMPTY`. Per data-model §5, "Do NOT clear it to null".
  - **Test double** — `{test}/testdoubles/FakeCookieJarSnapshotManager.kt`, which must be able to append to a shared call log.
- [X] T066 [US4] Add `clearAll()` to `FaviconCache` per [contracts/ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt), in `{main}/data/local/cache/FaviconCache.kt`. The `DiskFaviconCache` implementation works on `dispatchers.io`: it deletes every file in its cache directory, logs and skips any file it cannot delete, and bumps `version` afterwards. **No default body in the interface** (research.md R13) — add the member to every double:
  - `{androidTest}/presentation/browser/BrowserScreenInstrumentedTest.kt`
  - `{androidTest}/presentation/browser/BrowserScreenOfflineErrorTest.kt`
  - `{androidTest}/presentation/browser/BrowserScreenLoadingIndicatorTest.kt`
  - `{androidTest}/presentation/history/HistoryScreenTestDoubles.kt`
  - `{test}/presentation/tabs/TabsViewModelTest.kt`
  - `{test}/presentation/browser/BrowserViewModelTest.kt`
  - `{test}/presentation/browser/BrowserViewModelHistoryRecordSequenceTest.kt`
  - `{test}/presentation/browser/BrowserViewModelIncognitoCacheGateTest.kt` (both its no-op and recording doubles)
  - `{test}/presentation/browser/BrowserViewModelStarToggleTest.kt`
  - `{test}/presentation/history/HistoryViewModelTest.kt`
- [X] T067 [US4] Implement `ClearBrowsingDataUseCase` at `{main}/domain/usecase/ClearBrowsingDataUseCase.kt` exactly per [contracts/SettingsUseCases.kt](contracts/SettingsUseCases.kt) (after T063–T066). Its dependencies are `HistoryRepository`, `WebDataCleaner`, `CookieJarSnapshotManager`, `FaviconCache` and `ScreenshotCache`.
  - **Order**: History, then CookiesAndSiteData, then CachedImagesAndFiles.
  - **Cookies and site data**: `discardSetAsideCookies()` **first**, then `clearCookiesAndSiteData()`. `Partial` counts as cleared; only `Failed` marks the category failed.
  - **Cache**: skip `clearWebCache()` only when the same call already produced `Complete`; always run `FaviconCache.clearAll()` and `ScreenshotCache.clearAll()`.
  - **Failure boundaries**: every category and every step is wrapped in its own boundary, and every selected category is attempted.
  - **Contract edges**: `CancellationException` is rethrown; `require(categories.isNotEmpty())`.
- [X] T068 [US4] ⚠️ **Non-negotiable privacy test.** Create `{test}/testdoubles/FakeWebDataCleaner.kt`, with a configurable outcome, boolean result or thrown exception, appending to a shared call log, and `{test}/domain/usecase/ClearBrowsingDataUseCaseTest.kt` (after T067). Using one call log shared across every fake, assert:
  - `discardSetAsideCookies` is logged **before** `clearCookiesAndSiteData`.
  - History alone touches no other fake.
  - `Complete` skips `clearWebCache`, while `Partial` does not.
  - `Partial` never appears in `failed`.
  - A `Failed` outcome, a `false` return, or a thrown step marks only its own category, and later categories still run.
  - Both app caches are cleared even when `clearWebCache` fails.
  - An empty set throws `IllegalArgumentException`.
  - `CancellationException` propagates.
- [X] T069 [US4] ⚠️ **Non-negotiable privacy test.** Two parts (after T065):
  - **Robolectric** — `{test}/data/local/cookies/CookieJarSnapshotManagerDiscardTest.kt`: discarding with nothing held leaves `hasSnapshot()` false; discarding after a capture leaves `hasSnapshot()` **true**, because the snapshot is `EMPTY`, not null.
  - **Instrumented** — two new cases in `{androidTest}/data/local/cookies/CookieRestoreInstrumentedTest.kt`, which uses the real cookie manager:
    - set a normal cookie → capture → set an incognito-session cookie → discard → restore → assert **both** cookies are absent: the wipe still ran, and nothing was written back;
    - set a normal cookie A → capture → discard → set cookie B → capture **again** → restore → assert **both** A and B are absent. Had the second capture replaced `EMPTY`, B would have been written back — this is the observable proof, since `hasSnapshot()` cannot tell the two apart.

### Platform seam (research.md R5, R6)

- [X] T070 [US4] Implement `AndroidWebDataCleaner` at `{main}/data/local/webdata/AndroidWebDataCleaner.kt` exactly per [contracts/WebDataCleaner.kt](contracts/WebDataCleaner.kt) (after T064), as a `@Singleton` taking `@ApplicationContext` and `DispatcherProvider`.
  - **`clearCookiesAndSiteData()`, complete path**: when `WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)`, call `WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()) { … }` on the main dispatcher and suspend until the callback runs → `Complete`.
  - **Fallback path**: call `CookieManager.getInstance().removeAllCookies { … }` on the main dispatcher and suspend until the callback runs, then `flush()` on the IO dispatcher, and call `WebStorage.getInstance().deleteAllData()` on the main dispatcher → `Partial`. Any required call throwing → `Failed`.
  - **`clearWebCache()`**: on the main dispatcher, construct `WebView(appContext)`, call `clearCache(true)`, then `destroy()` → `true`; a throw → `false`.
  - **Safety**: every platform call is inside `runCatching` (FR-043).
  - **Logging**: log **only** which path ran and the elapsed milliseconds, under a class-private `const val` tag — never a URL, cookie or stored value (§I). Quickstart G5 reads this log to record which path ran; G8 treats its durations as supplementary to a screen recording.
- [X] T071 [US4] In `{main}/di/SettingsPlatformModule.kt` (after T070), add to `SettingsPlatformBindingModule` — created empty in T023 — a `@Binds @Singleton` binding `WebDataCleaner` → `AndroidWebDataCleaner`. This does not depend on US3.

### UI

- [X] T072 [P] [US4] Strings ×8:
  - The row: `settings_clear_browsing_data_title`.
  - The dialog title: `settings_clear_dialog_title`.
  - One label and one short description per category: `settings_clear_category_history` / `_history_description`, `settings_clear_category_cookies` / `_cookies_description`, `settings_clear_category_cache` / `_cache_description`.
  - Actions: `settings_clear_confirm`, plus a cancel label — reuse an existing Cancel string if `{res}/values/strings.xml` already has one, otherwise add `settings_action_cancel`.
  - Status and outcome messages: `settings_clear_in_progress`, `settings_clear_done`, `settings_clear_partial_failure`.
- [X] T073 [US4] Create `ClearBrowsingDataDialog` at `{main}/presentation/settings/components/ClearBrowsingDataDialog.kt` (after T063, T072).
  - Three checkbox rows, each with `Role.Checkbox` semantics and a height of at least 48dp.
  - Confirm is disabled while the selection is empty (FR-026).
  - While `inProgress`: show a progress indicator, disable confirm, and ignore dismissal (FR-032).
- [X] T074 [US4] In `SettingsViewModel` and its state types (after T067), inject `ClearBrowsingDataUseCase` and add three things: `SettingsDialog.ClearBrowsingData(selection: Set<ClearBrowsingDataCategory>, inProgress: Boolean)`, `SettingsEvent.BrowsingDataCleared` and `SettingsEvent.BrowsingDataPartiallyCleared`.
  - Opening the dialog selects **all three** categories (FR-025), and each category can be toggled.
  - Confirm sets `inProgress`, runs the use case, closes the dialog, then emits `BrowsingDataCleared` when `failed` is empty or `BrowsingDataPartiallyCleared` otherwise.
  - While `inProgress`, both confirm and dismiss are ignored.
  - Add a defaulted `ClearBrowsingDataUseCase` parameter to the JVM factory (T030), built from the fakes of T065 and T068; T077 adds the instrumented one.
- [X] T075 [US4] In `SettingsScreen` (after T073, T074), add the privacy-section row, render the dialog, and map both outcome events to their snackbars.
- [X] T076 [P] [US4] Extend `SettingsViewModelTest` (after T074): the dialog opens with all three categories selected; toggling works; confirm with an empty selection is impossible; a second confirm while in progress is ignored; each outcome maps to its event.
- [X] T077 [P] [US4] Compose test `{androidTest}/presentation/settings/SettingsScreenClearDataTest.kt` (after T075). First add to `SettingsScreenTestDoubles.kt` instrumented fakes for `WebDataCleaner`, `CookieJarSnapshotManager`, `FaviconCache`, `ScreenshotCache` and `HistoryRepository` — reuse the in-memory history repository from `{androidTest}/presentation/history/HistoryScreenTestDoubles.kt` where it fits — plus a defaulted `ClearBrowsingDataUseCase` parameter in the instrumented factory built from them. Then assert: three checked boxes on open; deselecting all disables confirm; the in-progress state blocks dismissal.

### Gate tooling and gates

- [X] T078 [P] [US4] Create the debug-only `TabSeeder` at `app/src/debug/kotlin/com/raumanian/thirtysix/browser/dev/TabSeeder.kt`, structured like `HistorySeeder` in the same folder, and register it in `app/src/debug/AndroidManifest.xml` with the action `com.raumanian.thirtysix.browser.debug.SEED_TABS`, taking an optional `--ei count N` that defaults to 50. It creates normal tabs through the tab repository until the **total** normal-tab count reaches `min(N, BrowserLimits.MAX_TABS)` — the app always holds at least one tab and `MAX_TABS` is 50, so creating N new tabs would hit the cap — and writes a placeholder preview image and site icon for each tab through `ScreenshotCache` and `FaviconCache`, using a distinct host per tab, so the cache category has real files to delete (research.md R15).
- [X] T079 [US4] Manual gates per quickstart.md, recording each device's web-engine version and which path ran:
  - **G5** on both AVDs, with step 5 run three times on each (SC-007).
  - **G6** on both AVDs, including the control run that must bring the cookie back.
  - **G8**: the gating run on the API 24 AVD, and the non-gating hardware figure — or "not measured — no hardware", never an emulator substitute.

---

## Phase 7: User Story 5 — Choose how long history is kept (Priority: P2)

**Goal**: A retention window of 7, 30, 90 or 180 days; shortening warns, confirms and deletes immediately; the start-up sweep enforces the chosen window.

**Independent Test**: With 300 entries spanning 200 days, walk through all four windows — shorten to 30 days (cancel, then confirm), then to 7 days, then lengthen to 180 days and shorten to 90 — verifying after each change that nothing older than the window remains, immediately and after a relaunch, and that lengthening shows no warning (quickstart G7).

- [X] T080 [P] [US5] Create `HistoryRetentionChangeResult` at `{main}/domain/model/HistoryRetentionChangeResult.kt` exactly per [contracts/SettingsUseCases.kt](contracts/SettingsUseCases.kt): `Applied(rowsRemoved: Int)`, `SavedPruneDeferred`, `NotSaved`.
- [X] T081 [US5] Implement `ChangeHistoryRetentionUseCase` at `{main}/domain/usecase/ChangeHistoryRetentionUseCase.kt` per the contract (after T080). It takes `SettingsRepository` and `HistoryRepository`.
  - Write first: a `Result.Error` → `NotSaved`, **without pruning**.
  - Then prune with `historyRepository.pruneOlderThan(now - TimeUnit.DAYS.toMillis(retention.days.toLong()))`: a throw → `SavedPruneDeferred`; success → `Applied(rowsRemoved)`.
  - `CancellationException` is rethrown. `now` is an injectable parameter.
- [X] T082 [P] [US5] Unit test `{test}/domain/usecase/ChangeHistoryRetentionUseCaseTest.kt` (after T081), using `FakeSettingsRepository` and the existing `{test}/testdoubles/FakeHistoryRepository.kt`. Cover: the write happens before the prune; `NotSaved` never prunes; a throwing prune yields `SavedPruneDeferred` with the new window still saved; lengthening removes no rows that are inside the old window; and, **parameterised over all four windows** — 7, 30, 90 and 180 days (SC-010) — with rows seeded one hour inside and one hour outside the window for an injected `now`, exactly the outside rows are removed.
- [X] T083 [US5] Switch the start-up sweep to the persisted window (FR-024):
  - `{main}/domain/usecase/PruneOldHistoryUseCase.kt` gains `SettingsRepository` and computes its cutoff from `settingsRepository.observeSettings().first().historyRetention.days`, per the contract.
  - **Delete** `MAX_HISTORY_DAYS` from `{main}/core/constants/BrowserLimits.kt`, and update the KDoc that references it in `{main}/domain/repository/HistoryRepository.kt`.
  - Update `{test}/domain/usecase/PruneOldHistoryUseCaseTest.kt` for the new constructor, with a default 90-day case and a 7-day case.
  - Afterwards, `grep -rn MAX_HISTORY_DAYS app/src` MUST return nothing.
- [X] T084 [P] [US5] Strings ×8:
  - The row: `settings_history_retention_title`.
  - Option labels: `<plurals name="settings_history_retention_days">` with `%d` — use each locale's plural categories (for example `one`/`few`/`many`/`other` for Russian, `other` only for Korean, Japanese, Chinese and Vietnamese).
  - The warning dialog: `settings_retention_shorten_title`, `settings_retention_shorten_body` (which names the new window), `settings_retention_shorten_confirm`.
  - The deferred-prune message: `settings_retention_prune_deferred`.
- [X] T085 [US5] Create two dialogs in `{main}/presentation/settings/components/` (after T084):
  - `HistoryRetentionChooserDialog.kt` — exactly four options, built on `SingleChoiceDialog`.
  - `ConfirmShortenRetentionDialog.kt` — confirm and cancel actions.
- [X] T086 [US5] In `SettingsViewModel` and its state types (after T081), inject `ChangeHistoryRetentionUseCase` and add `SettingsDialog.RetentionChooser`, `SettingsDialog.ConfirmShortenRetention(target: HistoryRetention)` and `SettingsEvent.RetentionPruneDeferred`.
  - Selecting the current window is a no-op (FR-006).
  - A shorter window opens `ConfirmShortenRetention` (FR-021); a longer one applies immediately (FR-023).
  - Confirming applies the change; cancelling changes nothing.
  - `NotSaved` → `SettingNotSaved`; `SavedPruneDeferred` → `RetentionPruneDeferred`.
  - Add a defaulted `ChangeHistoryRetentionUseCase` parameter to the JVM factory (T030), built from `FakeSettingsRepository` and `FakeHistoryRepository`; T089 adds the instrumented one.
- [X] T087 [US5] In `SettingsScreen` (after T085, T086), add the privacy-section retention row showing the localized current window, and render both dialogs.
- [X] T088 [P] [US5] Extend `SettingsViewModelTest` (after T086): shorter → confirmation; longer → no confirmation; cancel → no use-case call; each outcome maps to its event.
- [X] T089 [P] [US5] Compose test `{androidTest}/presentation/settings/SettingsScreenRetentionTest.kt` (after T087). First add to `SettingsScreenTestDoubles.kt` a defaulted `ChangeHistoryRetentionUseCase` parameter in the instrumented factory, backed by an in-memory `HistoryRepository`. Then assert: the row shows 90 days by default; the chooser lists exactly four options; choosing a shorter window shows the warning, and cancelling leaves the row unchanged.
- [X] T090 [US5] Manual gate **G7** per quickstart.md on the API 24 AVD — all four windows, plus the 10,000-entry step with no ANR (FR-022, SC-010).

---

## Phase 8: User Story 6 — Turn dynamic color on or off (Priority: P3)

**Goal**: On Android 12+, a switch toggles wallpaper-derived color; it is hidden on older versions.

**Independent Test**: On API 36, toggle it off and on and confirm the palette changes immediately and persists; on API 24, confirm no control is shown (quickstart G2).

- [X] T091 [P] [US6] Create `SetDynamicColorEnabledUseCase` at `{main}/domain/usecase/SetDynamicColorEnabledUseCase.kt` — pure delegation per the contract — and add its case to `{test}/domain/usecase/SettingsUseCasesTest.kt`.
- [X] T092 [US6] In `{main}/MainActivity.kt`, pass `dynamicColor = settings.isDynamicColorEnabled` to the existing `ThirtySixTheme(...)` call (research.md R9). Same file as T055 — run after it if US3 is already done.
- [X] T093 [P] [US6] Strings ×8: `settings_dynamic_color_title`, `settings_dynamic_color_summary`.
- [X] T094 [US6] In `SettingsViewModel` (after T091), inject `SetDynamicColorEnabledUseCase` and add `onDynamicColorToggled(enabled: Boolean)`: a no-op when unchanged, a write otherwise, and `SettingNotSaved` on error. Add a defaulted `SetDynamicColorEnabledUseCase` parameter to both factories (T030, T031).
- [X] T095 [US6] In `SettingsScreen` (after T093, T094), add the appearance-section switch row, rendered **only** while `uiState.isDynamicColorSupported` is true (FR-010).
- [X] T096 [P] [US6] Extend `SettingsViewModelTest` with the toggle cases, and create `{androidTest}/presentation/settings/SettingsScreenDynamicColorTest.kt` asserting that the row is absent when unsupported and that toggling writes (after T095).
- [X] T097 [US6] Manual gate **G2** per quickstart.md.

---

## Phase 9: User Story 7 — See the app version and privacy statement (Priority: P3)

**Goal**: About shows the app name, the installed version and the privacy statement, offline.

**Independent Test**: Compare the version shown with `dumpsys package`, and check that About renders in airplane mode (quickstart G9).

- [X] T098 [P] [US7] Strings ×8: `settings_about_version` (for example `Version %1$s`) and `settings_about_privacy_statement`. The statement MUST say that the app collects no personal data, sends nothing to any ThirtySix-controlled server, and keeps browsing data on the device (FR-035). The app name comes from the existing `app_name`.
- [X] T099 [P] [US7] Create `AboutSection` at `{main}/presentation/settings/components/AboutSection.kt`: the app name, `settings_about_version` filled with the state's `appVersionName`, and the statement — plain text only, with no links, licence list or rating prompt (spec A10).
- [X] T100 [US7] In `SettingsScreen` (after T098, T099), render `AboutSection` under the About header, last in the list.
- [X] T101 [P] [US7] Compose test `{androidTest}/presentation/settings/SettingsScreenAboutTest.kt` (after T100): the version text equals the injected value, and the statement is displayed.
- [X] T102 [US7] Manual gate **G9** per quickstart.md.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Accessibility and hard-code audits, the full gate set, size and alignment checks, regression gates, and documentation.

- [X] T103 [P] Accessibility pass across `{main}/presentation/settings/`: every interactive element has a localized label and announces its selected, checked or toggled state; every touch target is at least 48×48dp; decorative icons use `contentDescription = null` (FR-039, Constitution §VIII).
- [X] T104 [P] Hard-code audit of every new or changed file in this spec, per Constitution §III's red-flag list: no `Color(0x`, no `fontSize` or `.sp` literals, no `"http`, no inline `stringPreferencesKey("` or `intPreferencesKey("`, no `Text("` literals, no undeclared numbers. Also confirm `grep -rn "LanguageOverride\|MAX_HISTORY_DAYS" app/src` returns nothing.
- [X] T105 Run the full automated gate set — `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck assembleDebug assembleRelease` plus `./gradlew connectedDebugAndroidTest` — until everything is green, with the **detekt baseline UNCHANGED**.
- [X] T106 Run `.specify/scripts/bash/verify-16kb-alignment.sh`. Every native library entry must be at `align 0x4000`, and the APK must still contain exactly the existing eight `.so` entries (SC-015).
- [X] T107 Measure `app/build/outputs/apk/release/app-release.apk` in bytes. It MUST be **≤ 3,258,091 B** (SC-014). Record the exact figure and its delta against T001's baseline; if T002 or T003 picked newer versions than planned, record that too.
- [X] T108 Manual gate **G10**, steps 2–4, on both AVDs: overflow-menu regressions, screenshot protection, and the start-up retention sweep without opening History. Step 1 ran in T062. In the same pass, run **G8's settings-list scrolling measurement** (Constitution §V) now that every row exists, recording the Pixel 5-class figure only if such hardware is available.
- [ ] T109 Manual gate **G11**: the 8-locale visual sweep of the screen, all six dialogs and every message, plus a TalkBack pass in English and in Japanese or Russian.
- [X] T110 Manual gate **G12**: consolidate T105–T107; confirm research.md records each new dependency's version, lookup date and native-code status (FR-045); and run **Constitution Testing Gate 8** — install and exercise the release APK on an API 35+ **16 KB page-size** emulator image, recording `getconf PAGE_SIZE` (G12 step 4).
- [X] T111 Re-run the Constitution Check post-implementation and confirm **11/11 PASS** with no deviations. In `.specify/memory/constitution.md`, mark `TODO(MANIFEST_COMMENT)` as done inline in the Sync Impact Report — the precedent `TODO(SIGNING)` set; bookkeeping only, no version change.
- [X] T112 Update `CLAUDE.md` (roadmap row, Active Spec, Recent Changes), `.claude/claude-app/project-context.md` and `.claude/claude-app/sdd-roadmap.md` with the outcome: the task count, every gate result including any DEFERRED, the release APK bytes against the budget, and the `language_override` retirement. Per the Constitution's Review Requirements, `project-context.md` MUST record each new dependency's version, lookup date **and 16 KB compliance status** — research.md already does.
- [ ] T113 Open the PR `016-settings-screen → main` with a body containing:
  - a link to this `tasks.md`
  - the SC-014 result in bytes against the 3,258,091 B budget
  - the Constitution Check line
  - the nine clarifications
  - both new dependencies with their versions and 16 KB status
  - the A16 and A17 behaviours
  - the SC-009 hardware figure, or "not measured — no hardware"
  - the informational locale-filter finding (−221,076 B, not adopted)
  - every gate recorded as DEFERRED

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies. T002 → T003 → T004 → T005 run in that order because T002 and T003 share a file and T004–T005 depend on them.
- **Foundational (Phase 2)**: depends on Setup and **blocks every story**. Inside it, T006 → T009 → T010 → T011 and T012 → T013 → T014 → T015 → T016 → T017 are strict chains, and T018 can run as soon as T016 is done; T021–T028 build the screen in order.
- **User stories (Phases 3–9)**: each depends only on Foundational.
- **Polish (Phase 10)**: depends on every story being done.

### User-story dependencies

| Story | Depends on | Shares files with |
|---|---|---|
| US1 Theme | Foundational | every story — `SettingsViewModel.kt`, `SettingsScreen.kt`, `SettingsUiState.kt`, the 8 `strings.xml` |
| US2 Search engine | Foundational | as above |
| US3 Language | Foundational | `MainActivity.kt` (with US6), `SettingsPlatformModule.kt` (with US4) |
| US4 Clear data | Foundational | `SettingsPlatformModule.kt` (with US3) |
| US5 Retention | Foundational | — beyond the shared screen files |
| US6 Dynamic color | Foundational | `MainActivity.kt` (with US3) |
| US7 About | Foundational | — beyond the shared screen files |

No story needs another story's **behaviour** — each is independently testable after Foundational. They do share a handful of **files**, so run them sequentially in priority order unless edits to those files are coordinated.

### Within each story

Strings and domain types → use cases and seams → view-model → screen wiring → tests → manual gate.

### Parallel opportunities

- **Phase 2 first wave**: T006, T007, T008, T020, T022, T026.
- **US1**: T032 and T034 together; then T036 alongside T033 and T035.
- **US3**: T046, T048 and T056 together; T047 once T046 exists.
- **US4**: T063 and T072 together; then T064, T065, T066 and T078; T068 and T069 once their subjects exist.
- **Different stories**: can proceed in parallel only with coordination on the shared files listed above.

---

## Parallel Example: User Story 4

```bash
# Wave 1 — no dependencies inside the story:
Task: "T063 Create ClearBrowsingDataCategory, ClearBrowsingDataResult, SiteDataClearOutcome in {main}/domain/model/"
Task: "T072 Strings ×8 for the clear-browsing-data row, dialog, categories and messages"
Task: "T078 Create debug-only TabSeeder in app/src/debug/kotlin/com/raumanian/thirtysix/browser/dev/TabSeeder.kt"

# Wave 2 — after T063:
Task: "T064 Create WebDataCleaner interface in {main}/domain/repository/WebDataCleaner.kt"
Task: "T065 Add discardSetAsideCookies() to CookieJarSnapshotManager + impl + fake"
Task: "T066 Add FaviconCache.clearAll() + DiskFaviconCache impl + the 10 test doubles"

# Wave 3 — the privacy tests, once their subjects exist:
Task: "T068 ClearBrowsingDataUseCaseTest (discard-before-wipe ordering)"
Task: "T069 CookieJarSnapshotManagerDiscardTest + CookieRestoreInstrumentedTest case"
```

---

## Implementation Strategy

### MVP first (User Story 1)

1. Phase 1: Setup — the dependencies are on the classpath and `BuildConfig` is generated.
2. Phase 2: Foundational — Settings opens from the overflow menu.
3. Phase 3: US1 — the theme can be changed.
4. **Stop and validate** with gate G1.

### Incremental delivery

1. **US2** — the smallest increment; it only adds a control to existing behaviour.
2. **US3** — the highest-risk story, done while P1 work is still fresh. T055 is the moment the app could fail to launch, so install on both AVDs straight after it.
3. **US4** — the most edge cases, and both non-negotiable privacy tests.
4. **US5**, **US6**, **US7** — each a self-contained increment.
5. **Polish** — audits, gates, size, documentation, PR.

---

## Notes

- **Commit per task or per logical group.** Stop at any checkpoint to validate a story on its own.
- **Never measure performance on a debug build or an emulator against a hardware target.** SC-009's gating part is deliberately emulator-based; its 3-second figure is recorded only from real hardware (Spec 014 T103b and Spec 015 SC-006 are the precedents).
- **Always read `adb install` output.** A full `/data` partition fails silently, and Spec 014 lost time to it.
- **The two platform seams are total**: failure travels in return values, never exceptions. A `runCatching` missing around any platform call is a defect, not a style issue.
- **Files from earlier specs** touched here — Specs 003, 004, 006, 011, 012, 014 and 015 — are enumerated in research.md R13; flag them in the PR body for review.

---

## Implementation Notes

Recorded during `/speckit-implement`, 2026-09-11. Each entry is a deviation from the task text or a finding the task list did not anticipate.

- **Gradle needs Android Studio's JDK in this environment.** The shell has no system Java, so every Gradle command runs with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Without it `./gradlew` exits 1 with no BUILD line, which a filtered output can hide.
- **T001 baseline**: 480 unit tests, 0 failures; release APK **2,558,178 B**, identical to the research.md R3 figure.
- **T002 / T003**: lookup at 2026-09-11T04:16Z confirmed the planned versions, `appcompat` **1.8.0** and `webkit` **1.17.0**. The webkit Maven metadata's `<release>` tag named 1.18.0-alpha01, which was excluded. No newer stable versions exist to report for T107.
- **T005**: the generated `BuildConfig` declares `VERSION_NAME = "1.0"`.
- **T020**: `settings_action_back` and `settings_action_cancel` were added here, not reused from History. The project names back and cancel labels per feature (`history_action_back`, `downloads_action_back`, `downloads_delete_dialog_cancel`), which is analyze finding IN5. `SingleChoiceDialog` (T026) needs the cancel label already, and T072 reuses it.
- **T024**: `SettingsViewModel` does **not** take `DispatcherProvider`. Nothing in the view model dispatches work itself — `SetAppLanguageUseCase` owns its own main-thread hop — and detekt's active `UnusedParameter` rule rejects an unused constructor parameter. The `@Named` qualifiers use no `param:` target: Kotlin 2.3 reports that target as redundant on a constructor parameter that is not a property.
- **T026**: besides dismissing on an outside tap and system back, `SingleChoiceDialog` shows a **Cancel** button. That gives TalkBack users a visible way out of the dialog.
- **T027**: `SettingsScreen` keeps a defaulted `navController = rememberNavController()`, as `DownloadsScreen` does, so Compose tests do not need to build a navigation graph.
- **T030**: the project has no `MainDispatcherRule`. `SettingsViewModelTest` uses `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `@After`, the pattern `HistoryViewModelTest` uses.
- **⚠️ Lint finding caused by T004: `MissingOnRenderProcessGone` — then fixed on this branch.** `androidx.webkit` 1.17.0 ships its own lint checks. One of them flagged Spec 007's `BrowserWebViewClient` for not overriding `onRenderProcessGone`, so a renderer crash or out-of-memory kill took the whole app down. That was already true before this spec; the new dependency only made lint see it.
  - **First pass**: suppressed on that one class and recorded as carry-forward debt.
  - **Fix (owner request, 2026-09-11)**: `BrowserWebViewClient.onRenderProcessGone` now returns `true`. `BrowserWebView` keys its WebView to a renderer generation held by a small `WebViewHost`. Losing the renderer advances the generation, which destroys the dead WebView — nothing else is called on it, as the platform requires — and builds a replacement for the same tab.
    - **Renderer crashed** (`didCrash()` true, or no detail): the error state shows (`ErrorReason.Generic`) and the replacement waits for Reload, so a page that crashes its renderer cannot do it in a loop. Reload on a WebView that has loaded nothing now loads the tab's current address; before, `reload()` on an empty WebView did nothing.
    - **Renderer reclaimed by the system**: the replacement reloads the page by itself.
    - **Not carried over**: back/forward history. `canGoBack` / `canGoForward` are reset to false, and `WebViewActionsHandle.detach()` silences the handle until the replacement re-wires it.
    - **Incognito**: the dead WebView is only destroyed. The replacement belongs to the same tab and runs Spec 012's full 9-step teardown when released, which is where the two global clears (SSL decisions, disk cache) happen.
  - **Lint**: the suppression stays, with a new rationale. The detector has two halves (read from its bytecode in `webkit-1.17.0.aar`'s `lint.jar`). `visitClass` looks for the override, which now exists. `visitConstructor` reports every `WebViewClient` constructor call unconditionally, and in Kotlin that includes this class's own superclass call. Lint underlines `WebViewClient()` in the class header, not the class name, so no implementation can clear it.
  - **Tests**: new `BrowserWebViewRendererRecoveryTest` (androidTest, API 26+) loads `chrome://crash` and `chrome://kill` through the WebView API. It passed **2/2** on the 16 KB API 36 AVD, with logcat showing `crashed=true` and `crashed=false` respectively.
  - **Release build on device**: typing `chrome://crash` into the address bar did not crash the renderer, so both paths were driven by signalling the app's renderer process as root on the 16 KB AVD. `SIGSEGV` → "Page didn't load", and Reload recovered the page. `SIGKILL` → the page reloaded by itself. The app stayed in the foreground throughout, and the crash buffer stayed empty.
  - **For review**: this touches Spec 007's `BrowserWebView.kt` and `WebViewActionsHandle.kt` from the Spec 016 branch; flag it in the PR body.
- **T049 / T058**: the view model reads the platform language when it is created **and** each time the screen starts. Reading only on start would show "Follow system" for one frame on a device that has a language set.
- **T066**: `clearAll()` went into **11** doubles across the 10 listed files. `BrowserViewModelIncognitoCacheGateTest` holds two, a no-op and a recording one.
- **T068**: `FakeHistoryRepository` and `FakeCookieJarSnapshotManager` gained an optional shared call log and error switches. Both are defaulted, so no existing caller changed. `FakeWebDataCleaner`, `FakeFaviconCache` and `FakeScreenshotCache` are new.
- **T069**: the Robolectric part holds exactly the two `hasSnapshot()` cases the task names. What a restore actually writes back is proven only by the two instrumented cases, on the real cookie manager.
- **T070**: added `BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS` (15 s).
  - **Why**: the clear dialog cannot be dismissed while a clear runs (FR-032). A web-engine completion callback that never arrived would leave the user stuck behind it for good, so a timeout makes the step `Failed`, which the contract already allows for "never completed".
  - **Lint**: the `WebViewFeature` guard and the `WebStorageCompat.deleteBrowsingData` call share one function. Lint accepted it, and the API needed no opt-in annotation.
- **T075**: added `SettingsActionRow`. The clear-browsing-data row starts an action and has no current value, so neither existing row type fit.
- **T078**: lint's `UseKtx` required `androidx.core.graphics.createBitmap` in `TabSeeder`.
- **T083 — 🐞 a Spec 006 test turned flaky, found and fixed.** `SettingsModuleSmokeTest` failed about 1 run in 3 with "There are multiple DataStores active for the same file".
  - **Cause**: Robolectric boots the manifest's `ThirtySixApplication`. Since T083, its start-up retention sweep reads settings through Hilt's singleton DataStore — on the exact file this test opens with a second DataStore. Production is unaffected, because the app only ever has the one singleton.
  - **Fix**: the test now runs under a plain `Application`, via `@Config(application = Application::class)`.
  - **Verified**: 5/5 isolated runs, then the full suite.
- **T084**: the confirmation body names the new window through the same plural resource the row uses, so Russian and French get correct plural forms. Endonyms and brand names are `translatable="false"` in the default locale only.
- **T098**: the privacy statement says "This app" rather than repeating the product name, which already heads the About section.
- **Tooling — lint crash, not a code defect.** Running `assembleRelease` and `lintDebug` in one Gradle invocation crashed `lintAnalyzeDebugUnitTest` with a `FileNotFoundException`: lint read a Hilt-generated *release* source while that task was regenerating it, and lint itself calls this "a bug in lint". `lintDebug` on its own passes. Run the two in separate invocations.
- **Gate fixture**: the page stores its five kinds of data only at `http://localhost:8000/?seed=1`, and merely reports at `/`. A page that stored data on every load could never show that a clear had removed it. The server serves the image with `Cache-Control: max-age=31536000` and logs every request.
- **T106**: the release APK holds exactly **8** `.so` entries — `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` × 4 ABIs — and every one is at `align=0x4000`. Neither new dependency added native code.
- **T107**: release APK **3,122,115 B**, against the **3,258,091 B** budget (SC-014).
  - **Delta**: **+563,937 B** over the T001 baseline of 2,558,178 B, which leaves 135,976 B of headroom.
  - **Versions**: appcompat 1.8.0 and webkit 1.17.0, exactly as planned.
- **Web engines on the gate AVDs**:
  - API 36 (`BE4B.251210.005`): WebView **134.0.6998.135**, which is past the 133 cut-off, so the **Complete** path is expected.
  - API 24 (`NYC`): WebView **53.0.2785.124**, so the **fallback (Partial)** path is expected.
- **T104 — ✅ hard-code audit** over the 52 Kotlin files this spec added or changed under `app/src/main` and `app/src/debug`:
  - **No hits**: `Color(0x`, `fontSize =`, `.sp` or `.dp` literals, `Text("…")` literals, inline preference keys.
  - **`https://` literals**: two, both legitimate. One is the debug-only `TabSeeder`'s seed host, the precedent `HistorySeeder` set. The other is `BrowserScreen.kt:296-297`, Spec 009's existing scheme check, which this spec did not write.
  - **Numbers in `presentation/settings/`**: appear only in comments.
  - **Retired names**: `grep -rn "LanguageOverride\|MAX_HISTORY_DAYS" app/src` returns nothing.
- **T055 — ✅ launch check on both AVDs, before any other device work.** The debug build installed and `MainActivity` launched on API 36 and API 24, with an empty crash buffer.
  - **API 24 detail**: the first launch was later killed by the system — "depends on provider … DownloadProvider in dying proc android.process.media". That is an emulator media-process restart, not an app crash. A relaunch reached `mResumedActivity` in 978 ms.
- **Second device pass (2026-09-11, owner request to fix what could be fixed and run what could be run)** — findings that are not gate results:
  - **Two input-dispatch ANRs on `G9_API36_Clean` came from the AVD, not the app.**
    - **Circumstances**: both were "Input dispatching timed out" while scripted input was being injected. CPU pressure stood at 75 % and 86 % (IO 40 %) on a 2 GB AVD that was swapping about 1 GB, with four emulators and an instrumentation run on the host.
    - **Traces**: the first trace shows the main thread idle in `Looper.loop → MessageQueue.next → nativePollOnce`, with no app, Compose, WebView or storage frame. The second dropbox entry carried no trace.
    - **Counter-evidence**: five scripted cold launches with Settings navigation raised none afterwards, and no app ANR occurred on the API 24, API 29 or 16 KB API 36 AVDs in any run of this pass.
  - **FR-013a is intact.** The address bar that kept focus after submitting under automation is an input-mode artefact. Key events injected with `adb shell input` take the window out of touch mode, and there clearing focus hands it straight back to the first focusable view. Driven by taps alone (touch mode), submitting with the keyboard's Go key cleared focus and hid the keyboard, as specified.
  - **A language change shows 111–288 ms of black frames while the activity is recreated.**
    - **Measurement**: host-side 60 fps recording on the 16 KB API 36 AVD, 6 changes.
    - **Comparison**: a rotation recorded the same way shows none, because the system rotates a preserved window.
    - **Verdict**: the new language is on screen 169–504 ms after the dialog closes, inside SC-004. This is recorded as an observation against R12's "the same thing a rotation does", not as a failure.
  - **`DecorView` identity is not evidence of recreation on API 33+.** It changed on every language change on API 24, where AppCompat recreates the activity. It never changed on API 36, where the platform's per-app locale change relaunches with a preserved window. The on-device FR-006 check therefore rests on API 24.
  - **Tooling**:
    - A Gradle `connectedDebugAndroidTest` run restarted the adb server, which silently dropped every `adb reverse` rule. The fixture became unreachable (`ERR_CONNECTION_REFUSED`) mid-run, so later instrumented runs used `adb shell am instrument` directly.
    - `adb shell input text` cannot type non-ASCII, and host → guest clipboard sync did not propagate. G3's queries were therefore put on the device clipboard by a fixture page (a user-gesture `execCommand('copy')`) and pasted with KEYCODE_PASTE — the real text-field input path.
    - A web page's inputs also appear as `EditText` in uiautomator dumps, so the address bar has to be identified by its position.
    - Screenshots are black while any incognito tab exists (`FLAG_SECURE`, Spec 012). Close incognito tabs before any screenshot-based check.

---

## Device Gate Results

Run 2026-09-11 against the debug build on two AVDs, driven over `adb` with a local fixture server:

- **API 36**: `G9_API36_Clean`, Android 16 build `BE4B.251210.005`, WebView **134.0.6998.135**.
- **API 24**: `TA016_API24`, Android 7.0 build `NYC`, WebView **53.0.2785.124**.

Each item below is what was actually observed. PASS means every step the gate describes ran and held. PARTIAL and DEFERRED name exactly what did not run.

### ✅ T038 — G1 theme: PASS on both AVDs (SC-002 measured on an emulator, indicative)

- **Entry point**: the overflow menu lists Bookmarks → History → Downloads → **Settings**, and Settings is reached in 2 taps (both AVDs).
- **Not rebuilt**: after scrolling the list, choosing Light, System and Dark in turn left every row at exactly the same on-screen position (both AVDs).
- **Tab kept**: back in the browser, the same tab was still open at the same address.
- **Persistence**: the choice survived force-stop + relaunch (API 24 twice, API 36 once).
- **Following the system**: on API 36 with System selected, `cmd uimode night yes` turned the Settings background `#0d0e12` and `night no` turned it `#faf8fe`. On API 24, which has no system-wide dark mode, System resolves to light (`#ffffff`).
- **SC-002 ≤ 100 ms — measured with the emulator's host-side recorder.**
  - **Why that recorder**: the guest `screenrecord` failed on both AVDs. API 24 reported `Encoder failed (err=-38)`, and on API 36 it produced 8 frames of the light screen that missed the dialog and the change. `adb emu screenrecord start --fps 60` records the emulator's display on the host instead.
  - **Setup**: 16 KB API 36 AVD, debug build, three changes in each direction, mean luma read per frame.
  - **Dark → light**: the dialog closed and the new theme landed in the same frame, 3/3.
  - **Light → dark**: the first dark frame came 22–59 ms after the dialog's last frame. Measured from the first frame of the dialog fading out, it came at 69 ms, 93 ms and 121 ms, so one of six transitions is over 100 ms by that stricter reading.
  - **Status**: a debug build on an emulator, with other emulators running on the host, so these figures are indicative only. The Pixel 5-class figure is not measured — no hardware.

### ✅ T045 — G3 search engine: PASS

- **Input path**: `adb shell input text` cannot type non-ASCII. So a fixture page put each query on the device clipboard, through a user-gesture `execCommand('copy')`, and the query was pasted into the address bar with KEYCODE_PASTE and submitted — the real text-field path. The field was read back before every submit.
- **API 24 — 9/9**, after an earlier ASCII pass ("coffee") on all three engines:

  | Engine | `cà phê sữa` | `東京タワー` | `weather & forecast` |
  |---|---|---|---|
  | Google | `www.google.com/search?q=c%C3%A0+ph%C3%AA+s%E1%BB%AFa` | `…/search?q=%E6%9D%B1%E4%BA%AC%E3%82%BF%E3%83%AF%E3%83%BC` | `…/search?q=weather+%26+forecast` |
  | DuckDuckGo | `duckduckgo.com/?q=c%C3%A0+ph%C3%AA+s%E1%BB%AFa` | `…/?q=%E6%9D%B1%E4%BA%AC%E3%82%BF%E3%83%AF%E3%83%BC` | `…/?q=weather+%26+forecast` |
  | Bing | `www.bing.com/search?q=c%C3%A0+ph%C3%AA+s%E1%BB%AFa` | `…/search?q=%E6%9D%B1%E4%BA%AC%E3%82%BF%E3%83%AF%E3%83%BC` | `…/search?q=weather+%26+forecast` |

  Every recorded tab URL belongs to the chosen engine and decodes back to the exact query, and each results page shows the query.
- **FR-012**: tab A was left on Bing's results for `weather & forecast`. The engine was then switched to Google and the same query searched in a new tab B.
  - Tab A's address was still `www.bing.com/search?q=weather+%26+forecast`.
  - The switcher showed both results pages, and returning to tab A showed `www.bing.com`.
- **Persistence**: after force-stop + relaunch, Settings still showed Google.

### ✅ T062 — G4 language + G10 step 1: PASS

- **API 36 — system round trip (SC-005)**, on `G9_API36_Clean`:
  - the chooser lists Follow system plus the eight endonyms in order;
  - in-app Tiếng Việt made the whole screen Vietnamese and left the user on Settings (R12), with `get-app-locales` = `[vi]`;
  - system → app: `cmd locale set-app-locales … vi` updated the row, **3/3**; app → system: in-app English made the platform report `[en]`, **3/3**; Follow system → `[]`.
- **Every language with 3 normal tabs and 1 incognito tab open** (steps 1–2), with the incognito tab active:

  | AVD | Changes | Held on every change |
  |---|---|---|
  | API 24 `TA016_API24` (AppCompat path) | vi · de · ru · ko · ja · zh · fr · en · Follow system | Settings fully in the new language and still showing (R12); the chooser self-named (FR-014); 3 normal tabs in the database and the badge at 3; the incognito indicator on the active tab; `FLAG_SECURE` set on Settings and on the browser |
  | API 36 `TA016_API36_16K` | the same nine | the same |

  - **Badge**: it counts normal tabs only (`ObserveTabsUseCase`), so 3 normal + 1 incognito shows 3. The incognito tab's survival is shown by its indicator.
  - **API 24, "en"**: the scripted poll gave up after 29 s under host load, and the next step found Settings in English. Every other check held.
  - **Why the 16 KB AVD**: the API 36 sweep ran there because `G9_API36_Clean` (2 GB RAM) was CPU-starved at the time — see Implementation Notes.
- **SC-004 timing**: host-side 60 fps recording on the 16 KB API 36 AVD, 6 changes. The new language is on screen **169–504 ms** after the dialog closes, inside the 1 s target. In between, the activity shows 111–288 ms of black frames while it is recreated; a rotation shows none (Implementation Notes).
- **FR-006 on device** (API 24): re-selecting the language already showing left the `DecorView` unchanged, while every real change replaced it. On API 33+ the window is preserved across the relaunch, so this signal only means something on API 24. The unit test covers FR-006 as well.
- **Step 4 — device-language fallback**, on the 16 KB API 36 AVD with the app on Follow system. The device language was set with root `setprop persist.sys.locale` plus a framework restart, because this image has no `cmd locale set-device-locale`.
  - Device `es-ES`, which the app does not support → the app appeared in **English**.
  - Device `fr-CA`, a regional variant → the app appeared in **French** ("Paramètres", "Utiliser la langue du système"), with the system clock reading "16 h 02".
- **API 24 persistence**: in-app Deutsch survived force-stop + relaunch (`autoStoreLocales`), and Follow system returned the app to English.
- **G10 step 1 — no flash of the wrong window background on cold start.** Host-side 60 fps recordings launched from the launcher, reading each frame's mean luma:
  - **API 36, system dark**: the splash and first app frames stay dark (launcher 80.8 → 42 → 36 → 21 → Google page 51). No bright frame.
  - **API 36, system light**: the splash and app stay light (launcher 87 → one 159 transition frame → 228). No dark frame.
  - **API 24, system light** (Android 7 has no system dark mode): launcher 135 → 142 → 180 → 196 → 217 → 222. No dark frame.

### ✅ T079 — G5 / G6 / G8: PASS

**G5, API 36 — Complete path**

| Step | Selection | What the log and fixture showed | Web cache |
|---|---|---|---|
| 2 | History only | no web-data call; all five kinds of site data still present | image still cached |
| 3 | Cache only | `web cache: cleared=true 3764 ms`; all five still present | image re-requested |
| 4 | Cookies & site data only | `outcome=Complete 3672 ms`; cookie, localStorage, IndexedDB, service worker and Cache Storage all gone | image re-requested, because the engine emptied the cache too (A16) |

- **Step 5**: ran 3 times, and all three runs held. Each time all five kinds were absent, both on return to the browser and after a reload. Tabs 1 → 1 and bookmarks 1 → 1 stayed unchanged.
- **Step 6**: confirm tapped three times in a row produced exactly one clear.

**G5, API 24 — fallback path**

| Step | Selection | What the log and fixture showed | Web cache |
|---|---|---|---|
| 2 | History only | no web-data call; all five present | image still cached |
| 3 | Cache only | `web cache: cleared=true 50 ms` | image re-requested |
| 4 | Cookies & site data only | `outcome=Partial 173 ms`; cookie, localStorage and IndexedDB gone; the service worker and Cache Storage **remain** (the documented A17 limitation) | not touched |

- **Step 5**: ran 3 times, and all three runs held. Each run cleared history, re-requested the image, and removed cookie, localStorage and IndexedDB while the worker and Cache Storage remained. Tabs and the **15 downloads** stayed unchanged (FR-031).

**G6 — incognito cookies stay cleared**

- **API 36**: 3/3 runs passed. Each time the cookie was cleared from inside an incognito tab, then all incognito tabs were closed, and the normal tab showed **cookie absent**.
- **API 24**: 3/3 runs passed in the same way.
- **Control runs** (no clear), on both AVDs: the cookie **came back**. That proves Spec 012's restore really runs, so this gate is capable of failing.

**G8 — performance, gating part (API 24)**

- **Data**: 10,091 history entries, 50 tabs and the seeded fixture.
- **Result**: clearing all three categories showed the completion message within **≤ 5.7 s** of the tap. That is an upper bound set by uiautomator polling, because this AVD cannot record video.
- **Engine log**: `Partial 456 ms; web cache 45 ms`.
- **Stability**: no new ANR and no ANR dialog.
- **FR-031**: tabs 50 → 50, downloads 15 → 15.
- **App caches**: site icons 52 → 0, tab previews 50 → 1 (one re-captured on return).
- **SC-009 hardware figure**: not measured — no hardware.

### ✅ T090 — G7 retention (API 24): PASS

- **Step 1**: the chooser offers exactly 7, 30, 90 and 180 days.
- **Step 2**: 300 rows spanning 200 days, then relaunch → "removed 165 row(s)", oldest 89.33 days.
- **Step 3**: 30 days → the warning names "30 days" → Cancel → nothing changed.
- **Step 4**: 30 days → Delete → oldest 29.33 days immediately, and still 29.34 days after a relaunch.
- **Step 5**: 7 days → oldest 6.68 days, immediately and after a relaunch.
- **Step 6**: 180 days → no warning; after reseeding and a relaunch, oldest 179.33 days.
- **Step 7**: 10,320 rows → 90 days → 4,685 rows, oldest 89.99 days, with no ANR.
- **SC-010**: all four windows were exercised.
- **Tooling — fixed afterwards**: seeding 10,000 rows in a single `HistorySeeder` broadcast took 66.8 s and triggered a system *broadcast* ANR. That was the debug seeder, not the feature.
  - **First fix**: each batch of 500 inserts now shares one transaction. On the 16 KB API 36 AVD a 10,000-row seed then took 7.2 s. On the API 24 AVD it still took 60.5 s — a 500-row batch took 3.5 s at the median — and still ended in an ANR.
  - **Second fix**: a seed is now split across broadcasts of at most 1,000 rows. Each broadcast sends the next part to itself, and every part shares one start time, so the spread of visits is unchanged.
  - **Result on the API 24 AVD**: 10,000 rows seeded in 10 parts of 4.1–12.0 s (78 s in all). No ANR occurred, and the database held 10,000 rows.

### ✅ T097 — G2 dynamic color: PASS

- **API 36**: dynamic color defaults to on. Turning it off switched the dark background from `#0d0e12` to the teal `#0a0f0e` at once; light mode still worked; the switch stayed off after force-stop + relaunch.
- **API 24**: no dynamic color control is shown.

### ✅ T102 — G9 About (API 36): PASS

About shows "Version 1.0", which matches `dumpsys package` `versionName=1.0`, plus the full privacy statement. It renders identically in airplane mode after a relaunch.

### ✅ T103 — accessibility, code-level: PASS (TalkBack pass is under T109)

- **Roles and labels**: every settings row is one merged node with a text label. Value rows are clickable; the switch row is `toggleable` with `Role.Switch`; chooser options are `selectable` with `Role.RadioButton`; clear-data categories are `toggleable` with `Role.Checkbox`; section titles are headings; the top-bar back button carries a localized description. uiautomator dumps showed the labels on both AVDs.
- **Touch targets**: every row uses `minimumInteractiveComponentSize()`.
- **Decorative icons**: none in this spec.

### ✅ T105 — full automated gate set: PASS

- **JVM and static checks**: `testDebugUnitTest` **557/557**, `detekt` with the baseline unchanged, `ktlintCheck`, `assembleDebug` and `assembleRelease` all green. Re-run after the renderer-recovery fix and both `HistorySeeder` changes, with the same result.
- **Lint**: `lintDebug` passes when run in its own Gradle invocation; see the tooling note under Implementation Notes.
- **Instrumented**:
  - `connectedDebugAndroidTest` **107/107** on the API 36 AVD, including the new Settings Compose tests, `SettingsDataStoreInstrumentedTest` and both new cookie-discard privacy cases.
  - The renderer-recovery fix added `BrowserWebViewRendererRecoveryTest`, taking the suite to **109**. It ran **109/109** on an API 29 AVD with WebView 74 — the CI emulator's API level — and **109/109** on the 16 KB API 36 AVD, both through `adb shell am instrument`.

### ✅ T108 — G10 steps 2–4 + G8 list scrolling: PASS (hardware figure not measured)

- **Step 2**: from the overflow menu, Bookmarks, History and Downloads still open correctly on both AVDs.
- **Step 3**: `FLAG_SECURE` is on while an incognito tab is active and off afterwards on both AVDs. API 24 reports flags as hex: `0x81812100` has bit `0x2000` set; `0x81810100` does not.
- **Step 4**: the start-up sweep ran on every relaunch without History being opened.
- **Settings list scrolling**, API 36, indicative only (emulator, debug build, `font_scale 1.3`): 266 frames, 0.38 % janky, p50 17 ms, p90 19 ms, p99 19 ms. Pixel 5-class figure: not measured — no hardware.

### ⬜ T109 — G11 localization + TalkBack: localization PASS, TalkBack pass DEFERRED — left open

- **Step 1 — 8-locale sweep: PASS.** Run on the 16 KB API 36 AVD at **360dp** wide (`wm density 480` on 1080 px), switching locales with `cmd locale set-app-locales`.
  - **Text**: each expected string was read from that locale's `strings.xml` and found verbatim on screen, with zero misses in en, vi, de, ru, ko, ja, zh and fr. The sweep covered:
    - the Settings screen, top and bottom;
    - all six dialogs — theme, search engine, language, retention, the shorten-retention confirmation with its formatted body, and clear browsing data with all three category descriptions;
    - the "Browsing data cleared" message.
  - **Plurals**: the retention options used each locale's plural form, including Russian `many`.
  - **Clipping**: contact sheets of all 72 captures were reviewed. Long titles and descriptions wrap — for example "Systemsprache verwenden", "Удалить более старую историю?", "Supprimer l'historique plus ancien ?", "閲覧履歴データを削除しますか？" — and every button label is whole.
  - **Failure-only messages** (not saved, language not applied, partial clear, prune deferred) cannot be raised on a healthy device. They are present and translated in all seven non-English locales.
  - **Identical strings**: the only ones are intended — "Google", "Version 1.0" in de/fr, and "ThirtySix Browser" in de.
- **Step 2 — TalkBack pass: DEFERRED — needs a person.** TalkBack 16 was driven over adb with its verbose log and "Display speech output" enabled, but scripted input does not reach TalkBack reliably on this emulator:
  - **Swipes and keys**: injected swipes did not move accessibility focus even in the system Settings app, and Alt+Right moved it only intermittently.
  - **Taps**: injected taps were delivered to the app as clicks instead of being explored, even with `touch_exploration_enabled` set.
  - **What did get through was correct**: TalkBack announced the window "ThirtySix Browser", the section heading as "Appearance, Heading, In list, 12 items", the row "Search engine", the top-bar "Back", and "Double-tap to activate".
  - **Code level (T103) and view tree**: every row is one merged node carrying its label, and roles and states are exposed (switch, radio buttons, checkboxes). On the API 36 AVD's Settings screen every interactive target measured ≥ 48dp — rows 393×56–60dp, the top-bar button 48×48dp.

### ✅ T110 — G12 artefacts + Constitution Testing Gate 8: PASS

- **Artefacts**, re-measured after the renderer-recovery fix:
  - release APK **3,122,115 B**, unchanged, within the ≤ 3,258,091 B budget;
  - 8 `.so` entries, all `align=0x4000`, and no new native libraries;
  - research.md records each new dependency's version, lookup date and native-code status (FR-045).
- **Constitution Testing Gate 8** on AVD `TA016_API36_16K`, created from `system-images;android-36;google_apis_ps16k;arm64-v8a` (build `BE2A.250530.026.F3`, WebView 133.0.6943.137). `adb shell getconf PAGE_SIZE` printed **16384**.
  - **Release APK**: installed (`Success`) and launched. Settings opened; the theme changed to Dark; the language changed to Deutsch, staying on Settings; the clear-browsing-data dialog opened; then all of it was restored. The app stayed in the foreground throughout and the crash buffer stayed empty.
  - **Renderer recovery, release build**: `SIGSEGV` to the app's renderer process → "Page didn't load", and Reload recovered the page. `SIGKILL` → the page reloaded by itself.
  - **Instrumented suite**: **109/109** on the same AVD (debug build, `am instrument`).

### ✅ T111 — Constitution Check post-implementation: 11/11 PASS

- **No deviations.**
- **Recorded as debt, not a deviation**: the class-level `@SuppressLint("MissingOnRenderProcessGone")` on Spec 007's `BrowserWebViewClient` (see Implementation Notes).
- **Bookkeeping**: `TODO(MANIFEST_COMMENT)` marked ✅ DONE inline in the constitution's Sync Impact Report, with no version change.
