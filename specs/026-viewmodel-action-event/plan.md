# Implementation Plan: ViewModel Action/Event

**Branch**: `026-viewmodel-action-event` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/026-viewmodel-action-event/spec.md`

## Summary

Move all 16 screen state holders that take input to one `onAction(XAction)` entry point. Their one-off effects go to one `events: Flow<XEvent>`, collected at a new `XRoute` composable that hands `XScreen` only state and `onAction`. Remove the 33 use cases that only forward one call; the holders inject the `core/domain` contracts instead. Replace hand-written `setMain`/`resetMain` in 22 tests with one `MainDispatcherRule`. Delete the ktlint baseline (23 findings) and add a Konsist check, moving principle III from Review to Enforced at constitution 2.1.0. Nothing the person using the browser sees or keeps changes, except that a snackbar interrupted by rotation is no longer shown a second time (research R4, required by spec US1-2).

Key research results:
- All 33 use cases are pure forwards over pure-Kotlin contracts, so none is kept (R1).
- `PopupTabs`, `SitePermissionGate` and `LongPressActions` are WebView bridges with synchronous results and stay outside the action rule (R2).
- Values returned to the screen today become `BrowserEvent.LoadUrl` / `SetDesktopMode` or a route decision from state. Synchronous lookups become lookup properties (R3).
- One-off effects come in two kinds, Drop (7 SharedFlows) and Keep (one Channel plus 7 `UiState` fields with `consume…`). Each keeps its kind (R4).

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1, Gradle 9.5.0 on the Android Studio JBR (JDK 21)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP 2.3.7), Lifecycle 2.10.0, Navigation Compose 2.9.8, hilt-navigation-compose 1.3.0. No dependency is added or changed. Checks: Konsist 0.18.1, compose-rules ktlint 0.6.8 (ktlint plugin 14.2.0)
**Storage**: Room `thirtysix_browser.db` v5 and DataStore `thirtysix_settings`, unchanged
**Testing**: JUnit 4.13.2, kotlinx-coroutines-test 1.10.2, Turbine 1.2.1, Robolectric 4.16.1, Compose UI test, Espresso-Web. 178 unit-test files and 66 instrumented-test files; counts at G0
**Target Platform**: Android 7.0 (API 24) to 16 (API 36), 16 KB page size
**Project Type**: single-module Android app (feature-first)
**Performance Goals**: no change. Release APK ±2 %, cold start ±5 % against `0372d0f`; Compose report 0 non-skippable
**Constraints**: no user-visible change beyond the one in research R4; each effect keeps its delivery kind; no new runtime dependency; every commit green
**Scale/Scope**: 18 holders (151 public functions), 33 use cases removed (82 → 49), 10 navigation screens plus 4 embedded hosts, 23 ktlint findings, 22 test files on `setMain`, about 15 instrumented test files that call screens

## Constitution Check

*Gate against constitution **2.0.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first Clean Architecture | ✅ | No file changes feature. Holders inject `core/domain` contracts, which presentation may see (`ArchitectureTest.layers depend only inward`) |
| II Every type has one home | ✅ | `XAction` and `XEvent` live in the screen folder; `MainDispatcherRule` in `T/core/testing`; `EventEffect` in `core/presentation/util` (`PlacementTest`) |
| III Unidirectional data flow | ✅ makes it true | This spec delivers the Review item "Action/Event shape (… Spec 026)" and the first bullet's direct-contract call; the check is added (R7) |
| IV Compose render discipline | ✅ | State is collected once at the route, and children get fields. Progress stays a separate flow in `drawBehind` (ADR 0015); `onAction` is a stable reference (R10) |
| V Comments | ✅ | New files follow the policy; a stale comment in `BookmarkFolder.kt` is fixed (R1) (`CommentPolicyTest`, detekt) |
| VI No hard-coding | ✅ | No new text or color (`NoHardcodeTest`) |
| VII Data and migrations | ✅ | No schema or key change |
| VIII Tests | ✅ | Holder tests drive `onAction` and use `MainDispatcherRule`; tests of removed use cases are handled per R1 (FR-006) |
| Product Constraints | ✅ | No permission, page-script, privacy or incognito behaviour change |

Every **Enforced by** check in 2.0.0 stays green on every commit (G1).

**Post-design re-check**: the design adds two Konsist checks and removes one allowlist (the ktlint baseline). It changes no principle except moving III's action/event bullet from Review to Enforced, which is a MINOR amendment (2.1.0) with an ADR and a history line in the same commit, as the constitution's governance section requires. Gate: **PASS**. Complexity Tracking lists the one exemption.

## Project Structure

### Documentation (this feature)

```text
specs/026-viewmodel-action-event/
├── spec.md
├── plan.md                      # this file
├── research.md                  # R1–R10
├── data-model.md                # shapes, holder inventory, effect kinds, use-case swaps
├── quickstart.md                # G0–G6
├── contracts/screen-contract.md # rules C1–C8 and their checks
├── checklists/requirements.md
└── tasks.md                     # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── MainActivity.kt                         injects SettingsRepository, DefaultBrowserInviteRepository
├── navigation/AppNavHost.kt                calls XRoute through feature graphs
├── core/
│   ├── domain/usecase/                     14 forwarding use cases deleted
│   └── presentation/util/EventEffect.kt    new
└── feature/<f>/
    ├── domain/usecase/                     19 forwarding use cases deleted
    └── presentation/
        ├── <screen>/XAction.kt, XEvent.kt  new per screen
        ├── <screen>/XScreen.kt             XRoute + XScreen
        ├── <screen>/XViewModel.kt          onAction, events
        └── navigation/<F>Navigation.kt     composable(ROUTE) { XRoute(…) }

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── architecture/ArchitectureTest.kt        +2 checks
├── core/testing/MainDispatcherRule.kt      new
└── feature/<f>/…                           holder tests drive onAction; removed use-case tests per R1

