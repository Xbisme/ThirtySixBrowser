# Implementation Plan: Tab History and Swipe Navigation

**Branch**: `028-tab-history-swipe-nav` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/028-tab-history-swipe-nav/spec.md`

## Summary

**Part A, tab history.** Each tab keeps the web engine's own back and forward state, the same approach as DuckDuckGo's Android browser (R1, R2).
- **Normal tabs**: the state is saved, bounded to 256 KiB, into a new Room table tied to the tab with a cascade.
- **Incognito tabs**: memory only.
- **Saving** happens after every finished load or history change, when the tab's WebView is released, and when the screen stops (R3).
- **Restoring** happens before the tab's WebView would load its address. A missing or unreadable state falls back to today's load.
- **Clearing history**, from Fire, Settings or History, removes every saved state and empties the live WebView's back and forward (R7).

**Part B, swipe.** A `WebView` subclass turns sideways overscroll the page did not use into back or forward, with an arrow drawn per frame in `graphicsLayer` (R5, R6). A Settings switch, on by default, turns it off (R9).

**Spike first.** One spike checks two assumptions on API 24, 29 and 36 before the dependent steps:
- the saved bundle holds only bytes (R2);
- WebView reports horizontal overscroll for unconsumed drags (R5).

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1, Gradle 9.5.0 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP), Room 2.8.4, DataStore 1.2.1, androidx.webkit 1.17.0 (`WebViewCompat.saveState` is present). No new dependency.
**Storage**: Room v5 → v6, adding `tab_sessions` with an additive migration and the schema JSON. DataStore gets one boolean key.
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric, in-memory Room, Compose UI, Konsist. Baseline on `main` `e89632d`: 943 unit and 189 instrumented tests.
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**:
- cold start within 5% of `main` (SC-006);
- a save costs under 5 ms on the main thread, measured in the spike;
- no recomposition while dragging.

**Constraints**:
- incognito writes nothing to disk;
- the state is ≤ 256 KiB per tab;
- the system Back gesture is unchanged;
- 8 locales.

**Scale/Scope**: at most the tab limit × 256 KiB on disk. Files touched:
- **browser**: WebView, content, route, page holders, a new holder;
- **tabs**: repository and DI;
- **core**: entity, DAO, migration, model, contract, use case, settings;
- **privacy, settings, history**: switch to the new clear use case;
- **settings screen**: one row.

## Constitution Check

*Gate against constitution **2.1.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | The contract, model and use case are in `core/domain`, because four features use them (browser, privacy, settings, history). The implementation is in `feature/tabs/data`, and no feature imports another |
| II One home | ✅ | Placement follows the rules (Konsist):<br>• `TabSessionEntity` and `TabSessionDao` in `core/data/datasource/local`<br>• `TabSessionRepositoryImpl` in `feature/tabs/data/repository`<br>• `TabSessionViewModel` and state in `feature/browser/presentation/browser` |
| III Unidirectional flow | ✅ | The new holder takes `onAction(TabSessionAction)` and emits `TabSessionEvent`. Repository work runs on the injected dispatcher. The route alone obtains the holder |
| IV Compose | ✅ | Swipe progress is read in `graphicsLayer {}`. No `ByteArray` crosses into composables: the holder exposes a lookup. The Compose report and Layout Inspector recomposition counts are attached (Quality Gate 6) |
| V No hardcoded values | ✅ | The 256 KiB bound, 1 s debounce, threshold dp and 24 dp fallback edge are named constants. New strings are in all 8 locales |
| VI Comments | ✅ | Policy applies. The codec's format note is the only non-obvious comment expected |
| VII Build and data | ✅ | Additive `Migration5To6` with the exported `6.json`, and `AppDatabaseMigrationTest` extended. No new dependency |
| VIII Tests | ✅ | Tests for the codec, DAO (cascade), repository, use case, holder and settings mapper. An instrumented restore test, plus a swipe test with a fake overscroll |
| Data and privacy | ✅ | The database is already excluded from backup. Incognito is memory only. Clearing history covers saved and live state |
| Web content | ✅ with note | A restore does not go through `loadProtected`. The engine reloads its own entry with the client and protections already attached (R4). ADR 0028 records why the rule still holds |

**Post-design re-check**: no exemption and no allowlist entry. **Gate: PASS.**

## Project Structure

### Documentation (this feature)

```text
specs/028-tab-history-swipe-nav/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/tab-session-repository.md, contracts/swipe-navigation.md
├── checklists/requirements.md
└── tasks.md                                   # /speckit-tasks
```

### Source Code

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── data/datasource/local/
│   │   ├── entity/TabSessionEntity.kt                 new
│   │   ├── TabSessionDao.kt                           new
│   │   ├── migration/Migration5To6.kt                 new
│   │   ├── AppDatabase.kt                             + entity, SCHEMA_VERSION 6
│   │   └── datastore/SettingsDataStore.kt             + swipe key
│   ├── di/DatabaseModule.kt                           + migration, DAO
│   └── domain/
│       ├── model/TabSession.kt                        new
│       ├── model/UserSettings.kt                      + isSwipeNavigationEnabled
│       ├── repository/TabSessionRepository.kt         new
│       ├── repository/SettingsRepository.kt           + setSwipeNavigationEnabled
│       └── usecase/ClearBrowsingHistoryUseCase.kt     new
├── feature/tabs/
│   ├── data/repository/TabSessionRepositoryImpl.kt    new (Room + in-memory incognito)
│   └── di/                                            + binding
├── feature/browser/presentation/browser/
│   ├── TabSessionViewModel.kt, TabSessionAction.kt, TabSessionEvent.kt, TabSessionUiState.kt   new
│   ├── TabStateCodec.kt                               new (Bundle ⇄ bytes)
│   ├── BrowserPageView.kt                             new (WebView subclass: overscroll + touch)
│   ├── SwipeNavigationState.kt, SwipeNavigationIndicator.kt                                   new
│   ├── PageSettingsViewModel.kt, PageSettingsUiState.kt   (from PageTextSizeViewModel)
│   ├── BrowserWebView.kt                              restore before first load; save hooks
│   ├── BrowserContent.kt, BrowserRoute.kt, BrowserScreen*.kt   wiring
│   └── WebViewLifecycle.kt                            + ON_STOP save
├── feature/privacy/domain/usecase/RunFireUseCase.kt          uses ClearBrowsingHistoryUseCase
├── feature/settings/domain/usecase/ClearBrowsingDataUseCase.kt  uses ClearBrowsingHistoryUseCase
├── feature/settings/{data,presentation}/                     + swipe switch
└── feature/history/presentation/history/HistoryViewModel.kt  uses ClearBrowsingHistoryUseCase
app/schemas/.../6.json                                        new
docs/adr/0028-tab-sessions-kept-by-the-engine.md              new
```

