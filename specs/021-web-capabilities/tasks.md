---
description: "Task list for Spec 021 — Web Capabilities"
---

# Tasks: Web Capabilities

**Input**: Design documents from `specs/021-web-capabilities/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: REQUIRED — Constitution §VI (unit tests for business rules, Room integration tests). Every
row of contracts C1 is a test case; migration 2→3 has its own test.

**Organization**: by user story (US1–US3 P1, US4–US5 P2, US6 P3). US1 is the MVP.

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`

---

## Phase 1: Setup

- [X] T001 [P] Add the ~45 string keys of contracts C2.9 to `RES/values/strings.xml` and translate them in `RES/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` (site names stay as given; "ThirtySix" stays Latin)
- [X] T002 [P] Add constants to `MAIN/core/constants/AppConstants.kt` (`FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"`, `CAPTURE_CACHE_DIR = "capture"`) and to `MAIN/core/constants/BrowserLimits.kt` (`DIALOG_SUPPRESS_FROM_COUNT = 2` — "from a site's second dialog", FR-031)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the request queue and page-generation plumbing every story uses (research R1).

- [X] T003 Create `MAIN/presentation/browser/page/PageRequest.kt`: sealed `PageRequest` with common `tabId`, `pageGeneration`, `origin`, and the variants of data-model §3 (`Permission`, `FileChooser`, `JsDialog`, `LeavePage`, `HttpAuth`, `PopupBlocked`, `LongPressMenu`), each exposing `answerCancelled()` that settles its platform callback exactly once (INV-3)
- [X] T004 Create `MAIN/presentation/browser/page/PageRequestQueue.kt`: FIFO with `enqueue`, `head: StateFlow<PageRequest?>`, `complete(request)`, `dropStale(activeTabId, activeGeneration)` that answers stale requests cancelled (FR-034), merging rule for duplicate `Permission` requests (data-model §3), and `dispose()` that cancels everything
- [X] T005 [P] Write `TEST/presentation/browser/page/PageRequestQueueTest.kt`: FIFO order, one head at a time (FR-035), stale requests answered once and removed, duplicate permission merge, dispose cancels all
- [X] T006 Create `MAIN/presentation/browser/page/PageGenerations.kt` (per-tab counter advanced on new main-frame navigation and on tab switch; data-model §4) and wire it in `MAIN/presentation/browser/BrowserWebView.kt` (`onPageStarted` for a new URL) and `MAIN/presentation/browser/BrowserScreen.kt` (`activeTab` change → advance + `dropStale`)
- [X] T007 Create `MAIN/presentation/browser/page/PageRequestHost.kt`: composable that renders the queue head by variant (initially an empty `when` over the variants, which each story fills in) and is placed once in `MAIN/presentation/browser/BrowserScreen.kt`; on disposal calls `queue.dispose()`
- [X] T008 Extend `BrowserChromeClient` and `BrowserWebViewClient` in `MAIN/presentation/browser/BrowserWebView.kt` with a `PageRequestSink` parameter (enqueue + current tab id/generation/incognito) passed through `BrowserWebViewCallbacks`; no behaviour change yet (existing deny paths stay until US1)

**Checkpoint**: unit tests green; the app behaves exactly as before.

---

## Phase 3: User Story 1 — Site permissions (Priority: P1) 🎯 MVP

**Goal**: camera / microphone / location asked in-app with Always allow / Allow this time / Don't allow; remembered per site in normal tabs; Site permissions screen; clear-data category.

**Independent Test**: quickstart G1–G8.

### Tests for US1

- [X] T009 [P] [US1] Write `TEST/domain/usecase/ResolveSitePermissionUseCaseTest.kt` covering rows 1–10 of contracts C1.1
- [X] T010 [P] [US1] Write `TEST/domain/usecase/ApplySitePermissionChoiceUseCaseTest.kt` covering contracts C1.2 (normal/incognito × three choices × dismiss; incognito never writes — INV-1)
- [X] T011 [P] [US1] Write `TEST/data/local/database/AppDatabaseMigration2To3Test.kt` (Spec 015 style, real `Room.databaseBuilder` on the JVM): v2 data survives, `site_permissions` exists, no other table touched
- [X] T012 [P] [US1] Write `TEST/data/repository/SitePermissionRepositoryImplTest.kt`: save/replace per `(origin, capability)`, `decisionsFor`, `removeSite`, `clearAll`, http origins rejected (INV-2)

### Implementation for US1

