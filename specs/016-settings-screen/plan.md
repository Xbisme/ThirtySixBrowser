# Implementation Plan: Settings Screen

**Branch**: `016-settings-screen` | **Date**: 2026-09-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/016-settings-screen/spec.md`

## Summary

Replace the Spec 002 placeholder with a real Settings screen, reached as a fourth entry in the overflow menu Spec 015 built for it. Theme and search engine already persist and apply, so they need only controls. Dynamic color and history retention gain new DataStore keys, and retention becomes a confirmed, write-then-prune change that keeps Spec 014's start-up sweep in force.

Two parts of the feature reach into the platform, and each costs a dependency:
- **App language** is handed to the platform's per-app language setting through `androidx.appcompat`. That makes the system the single source of truth, retires Spec 006's never-applied `language_override` key, and forces `MainActivity` onto `AppCompatActivity` with AppCompat window themes.
- **Clearing cookies and site data** uses `androidx.webkit`'s complete removal, because the framework's long-standing call provably leaves service workers and Cache Storage behind (R5); older web engines fall back to the framework path.

A Spec 012 amendment empties — never nulls — the incognito cookie set-aside, so cleared cookies cannot be resurrected when the incognito session ends. Both libraries were measured in release builds before the plan relied on them: **+478,564 bytes** for appcompat, almost entirely structural, and **+16,549 bytes** for webkit, which fixes SC-014's budget at +699,913 bytes (R3).

## Technical Context

**Language/Version**: Kotlin 2.3.21 (per `gradle/libs.versions.toml`, set at Spec 001)
**Primary Dependencies**: Compose BOM 2026.04.01, Material3, material-icons-core, Hilt 2.59.2, KSP 2.3.7, Room 2.8.4, DataStore Preferences 1.2.1, kotlinx-coroutines/Flow, `desugar_jdk_libs` 2.1.5 — plus **two new**: `androidx.appcompat:appcompat` **1.8.0** (latest stable at lookup 2026-09-11; zero `.so` confirmed in a real release build) and `androidx.webkit:webkit` **1.17.0** (latest stable, released 2026-08-12, verified 2026-09-11; zero `.so` in the AAR, its declared AndroidX dependencies, and a real release build). Both are re-verified at the moment of addition (§IX).
**Storage**: DataStore Preferences `thirtysix_settings` — +2 keys (`dynamic_color_enabled`, `history_retention_days`), −1 key (`language_override`). Room **unchanged at version 2**. The app language is held by the platform's per-app language storage (AppCompat below API 33, `LocaleManager` on API 33+), not by the app.
**Testing**: JUnit 4 + Robolectric 4.16.1 + Turbine 1.2.1 + kotlinx-coroutines-test (JVM); Compose UI Test + Hilt-android-testing (instrumented)
**Target Platform**: Android 7.0 (minSdk 24) → Android 16 (targetSdk 36, compileSdk 36 with `minorApiLevel = 1`)
**Project Type**: Mobile app (single Android module under `app/`)
**Performance Goals**: SC-002 — theme and dynamic color changes visible within 100 ms (§VII); SC-004 — a language change complete within 1 s; SC-009 — **gating**: clearing all three categories with 10,000 history entries and 50 tabs completes with no ANR on the API 24 emulator; **non-gating**: 3 s on Pixel 5-class hardware with a release build, recorded when available; §V — 60 fps while scrolling the settings list (indicative emulator frame statistics in G8; the Pixel 5-class figure is recorded only from real hardware)
**Constraints**: SC-014 — release APK at most **3,258,091 B**, i.e. **+699,913 B** over the 2,558,178 B baseline (the two libraries measured at +495,113 B, plus 200 KiB for the feature — R3); SC-015 — every native library `align 0x4000`; no permission added or removed; icon set limited to `material-icons-core`; no Room migration
**Scale/Scope**: one rewritten screen, six dialogs, two platform seams, five new use cases (one amended, one removed), two new DataStore keys, one overflow-menu entry; performance envelope of 10,000 history rows and 50 tabs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.3.0 — **11/11 PASS pre-implementation, no deviations.**

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Privacy & Security First | ✅ PASS | Every setting stays on-device. Clearing strengthens privacy, and FR-030 closes a path by which Spec 012's restore could resurrect cleared cookies (R7). Site-data clearing removes service workers and Cache Storage wherever the engine allows it (R5), with the older-engine limit documented rather than hidden (spec A17). No analytics. The only new log lines record which clearing path ran and how long it took — never URLs, cookies or site data. |
| II | Google Play Compliance | ✅ PASS | No permission added or removed. The per-app language service entry is declared `android:enabled="false"` and `android:exported="false"`. |
| III | Code Quality & Safety / No-Hardcode | ✅ PASS | New storage keys in `StorageKeys`; defaults in `AppDefaults`; retention day counts as `BrowserLimits` constants, because detekt's `MagicNumber` flags enum arguments (R8); Hilt qualifier names as `const val`s; language names as non-translatable string resources (R11); every UI string via `stringResource(R.string.*)` in all 8 locales. |
| IV | Clean Architecture (MVVM) | ✅ PASS | Two new platform seams — `AppLanguageController`, `WebDataCleaner` — as pure-Kotlin domain interfaces with data-layer implementations. Coordination lives in use cases: `ClearBrowsingDataUseCase` joins history, web data, caches and the cookie set-aside; `ChangeHistoryRetentionUseCase` joins settings and history. No repository depends on another. The view-model receives platform facts only as injected values (R9, R10). `ClearBrowsingDataUseCase` consumes the `FaviconCache` and `ScreenshotCache` interfaces from `data/local/cache/`, the same arrangement `CloseTabUseCase` has used since Spec 011. |
| V | Performance Excellence | ✅ PASS | Theme and dynamic color apply through Compose state, so the app's screens are never rebuilt for them (FR-008). Clearing runs off the main thread except the platform calls that must run on it. SC-009 has a gate that can actually be closed. The APK grows (R3), but stays far below §V's 10 MB ceiling. |
| VI | Testing Discipline | ✅ PASS | JVM tests for the mapper, DataStore, repository, every new use case (order, outcomes, the discard rule), the view-model, and locale parity. **Instrumented** tests for DataStore read/write of the new keys and for the cookie restore-after-discard wipe, as §VI requires, plus Compose UI tests for the settings toggles §VI names explicitly. Twelve manual gates, including Testing Gate 8's launch on a 16 KB image (G12). |
| VII | Offline-First Architecture | ✅ PASS | DataStore writes are suspending; the settings snapshot is observed, so changes reach the UI within 100 ms. Every part of the feature works offline, About included (FR-036). Write-before-prune means a failed retention change can never delete history while leaving the old window recorded (R8). |
| VIII | Localization & Accessibility | ✅ PASS | Language switching uses `AppCompatDelegate.setApplicationLocales`, exactly as §VIII names, and needs no restart. All 8 locales with lint-enforced parity; language names shown in their own scripts; every control labelled, with its state announced and a 48dp target. |
| IX | Dependency Currency / 16KB | ✅ PASS | Two new dependencies, each the latest stable at lookup and each with zero native code — both confirmed in real release builds with `verify-16kb-alignment.sh` exiting 0 (R3, R5). Versions and lookup dates are recorded here, in research, and in the version catalog; both are re-checked at the moment of addition. |
| X | Simplicity & Build Order | ✅ PASS | Phase 4's first spec, after Specs 001–015 all merged. The choosers are stateless so Spec 018 can reuse them (FR-037) without a line of onboarding code. Twelve explicit out-of-scope items, including a home page setting, time-ranged clearing and per-site data. |
| XI | Build Configuration | ✅ PASS | `buildFeatures.buildConfig = true` — `BuildConfig` is §XI's sanctioned home for build values. Debug and release only; no signing change; `targetSdk = 36` unchanged. |

## Project Structure

### Documentation (this feature)

```text
specs/016-settings-screen/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 — 15 R-items, zero NEEDS CLARIFICATION
├── data-model.md        # Phase 1 — amended settings snapshot, 5 new domain types, cookie set-aside states, UI state
├── quickstart.md        # Phase 1 — automated gates + 12 manual gates G1–G12
├── spec.md              # /speckit-specify + /speckit-clarify output, amended during planning (A13, A16, A17, FR-028)
├── checklists/
│   └── requirements.md  # Spec quality checklist — 16/16
├── contracts/
│   ├── SettingsRepository.kt            # amended repository (+2 setters, −1)
│   ├── AppLanguageController.kt         # platform seam: per-app language
│   ├── WebDataCleaner.kt                # platform seam: cookies, site data, web cache
│   ├── ExistingInterfaceAmendments.kt   # CookieJarSnapshotManager + FaviconCache additions
│   └── SettingsUseCases.kt              # use-case signatures and behavioural rules
└── tasks.md             # Phase 2 output of /speckit-tasks (NOT created here)
```

### Source Code (repository root — Android single-module under `app/`)

```text
gradle/libs.versions.toml                             # MOD  +appcompat 1.8.0, +webkit 1.17.0 (lookup dates + 16 KB notes)
app/build.gradle.kts                                  # MOD  +2 implementation deps; buildFeatures.buildConfig = true
app/src/main/AndroidManifest.xml                      # MOD  +AppLocalesMetadataHolderService (autoStoreLocales);
                                                      #      stale permission comment corrected — closes Constitution TODO(MANIFEST_COMMENT)