**Structure Decision**: The existing feature-first layout. The shared pieces go in `core/` because four features need them.

## Step plan (one commit each, G1 green)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline G0 on `e89632d`: counts, APK, Compose report, cold start | — |
| 1 | **Spike** on 3 AVDs, in a throwaway debug screen or test, not committed:<br>• bundle value types<br>• `saveState` size and time<br>• overscroll on horizontal drags (plain page, carousel, map)<br>Record the results in research R2/R5 | notes |
| 2 | Data: `TabSession`, contract, entity, DAO, `Migration5To6`, `6.json`, impl with in-memory incognito, binding, tests (DAO cascade, migration, repository) | G1 |
| 3 | `TabStateCodec` with tests (same commit as step 2) | G1 |
| 4 | `TabSessionViewModel` (load for the active tab; the bytes stay in the holder behind the `savedState` lookup) with tests, and the route wiring | G1 |
| 5 | WebView integration: restore before first load, skip the next tab-switch load once, save on finish/history change (debounced, 1 s or 3 s per the spike), release and `ON_STOP`, renderer recovery restores. Instrumented restore and tab-switch tests | G1 + AVD |
| 6 | `ClearBrowsingHistoryUseCase`, used by Fire, Settings and History; live `clearHistory`; sweep; retention check; tests | G1 |
| 7 | `PageSettingsViewModel` rename, swipe setting (DataStore, model, repository, Settings row, 8 locales), tests | G1 |
| 8 | `BrowserPageView`, `SwipeNavigationState`, indicator, gesture wiring; RTL by `LocalLayoutDirection`; tests; Compose report and Layout Inspector counts | G1 + AVD |
| 9 | ADR 0028 | G1 |
| 10 | Device walk G3 on both AVDs, G4, PR | G3, G4 |

## Risks

| Risk | Mitigation |
|---|---|
| The saved bundle has non-byte values on some WebView version | The spike checks it. The codec then refuses, and the tab falls back to its URL (FR-005) |
| No overscroll report for horizontal drags | The R5 fallback (edge zone plus `canScrollHorizontally`), decided after the spike |
| Restore races the first load at cold start | The WebView is composed only after the session read for that tab id completes (R3) |
| A save on the main thread at `ON_STOP` is slow on huge histories | The bound caps the work. The spike measures it |

## Complexity Tracking

No constitution violation to justify.