- [X] T013 [P] [US1] Create `MAIN/domain/model/SitePermission.kt` (`SiteCapability { Camera, Microphone, Location }`, `SiteDecision { Allow, Deny }`, `SitePermissionDecision`) and an origin normaliser (`scheme://host[:port]`, lower-cased, default port omitted) in `MAIN/domain/model/SiteOrigin.kt`
- [X] T014 [US1] Create `MAIN/data/local/entity/SitePermissionEntity.kt` (table `site_permissions`, columns `origin` TEXT, `capability` TEXT ("camera"|"microphone"|"location"), `decision` TEXT ("allow"|"deny"), `decided_at` INTEGER, primary key `(origin, capability)`) and `MAIN/data/local/dao/SitePermissionDao.kt` (`upsert`, `observeAll`, `forOrigin`, `deleteOrigin`, `deleteAll`)
- [X] T015 [US1] Bump `MAIN/data/local/database/AppDatabase.kt` to `SCHEMA_VERSION = 3`, add the entity/DAO, create `MAIN/data/local/database/Migration2To3.kt` (`CREATE TABLE site_permissions …` only), register it in `MAIN/di/DatabaseModule.kt` beside `MIGRATION_1_2`, export `app/schemas/…/3.json`; `fallbackToDestructiveMigration` stays absent
- [X] T016 [US1] Create `MAIN/domain/repository/SitePermissionRepository.kt` (contracts C3.2), `MAIN/data/mapper/SitePermissionMapper.kt`, `MAIN/data/repository/SitePermissionRepositoryImpl.kt`, and bind it in new `MAIN/di/SitePermissionModule.kt`
- [X] T017 [US1] Create `MAIN/presentation/browser/page/OneTimeGrantStore.kt` (`@Singleton`; key `(tabId, pageGeneration, origin, capability)`; drop by tab/generation; `clear()`) — data-model §2
- [X] T018 [US1] Create use cases `MAIN/domain/usecase/ResolveSitePermissionUseCase.kt` and `ApplySitePermissionChoiceUseCase.kt` per contracts C1.1/C1.2 (one-time grants passed in as a lookup so the domain stays pure)
- [X] T019 [US1] Replace the deny-all hooks in `MAIN/presentation/browser/BrowserWebView.kt`: `onPermissionRequest` maps resources to capabilities (any other resource → `deny`, FR-008), resolves, then `grant`/`deny`/enqueue `PageRequest.Permission`; `onPermissionRequestCanceled` drops it; geolocation prompt the same with `callback.invoke(origin, allow, retain = false)` (INV-6); enable `setGeolocationEnabled(true)`
- [X] T020 [US1] Create `MAIN/presentation/browser/page/SitePermissionDialog.kt` (contracts C2.1; incognito → two buttons) and the permission flow in `PageRequestHost`: after an allow choice request missing Android permissions with `rememberLauncherForActivityResult(RequestMultiplePermissions())` (location = FINE + COARSE together, either grant counts), detect permanently denied via `shouldShowRequestPermissionRationale`, show the blocked explanation with Open settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) — FR-002, FR-006, INV-4
- [X] T021 [US1] Update `app/src/main/AndroidManifest.xml` per contracts C3.3: `CAMERA`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, and `uses-feature` camera / microphone / location `required="false"`, each with a comment citing Spec 021 FR-002/FR-037
- [X] T022 [P] [US1] Write `TEST/manifest/WebCapabilitiesManifestTest.kt` (Robolectric): the four permissions present, the three features not required, no other dangerous permission beyond Spec 015's capped storage one
- [X] T023 [US1] Add `AppDestination.SitePermissions` in `MAIN/presentation/navigation/AppDestination.kt` + route in `AppNavGraph.kt`; create `MAIN/presentation/settings/sitepermissions/SitePermissionsViewModel.kt` (observe, set, remove site, remove all) and `SitePermissionsScreen.kt` (contracts C2.7: keyed lazy list per origin, choice dialog Always allow / Don't allow, remove site, remove all with confirm, empty state)
- [X] T024 [US1] Add the Privacy row "Site permissions" in `MAIN/presentation/settings/SettingsScreen.kt` navigating to the new destination
- [X] T025 [US1] Add `SitePermissions` to `MAIN/domain/model/ClearBrowsingDataCategory.kt` (not in the default selection), handle it in `MAIN/domain/usecase/ClearBrowsingDataUseCase.kt` (`SitePermissionRepository.clearAll()` + `OneTimeGrantStore.clear()`), and show the unticked row in the clear-data dialog (FR-010a); update `TEST/domain/usecase/ClearBrowsingDataUseCaseTest.kt` and the settings test factories for the new dependency
- [X] T026 [P] [US1] Write `TEST/presentation/settings/sitepermissions/SitePermissionsViewModelTest.kt` and `ATEST/presentation/settings/SitePermissionsScreenTest.kt` + `ATEST/presentation/browser/page/SitePermissionDialogTest.kt` (three vs two buttons, labels)
- [X] T027 [US1] Device gates G1–G8 (quickstart) on the Galaxy A55 and the 16 KB AVD; if WebRTC audio fails without `MODIFY_AUDIO_SETTINGS`, add it with a Constitution table row (research R10); record results

**Checkpoint**: MVP — sites can use camera, microphone and location with the person's consent.

---

## Phase 4: User Story 2 — File upload (Priority: P1)

**Goal**: system picker with accept types and multiple selection; camera capture via the camera app.

**Independent Test**: quickstart G9.

- [X] T028 [P] [US2] Write `TEST/presentation/browser/page/FileChooserIntentsTest.kt` (Robolectric): picker intent honours accept types and multiple; capture intents added only when capture is requested/only media accepted; capture omitted when camera permission is not granted
- [X] T029 [US2] Add the `FileProvider` and `RES/xml/file_paths.xml` (`cache/capture/` only) per contracts C3.3
- [X] T030 [US2] Create `MAIN/presentation/browser/page/FileChooserLauncher.kt`: builds the picker/chooser (research R3), launches with `StartActivityForResult`, answers the callback with `FileChooserParams.parseResult` / the capture URI / `null`, always answering even on `ActivityNotFoundException`; requests `CAMERA` at that moment when capture is wanted and falls back to the picker on refusal (FR-013); deletes `cacheDir/capture/` files when the page generation changes
- [X] T031 [US2] Implement `onShowFileChooser` in `MAIN/presentation/browser/BrowserWebView.kt` → `PageRequest.FileChooser` (returns `true`) and render it in `PageRequestHost` via `FileChooserLauncher`
- [X] T032 [US2] Instrumented test `ATEST/presentation/browser/page/FileChooserInstrumentedTest.kt` with a local fixture page and `Intents` stubbing a picker result (one file, several files, cancel → page receives none)
- [X] T033 [US2] Device gate G9; record

---

## Phase 5: User Story 3 — Fullscreen (Priority: P1)

**Goal**: page fullscreen over everything, bars hidden, physical orientation, Back exits, no reload.

**Independent Test**: quickstart G10–G11.

- [X] T034 [US3] Widen `MainActivity` `android:configChanges` in `app/src/main/AndroidManifest.xml` to `locale|layoutDirection|screenLayout|orientation|screenSize|smallestScreenSize|keyboardHidden` with a comment citing research R4; extend the manifest test (T022) to assert it
- [X] T035 [US3] Create `MAIN/presentation/browser/page/FullscreenController.kt` (holds the custom view + callback; `enter`, `exit`) and render it in `MAIN/presentation/browser/BrowserScreen.kt` as a full-window `AndroidView` above all content, hiding system bars with `WindowInsetsControllerCompat` (transient swipe behaviour), setting `requestedOrientation = SCREEN_ORIENTATION_FULL_SENSOR`, and a `BackHandler` that exits first (FR-014–FR-016); restoring bars and `SCREEN_ORIENTATION_UNSPECIFIED` on exit
- [X] T036 [US3] Implement `onShowCustomView` / `onHideCustomView` in `MAIN/presentation/browser/BrowserWebView.kt` → `FullscreenController`
- [X] T037 [P] [US3] Write `TEST/presentation/browser/page/FullscreenControllerTest.kt` (enter/exit state, callback answered once, exit on tab change)
- [X] T038 [US3] Re-run the full instrumented suite to confirm the `configChanges` change breaks no rotation/recreation test (Spec 018 onboarding, Spec 020 saved request); device gates G10–G11; record

---

## Phase 6: User Story 4 — Long-press menu (Priority: P2)

**Goal**: link/image menus with background tab, incognito tab, copy, share, download.

**Independent Test**: quickstart G12.

- [X] T039 [P] [US4] Write `TEST/domain/usecase/BuildLongPressMenuUseCaseTest.kt` covering every row of contracts C1.4
- [X] T040 [P] [US4] Create `MAIN/domain/model/LongPressMenu.kt` (`LongPressHit { Link, Image, ImageLink }`, `LongPressAction`, `LongPressMenu`) and `MAIN/domain/usecase/BuildLongPressMenuUseCase.kt` (data-model §7)
- [X] T041 [US4] Add `createBackgroundTab(url)` with a default body delegating to `createTab(url)` to `MAIN/domain/repository/TabRepository.kt` and `IncognitoTabRepository.kt`; override it in `MAIN/data/repository/TabRepositoryImpl.kt` (insert with `lastActiveAt = currentActive.lastActiveAt - 1`, same cap) and the incognito implementation (research R9); add `TEST/data/repository/TabRepositoryBackgroundTabTest.kt` (active tab unchanged, recency order, cap)
- [X] T042 [US4] Create `MAIN/domain/usecase/OpenInBackgroundTabUseCase.kt` (normal or incognito; returns the new tab id or `TabLimit`)
- [X] T043 [US4] Wire `WebView.setOnLongClickListener` in `MAIN/presentation/browser/BrowserWebView.kt` (hit test types, `requestFocusNodeHref` for image links via a main-thread `Handler`, return `false` when no menu) → `PageRequest.LongPressMenu`
- [X] T044 [US4] Create `MAIN/presentation/browser/page/LongPressSheet.kt` (contracts C2.4) and its actions in `PageRequestHost`: background tab + "Switch" snackbar, incognito tab, `ClipboardWriter` copy + confirmation, `ACTION_SEND` chooser, `StartDownloadUseCase` for downloads (Spec 015), tab-limit message (FR-018–FR-024)
- [X] T045 [P] [US4] Write `ATEST/presentation/browser/page/LongPressSheetTest.kt` (rows, header, callbacks, Back closes)
- [X] T046 [US4] Device gate G12; record

---

## Phase 7: User Story 5 — Pop-ups and new windows (Priority: P2)

**Goal**: user-gesture windows become adopted tabs; others blocked with a one-shot Open; page can close its own pop-up tab.

**Independent Test**: quickstart G13.

- [X] T047 [P] [US5] Write `TEST/domain/usecase/DecidePopupUseCaseTest.kt` covering contracts C1.3
- [X] T048 [P] [US5] Create `MAIN/domain/usecase/DecidePopupUseCase.kt`
- [X] T049 [US5] Create `MAIN/presentation/browser/page/PopupWebViewRegistry.kt` (`@Singleton`, data-model §6) and make `MAIN/presentation/browser/BrowserWebView.kt` adopt a registered WebView for the composed tab instead of building one (configuring it with the same clients and settings; released like any WebView)
- [X] T050 [US5] Implement `onCreateWindow` / `onCloseWindow` and enable `setSupportMultipleWindows(true)` + `javaScriptCanOpenWindowsAutomatically = true` in `MAIN/presentation/browser/BrowserWebView.kt`: gesture → new tab (normal or incognito per opener) + transport WebView + registry; no gesture → throw-away WebView captures the address, then `PageRequest.PopupBlocked(url?)`; at the cap → existing tab-limit message (research R5, FR-025–FR-028)
- [X] T051 [US5] Render `PopupBlocked` in `PageRequestHost` as a snackbar with Open (one-shot new active tab, FR-026a)
- [X] T052 [US5] Instrumented test `ATEST/presentation/browser/page/PopupInstrumentedTest.kt` with a local fixture: `target=_blank` click → one new tab; `window.open` on load → no tab + notice; `window.close()` in an adopted pop-up → tab closed, opener active
- [X] T053 [US5] Device gate G13 (including a real sign-in pop-up demo); record

---

## Phase 8: User Story 6 — Site dialogs and HTTP auth (Priority: P3)

**Goal**: styled alert/confirm/prompt/leave dialogs naming the site, flood control; HTTP auth never stored.

**Independent Test**: quickstart G14–G15.

- [X] T054 [P] [US6] Write `TEST/presentation/browser/page/DialogSuppressionTest.kt` covering contracts C1.5
- [X] T055 [US6] Create `MAIN/presentation/browser/page/DialogSuppression.kt` (per tab, reset on generation change) and implement `onJsAlert` / `onJsConfirm` / `onJsPrompt` / `onJsBeforeUnload` in `MAIN/presentation/browser/BrowserWebView.kt` → `PageRequest.JsDialog` / `LeavePage` (return `true`; suppressed → `cancel()` silently)
- [X] T056 [US6] Create `MAIN/presentation/browser/page/SiteJsDialog.kt` (contracts C2.2) and render alert/confirm/prompt/leave in `PageRequestHost`
- [X] T057 [US6] Implement `onReceivedHttpAuthRequest` in `BrowserWebViewClient` → `PageRequest.HttpAuth`; create `MAIN/presentation/browser/page/HttpAuthDialog.kt` (contracts C2.3; masked password, autofill disabled, fields cleared on dismiss); `proceed` / `cancel` only — never `setHttpAuthUsernamePassword` (FR-032, INV-5)
- [X] T058 [P] [US6] Write `ATEST/presentation/browser/page/SiteDialogsTest.kt` (alert/confirm/prompt answers, suppress checkbox from the 2nd dialog, HTTP auth sign in/cancel, Back = cancel)
- [X] T059 [US6] Device gates G14–G15 (credential search: `run-as` files, DataStore, Room, logcat); record

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T060 [P] Stale-request and one-at-a-time device gate G16; record
- [X] T061 [P] Privacy review (FR-036): grep new/changed files for logs carrying origins/URLs/files/credentials; device gate G20; record
- [X] T062 [P] Locales + accessibility gate G17 (DE/RU at 360dp for all new UI; TalkBack DEFERRED to a person)
- [X] T063 Update the Constitution permission table (`.specify/memory/constitution.md` Technical Standards → Android Permissions) with the rows of contracts C3.4 (PATCH-level wording change, Sync Impact note), replacing the "Pending, Spec 021" note
- [X] T064 Compose compiler report (Testing Gate 9) + recomposition note for the fullscreen overlay and Site permissions list (Gate 10, "needs a person" if Layout Inspector is unavailable)
- [X] T065 Full automated gates: `testDebugUnitTest lintDebug detekt ktlintCheck`, then `assembleRelease` separately, 16 KB script, `connectedDebugAndroidTest` on the 16 KB AVD (force-stop first)
- [X] T066 Gate G18 (release on `TA016_API36_16K`, zero FATAL; APK ≤ +150 KB) and G19 (install over `main`'s build: data intact, migration 2→3 applied); record
- [X] T067 [P] Play Data safety check (Constitution §II): verify against Play's definitions that camera/microphone/location reaching a website at the person's direction is not "collected" or "shared" by the app; record the conclusion and the declaration to make in `specs/021-web-capabilities/tasks.md` › Implementation Notes (if it is, propose the declaration change to the owner before merging)
- [X] T068 SC-001 real-site run on the Galaxy A55 (release): a WebRTC call demo, a maps site (location), a form with file upload, YouTube fullscreen, a Google sign-in pop-up, `httpbin.org/basic-auth`, a long-press on a search result, a JS-dialog test page, a site using `target=_blank`, and a camera-capture upload; record n/10 in "Device Gate Results"
- [X] T069 Update docs: `CLAUDE.md` (roadmap row, Recent Changes, Active Spec, facts to keep), `.claude/claude-app/sdd-roadmap.md`, `dev-workflow.md`, `project-context.md`
- [X] T070 Open the PR `021-web-capabilities` → `main` with the gate table, Constitution line, permission additions, FR-015 amendment and APK delta

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none; T001–T002 parallel.
- **Foundational (Phase 2)**: T003 → T004 (T005 parallel after T004) → T006 → T007 → T008.
- **US1**: needs Phase 2. Tests T009–T012 first; T013 → T014 → T015 → T016; T017–T018 after T013; T019–T020 after T016–T018; T021–T022 any time in US1; T023–T026 after T016; T027 last.
- **US2**: needs Phase 2; independent of US1 except that capture shares the Android permission launcher pattern from T020 (reuse, not a hard dependency).
- **US3**: needs Phase 2; independent.
- **US4**: needs Phase 2; independent (T041 touches tab repositories only).
- **US5**: needs Phase 2; T049 changes `BrowserWebView` adoption — schedule after US3's T036 to avoid conflicts in the same file.
- **US6**: needs Phase 2; independent.
- **Polish**: after the stories in scope.

```text
Setup → Foundational ─┬─> US1 (P1, MVP)
                      ├─> US2 (P1)
                      ├─> US3 (P1) ──> US5 (P2)   (same file, sequential)
                      ├─> US4 (P2)
                      └─> US6 (P3)
                                   └──────> Polish
```

## Parallel Execution Examples

- **Phase 1**: T001, T002.
- **US1 tests**: T009, T010, T011, T012 together; T022 and T026 alongside implementation.
- **Across stories** (separate files): US2's T028–T030, US4's T039–T042, US6's T054 can proceed in parallel once Phase 2 is done.

## Implementation Strategy

1. Phases 1–2, then **US1** → G1–G8 on the A55 → stop and validate (MVP: sites get camera/mic/location with consent).
2. + US2 (upload) and US3 (fullscreen) — the other P1 breakages people notice first.
3. + US4 and US5.
4. + US6.
5. Polish → gates, Constitution table, docs, PR.

## Notes

- Device-gate tasks record their results in this file, "Device Gate Results" (no other file path).

- `[P]` = different files, no dependency on an incomplete task.
- Write each story's tests first and see them fail.
- `BrowserWebView.kt` is touched by most stories: keep each story's hook in its own private
  class/function and land stories sequentially there.
- On the A55: never change the default browser; reinstall the debug build after instrumented runs.

## Implementation Notes

Deviations from the task text, recorded as they happened:

- **Generations live in `page/PageSession.kt`** (not `PageGenerations.kt`): one class owns the
  tab/page generation, the queue's staleness and the permission entry point.
- **`SitePermissionGate` is a `@HiltViewModel`** wrapping the resolve/apply use cases, the
  one-time grants and the Android permission check, so platform callbacks never reach a
  ViewModel's state and the domain stays pure.
- **`OneTimeGrantStore` lives in `data/local/permission/`**, not `presentation/browser/page/`:
  `ClearBrowsingDataUseCase` (domain) clears it, and domain must not import presentation. Same
  standing as `FaviconCache`, which that use case already uses.
- **`MODIFY_AUDIO_SETTINGS` declared** (normal permission, no prompt): `PermissionRequest`
  documents it as required with `RECORD_AUDIO` for `RESOURCE_AUDIO_CAPTURE`. Needs its
  Constitution table row in T063.
- **Clear-data string keys** are `settings_clear_category_site_permissions(_description)`, matching
  the existing `settings_clear_category_*` family, not the contract's `clear_data_site_permissions`.
  `ClearBrowsingDataCategory.DEFAULT_SELECTION` now names the three ticked categories; the
  Spec 016 tests that assumed "every category is ticked" use it.
- **Migration test**: the v3 cases extend the existing `AppDatabaseMigrationTest` instead of a
  separate `AppDatabaseMigration2To3Test`.
- **Models created early**: `LongPressMenu` (US4) and `SitePermission` landed in Phase 2 with the
  request types that reference them.
- **Four icons added** (`ic_share`, `ic_photo_camera`, `ic_mic`, `ic_location_on`), fetched
  from google/material-design-icons @ 49d4db35 — the same source and style (Rounded, weight 300,
  grade −25) as Spec 019's set; used by the long-press sheet, the permission dialog glyph and the
  Site permissions rows.
- **Manifest test** checks an exact permission allow-list: Robolectric has no framework permission
  metadata, so "no other dangerous permission" cannot be read from protection levels.
- **Capture files are deleted when the next capture starts and when the browser screen goes
  away**, not on every page change (T030 said "when the page generation changes"): a form submit
  starts a navigation while the upload may still be reading the photo.
- **`espresso-intents` 3.7.0 added** (androidTest only, same Espresso train as core/web, no `.so`;
  3.7.0 confirmed on Google Maven 2026-10-11) to stub the picker in T032.
- **Spec 012's `IncognitoPermissionDenialInstrumentedTest` rewritten**: it mirrored the old
  deny-everything client in a copy. It now drives the real `BrowserChromeClient` and checks
  what still holds — no page → refused, incognito requests marked incognito, `retain = false`.
- **Fullscreen lives in `PageSession.fullscreen`** and reaches the chrome client through
  `PageRequestSink.onShowCustomView/onHideCustomView` (default bodies refuse), so
  `BrowserWebViewCallbacks` gains nothing. A tab switch ends fullscreen; a navigation in the same
  tab does not. Background is `colorScheme.scrim` (no inline colour, §III).
- **T038 suite part**: full `connectedDebugAndroidTest` 120/120 on `TA016_API36_16K` after the
  `configChanges` widening (2026-10-11). Device gates G10–G11 still to run.
- **`createBackgroundTab(url, activeAt)`** takes the active tab's `lastActiveAt` (contract C3.2
  had `url` only): the repository cannot see the other repository's tabs, and the active tab can
  be incognito while the new one is normal (or the reverse). `OpenInBackgroundTabUseCase` reads
  it from `ObserveActiveTabUseCase`.
- **`SwitchToTabUseCase` added** for the notice's Switch: the existing `SwitchActiveTabUseCase`
  only knows normal tabs (incognito switching in `TabsViewModel` is a documented no-op).
- **Long-press actions live in `LongPressActions` (`@HiltViewModel`)** plus `LongPressHooks`
  passed by `BrowserScreen`; downloads go through a second `rememberDownloadRequestHandler`, so
  the Spec 015 storage gate applies, with the WebView's default user agent.
- **Pop-ups (US5)**: `DecidePopupUseCase` decides gesture/cap/blocked; whether Open is offered is
  a separate `openUrlFor(target)` because a blocked window's address only arrives later (from a
  throw-away WebView, `BlockedPopupCatcher`). `PageRequest.PopupBlocked` was removed: the notice
  is a snackbar driven by `PopupTabs` events, so it never holds the dialog queue.