app/src/main/res/values/themes.xml                    # MOD  parent → Theme.AppCompat.Light.NoActionBar (items kept)
app/src/main/res/values-night/themes.xml              # MOD  parent → Theme.AppCompat.NoActionBar (items kept)
app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/strings.xml
                                                      # MOD  new keys ×8; settings_screen_placeholder REMOVED;
                                                      #      8 language endonyms translatable="false" in values/ only

app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── MainActivity.kt                                   # MOD  AppCompatActivity; dynamicColor from settings
├── core/constants/
│   ├── BrowserLimits.kt                              # MOD  −MAX_HISTORY_DAYS; +HISTORY_RETENTION_DAYS_{7,30,90,180}
│   ├── StorageKeys.kt                                # MOD  +DYNAMIC_COLOR_ENABLED, +HISTORY_RETENTION_DAYS; −LANGUAGE_OVERRIDE
│   ├── AppDefaults.kt                                # MOD  +DYNAMIC_COLOR_ENABLED, +HISTORY_RETENTION; −LANGUAGE_OVERRIDE
│   └── AppConstants.kt                               # MOD  +Hilt qualifier names (dynamic-color support, version name)
├── data/
│   ├── local/datastore/SettingsDataStore.kt          # MOD  +setDynamicColorEnabled, +setHistoryRetention; −setLanguageOverride
│   ├── local/cache/FaviconCache.kt                   # MOD  +clearAll() on the interface and DiskFaviconCache
│   ├── local/cookies/CookieJarSnapshotManagerImpl.kt # MOD  +discardSetAsideCookies()
│   ├── local/locale/AppCompatAppLanguageController.kt  # NEW
│   ├── local/webdata/AndroidWebDataCleaner.kt        # NEW  complete path + framework fallback + web-cache path; logs path and duration only
│   ├── mapper/SettingsMapper.kt                      # MOD  new decoding rules (data-model §1)
│   └── repository/SettingsRepositoryImpl.kt          # MOD
├── domain/
│   ├── model/
│   │   ├── UserSettings.kt                           # MOD  +isDynamicColorEnabled, +historyRetention; −languageOverride
│   │   ├── LanguageOverride.kt                       # DEL
│   │   ├── HistoryRetention.kt                       # NEW
│   │   ├── HistoryRetentionChangeResult.kt           # NEW
│   │   ├── AppLanguage.kt                            # NEW
│   │   ├── ClearBrowsingDataCategory.kt              # NEW
│   │   ├── ClearBrowsingDataResult.kt                # NEW
│   │   └── SiteDataClearOutcome.kt                   # NEW
│   ├── repository/
│   │   ├── SettingsRepository.kt                     # MOD
│   │   ├── CookieJarSnapshotManager.kt               # MOD  +discardSetAsideCookies()
│   │   ├── AppLanguageController.kt                  # NEW
│   │   └── WebDataCleaner.kt                         # NEW
│   └── usecase/
│       ├── SetDynamicColorEnabledUseCase.kt          # NEW
│       ├── GetAppLanguageUseCase.kt                  # NEW
│       ├── SetAppLanguageUseCase.kt                  # NEW
│       ├── ChangeHistoryRetentionUseCase.kt          # NEW
│       ├── ClearBrowsingDataUseCase.kt               # NEW
│       ├── PruneOldHistoryUseCase.kt                 # MOD  reads the persisted window
│       └── SetLanguageOverrideUseCase.kt             # DEL
├── di/
│   └── SettingsPlatformModule.kt                     # NEW  @Binds both seams; @Provides the two named values
└── presentation/
    ├── browser/BrowserScreen.kt                      # MOD  overflow → Settings
    ├── browser/components/BrowserOverflowMenu.kt     # MOD  +Settings entry (fourth)
    ├── browser/components/BrowserOverflowMenuCallbacks.kt  # MOD  +onSettingsClick
    ├── navigation/AppNavGraph.kt                     # MOD  SettingsScreen(navController)
    └── settings/
        ├── SettingsScreen.kt                         # REWRITTEN (was the Spec 002 placeholder Text)
        ├── SettingsViewModel.kt                      # NEW
        ├── SettingsUiState.kt                        # NEW  incl. SettingsDialog
        ├── SettingsEvent.kt                          # NEW
        └── components/
            ├── SettingsTopBar.kt                     # NEW
            ├── SettingsSectionHeader.kt              # NEW
            ├── SettingsRows.kt                       # NEW  value row + switch row
            ├── SingleChoiceDialog.kt                 # NEW  internal, shared by the choosers
            ├── ThemeModeChooserDialog.kt             # NEW  public, stateless (FR-037)
            ├── AppLanguageChooserDialog.kt           # NEW  public, stateless (FR-037)
            ├── SearchEngineChooserDialog.kt          # NEW  public, stateless (FR-037)
            ├── HistoryRetentionChooserDialog.kt      # NEW
            ├── ConfirmShortenRetentionDialog.kt      # NEW
            ├── ClearBrowsingDataDialog.kt            # NEW
            └── AboutSection.kt                       # NEW

