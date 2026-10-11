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
- [ ] T027 [US1] Device gates G1–G8 (quickstart) on the Galaxy A55 and the 16 KB AVD; if WebRTC audio fails without `MODIFY_AUDIO_SETTINGS`, add it with a Constitution table row (research R10); record results

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
- [ ] T033 [US2] Device gate G9; record

---

## Phase 5: User Story 3 — Fullscreen (Priority: P1)

**Goal**: page fullscreen over everything, bars hidden, physical orientation, Back exits, no reload.

**Independent Test**: quickstart G10–G11.

- [ ] T034 [US3] Widen `MainActivity` `android:configChanges` in `app/src/main/AndroidManifest.xml` to `locale|layoutDirection|screenLayout|orientation|screenSize|smallestScreenSize|keyboardHidden` with a comment citing research R4; extend the manifest test (T022) to assert it
- [ ] T035 [US3] Create `MAIN/presentation/browser/page/FullscreenController.kt` (holds the custom view + callback; `enter`, `exit`) and render it in `MAIN/presentation/browser/BrowserScreen.kt` as a full-window `AndroidView` above all content, hiding system bars with `WindowInsetsControllerCompat` (transient swipe behaviour), setting `requestedOrientation = SCREEN_ORIENTATION_FULL_SENSOR`, and a `BackHandler` that exits first (FR-014–FR-016); restoring bars and `SCREEN_ORIENTATION_UNSPECIFIED` on exit
- [ ] T036 [US3] Implement `onShowCustomView` / `onHideCustomView` in `MAIN/presentation/browser/BrowserWebView.kt` → `FullscreenController`
- [ ] T037 [P] [US3] Write `TEST/presentation/browser/page/FullscreenControllerTest.kt` (enter/exit state, callback answered once, exit on tab change)
- [ ] T038 [US3] Re-run the full instrumented suite to confirm the `configChanges` change breaks no rotation/recreation test (Spec 018 onboarding, Spec 020 saved request); device gates G10–G11; record

---

## Phase 6: User Story 4 — Long-press menu (Priority: P2)

**Goal**: link/image menus with background tab, incognito tab, copy, share, download.

**Independent Test**: quickstart G12.

- [ ] T039 [P] [US4] Write `TEST/domain/usecase/BuildLongPressMenuUseCaseTest.kt` covering every row of contracts C1.4
- [ ] T040 [P] [US4] Create `MAIN/domain/model/LongPressMenu.kt` (`LongPressHit { Link, Image, ImageLink }`, `LongPressAction`, `LongPressMenu`) and `MAIN/domain/usecase/BuildLongPressMenuUseCase.kt` (data-model §7)
- [ ] T041 [US4] Add `createBackgroundTab(url)` with a default body delegating to `createTab(url)` to `MAIN/domain/repository/TabRepository.kt` and `IncognitoTabRepository.kt`; override it in `MAIN/data/repository/TabRepositoryImpl.kt` (insert with `lastActiveAt = currentActive.lastActiveAt - 1`, same cap) and the incognito implementation (research R9); add `TEST/data/repository/TabRepositoryBackgroundTabTest.kt` (active tab unchanged, recency order, cap)
- [ ] T042 [US4] Create `MAIN/domain/usecase/OpenInBackgroundTabUseCase.kt` (normal or incognito; returns the new tab id or `TabLimit`)
- [ ] T043 [US4] Wire `WebView.setOnLongClickListener` in `MAIN/presentation/browser/BrowserWebView.kt` (hit test types, `requestFocusNodeHref` for image links via a main-thread `Handler`, return `false` when no menu) → `PageRequest.LongPressMenu`
- [ ] T044 [US4] Create `MAIN/presentation/browser/page/LongPressSheet.kt` (contracts C2.4) and its actions in `PageRequestHost`: background tab + "Switch" snackbar, incognito tab, `ClipboardWriter` copy + confirmation, `ACTION_SEND` chooser, `StartDownloadUseCase` for downloads (Spec 015), tab-limit message (FR-018–FR-024)
- [ ] T045 [P] [US4] Write `ATEST/presentation/browser/page/LongPressSheetTest.kt` (rows, header, callbacks, Back closes)
- [ ] T046 [US4] Device gate G12; record

---

## Phase 7: User Story 5 — Pop-ups and new windows (Priority: P2)

**Goal**: user-gesture windows become adopted tabs; others blocked with a one-shot Open; page can close its own pop-up tab.

**Independent Test**: quickstart G13.

- [ ] T047 [P] [US5] Write `TEST/domain/usecase/DecidePopupUseCaseTest.kt` covering contracts C1.3
- [ ] T048 [P] [US5] Create `MAIN/domain/usecase/DecidePopupUseCase.kt`
- [ ] T049 [US5] Create `MAIN/presentation/browser/page/PopupWebViewRegistry.kt` (`@Singleton`, data-model §6) and make `MAIN/presentation/browser/BrowserWebView.kt` adopt a registered WebView for the composed tab instead of building one (configuring it with the same clients and settings; released like any WebView)
- [ ] T050 [US5] Implement `onCreateWindow` / `onCloseWindow` and enable `setSupportMultipleWindows(true)` + `javaScriptCanOpenWindowsAutomatically = true` in `MAIN/presentation/browser/BrowserWebView.kt`: gesture → new tab (normal or incognito per opener) + transport WebView + registry; no gesture → throw-away WebView captures the address, then `PageRequest.PopupBlocked(url?)`; at the cap → existing tab-limit message (research R5, FR-025–FR-028)
- [ ] T051 [US5] Render `PopupBlocked` in `PageRequestHost` as a snackbar with Open (one-shot new active tab, FR-026a)
- [ ] T052 [US5] Instrumented test `ATEST/presentation/browser/page/PopupInstrumentedTest.kt` with a local fixture: `target=_blank` click → one new tab; `window.open` on load → no tab + notice; `window.close()` in an adopted pop-up → tab closed, opener active
- [ ] T053 [US5] Device gate G13 (including a real sign-in pop-up demo); record