- **Pop-up WebViews are registered before their tab exists**, under a unique placeholder address
  (`about:blank#thirtysix-popup-N`) the tab is created with: the tab store can emit (and compose
  the tab) before the creating coroutine resumes, so keying by tab id raced (seen in T052).
- **🐞 Found by T052**: a page-created WebView with no `WebViewClient` hands its navigation to
  another app — the emulator opened Chrome. The pop-up gets a plain client the moment it is made.
- **`window.close()` before the tab exists** (a fast page) is remembered and applied once the tab
  is created.
- **JS dialogs (US6)**: `DialogSuppression` lives in `PageSession.dialogs` and is consulted through
  `PageRequestSink.admitDialog()`. "Leave site?" is never suppressed — a suppressed page answering
  "stay" would trap the person on it.
- **No `http_auth_realm` string**: the realm is the site's own text and is shown as-is as the
  dialog body when present.
- **🐞 Found by lint**: the blocked-pop-up catcher and the pending pop-up client lacked
  `onRenderProcessGone`, so a renderer loss in either would have ended the app. Both now handle
  it (`PendingPopupWebViewClient`); the constructor half of the check is the known Spec 016 false
  positive and is suppressed with that note.
- **Full suite after US6 (2026-10-11, `TA016_API36_16K`)**: unit 757/757; instrumented 132/135 on
  the first run. Two were load-dependent flakes that pass in isolation twice (Spec 020's
  duplicate-link window; T052's intermediate "+2 tabs" count, which the test no longer asserts
  because a fast page opens and closes its tab between two polls). The third,
  `BrowserWebViewRendererRecoveryTest.rendererCrash_…`, fails identically on the US3 commit
  `8611c71` in a clean worktree on the same emulator boot — the known Spec 019 environment flake,
  not a Spec 021 regression.