app/src/debug/kotlin/com/raumanian/thirtysix/browser/dev/TabSeeder.kt   # NEW  debug source set only (R15)
app/src/debug/AndroidManifest.xml                                        # MOD  register TabSeeder
```

Tests touched from earlier specs are enumerated in [research.md R13](research.md#r13--files-and-tests-from-earlier-specs-touched): the five settings test files, `PruneOldHistoryUseCaseTest`, `FakeCookieJarSnapshotManager`, ten `FaviconCache` doubles, and `BrowserOverflowMenuTest`. New tests cover every row of the automated-gate table in [quickstart.md](quickstart.md#a-automated-gates); the restore-after-discard wipe is added as a case in the existing `CookieRestoreInstrumentedTest`, since it needs the real cookie manager.

**Structure Decision**: Mirror Specs 013–015 — one feature folder under `presentation/settings/` with a `components/` subfolder, and the standard layering elsewhere. Two new `data/local/` packages hold the platform seams (`locale/`, `webdata/`), parallel to Spec 014's `clipboard/` and Spec 015's `download/`. No new Room package, because nothing here touches the database.

## Phase 0 — Research

Detailed in [research.md](research.md). Fifteen R-items, **zero NEEDS CLARIFICATION**. One product decision (R5) was put to the product owner mid-planning, once the evidence showed the framework API could not meet FR-028.

| R# | Topic | Decision (preview) | Basis |
|----|-------|--------------------|-------|
| R1 | Per-app language mechanism | AndroidX per-app language API, automatic storage; empty list = follow system | documented + gate |
| R2 | `MainActivity` base class and window theme | `AppCompatActivity`; AppCompat NoActionBar parents in both theme files, items kept | documented + gate |
| R3 | Size cost of the new libraries | appcompat **+478,564 B**, webkit **+16,549 B**, zero new `.so`; SC-014 budget **+699,913 B** | measured |
| R4 | Retiring the Spec 006 language value | Delete outright — never read, never released | verified-here |
| R5 | Clearing cookies and site data | `androidx.webkit` complete removal where supported; framework fallback | documented + gate |
| R6 | Clearing cached images and files | Detached throwaway `WebView.clearCache(true)` unless R5 already emptied it; `clearAll()` on both app caches | documented + gate |
| R7 | Incognito cookie set-aside | Replace with `EMPTY`, never null; discard before wipe | verified-here |
| R8 | Retention persistence and enforcement | Day count in DataStore; write-then-prune, three outcomes; sweep reads the setting | verified-here |
| R9 | Dynamic color | New Boolean key; existing theme parameter; capability injected | verified-here |
| R10 | Installed version | Enable `buildConfig`; `BuildConfig.VERSION_NAME` | documented |
| R11 | Language labels and locale parity | Non-translatable endonyms; parity test against `locales_config.xml` | verified-here |
| R12 | Fresh language display | Re-read on every screen start | verified-here + gate |
| R13 | Files from earlier specs touched | Enumerated; no default no-op members | verified-here |
| R14 | Reusable choosers | Stateless composables; no onboarding code | verified-here |
| R15 | SC-009 seeding | Reuse `HistorySeeder`; add debug-only `TabSeeder` | verified-here |

## Phase 1 — Design & Contracts

Detailed in [data-model.md](data-model.md), [contracts/](contracts/) and [quickstart.md](quickstart.md).

### Entities

- **`UserSettings`** (amended): `+isDynamicColorEnabled` (default `true`), `+historyRetention` (default 90 days), `−languageOverride`. Unknown or corrupt stored values decode to defaults, so no stored value can ever yield an unbounded window.
- **`HistoryRetention`**: exactly `Days7 · Days30 · Days90 · Days180`. "Keep forever" is unrepresentable, not merely unoffered.
- **`AppLanguage`**: `FollowSystem` plus the eight languages. Never persisted by the app; mapped from the platform by primary language subtag.
- **`ClearBrowsingDataCategory` / `ClearBrowsingDataResult` / `SiteDataClearOutcome`**: transient; they carry exactly what the completion and partial-failure messages need.
- **Incognito cookie set-aside**: one new transition, `Held(x) → Held(EMPTY)`, analysed in data-model §5.
- **`SettingsUiState`** (immutable), **`SettingsDialog`** (sealed, at most one open), **`SettingsEvent`** (one-shot on a `SharedFlow`, `replay=0 / capacity=1 / DROP_OLDEST`).

### Contracts

- **`SettingsRepository`** — amended; still depends only on DataStore and its mapper.
- **`AppLanguageController`** and **`WebDataCleaner`** — the two platform seams. Every member is total, so failure travels in return values.
- **`CookieJarSnapshotManager`** and **`FaviconCache`** — one member each, added to interfaces shipped by Specs 012 and 011.
- **Use cases** — behavioural rules that the tests are written against. The two that carry real logic:
  - `ChangeHistoryRetentionUseCase` — write, then prune, with three outcomes.
  - `ClearBrowsingDataUseCase` — fixed order, discard before wipe, the web-cache step skipped after a complete site-data clear, and every category attempted.

### Quickstart (gates)

[quickstart.md](quickstart.md) defines the automated gates and twelve manual gates. Four carry unusual weight:

- **G4** — language on both AVDs with an incognito tab open, plus the Android 13+ system-settings round trip in both directions.
- **G5** — clearing on both AVDs with the web-engine version recorded, verifying the **complete** path and the **fallback** path separately against a fixture that stores all five kinds of site data.
- **G6** — the incognito discard rule, with a **control run** that must fail without the clear, so the gate cannot pass vacuously.
- **G8** — the SC-009 split: a gating emulator run, and a hardware figure that is recorded only from real hardware and never substituted with an emulator number.

### Agent context update

The `<!-- SPECKIT START -->` / `<!-- SPECKIT END -->` block in [CLAUDE.md](../../CLAUDE.md) gets its "Active Spec" section repointed at this plan. It records the two new dependencies, the measured size cost, the `language_override` retirement and the clearing paths.

## Constitution Check (Post-Design Re-Check)

After data-model, contracts and quickstart were drafted: **11/11 still PASS, no deviations.** Design decisions checked rather than assumed:

- **§IV held twice.** Clearing touches four subsystems and retention touches two. Both joins sit in use cases, and the discard-before-wipe rule lives in the use case, so Spec 012's own restore contract is left untouched (R7).
- **§IX was applied as a gate, not a formality.** The second dependency was adopted only after the product owner saw evidence from Chromium's source that the framework call cannot meet FR-028 on any device (R5). Both libraries were checked for native code before being written into the plan.
- **§V was tested against real numbers.** appcompat's +478,564 B and webkit's +16,549 B were measured in release builds before the plan relied on them. Its causes are structural (R3) and the result stays within the 10 MB ceiling; the budget formula was set by the product owner (clarification Q4).
- **Two departures from documentation guidance, neither a Constitution matter, are recorded where a reviewer will look**: the throwaway `WebView` created without an activity context (R6), and the inferred need to flush cookies after removal (R5).
- **An open Constitution follow-up closes here**: this spec edits `AndroidManifest.xml`, so it corrects the stale permission comment recorded as `TODO(MANIFEST_COMMENT)` in Constitution v1.3.0.
- **Post-analysis remediation (2026-09-11)**: `/speckit-analyze` found two Constitution gaps in the first task list — no instrumented DataStore test (§VI) and no launch on a 16 KB page-size image (Testing Gate 8) — both now covered (T018, G12). It also found that leaving the browser screen always releases its web view, which made every "no page reload" criterion unverifiable; the spec now asks that the app's screens are not rebuilt and that the same tab returns at the same address.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No Constitution violations; the table is intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