---

## Phase 8: User Story 6 — Site dialogs and HTTP auth (Priority: P3)

**Goal**: styled alert/confirm/prompt/leave dialogs naming the site, flood control; HTTP auth never stored.

**Independent Test**: quickstart G14–G15.

- [ ] T054 [P] [US6] Write `TEST/presentation/browser/page/DialogSuppressionTest.kt` covering contracts C1.5
- [ ] T055 [US6] Create `MAIN/presentation/browser/page/DialogSuppression.kt` (per tab, reset on generation change) and implement `onJsAlert` / `onJsConfirm` / `onJsPrompt` / `onJsBeforeUnload` in `MAIN/presentation/browser/BrowserWebView.kt` → `PageRequest.JsDialog` / `LeavePage` (return `true`; suppressed → `cancel()` silently)
- [ ] T056 [US6] Create `MAIN/presentation/browser/page/SiteJsDialog.kt` (contracts C2.2) and render alert/confirm/prompt/leave in `PageRequestHost`
- [ ] T057 [US6] Implement `onReceivedHttpAuthRequest` in `BrowserWebViewClient` → `PageRequest.HttpAuth`; create `MAIN/presentation/browser/page/HttpAuthDialog.kt` (contracts C2.3; masked password, autofill disabled, fields cleared on dismiss); `proceed` / `cancel` only — never `setHttpAuthUsernamePassword` (FR-032, INV-5)
- [ ] T058 [P] [US6] Write `ATEST/presentation/browser/page/SiteDialogsTest.kt` (alert/confirm/prompt answers, suppress checkbox from the 2nd dialog, HTTP auth sign in/cancel, Back = cancel)
- [ ] T059 [US6] Device gates G14–G15 (credential search: `run-as` files, DataStore, Room, logcat); record

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T060 [P] Stale-request and one-at-a-time device gate G16; record
- [ ] T061 [P] Privacy review (FR-036): grep new/changed files for logs carrying origins/URLs/files/credentials; device gate G20; record
- [ ] T062 [P] Locales + accessibility gate G17 (DE/RU at 360dp for all new UI; TalkBack DEFERRED to a person)
- [ ] T063 Update the Constitution permission table (`.specify/memory/constitution.md` Technical Standards → Android Permissions) with the rows of contracts C3.4 (PATCH-level wording change, Sync Impact note), replacing the "Pending, Spec 021" note
- [ ] T064 Compose compiler report (Testing Gate 9) + recomposition note for the fullscreen overlay and Site permissions list (Gate 10, "needs a person" if Layout Inspector is unavailable)
- [ ] T065 Full automated gates: `testDebugUnitTest lintDebug detekt ktlintCheck`, then `assembleRelease` separately, 16 KB script, `connectedDebugAndroidTest` on the 16 KB AVD (force-stop first)
- [ ] T066 Gate G18 (release on `TA016_API36_16K`, zero FATAL; APK ≤ +150 KB) and G19 (install over `main`'s build: data intact, migration 2→3 applied); record
- [ ] T067 [P] Play Data safety check (Constitution §II): verify against Play's definitions that camera/microphone/location reaching a website at the person's direction is not "collected" or "shared" by the app; record the conclusion and the declaration to make in `specs/021-web-capabilities/tasks.md` › Implementation Notes (if it is, propose the declaration change to the owner before merging)
- [ ] T068 SC-001 real-site run on the Galaxy A55 (release): a WebRTC call demo, a maps site (location), a form with file upload, YouTube fullscreen, a Google sign-in pop-up, `httpbin.org/basic-auth`, a long-press on a search result, a JS-dialog test page, a site using `target=_blank`, and a camera-capture upload; record n/10 in "Device Gate Results"
- [ ] T069 Update docs: `CLAUDE.md` (roadmap row, Recent Changes, Active Spec, facts to keep), `.claude/claude-app/sdd-roadmap.md`, `dev-workflow.md`, `project-context.md`
- [ ] T070 Open the PR `021-web-capabilities` → `main` with the gate table, Constitution line, permission additions, FR-015 amendment and APK delta

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
- **No new icons**: the dialogs use existing glyphs (Shield, Settings); camera/mic/location
  vectors were not added rather than drawn from memory.
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
- **WebChromeClient moved** to its own `BrowserChromeClient.kt` (internal), since every story adds
  hooks to it.

---

## Device Gate Results

*(Filled during implementation — device, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Result | Notes |
|---|---|---|
| G1 Permission dialog | — | |
| G2 Remembered + one-time | — | |
| G3 Incognito | — | |
| G4 Combined + location | — | |
| G5 Permanently denied | — | |
| G6 Not secure + other permissions | — | |
| G7 Site permissions screen | — | |
| G8 Clear-data category | — | |
| G9 File upload | — | |
| G10 Fullscreen | — | |
| G11 Rotation no reload | — | |
| G12 Long-press | — | |
| G13 Pop-ups | — | |
| G14 JS dialogs + leave | — | |
| G15 HTTP auth | — | |
| G16 Stale requests | — | |
| G17 Locales + a11y | — | |
| G18 16 KB + size | — | |
| G19 Upgrade | — | |
| G20 Privacy | — | |
| SC-001 real sites | — | |
| TalkBack | — | |