app/src/androidTest/kotlin/…/feature/<f>/   screen tests call XRoute(viewModel = …)
config/ktlint/baseline.xml                  deleted
.specify/memory/constitution.md             2.1.0
docs/adr/0026-screens-take-actions-and-emit-events.md, docs/constitution-history.md, AGENTS.md
```

**Structure Decision**: no folder moves. Every change is inside the existing feature-first layout from Spec 025.

## Step plan (one commit each, research R9)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline G0 on `0372d0f`, recorded in tasks.md | — |
| 1 | `MainDispatcherRule`, the 22 tests switched to it, Konsist `main dispatcher is set only by the rule` | G1 |
| 2 | `EventEffect` helper | G1 |
| 3 | sitepermission: ObserveSitePermissions and SetSitePermission removed; action; route | G1 + its instrumented test |
| 4 | privacy: protection exceptions, fireproof, fire (ObserveProtectionExceptions; ObserveFireproofSites stays until browser) | G1 + instrumented |
| 5 | downloads: ObserveDownloads callers switched (incl. `ObserveHasActiveDownloadsUseCase`) | G1 + instrumented |
| 6 | history: three use cases; `HistoryEvent` merge of Keep and Drop | G1 + instrumented |
| 7 | onboarding: SetOnboardingCompleted; `Back` decided at the route | G1 |
| 8 | settings: incl. `QuickSettingsHost` / `SettingsDialogHost` | G1 + instrumented |
| 9 | tabs: five tab use cases; `images` lookup; `TabsEvent` merge | G1 |
| 10 | bookmarks: six use cases; `BookmarkFolder.kt` comment | G1 |
| 11 | entrypoint: `MainActivity`, `OpenIncomingRequestUseCase`, `EntryPointViewModel` constructor | G1 + instrumented (incoming link) |
| 12 | browser: home, invite, hub, suggestions, protection, page text size, browser (`BrowserAction` nested, `BrowserEvent`, `desktopMode` lookup); last core use cases deleted | G1 + instrumented |
| 13 | Konsist `view models take input only through onAction`; ktlint baseline deleted; constitution 2.1.0, history, ADR 0026, AGENTS.md | G1, G2, G3 |
| 14 | Device and size gates; PR | G4, G5, G6 |

Each feature step removes its lines from `config/ktlint/baseline.xml`, so the baseline is empty before step 13 deletes it.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Three `@HiltViewModel` WebView bridges (`PopupTabs`, `SitePermissionGate`, `LongPressActions`) keep public functions instead of `onAction` | Their callers are WebView client callbacks that need a synchronous answer (`onCreateWindow` → `PopupDecision`, pop-up `WebView` hand-over, permission outcome) (ADR 0013) | `onAction` returns nothing, so the answer would arrive after the WebView call returned. Moving them out of the ViewModel scope changes WebView lifecycle, which this spec does not touch |
