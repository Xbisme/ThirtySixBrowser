# Implementation Plan: Default Browser & Entry Points

**Branch**: `020-default-browser-entry-points` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/020-default-browser-entry-points/spec.md`

## Summary

Make ThirtySix reachable as a browser from the rest of the phone. `MainActivity` declares
http/https `VIEW`, `SEND`, `PROCESS_TEXT`, `WEB_SEARCH` and `APP_BROWSER` filters and becomes
`singleTask`; incoming intents are read once into a pure-Kotlin `IncomingRequest`, held in a
process-scoped relay until onboarding is done, resolved by a domain use case (validate,
extract, search) and opened in a new normal tab — or, at the 50-tab cap, in the most recently
used normal tab. Back on an untouched externally opened tab closes it and returns to the
sending app. A Settings row and a Home invitation (from the 3rd launch, dismissible forever)
let the user make ThirtySix the default browser through the system role request (API 29+) or
the default-apps screen (API 24–28). No new dependency, no permission, no Room change.

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1, Gradle 9.5.0
**Primary Dependencies**: existing only — Compose BOM 2026.04.01 / Material3, Hilt 2.59.2,
Navigation Compose, DataStore Preferences 1.2.1, `activity-compose` (result launcher),
`lifecycle-runtime-compose` 2.10.0 (already on the classpath transitively; declared
explicitly on the existing `lifecycle` key — research R12). Platform: `RoleManager` (API 29+),
`Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`, `Intent` actions.
**Storage**: DataStore `thirtysix_settings` + 2 keys (`default_browser_invite_dismissed`,
`app_launch_count`). Room unchanged at v2.
**Testing**: JUnit + Turbine + coroutines-test (unit); Robolectric for the `Intent` reader;
Compose UI tests + Hilt instrumented tests (`ActivityScenario` with incoming intents);
device gates G1–G16 on the Galaxy A55 and `TA016_API24`.
**Target Platform**: Android minSdk 24 → targetSdk 36
**Project Type**: mobile app (single module `:app`)
**Performance Goals**: incoming link → page visible ≤ 1 s warm / ≤ 2 s cold (SC-001); the
Home invitation adds one static `LazyColumn` item (no frame-rate state)
**Constraints**: no permission (FR-028); nothing logged (FR-016); 16 KB gate green; APK
≤ +60 KB (SC-010); Constitution v1.5.0
**Scale/Scope**: 4 user stories, 30 FRs, ~12 new strings × 8 locales, ~14 new/changed files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design (below).*

| § | Principle | Status | How |
|---|---|---|---|
| I | Privacy & Security First | ✅ | Incoming content never logged or stored (FR-016, INV-9); only the page or the chosen search engine receives it; invitation state is on-device only |
| II | Google Play Compliance | ✅ | No permission; browser role is a standard platform role; no JS bridge or injection |
| III | Code Quality & Safety | ✅ | New limits/keys/tags in `core/constants`, strings in resources, design-system tokens; no magic numbers (2,048, 2,000 ms, 3 launches become constants) |
| IV | Clean Architecture | ✅ | `Intent` read only in presentation; rules in domain use cases; platform role behind `DefaultBrowserController`; invitation storage behind its own repository; moving `classifyAddressBarInput` to domain removes a would-be presentation import (research R4) |
| V | Performance & Compose Phase Discipline | ✅ | New UI is static (row, card); keyed `LazyColumn` items with content types; no frame-rate state; recomposition count for Home recorded (Testing Gate 10) |
| VI | Testing Discipline | ✅ | §VI now names "incoming-link and intent handling (Spec 020)": every C2 table row is a unit test; manifest scheme test; instrumented intent delivery |
| VII | Offline-First | ✅ | Everything except loading the page works offline; invitation state persisted via DataStore suspend API |
| VIII | Localization & Accessibility | ✅ | 12 keys × 8 locales, lint parity at error; every new control labelled; TalkBack pass DEFERRED to a person (as 016–019) |
| IX | Dependency Currency & 16 KB | ✅ | No new artifact; the explicit `lifecycle-runtime-compose` declaration reuses version 2.10.0 already in the graph |
| X | Simplicity & Build Order | ⚠️ justified | Phase 6 order respected (020 first). **"Design before spec" waived by the owner** (Claude Design unavailable) — see Complexity Tracking |
| XI | Build Configuration | ✅ | No build-type or flavour change |

**Post-design re-check (after Phase 1)**: unchanged — 10 ✅, 1 ⚠️ justified (§X waiver). The
design added no permission, dependency, persistence beyond two DataStore keys, or JS bridge.

## Project Structure

### Documentation (this feature)

```text
specs/020-default-browser-entry-points/
├── plan.md              # This file
├── research.md          # R1–R12
├── data-model.md        # entities, invariants INV-1..9
├── quickstart.md        # automated + device gates G1–G16
├── contracts/
│   ├── manifest-entry-points.md   # C1
│   ├── incoming-requests.md       # C2
│   └── default-browser-and-ui.md  # C3
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                         # C1: filters + singleTask
├── res/values*/strings.xml                     # 12 keys × 8 locales
└── kotlin/com/raumanian/thirtysix/browser/
    ├── MainActivity.kt                          # read intents once (R3), count launches (R9)
    ├── core/constants/
    │   ├── BrowserLimits.kt                     # + MAX_INCOMING_TEXT_LENGTH, INCOMING_DUPLICATE_WINDOW_MS, DEFAULT_BROWSER_INVITE_MIN_LAUNCHES
    │   ├── StorageKeys.kt                       # + 2 keys
    │   └── UrlPatterns.kt                       # + EMBEDDED_WEB_URL_REGEX
    ├── domain/
    │   ├── model/AddressInput.kt                # MOVED from presentation/browser (unchanged)
    │   ├── model/IncomingRequest.kt             # + IncomingTarget, OpenOutcome
    │   ├── repository/DefaultBrowserController.kt
    │   ├── repository/DefaultBrowserInviteRepository.kt
    │   └── usecase/
    │       ├── ResolveIncomingRequestUseCase.kt
    │       ├── OpenIncomingRequestUseCase.kt
    │       ├── ObserveDefaultBrowserInviteUseCase.kt
    │       ├── DismissDefaultBrowserInviteUseCase.kt
    │       └── RecordAppLaunchUseCase.kt
    ├── data/
    │   ├── local/datastore/SettingsDataStore.kt # + 2 keys' read/write
    │   ├── repository/DefaultBrowserInviteRepositoryImpl.kt
    │   └── platform/AndroidDefaultBrowserController.kt
    ├── di/DefaultBrowserModule.kt               # binds controller, repository, relays, clock
    └── presentation/
        ├── entry/
        │   ├── IncomingIntentReader.kt          # Intent → IncomingRequest
        │   ├── IncomingRequestRelay.kt          # + ActiveTabNavigationRelay, ExternalTabRegistry
        │   ├── EntryPointViewModel.kt
        │   └── DefaultBrowserRequest.kt         # rememberDefaultBrowserRequest
        ├── navigation/AppNavGraph.kt            # collect ShowBrowser → pop to Browser
        ├── browser/BrowserScreen.kt             # external-tab Back handler; collect navigation relay
        ├── browser/BrowserViewModel.kt          # onBackLeavingExternalTab()
        ├── browser/AddressBarInputClassifier.kt # REMOVED (moved to domain)
        ├── home/HomePage.kt, HomeViewModel.kt, HomeUiState.kt, DefaultBrowserInviteCard.kt
        └── settings/SettingsScreen.kt, SettingsViewModel.kt   # default-browser row

