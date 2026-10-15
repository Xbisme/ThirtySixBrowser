# Implementation Plan: Browser View Model Split

**Branch**: `027-browser-viewmodel-split` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/027-browser-viewmodel-split/spec.md`

## Summary

Split the 514-line `BrowserViewModel` into five holders by concern: page, address bar and Home, find in page, download requests, and page actions. The state they share (current URL, page title, active tab, incognito, page generation) moves into one in-memory `CurrentPageRepository` behind a domain contract. It is written synchronously, so every holder reads what today's single holder reads, at the same moment.

`BrowserRoute` obtains the holders and assembles today's `BrowserUiState` for the screen. `BrowserScreen.kt` is divided into files by part. `BaseViewModel` is deleted, and the size suppressions on these files go.

Research results:
- Only four values cross concerns (R1).
- The store is `ActivityRetained`-scoped, so it lives as long as the holders' state (R2).
- `FindResult` moves to the find holder (R3).
- `BrowserUiState` stays as a screen-level value, so the composables' signatures do not change (R4).

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1, Gradle 9.5.0 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP), Lifecycle 2.10.0; no dependency added
**Storage**: unchanged (Room v5, DataStore)
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric, Compose UI, Konsist; 940 unit, 189 instrumented at `90cd024`
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**: no change; Compose 0 non-skippable; APK ±2 %
**Constraints**: no user-visible change; per-concern event kinds kept; every commit green
**Scale/Scope**: `BrowserViewModel` 514 lines, 29 actions, 14 deps; `BrowserScreen.kt` 615 lines; 7 browser VM test files (1,630 lines); 3 instrumented browser screen tests

## Constitution Check

*Gate against constitution **2.1.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | The store is a browser-only contract (`feature/browser/domain/repository`) with a data implementation (`feature/browser/data/repository`); nothing crosses features |
| II One home | ✅ | `CurrentPage` in `domain/model`, `*RepositoryImpl` in `data/repository`, binding in `di/`, holders and states in `presentation/browser` |
| III Unidirectional flow | ✅ | Each holder takes `onAction`, emits `XEvent`, reads the contract; the route alone obtains holders (Konsist + compose-rules) |
| IV Compose | ✅ | Progress stays a separate flow read in `drawBehind`; state collected once at the route |
| V Comments | ✅ | Moved code keeps its comments; new files follow the policy |
| VI No hard-coding | ✅ | — |
| VII Data | ✅ | No schema or key change |
| VIII Tests | ✅ | Every holder and the store get tests; assertions kept |

**Post-design re-check**: no new exemption and no allowlist; ADR 0027 records the store decision. Gate: **PASS**.

## Project Structure

```text
specs/027-browser-viewmodel-split/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/page-store.md
├── checklists/requirements.md
└── tasks.md                       # /speckit-tasks

app/src/main/kotlin/com/raumanian/thirtysix/browser/feature/browser/
├── domain/model/CurrentPage.kt                         new
├── domain/repository/CurrentPageRepository.kt          new
├── data/repository/CurrentPageRepositoryImpl.kt        new
├── di/                                                 + @ActivityRetainedScoped binding
└── presentation/browser/
    ├── PageViewModel.kt, PageAction.kt, PageUiState.kt          (from BrowserViewModel)
    ├── AddressBarViewModel.kt, AddressBarAction.kt, AddressBarEvent.kt, AddressBarUiState.kt
    ├── FindInPageViewModel.kt, FindInPageAction.kt              (FindInPageState kept)
    ├── DownloadRequestViewModel.kt, DownloadRequestAction.kt, DownloadRequestEvent.kt
    ├── PageActionsViewModel.kt, PageActionsAction.kt, PageActionsEvent.kt, PageActionsUiState.kt
    ├── BrowserRoute.kt, BrowserScreen.kt, BrowserContent.kt, BrowserHubHost.kt,
    │   BrowserBackHandlers.kt, BrowserCallbacks.kt, BrowserEffects.kt, BrowserScreenInputs.kt
    └── BrowserUiState.kt                                         (screen-level, assembled at the route)
core/presentation/util/BaseViewModel.kt                           deleted
```

`BrowserViewModel.kt`, `BrowserAction.kt` and `BrowserEvent.kt` are removed once their content has moved.

**Structure Decision**: same feature-first layout; new types in the browser feature's existing folders.

## Step plan (one commit each)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline G0 on `90cd024` | — |
| 1 | Store: contract, model, impl, binding, test; `BrowserViewModel` reads/writes the four values and `generation` through it | G1 |
| 2 | `FindInPageViewModel` (closes on `generation`) + tests | G1 |
| 3 | `DownloadRequestViewModel` + tests | G1 |
| 4 | `PageActionsViewModel` (star, desktop mode, long-press new tab) + tests | G1 |
| 5 | `AddressBarViewModel` (address bar, Home, Back-without-history) + tests | G1 |
| 6 | Remainder renamed `PageViewModel`; `BrowserAction`/`BrowserEvent` removed | G1 |
| 7 | `BrowserScreen.kt` split by part; suppressions removed | G1, G2 |
| 8 | `BaseViewModel` deleted | G1 |
| 9 | ADR 0027 | G1 |
| 10 | Device gates, PR | G3, G4 |

## Complexity Tracking

No constitution violation to justify.
