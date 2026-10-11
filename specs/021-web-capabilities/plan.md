# Implementation Plan: Web Capabilities

**Branch**: `021-web-capabilities` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/021-web-capabilities/spec.md`

## Summary

Answer what pages ask of the browser. All page requests (permission, file chooser, JS dialog,
leave-page, HTTP auth, pop-up, long-press menu) flow from the WebView clients into one
`PageRequestQueue` rendered by one host, tied to tab + page generation so stale requests are
refused (research R1). Site permissions use a pure decision rule over remembered decisions (Room
**v3**, `site_permissions`) and in-memory one-time grants, then request the Android permission
only after the person allows (R2). File upload uses the system picker plus camera capture via a
`FileProvider` (R3). Fullscreen hosts the custom view over everything, hides bars and follows the
physical orientation; rotation stops recreating the activity (R4). User-gesture windows adopt the
page-created WebView as a new tab; others are blocked with a one-shot Open (R5). Styled JS/HTTP-
auth dialogs never store credentials (R6). A pure builder defines the long-press menu (R7).
Settings gains Site permissions and Clear browsing data gains an opt-in category. Four dangerous
permissions are added with `required="false"` features. No new dependency.

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1, Gradle 9.5.0
**Primary Dependencies**: existing only — Compose BOM 2026.04.01 / Material3, Hilt 2.59.2, Room
2.8.4, DataStore 1.2.1, Navigation Compose, `activity-compose` (result contracts), `androidx.core`
(`FileProvider`, `WindowInsetsControllerCompat`), `lifecycle-runtime-compose` 2.10.0. Platform:
`WebChromeClient`, `WebViewClient`, `PermissionRequest`, `HttpAuthHandler`, `WebView.HitTestResult`.
**Storage**: Room **v2 → v3** (`site_permissions`, explicit additive migration); in-memory
one-time grants, request queue, dialog suppression, pop-up registry.
**Testing**: JUnit + Turbine (C1 tables, use cases, repository, migration on the JVM); Robolectric
(manifest, intent builders); Compose UI tests (dialogs, sheet, Site permissions screen);
instrumented WebView tests with local fixtures; device gates G1–G20.
**Target Platform**: Android minSdk 24 → targetSdk 36
**Project Type**: mobile app (single `:app` module)
**Performance Goals**: dialogs and sheets appear within one frame of the request; fullscreen
enter/exit without reload (SC-004)
**Constraints**: four new dangerous permissions only at point of use (FR-002, SC-003); no JS→native
bridge, no script injection (§II); credentials never stored (FR-032); APK ≤ +150 KB (SC-009);
16 KB gate; Constitution v1.5.0
**Scale/Scope**: 6 user stories, 40 FRs, ~45 strings × 8 locales, ~35 new/changed files

## Constitution Check

*GATE: before Phase 0; re-checked after Phase 1 (below).*

| § | Principle | Status | How |
|---|---|---|---|
| I | Privacy & Security First | ✅ | Decisions on device only; no logs of origins/URLs/files/credentials (FR-036); HTTP auth never stored (INV-5); incognito never remembers (INV-1); geolocation `retain=false` (INV-6) |
| II | Google Play Compliance | ✅ (with table update) | Four dangerous permissions requested only at point of use after an in-app choice (FR-002); `uses-feature required=false` keeps the app installable without camera/GPS; Constitution permission table updated by this spec (FR-037, §II minimal-permission rule satisfied by purpose rows); no JS bridge, no injection |
| III | Code Quality & Safety | ✅ | Limits/keys/tags as constants; strings in resources; design tokens |
| IV | Clean Architecture | ✅ | Decision rules pure domain; Room behind `SitePermissionRepository`; platform callbacks only in presentation request objects, never in ViewModels |
| V | Performance & Compose Phase Discipline | ✅ | Static dialogs/sheets; keyed lazy lists in Site permissions; fullscreen overlay is a plain `AndroidView`; compiler report + recomposition record (Gates 9–10) |
| VI | Testing Discipline | ✅ | §VI now names these rules: C1 tables + migration + repository as JVM tests; UI tests for dialogs/screen |
| VII | Offline-First | ✅ | Decisions persisted atomically in Room (WAL); additive migration with rollback semantics |
| VIII | Localization & Accessibility | ✅ | ~45 keys × 8; every control labelled; TalkBack DEFERRED to a person |
| IX | Dependency Currency & 16 KB | ✅ | No new artifact; no `.so` |
| X | Simplicity & Build Order | ⚠️ justified | Phase 6 order respected (021 after 020). §X design-before-spec **waived by the owner** (Complexity Tracking) |
| XI | Build Configuration | ✅ | No build-type change |

**Post-design re-check**: unchanged — 10 ✅, 1 ⚠️ justified. The design added the planned permissions, one Room table and a `FileProvider`; no dependency, no injection, no bridge.

## Project Structure

### Documentation (this feature)

```text
specs/021-web-capabilities/
├── plan.md · research.md (R1–R12) · data-model.md (INV-1..8) · quickstart.md (G1–G20)
├── contracts/decision-tables.md (C1) · ui-and-strings.md (C2) · platform-and-manifest.md (C3)
├── checklists/requirements.md
└── tasks.md   (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                     # 4 permissions, 3 features, FileProvider, configChanges
├── res/xml/file_paths.xml                  # cache/capture only
├── res/values*/strings.xml                 # ~45 keys × 8
└── kotlin/com/raumanian/thirtysix/browser/
    ├── core/constants/                     # StorageKeys n/a; BrowserLimits/AppConstants additions (authority suffix, capture dir)
    ├── domain/
    │   ├── model/SitePermission.kt         # SiteCapability, SiteDecision, SitePermissionDecision
    │   ├── model/LongPressMenu.kt          # menu + actions + hit type
    │   ├── model/ClearBrowsingDataCategory.kt   # + SitePermissions
    │   ├── repository/SitePermissionRepository.kt
    │   ├── repository/TabRepository.kt / IncognitoTabRepository.kt   # + createBackgroundTab (default body)
    │   └── usecase/                        # ResolveSitePermission, ApplySitePermissionChoice,
    │                                       # ObserveSitePermissions, SetSitePermission, RemoveSitePermissions,
    │                                       # DecidePopup, BuildLongPressMenu, OpenInBackgroundTab;
    │                                       # ClearBrowsingDataUseCase (+ category)
    ├── data/
    │   ├── local/entity/SitePermissionEntity.kt · dao/SitePermissionDao.kt
    │   ├── local/database/AppDatabase.kt   # v3 + MIGRATION_2_3; schemas/…/3.json
    │   ├── mapper/SitePermissionMapper.kt
    │   └── repository/SitePermissionRepositoryImpl.kt · TabRepositoryImpl / IncognitoTabRepositoryImpl (+ background)
    ├── di/SitePermissionModule.kt          # repository binding; DatabaseModule adds the DAO + migration
    └── presentation/
        ├── browser/BrowserWebView.kt       # chrome/client hooks → PageRequestQueue; adopt pop-up WebViews; long-press
        ├── browser/page/                   # PageRequest, PageRequestQueue, PageRequestHost, OneTimeGrantStore,
        │                                   # DialogSuppression, PopupWebViewRegistry, FullscreenController,
        │                                   # FileChooserLauncher, SitePermissionDialog, SiteJsDialog,
        │                                   # HttpAuthDialog, LongPressSheet
        ├── browser/BrowserScreen.kt        # hosts PageRequestHost + fullscreen overlay
        ├── settings/sitepermissions/       # SitePermissionsScreen + ViewModel
        ├── settings/SettingsScreen.kt      # Privacy row → Site permissions; clear-data category
        └── navigation/AppDestination.kt / AppNavGraph.kt   # + SitePermissions
```

**Structure Decision**: single `:app` module, Clean Architecture packages. A new
`presentation/browser/page/` package groups everything that answers a page's requests, so
`BrowserWebView.kt` only translates platform callbacks into queue entries.

## Complexity Tracking

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| §X "Design before spec" waived for the five new UI pieces | Owner decision 2026-10-11 (Claude Design unavailable); built from Spec 019 components, visuals may change later | Blocking the roadmap for conventional dialogs/sheets/screens |
| FR-015 amended to "follow physical orientation" | Reading a video's aspect needs script in the page, forbidden by §II outside bundled privacy scripts | Forcing landscape for every fullscreen would show portrait videos sideways |
| `configChanges` widened to orientation/screenSize | Rotation during fullscreen would destroy the WebView and the video | Saving/restoring WebView state across recreation does not keep fullscreen or playback |
| Adopting page-created WebViews for pop-up tabs (one-off exception to "one WebView built per tab") | Keeps `window.opener` so sign-in pop-ups work (FR-025, FR-027) | Loading the pop-up's URL in an ordinary tab breaks those flows |
| `createBackgroundTab` default interface body | Keeps the six existing tab-repository test doubles compiling (Spec 018 lesson) | Changing `createTab`'s signature breaks all six |