- **🐞 Blocked pop-up notice lost its Open (found at G13)**: when the opener page was loaded twice
  (the incoming-link load is replaced by the tab-switch load), the first page's throw-away window
  never navigated, reported "no address" after the wait and covered the real notice. The notice
  now carries the opener's page generation and is dropped if that page is gone.
- **Locale wording (G17)**: DE "möchte deine Kamera und Mikrofon verwenden" lacked an article
  for the second noun → "möchte auf Kamera und Mikrofon zugreifen"; RU had capability names in the
  accusative ("камеру"), which also showed as "Камеру" in Site permissions rows → nominative with
  "запрашивает доступ: …"; FR "votre caméra et micro" mixed genders → "demande l'accès : …".
  The blocked-explanation bodies were rephrased to match.
- **Release builds refuse cleartext http** (`ERR_CLEARTEXT_NOT_PERMITTED`) — pre-existing since
  Spec 007, already filed in `app/src/debug/AndroidManifest.xml` as undecided. Not changed here
  (out of scope); fixture gates used the debug build, which allows cleartext.
- **Play Data safety (T067)**: camera, microphone and location are delivered by the system
  WebView to the website the person chose, after the person allowed that site; nothing is sent
  to the developer and nothing is retained by the app except the per-site allow/deny choice on
  the device. Under Play's definitions this is neither "collected" (the app does not transmit it
  off the device to itself) nor "shared" (a user-initiated transfer the person expects is
  exempt). **Conclusion: no change to the declaration** ("no data collected or shared"). The
  owner should still confirm this reading in Play Console before the next release.