app/src/test/…      # Resolve/Open use cases (C2 tables), EntryPointViewModel, invite rules,
                    #   launch counter, IncomingIntentReader (Robolectric), manifest scheme test,
                    #   moved classifier tests
app/src/androidTest/… # incoming VIEW/SEND delivered to MainActivity, Settings row, Home card
```

**Structure Decision**: single `:app` module, Clean Architecture packages as in Specs
002–019. The new `presentation/entry/` package groups the activity-to-UI hand-off; platform
role access sits in `data/platform/` beside Spec 016's controllers.

## Complexity Tracking

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| §X "Design before spec" not followed for the Settings row and Home invitation | Owner decision 2026-10-11: Claude Design is unavailable on the owner's current account. UI is assembled from existing Spec 019 components and tokens; the owner supplies a design later and only visuals may change | Blocking Phase 6 until a design tool is available would stall the roadmap for two small, conventional UI pieces |
| FR-018's "page it replaced stays one Back away" holds only when the reused tab was already active | The app keeps one WebView rebuilt on every tab switch (Spec 011), so a switched-to tab has no back history | Keeping a WebView (or saved history) per tab is a Spec 011-scale architecture change with large memory cost; out of scope. Spec text amended to say so (research R6) |
| `classifyAddressBarInput` moves from `presentation/browser/` to `domain/model/` | The new domain use case must apply exactly the address-bar rule; domain may not import presentation (§IV) | Duplicating the rule in domain would let typing and sharing an address diverge |