- **🐞 Camera video never started (found at G1 on the A55)**: with WebView's default
  `mediaPlaybackRequiresUserGesture = true`, a camera stream attached to a `<video>` did not play —
  the permission dialog outlives the tap's activation — so video calls showed nothing although
  the camera was connected. Set to `false`, as mainstream browsers allow such media to start.
  Side effect: pages may start media on their own (YouTube now autoplays muted, as in Chrome).
- **Phone settings touched during the A55 pass**: auto-rotate was switched on for the fullscreen
  check and then locked to portrait (`accelerometer_rotation=0`); the original value was not
  recorded — the owner may want to restore it. The release app installed for the pass was
  cleared afterwards; density 420 and the app locale were restored; the default browser was not
  changed.
- **WebChromeClient moved** to its own `BrowserChromeClient.kt` (internal), since every story adds
  hooks to it.

---

## Device Gate Results

*(Filled during implementation — device, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Result | Notes |
|---|---|---|
| G1 Permission dialog | ✅ emulator + A55 | Emulator: dialog with 3 choices → Always allow → Android prompt → camera connected. **A55 (release, Vietnamese): "webrtc.github.io muốn dùng camera của bạn" → Luôn cho phép → Android prompt → live camera image** (after the autoplay fix below); the decision survived a reinstall |
| G2 Remembered + one-time | ✅ emulator | Reload after Always allow → no dialog, camera connects. Allow this time → second request on the same page silent → after reload asked again |
| G3 Incognito | ✅ emulator | Demo opened in an incognito tab (long-press → Open in incognito tab → Switch): two choices only; after Allow this time, Site permissions still empty |
| G4 Combined + location | ✅ emulator + A55 | Camera+mic demo (pc1) → one dialog; mic recording active, so WebRTC audio works with `MODIFY_AUDIO_SETTINGS`. Location precise on the emulator; **approximate only on the Galaxy A55** (release, Android 16): ThirtySix holds COARSE, not FINE, and browserleaks receives coordinates. On the A55 the pc1 dialog asked for the microphone only, because the camera was already allowed (C1.1 `Ask` subset) |
| G5 Permanently denied | ✅ emulator | `user-fixed` camera → Allow this time → "Camera is turned off for ThirtySix" with Open settings → app settings opened |
| G6 Not secure + other permissions | ✅ emulator | Local http fixture: camera → `NotAllowedError`, no dialog; MIDI sysex → `NotAllowedError`, no dialog |
| G7 Site permissions screen | ✅ emulator | Site listed with Camera · Allowed; changed to Not allowed → next request `NotAllowedError` with no camera connect; Remove → asked again; Remove all (confirm) → empty |
| G8 Clear-data category | ✅ emulator | Category unticked by default; clear without it → decision stays; ticked → gone |
| G9 File upload | ✅ emulator | Single file → `file:1`; multiple → `files:2`; cancel then reopen works; capture input: camera permission asked at that moment; refused → photo picker alone; granted → chooser with Camera → photo (75 KB, `cache/capture/`) → `capture:1` |
| G10 Fullscreen | ✅ emulator + A55 | Emulator: HTML5 video, sensor rotation to landscape, Back → same page, 0 requests. **A55 (release): YouTube fullscreen 10/10** — browser and system bars hidden in fullscreen, Back returns to the same watch page with bars restored; **incognito fullscreen screenshot is black** (window `fl` has FLAG_SECURE 0x2000) |
| G11 Rotation no reload | ✅ emulator | Rotate both ways: page state kept, 0 requests to the server |
| G12 Long-press | ✅ emulator + A55 | Release: link → 5 actions; background tab + Switch. Image → 4; image-in-link → both groups. A55: RU and DE sheets fit; long-press on a Google result → Copy → "Đã sao chép". **Text selection works on the A55** (Copy / Select all / Share bar on Wikipedia text). `javascript:` links get no menu (WebView reports an unknown hit) |
| G13 Pop-ups | ✅ emulator + A55 | Emulator: target=_blank, window.open after tap, blocked + Open. **A55: FirebaseUI demo "Sign in with Popup" → Google** opened accounts.google.com ("Tiếp tục tới fir-ui-demo…") as its own tab beside the opener tab; finishing the sign-in needs the owner's account and was not attempted. the-internet.herokuapp.com `/windows` "Click Here" → new tab "New Window" |
| G14 JS dialogs + leave | ✅ emulator | alert ×3: 1st plain, 2nd with "Don't let this site show more dialogs", ticked → 3rd silent; reload resets; confirm Cancel → `false`; prompt passes typed text; leave: Stay keeps the page, Leave navigates |
| G15 HTTP auth | ✅ emulator + A55 | Emulator: dialog, sign-in, asked again after restart, no credential in app files/logcat. **A55: `httpbin.org/basic-auth/u/p` → `"authenticated": true`**. Within one running session the engine reuses a realm's credentials from memory (as every browser does); after an app restart it asks again. An address bar left focused after signing in was traced to `adb input text` (hardware key events leave touch mode); typing on the soft keyboard leaves it unfocused |
| G16 Stale requests | ✅ emulator | confirm open, then a link from another app opens a new tab → dialog gone. One-at-a-time seen in G14 |
| G17 Locales | ✅ (TalkBack deferred) | **A55 at 360dp (density 480), DE and RU**: permission dialog, HTTP-auth dialog, long-press sheet, Site permissions screen and Settings all fit with no clipping, in the phone's dark theme. Wording fixed in DE/RU/FR (Implementation Notes). App locale and density 420 restored |
| G18 16 KB + size | ✅ | Release on `TA016_API36_16K`: Wikipedia + long-press + background tab + Switch, zero FATAL. APK **3,359,240 B** (+95,596 B vs Spec 020, budget +150 KB). 16 KB script: all `.so` `align=0x4000`, no new `.so` |
| G19 Upgrade | ✅ | `main` (`1bb8c6a`) release → 4 tabs + a bookmark → this release installed over it → tabs and bookmark intact, no crash (migration 2→3), Site permissions empty |
| G20 Privacy | ✅ emulator | App-process logcat during http fixture + https browsing: 43 lines, no address. Two older logs that printed URLs were fixed (FaviconCache, external-intent failure) |
| SC-001 real sites | ✅ 10/10 | Galaxy A55, release, 2026-10-11: (1) WebRTC pc1 call — local and remote video, mic active; (2) location — browserleaks, approximate; (3) file upload — tinypng opens the system picker (no file sent, to keep the owner's files private) + local fixture uploads; (4) YouTube fullscreen 10/10; (5) Google sign-in pop-up opens as a tab; (6) httpbin basic auth; (7) long-press a Google result → Copy; (8) the-internet JS alerts: alert / confirm Cancel / prompt answered; (9) the-internet target=_blank → new tab; (10) camera-capture upload — Samsung Camera → 2.5 MB photo reaches the page |
| TalkBack | DEFERRED | Needs a person (not scriptable on these emulators) |
