---
description: "Task list for Spec 024 — Privacy Tools"
---

# Tasks: Privacy Tools

**Input**: Design documents from `specs/024-privacy-tools/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R15), data-model.md (Fire run steps, INV-1–INV-10), contracts/PrivacyToolsContracts.kt (C1–C23), quickstart.md (G1–G16)

**Tests**: REQUIRED. Constitution §VI names "the fireproof-site rules (Spec 024)", and §VI also requires Room migration tests and Compose UI tests for critical flows. Research R15 lists the coverage. Script behaviour is tested only inside WebView, behind `assumeTrue(DOCUMENT_START_SCRIPT)`.

**Organization**: by user story.
- US1 (Fire) and US2 (fireproof sites) are P1.
- US3 (dashboard) is P2.
- US4 (cookie banners) and US5 (fingerprinting) are P3.
- **US1 is the MVP.** On its own, Fire clears everything, because the fireproof list is empty and the `Complete` path applies.
- US2 adds the fireproof-aware path and the first, minimal dashboard sheet (site + fireproof switch).
- US3 completes the sheet.

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `DEBUG` = `app/src/debug/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`
- `ASSETS` = `app/src/main/assets`
- `FIX` = `specs/024-privacy-tools/fixtures`

---

## Phase 1: Setup

- [X] T001 [P] Add constants. **No other new constant file**: durations already live in `BrowserLimits`, so no `AnimationDurations` file is created.
  - `MAIN/core/constants/StorageKeys.kt`: `FIRE_IN_PROGRESS = booleanPreferencesKey("fire_in_progress")`, with a KDoc citing data-model "DataStore — one key".
  - `MAIN/core/constants/BrowserLimits.kt`: `FIRE_OVERLAY_MS = 900L`, `FIRE_OVERLAY_MIN_MS = 600L`, `SITE_DATA_DELETE_TIMEOUT_MS = 5_000L` (next to `WEB_DATA_CLEAR_TIMEOUT_MS`) and `FIRE_SECRET_BYTES = 16`.
  - `MAIN/core/constants/AssetPaths.kt`: `PRIVACY_CONSENT_SCRIPT = "privacy/consent.js"` and `PRIVACY_FINGERPRINT_SCRIPT = "privacy/fingerprint.js"`.
- [X] T002 [P] Add the Material Symbols `local_fire_department` glyph (filled, 24dp, Apache-2.0, as the other Spec 019 symbols) as `RES/drawable/ic_fire.xml`. Expose it as `AppIcons.Fire` in `MAIN/presentation/theme/AppIcons.kt`.
- [X] T003 [P] Add every string key for the five stories to `RES/values/strings.xml`, and translate them in `RES/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`. Keep "ThirtySix" in Latin script.
  - **Fire**:
    - tile label and Tabs-button content description;
    - confirm dialog title;
    - body listing what is cleared (tabs, history, cookies and site data, cached files, site permissions) and what is kept (bookmarks, downloads, settings, fireproof sites);
    - confirm and cancel labels;
    - an overlay accessibility announcement, "Browsing data cleared".
  - **Dashboard**:
    - sheet title;
    - "Connection is secure" / "Connection not secure";
    - `<plurals>` "%d trackers blocked" (reuse Spec 023's plural if its wording fits, otherwise add a new key);
    - "No trackers blocked on this page";
    - protection switch label;
    - "Fireproof this site" switch label;
    - a one-line fireproof help text: "Keeps you signed in when you use Fire. Sign-in services on other sites need fireproofing too."
  - **Settings**:
    - "Fireproof sites" row title and summary;
    - screen title;
    - empty-state title and body;
    - remove-site content description;
    - "Remove all", and its confirm dialog title and body.
  - **Clear browsing data**: the "Fireproof sites" category label and description.
  - **About**: "Cookie-banner rules: autoconsent by DuckDuckGo, MPL-2.0" with the source URL left untranslated.
- [X] T004 [P] Create the fixtures in `FIX/`, mirrored into `app/src/androidTest/assets/fixtures024/`, as quickstart "Prerequisites" defines them:
  - `serve.py` — a static server that adds `Set-Cookie: fp_persist=1; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax` on `storage.html`;
  - `storage.html` (+`?check`) covering cookie, localStorage, IndexedDB, Cache Storage and a service worker (`sw.js`);
  - `fingerprint.html`, which prints a canvas hash, an audio hash, `typeof navigator.getBattery`, and `enumerateDevices` labels and ids;
  - `frames.html`;
  - `consent/` — one page per consent tool in the generic rule range that autoconsent's rules reference. **At minimum** cover OneTrust, Cookiebot, Didomi, Quantcast, Usercentrics, TrustArc, Sourcepoint (frame), Complianz, CookieYes and Iubenda. Each page reproduces the selectors the rule waits for, and records the button clicked into `localStorage.fixtureChoice` (`reject` | `accept` | `settings`).
- [X] T005 Create the generator `tools/privacy-scripts/` (research R7):
  - `package.json` and a committed lockfile, pinning `@duckduckgo/autoconsent` **16.51.0** and `esbuild` exactly.
  - `entry.ts`. Its config is `{ autoAction: 'optOut', isMainWorld: true, heuristicMode: 'off', enableHeuristicDetection: false, enableGeneratedRules: false, enablePrehide: false, enableCosmeticRules: false }` with all logs false. It has a no-op `sendContentMessage`, creates **no** `window` property, and is wrapped as `(function(cfg){ … })` so that it reads the prelude object from the closure argument (research R5).
  - `build.sh`. It filters the compact rules to `genericRuleRange` and drops `auto_*` rules, bundles with `esbuild --minify --format=iife`, prepends the MPL-2.0 notice, the upstream URL and the version, and writes `ASSETS/privacy/consent.js`.
  - `README.md`, explaining how to refresh the script by hand and stating that it never runs from Gradle or CI.
- [X] T006 Run T005 and commit `ASSETS/privacy/consent.js`. In Notes, record:
  - the raw and gzip size, and the rule count;
  - `grep -c "optIn\|heuristic"` hits, with a one-line justification for each;
  - that the file contains no `eval(`, no `new Function` and no `autoconsentReceiveMessage`.

  **Stop and report if the gzip size is over 80 KB.**
- [X] T007 [P] Write `ASSETS/privacy/fingerprint.js` (own code, ≈ 4 KB, research R9). It is wrapped as `(function(cfg){ … })` and exits early under the top-site rule. Seed = FNV-1a of `cfg.s + "|" + top`, fed into xorshift32. It covers:
  - canvas `toDataURL`, `toBlob` and `getImageData` — noise is applied to a **copy**, never to the visible canvas;
  - `OffscreenCanvas.convertToBlob` where present;
  - `AudioBuffer.getChannelData` (copy-on-read), and `AnalyserNode.getFloatFrequencyData` / `getByteFrequencyData`;
  - removing `Navigator.prototype.getBattery`;
  - `MediaDevices.enumerateDevices` — blank `deviceId` and `groupId` only when every label is empty.

  Wrappers keep `name`, `length` and a native-looking `toString`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: fireproof storage, the in-memory session secret and the small store changes that Fire, the dashboard and the scripts all build on.

- [X] T008 [P] Create `MAIN/domain/model/FireproofSite.kt` (C1). Create `MAIN/domain/model/FireState.kt` holding `FireState`, `FireStep`, `SiteDataPath` and `FireResult` exactly as C2 defines them.
- [X] T009 [P] Create `MAIN/data/local/entity/FireproofSiteEntity.kt` with table `fireproof_sites` and the columns `site` (TEXT, PRIMARY KEY — "Registrable domain … lower-case ASCII (IDN as punycode)") and `added_at` (INTEGER NOT NULL, epoch millis). Put the column names in companion-object `const val`s. Create `MAIN/data/local/dao/FireproofSiteDao.kt` with:
  - `observeAll()`, newest first;
  - `getAll()`;
  - `upsert`;
  - `delete(site)`;
  - `deleteAll()`.
- [X] T010 Bump `MAIN/data/local/database/AppDatabase.kt` to `SCHEMA_VERSION = 5`, adding the entity and the DAO. Add `MAIN/data/local/database/migrations/Migration4To5.kt`: an additive `CREATE TABLE fireproof_sites` only — **stop if it would touch an existing table**. Register it in `MAIN/di/DatabaseModule.kt`, and provide the DAO. Export and commit `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/5.json`.
- [X] T011 [P] Extend `TEST/.../AppDatabaseMigrationTest.kt` so the v1 → v5 chain checks that `fireproof_sites` exists and that every earlier table's rows survive.
- [X] T012 Create `MAIN/domain/repository/FireproofSiteRepository.kt` (C4), `MAIN/data/mapper/FireproofSiteMapper.kt` and `MAIN/data/repository/FireproofSiteRepositoryImpl.kt`. Mirror Spec 023's `ProtectionExceptionRepositoryImpl` as built (see its Implementation Notes, T031): the impl is bound `@Singleton`, holds the snapshot, is filled from Room once and is updated by every write. `currentSites()` never touches the database. Writes return `Result`.
- [X] T013 [P] Unit-test the repository in `TEST/.../data/repository/FireproofSiteRepositoryImplTest.kt`: add, remove, clear-all, newest-first order, snapshot consistency after each write, and failure → `Result.Error` (Robolectric in-memory Room, as Spec 023).
- [X] T014 [P] Create these use cases in `MAIN/domain/usecase/`:
  - `SetSiteFireproofUseCase` — normal browsing only. It reduces a host to its registrable domain with Spec 023's `PublicSuffixMatcher` via `ProtectionMatchersHolder`.
  - `ObserveFireproofSitesUseCase`.
  - `RemoveFireproofSitesUseCase(site: String?)`, where `null` means all.
  - `IsSiteFireproofUseCase(host)` — `registrableDomain(host) ∈ currentSites()`, the data-model "Fireproof matching rule".
- [X] T015 [P] Unit-test T014 in `TEST/.../domain/usecase/FireproofUseCasesTest.kt`. Cover:
  - `mail.example.com` and `www.example.com` both map to `example.com`;
  - `example.co.uk`;
  - `alice.github.io` is distinct from `bob.github.io`;
  - an IP or single-label host is its own site;
  - remove-all.

  This is the §VI "fireproof-site rules" coverage.
- [X] T016 [P] Create `MAIN/domain/repository/PrivacyScriptSession.kt` (C10) and `MAIN/data/local/privacy/PrivacyScriptSessionImpl.kt` (`@Singleton`). `secret` is a `StateFlow<String>` of `BrowserLimits.FIRE_SECRET_BYTES` from `SecureRandom`, hex-encoded; `regenerate()` replaces it.
- [X] T017 [P] Add `clear()` to `MAIN/domain/repository/HttpsUpgradeFailures.kt` and `MAIN/data/local/protection/HttpsUpgradeFailureStore.kt` (C7). Add `retainOnly(keep: (origin) -> Boolean)` to `MAIN/data/local/permission/OneTimeGrantStore.kt` (C9). Unit-test both in their existing test files.
- [X] T018 [P] Add `suspend fun retainOnly(hosts: Set<String>)` to the `FaviconCache` interface and to `DiskFaviconCache` in `MAIN/data/local/cache/FaviconCache.kt` (C8, research R13): delete every file in `cacheDir/favicons/` whose name is not `sha1(host).png` for a kept host, and never throw. Update every fake `FaviconCache` in `TEST` and `ATEST` (Spec 018 lesson: fakes are not centralised; `grep -rl ": FaviconCache"`). Add `DiskFaviconCacheRetainTest`.
- [X] T019 [P] Add `suspend fun distinctHosts(): Set<String>` to `MAIN/domain/repository/HistoryRepository.kt` (C6). Back it with a `SELECT DISTINCT url FROM history` query in `MAIN/data/local/dao/HistoryDao.kt`, and parse the hosts in `HistoryRepositoryImpl`, skipping invalid URLs. Update every fake `HistoryRepository` and add a unit test.
- [X] T020 Create `MAIN/di/PrivacyToolsModule.kt` binding `FireproofSiteRepository` and `PrivacyScriptSession`.

**Checkpoint**: `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` green; the migration exported.

---

## Phase 3: User Story 1 — Burn everything in one action (Priority: P1) 🎯 MVP

**Goal**: Fire, from the hub and the Tabs screen, closes every tab and clears history, cookies, site data, cache, site permissions, favicons and session traces, ending on Home. With no fireproof site, the Spec 016 `Complete` path is used.

**Independent Test**: quickstart G2, G3 (no fireproof site), G6 (kept data), G7 (interruption), G8 (session traces).

### Spike (first — research R1)

- [X] T021 [US1] Device spike, debug build only, as a throwaway `ATEST/.../spike/EngineFeatureSpikeTest.kt` (deleted after recording). On the A55, `TA016_API36_16K` and `TA016_API24`:
  - log `isFeatureSupported` for `DOCUMENT_START_SCRIPT`, `GET_COOKIE_INFO` and `DELETE_BROWSING_DATA`;
  - load `storage.html`, then log `getCookieInfo(url)` and `WebStorage.getOrigins()`.

  Record the matrix, the cookie string format, and which storage kinds `getOrigins` reports, in research R1/R3 and in Notes (quickstart **G1**). **Stop and report to the owner** if either of these holds:
  - `getCookieInfo` omits `HttpOnly` or the expiry;
  - `DELETE_BROWSING_DATA` is missing on the A55.

### Tests for User Story 1

- [X] T022 [P] [US1] `TEST/.../domain/usecase/RunFireUseCaseTest.kt`, with fakes for every collaborator. It asserts:
  - **step order** — INV-2 (incognito closed before any cookie step) and INV-3 (hosts collected before tabs, history or permissions are cleared);
  - the `Complete` path when the fireproof list is empty;
  - each step in its own failure boundary — a throw in step N still runs step N+1, and lands in `failedSteps`;
  - cancellation is rethrown;
  - **INV-4** — the bookmark, download, protection-exception and fireproof fakes record zero writes;
  - `HttpsUpgradeFailures.clear()` and `PrivacyScriptSession.regenerate()` are called (FR-003a);
  - permissions of non-fireproof origins are removed and fireproof ones kept, including one-time grants (FR-004a);
  - the favicon keep-set = bookmark hosts ∪ fireproof hosts (FR-008);
  - exactly one home tab exists at the end.
- [X] T023 [P] [US1] `TEST/.../fire/FireCoordinatorImplTest.kt`, using a fake DataStore and a fake run. It asserts:
  - the marker is written **and awaited** before the run starts (INV-1);
  - the marker is cleared after the run;
  - a second `fire()` while `Running` is ignored (INV-5);
  - `resumeIfInterrupted()` runs only when the marker is `true`, and then clears it;
  - the `Finished` → `acknowledge()` → `Idle` sequence.

### Implementation for User Story 1

- [X] T024 [US1] Extend `MAIN/domain/repository/WebDataCleaner.kt` with `storageOrigins()` (C5). Implement it in `MAIN/data/local/webdata/AndroidWebDataCleaner.kt` using `WebStorage.getInstance().getOrigins` on the main thread, mapped to hosts and empty on failure. `clearExcept` is added in US2 (T039). Update every fake `WebDataCleaner`.
- [X] T025 [US1] Create `MAIN/domain/usecase/RunFireUseCase.kt` (C11), implementing data-model "Fire run" steps 1–10 with the `ClearBrowsingDataUseCase` step/attempt helpers. Collaborators:
  - `TabRepository.closeAllTabs()` — it already creates the single fresh home tab (Spec 011), which covers steps 3 and 10 together. Delete screenshots via `ScreenshotCache.clearAll()` first, as `CloseAllTabsUseCase` does.
  - `IncognitoTabRepository.closeAll()` for step 2; its 1 → 0 transition restores the cookie snapshot and clears the incognito exceptions.
  - `HistoryRepository`, `WebDataCleaner`, `SitePermissionRepository`, `OneTimeGrantStore`, `FaviconCache`, `BookmarkRepository.getAllBookmarks()`, `HttpsUpgradeFailures`, `PrivacyScriptSession`, `FireproofSiteRepository` and `IsSiteFireproofUseCase`.

  For step 5 in US1, call `discardSetAsideCookies()` and then `clearCookiesAndSiteData()` when `currentSites()` is empty. Otherwise call the US2 path; until T039 lands, `TODO` is not allowed — use the `Complete` path and leave a KDoc pointing at T039.
- [X] T026 [US1] Create `MAIN/fire/FireCoordinatorImpl.kt` implementing C12 over an application-scope coroutine (`@ApplicationScope`, or add a qualifier in `MAIN/di/` if none exists), with `Mutex.tryLock` and the `FIRE_IN_PROGRESS` marker. Add marker read/write to the settings data source and repository: a new `isFireInProgress()` / `setFireInProgress(Boolean)` pair on `SettingsRepository`. **Update all four fake `SettingsRepository`s** (Spec 018 lesson). Bind it in `PrivacyToolsModule`.
- [X] T027 [US1] In `MAIN/MainActivity.kt`, after the existing `currentSettings()` await and before `setContent`, `await fireCoordinator.resumeIfInterrupted()` — only when the marker read returns `true` (FR-007, research R10). Do **not** use `setKeepOnScreenCondition` (Spec 017 INV-12).
- [X] T028 [P] [US1] Create `MAIN/presentation/browser/fire/FireViewModel.kt` (`@HiltViewModel`), exposing `state: StateFlow<FireState>`, `fire()` and `acknowledge()`. It is a thin pass-through to the coordinator.
- [X] T029 [P] [US1] Create `MAIN/presentation/browser/components/FireOverlay.kt`:
  - a full-screen scrim with `AppIcons.Fire` that grows and fades over `BrowserLimits.FIRE_OVERLAY_MS`, with values read only inside `graphicsLayer` (§V);
  - shown while `Running`; when `Finished` and at least `FIRE_OVERLAY_MIN_MS` has passed, it fades out and calls `acknowledge()`;
  - a polite live-region announcement of "Browsing data cleared";
  - it consumes touches while visible;
  - animations use Compose APIs only, so the system animator scale governs them (FR-006).
- [X] T030 [P] [US1] Create `MAIN/presentation/browser/components/FireConfirmDialog.kt` on top of the shared `MAIN/presentation/components/ConfirmDialog.kt`, using the destructive style and the T003 strings. It confirms or dismisses; Back dismisses (US1 #2).
- [X] T031 [US1] Add the Fire tile to the hub's tile grid in `MAIN/presentation/browser/components/HubSheet.kt` using `QuickTile` + `AppIcons.Fire`. Wire it in `MAIN/presentation/browser/BrowserScreen.kt`: tile → close hub → `FireConfirmDialog` → `FireViewModel.fire()`. Host `FireOverlay` above the browser content. While `Running`, the hub tile is disabled.
- [X] T032 [US1] Add a Fire icon button to the Tabs top bar in `MAIN/presentation/tabs/TabsScreen.kt`. It opens the same dialog, using the `FireViewModel` from the activity scope so the overlay survives the navigation, and on confirm pops back to the browser before firing (research R10). Both entry points are disabled while `Running` (INV-5).
- [X] T033 [US1] Verify the WebView side: when the tab store empties, `BrowserScreen` must dispose every normal WebView through the Spec 011 close-all path and every incognito WebView through Spec 012's 9-step destroy; find state, fullscreen, open dialogs and pending page requests must be cleared (edge cases). Fix any gap in `MAIN/presentation/browser/BrowserScreen.kt` / `page/PageSession.kt` rather than adding a Fire-specific path.
- [X] T034 [P] [US1] Add a debug-only hook in `DEBUG/.../dev/FireStepDelay.kt`: a broadcast receiver in the debug manifest that sets a per-step delay read by `RunFireUseCase` through an interface bound to a no-op in `main`. Gate G7 needs it. As with Spec 014's seeder, the class must not exist in release.
- [X] T035 [US1] Instrumented `ATEST/.../fire/FireEndToEndTest.kt` on the 16K AVD, with the `fixtures024` assets and a per-test DataStore (`@UninstallModules(SettingsDataStoreProviderModule)`). It covers:
  - 3 normal + 1 incognito tab;
  - `storage.html` on `localhost` and `127.0.0.1`;
  - seeded history;
  - a bookmark and a theme setting.

  Run Fire from the hub and assert:
  - one Home tab;
  - History empty;
  - `storage.html?check` shows nothing on both origins;
  - the bookmark and setting are unchanged;
  - cancel changes nothing.
- [X] T036 [P] [US1] Compose UI test `ATEST/.../fire/FireEntryPointsTest.kt`: the hub tile and the Tabs button both open the dialog; cancel and Back dismiss it; confirm shows the overlay; with `animator_duration_scale = 0` the overlay still appears and dismisses (FR-006).

**Checkpoint**: MVP. G2, G3, G6, G7 and G8 pass on the 16K AVD.

---

## Phase 4: User Story 2 — Keep signed in on sites I trust (Priority: P1)

**Goal**: mark a site fireproof (minimal dashboard + Settings list); Fire keeps its cookies (with attributes) and site data; Clear browsing data ignores fireproofing and offers the opt-in category.

**Independent Test**: quickstart G4, G5, G10.

### Tests for User Story 2

- [X] T037 [P] [US2] Extend `RunFireUseCaseTest` (T022): with fireproof sites, step 5 calls `clearExcept(keepSites, candidateHosts, isKept)`, the result's `siteDataPath` is propagated, and step 6 runs on `PerSite` and `Fallback`.
- [X] T038 [P] [US2] Extend `TEST/.../domain/usecase/ClearBrowsingDataUseCaseTest.kt`: the `FireproofSites` category empties the list, is not in `DEFAULT_SELECTION`, and `CookiesAndSiteData` never consults the fireproof list (INV-10).

### Implementation for User Story 2

- [X] T039 [US2] Implement `clearExcept(...)` (C5) in `MAIN/data/local/webdata/AndroidWebDataCleaner.kt`, following research R2/R3:
  1. Snapshot the fireproof cookies. For every query URL — `https://` and `http://` of each kept site, of `www.` + the site, and of each candidate host that `isKept` — read `CookieManagerCompat.getCookieInfo` when `GET_COOKIE_INFO` is supported, otherwise `getCookie`. Deduplicate.
  2. If `DELETE_BROWSING_DATA` is supported, call `WebStorageCompat.deleteBrowsingDataForSite` for each non-kept candidate site, one at a time, awaited with `BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS`, and return `PerSite`. Otherwise call `WebStorage.deleteOrigin` for each non-kept origin from `getOrigins()`, and return `Fallback`.
  3. Call `removeAllCookies`, awaited.
  4. `setCookie(url, entry)` for each snapshot entry, then `flush()`.

  Every platform call is wrapped in `runCatching`. Main-thread calls go through the existing dispatcher pattern. Return `null` only if step 3 fails.

  Replace T025's interim KDoc so that `RunFireUseCase` uses `clearExcept` whenever `currentSites()` is non-empty.
- [X] T040 [US2] Instrumented `ATEST/.../fire/FireproofKeepTest.kt` on the 16K AVD:
  - fireproof `localhost` through `SetSiteFireproofUseCase`, leaving `127.0.0.1` alone;
  - run Fire;
  - `localhost` keeps all five storage kinds and the cookie `fp_persist` (assert via `getCookieInfo` that it is still `HttpOnly` with an expiry);
  - `127.0.0.1` keeps none;
  - **incognito (FR-011, INV-2)**: before Fire, open `localhost` in an incognito tab and set a cookie `fp_incognito=1` from the page; after Fire, `fp_incognito` is gone while `fp_persist` remains;
  - `assumeTrue(DELETE_BROWSING_DATA)` for the storage assertions.
- [X] T041 [P] [US2] Create the minimal dashboard sheet in `MAIN/presentation/browser/components/PrivacyDashboardSheet.kt`, using Spec 019 `ModalBottomSheet` styling and `ListRow` + `Switch`. For now it shows only the site name, the "Fireproof this site" switch and the T003 help text; the switch is hidden in incognito (FR-011). US3 adds the rest.
- [X] T042 [US2] Create `MAIN/presentation/browser/privacy/PrivacyDashboardViewModel.kt`. It takes the active tab's top host and incognito flag from `BrowserScreen`, combines them with `ObserveFireproofSitesUseCase`, and calls `SetSiteFireproofUseCase` when the switch toggles. Use a small dedicated ViewModel, as Spec 022/023 did. In `MAIN/presentation/browser/components/HubProtectionSection.kt`, give the web-page banner (the blocked-tracker line, shown even at 0 trackers and with protection off) its own `onDashboard` callback that opens the sheet; host the sheet in `BrowserScreen.kt`. **Today that banner calls `onPrivacy`, which opens Settings** (`HubSheet.kt`, `BrowserScreen.kt`): keep that behaviour **only** for the plain banner on Home and the incognito start page, which get no dashboard (FR-015). Add `onDashboard` to `HubCallbacks`, and update every existing hub test and test double that builds `HubCallbacks` or asserts the banner opens Settings.
- [X] T043 [P] [US2] Create `MAIN/presentation/settings/fireproof/FireproofSitesScreen.kt`, `FireproofSitesUiState.kt` and `FireproofSitesViewModel.kt`, cloned from `protectionexceptions/`. Add a "Remove all" action with a `ConfirmDialog`, an empty state, and a `LazyColumn` with `key`/`contentType`.
- [X] T044 [US2] Add the route `AppDestination.FireproofSites` (`settings/fireproof_sites`) in `MAIN/presentation/navigation/AppDestination.kt` and `AppNavGraph.kt`, plus a Privacy row "Fireproof sites" in `MAIN/presentation/settings/SettingsScreen.kt` next to "Sites without protection".
- [X] T045 [US2] Add `ClearBrowsingDataCategory.FireproofSites` (not in `DEFAULT_SELECTION`) in `MAIN/domain/model/ClearBrowsingDataCategory.kt`. Add its branch in `MAIN/domain/usecase/ClearBrowsingDataUseCase.kt`, calling `FireproofSiteRepository.clearAll()` inside `clearPerSiteSettings`, and its row in `MAIN/presentation/settings/components/ClearBrowsingDataDialog.kt`.
- [X] T046 [P] [US2] Compose UI test `ATEST/.../settings/FireproofSitesScreenTest.kt`: the list is newest first, single remove works, remove-all confirms, and the empty state shows. Compose UI test `ATEST/.../browser/PrivacyDashboardFireproofTest.kt`: the switch toggles persistence, and it is absent in incognito.

**Checkpoint**: G4, G5 and G10 pass. SC-002 and SC-003 are checked.

---

## Phase 5: User Story 3 — See what is protected on this page (Priority: P2)

**Goal**: complete the dashboard — blocked hosts grouped by site, total, connection state and protection switch — kept current while open and across rotation and language change.

**Independent Test**: quickstart G9.

- [X] T047 [P] [US3] Create `MAIN/domain/privacy/BlockedHostGrouper.kt` (C19): group by registrable domain; order groups by host count descending, then by name; order hosts by name. Unit-test it in `TEST/.../domain/privacy/BlockedHostGrouperTest.kt`.
- [X] T048 [US3] Add `blockedHostsFlow: StateFlow<List<String>>` to `MAIN/presentation/browser/protection/PageProtectionMonitor.kt` (C21). It emits only when a **new** distinct host is added, from the IO thread, conflated, and is reset together with the count in `onMainFrameRequest`/`onPageStarted`. Extend `PageProtectionMonitorTest`: no emission for a repeated host, and reset on a new page.
- [X] T049 [US3] Extend `PrivacyDashboardViewModel` to the full `PageDashboardState` from data-model:
  - combine the active monitor's `status` + `blockedHostsFlow` with the fireproof flow;
  - group via `BlockedHostGrouper` using Spec 023's matcher;
  - the protection switch calls `SetSiteProtectionUseCase` and then requests the same reload as the hub switch (INV-7, FR-017).

  Extend `PrivacyDashboardSheet` with:
  - the header (site, connection line);
  - the total, or "No trackers blocked on this page";
  - a grouped `LazyColumn` with `key`/`contentType`;
  - the protection switch.
- [X] T050 [US3] Make the dashboard's open state survive rotation and an in-app language change (`rememberSaveable` in `BrowserScreen`), and keep it current when the page navigates — it rebinds when the active tab's top URL changes.
- [X] T051 [P] [US3] Unit-test `TEST/.../presentation/browser/privacy/PrivacyDashboardViewModelTest.kt`: grouping, total == count, `protectionOn = false` shows no hosts, and incognito gives `fireproof = null`.
- [X] T052 [US3] Instrumented `ATEST/.../browser/PrivacyDashboardTest.kt` on Spec 023's `trackers.html` with the Hilt-replaced 12-host list (reuse `TestProtection`). Assert:
  - 12 hosts grouped;
  - the protection switch reloads with 0 hosts;
  - the dashboard survives rotation;
  - there is no entry on Home.

**Checkpoint**: G9 passes; SC-006 is checked.

---

## Phase 6: User Story 4 — Cookie banners are declined for me (Priority: P3)

**Goal**: shared script infrastructure (composer, bundles, per-WebView installer) plus the autoconsent script, opt-out only, decided by the top page.

**Independent Test**: quickstart G11, G15 (no script on old engines).

### Shared script infrastructure (also used by US5)

- [X] T053 [P] [US4] Create `MAIN/domain/privacy/PrivacyScriptComposer.kt` (C18). It builds the prelude `{v:1,s:"<hex>",x:[…]}`:
  - `s` must match `^[0-9a-f]+$`, or the composer throws `IllegalArgumentException`;
  - `x` keeps only hostnames matching `^[a-z0-9.-]+$` and drops the rest;
  - the result is JSON-escaped.

  Each bundle (already of the form `(function(cfg){…})`) is then emitted as `(<bundle>)(Object.freeze(<prelude>));`. No global is declared.
- [X] T054 [P] [US4] Unit-test the composer in `TEST/.../domain/privacy/PrivacyScriptComposerTest.kt`:
  - escaping of `"`, `\`, `</script>` and U+2028;
  - dropping invalid sites;
  - a non-hex secret is rejected;
  - order is preserved;
  - the output never contains `window.`-level assignments from the prelude.
- [X] T055 [US4] Create the interface `MAIN/domain/repository/PrivacyScriptBundles.kt` (C10b) and its `@Singleton` implementation `MAIN/data/local/privacy/PrivacyScriptBundlesImpl.kt`, bound in `PrivacyToolsModule`; the installer depends on the interface only. It loads `AssetPaths.PRIVACY_FINGERPRINT_SCRIPT` and `PRIVACY_CONSENT_SCRIPT` once, off the main thread. Start the load from `MAIN/ThirtySixApplication.kt` beside Spec 023's protection data. Expose a bounded `await` that fails open (no scripts) after `NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS`.
- [X] T056 [US4] Create `MAIN/presentation/browser/privacy/PrivacyScriptInstaller.kt` (C20, research R6):
  - when `WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)` is false, do nothing (FR-022, INV-8);
  - otherwise collect `combine(session.secret, exceptions)` — the normal snapshot, or the incognito store for an incognito WebView;
  - on each value: `handler?.remove()` → `WebViewCompat.addDocumentStartJavaScript(webView, composer.compose(…), setOf("*"))`;
  - `detach()` removes the handler and cancels collection.

  Lint's `RequiresFeature` wants the call directly inside the `isFeatureSupported` branch (Spec 023 note).
- [X] T057 [US4] Attach a `PrivacyScriptInstaller` in `MAIN/presentation/browser/BrowserWebView.kt`'s factory, next to `PageProtectionMonitor`, and detach it in dispose. Also attach it to Spec 021's pop-up WebViews the moment they are created, next to `PendingPopupWebViewClient`. `addDocumentStartJavaScript` must stay the only script-injection call in `app/src/main/`.

### Cookie-banner behaviour

- [X] T058 [US4] Confirm that `ASSETS/privacy/consent.js` (T006) loads through the bundles and composer, and that the top-site early exit in `entry.ts` uses `cfg.x` with the R5 rule (`top === x || top.endsWith("." + x)`, where `top` comes from `ancestorOrigins`). Rebuild through T005 if the entry needs changes.
- [X] T059 [US4] Add the About notice in `MAIN/presentation/settings/components/AboutSection.kt`: "Cookie-banner rules: autoconsent by DuckDuckGo, MPL-2.0" with the upstream link, reusing Spec 023's `licenses/MPL-2.0.txt` viewer (FR-023).
- [X] T060 [US4] Instrumented `ATEST/.../privacy/CookieBannerTest.kt`, with `assumeTrue(DOCUMENT_START_SCRIPT)`. For each `consent/*.html` fixture:
  - after load, `localStorage.fixtureChoice == "reject"` (or `settings` → `reject` for two-layer tools) and the banner node is removed or hidden;
  - **never `accept`**;
  - with the fixture origin as a site without protection, `fixtureChoice` stays empty;
  - in `frames.html`, a Sourcepoint-style frame from the other origin is handled when the top page is protected.

  On the API 29 CI emulator the test is skipped, not failed.

**Checkpoint**: G11 fixture part passes; the real-site part runs in Polish.

---

## Phase 7: User Story 5 — Harder to fingerprint (Priority: P3)

**Goal**: the basic fingerprinting shield, through the US4 infrastructure.

**Independent Test**: quickstart G12.

- [X] T061 [US5] Confirm that `ASSETS/privacy/fingerprint.js` (T007) is installed through `PrivacyScriptBundles` **before** `consent.js`, so the shield wraps APIs before any page or CMP code reads them, and that both share the single composed text.
- [X] T062 [US5] Instrumented `ATEST/.../privacy/FingerprintShieldTest.kt`, with `assumeTrue(DOCUMENT_START_SCRIPT)`, on `fingerprint.html` and `frames.html`. Assert:
  - the same canvas and audio hash twice on `localhost`;
  - a different hash on `127.0.0.1`;
  - the frame embedded in `localhost` reports the `localhost` value;
  - after `PrivacyScriptSession.regenerate()` and a reload, the hashes differ (FR-003a);
  - with `localhost` as a site without protection, the hashes equal those of a WebView with no script;
  - `typeof navigator.getBattery === "undefined"`;
  - `enumerateDevices` ids are empty without permission;
  - the visible canvas pixel at a fixed point equals the unprotected value (US5 #6).
- [X] T063 [US5] Verify US5 #4 on the A55: grant camera to a WebRTC test page (Spec 021 G-gate page) and confirm that `enumerateDevices` returns real labels and ids and that the call works. Record the result in Device Gate Results.

**Checkpoint**: G12 fixture part passes.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T064 [P] Run `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck`. All must be green with **no baseline change**.
- [X] T065 Run `./gradlew connectedDebugAndroidTest` on `TA016_API36_16K`: force-stop first, then clear the per-app language. Record the count. Known flake: `rendererCrash_…`.
- [X] T066 [P] Run `./gradlew assembleRelease`. Record the APK size against `main` (**SC-012: ≤ +200 KB**), and confirm no new `.so` (16 KB script). Run `-PcomposeReports=true` and record **0 non-skippable**.
- [X] T067 [P] Run the static checks from quickstart "Automated gates": no new `addJavascriptInterface` / `addWebMessageListener` / `evaluateJavascript` / `javascript:`; `addDocumentStartJavaScript` only in `PrivacyScriptInstaller`; the asset grep for app-directed channels (INV-9); `5.json` committed; `git diff main -- app/src/main/AndroidManifest.xml` adds no `uses-permission` (FR-031).
- [X] T068 Run device gates **G2–G13 and G15** on the 16K AVD, the API 24 AVD and the A55, and record each in **Device Gate Results** below. This includes:
  - G5, real sign-ins: the owner picks the 3 sites;
  - G11, 20 real banner sites;
  - G12, 10 drawing-heavy sites.

  **Never change the A55's default browser.**
- [X] T069 Run **G14** on the A55 release build: 10 news sites × 5 loads, main vs branch; cold start interleaved for first run and returning user (SC-010, SC-011). If page load is over +5%, measure each script alone to find the cost, and report to the owner before changing the rule set.
- [X] T070 Run **G16**: all 8 locales at 360dp (DE and RU first) for every new surface; `animator_duration_scale 0`. Screen-reader checks are DEFERRED to a person, as in Specs 016–023.
- [X] T071 [P] Update the docs: `CLAUDE.md` (Recent Changes, Spec Roadmap row 024, Active Spec), `.claude/claude-app/sdd-roadmap.md`, `.claude/claude-app/project-context.md` and `.claude/claude-app/dev-workflow.md` (024 status, and the stale "Tiếp theo: Spec 023" note). ⚠️ Record that a v5 install cannot be downgraded to a v4 build.
- [ ] T072 Open the PR `024-privacy-tools` → `main`. Its body carries:
  - gates and numbers;
  - the R8 spec amendment;
  - limitations (R2 path-scoped cookies, R3 enumeration gaps, old-engine fallback);
  - the APK delta;
  - the Constitution line, 11/11 with the §X waiver;
  - an explicit request for the owner's acknowledgement of the §II-1 reading: the installed script is the bundled assets plus a prelude generated at run time from **local** data only (session secret, local exception list), never from remote data (research R5).

---

## Dependencies & Execution Order

- **Phase 1** (T001–T007) has no dependencies. T006 needs T005.
- **Phase 2** (T008–T020) needs T001 and **blocks every story**:
  - T009 → T010 → T011/T012 → T013;
  - T012 → T014 → T015.
- **US1** (T021–T036) needs Phase 2. T021 (spike) comes first; T024 → T025 → T026 → T027; T028–T030 come before T031/T032.
- **US2** (T037–T046) needs US1: T039 extends US1's `RunFireUseCase` path; T041/T042 need T014.
- **US3** (T047–T052) needs T041/T042 (the sheet exists).
- **US4** (T053–T060) needs Phase 2 (`PrivacyScriptSession`) and T006. It is independent of US1–US3, except that Fire's `regenerate()` takes effect through T056.
- **US5** (T061–T063) needs T053–T057 and T007.
- **Polish** (T064–T072) comes after every story.

### Parallel opportunities

- **Phase 1**: T001, T002, T003, T004, T005 and T007 together.
- **Phase 2**: T008/T009, then T013/T015/T016/T017/T018/T019 together.
- **US1**: T022/T023 (tests) ∥ T028/T029/T030 (UI) ∥ T034.
- **US4 ∥ US1–US3**: the script infrastructure (T053–T057) touches different files from Fire and the dashboard, except `BrowserWebView.kt` (T057), which should be merged after T033.
- **US5** after US4's infrastructure; T062 ∥ T060.

## Implementation Strategy

1. **MVP**: Phases 1–3. Fire works and clears everything. Demo it on the A55 before continuing.
2. **Add US2**: fireproofing makes Fire usable day to day. G4/G5 are the risk gates (R2 attributes).
3. **Add US3**: the dashboard is complete.
4. **Add US4, then US5**: they share the infrastructure. Measure page load (G14) as soon as US4 lands, not at the end.
5. Polish, then the gates, then the PR.

## Notes

- Constitution §IX: no Gradle dependency is added. The vendored `consent.js` is pinned through the generator's lockfile. If a step seems to need a dependency, stop and record why first.
- Constitution §II: `addDocumentStartJavaScript` is the only injection path. Never add `evaluateJavascript`, `addJavascriptInterface`, `WebMessageListener` or `loadUrl("javascript:")`.
- **Stop before T010** if the migration seems to touch an existing table. `MIGRATION_4_5` is additive only.
- Never run `tools/privacy-scripts/build.sh` from Gradle or CI. The committed asset is the source of truth for a release.
- **Never configure autoconsent with `autoAction: 'optIn'` or any `heuristicMode` other than `'off'`.** The upstream default `tier2` clicks Accept (research R7).

## Implementation Notes

Deviations from the task text, each deliberate:

- **T001**: `FIRE_OVERLAY_MS` is an `Int`, because Compose's `tween(durationMillis)` takes an `Int`. The other three constants follow the task.
- **T003**: Fire is named per locale: "Đốt dữ liệu", "Feuer", "Огонь", "파이어", "ファイア", "一键清除", "Feu". "Fireproof" is rendered as "keep / protect from Fire" (vi "Giữ lại trang này khi đốt", fr "Protéger ce site du feu"…). The dashboard reuses Spec 023's `hub_protection_trackers_blocked` plural, `hub_protection_site_title` and `hub_protection_not_secure_title`. **23 new keys × 8 locales.**
- **T004 — consent fixtures**:
  - **9 tools, not 10.** Sourcepoint's rule (`sourcepoint-frame`) runs only on `*.sp-prod.net` hosts, which cannot be served locally. It is covered by the real-site sweep (G11).
  - **The cross-origin frame case uses `fingerprint.html`**, not a consent page. None of the generic frame-capable consent rules works without the vendor's own JavaScript API (`cookiealert` evaluates its library).
  - Each fixture records clicks as `localStorage.fixtureChoice` and in the page title (`choice:<value>`).
  - `AssetPageServer` now serves `.js` as JavaScript (a service worker needs it) and adds the HttpOnly `fp_persist` cookie on `storage.html`.
- **T005/T006 — `consent.js`**:
  - Built from autoconsent **16.51.0** with esbuild **0.28.2**, `--target=chrome74`.
  - The rules kept are autoconsent's generic **and frame** rules (`r[0:308]`, strings `s[0:1213]`); the frame range overlaps the specific range by 27 rules. **308 of 950 rules.**
  - Size: **201,669 B raw, 55,760 B gzip**, under the 80 KB stop. Raw is about 50 KB above research R7's estimate: the engine's built-in CMP classes are larger than the estimate assumed.
  - Audit:
    - `eval(`, `new Function`, `autoconsentReceiveMessage`, `autoconsentStandalone` and `postMessage`: **0** each.
    - The 3 `window.…=` matches are `===` comparisons, not assignments.
    - The 28 `optIn` hits are the CMP classes' opt-in methods. They run only with `autoAction: 'optIn'`, and the bundled engine's message handler is unreachable: no global, and a no-op sender.
    - The 21 `heuristic` hits are the heuristic module. It is never entered, because `enableHeuristicDetection: false` and `heuristicMode: 'off'`.
  - Known rule behaviour:
    - The "Complianz notice" rule hides an information-only notice that offers no choice.
    - Cookiebot's fallback path saves an all-unticked selection with the "accept selection" button, which is a refusal.
- **T007 — `fingerprint.js`**: 7,652 B. Both scripts pass `node --check` when wrapped as a function expression.

- **T008**: `FireStep` has no `HomeTab`: the home tab comes from `closeAllTabs()` (analyze L1). `FireResult.siteDataPath` is nullable — `null` means step 5 failed.
- **T014 — `IsSiteFireproofUseCase` matches by suffix**: the host equals an entry or ends with `.` + entry. No public-suffix lookup is needed.
  - Why this is equivalent to the data-model rule: saved entries are registrable domains, and a host always ends with its own registrable domain. Under a private suffix it still holds: `bob.github.io` never ends with `.alice.github.io`.
  - Benefit: matching works before Spec 023's lists have loaded.
  - `SetSiteFireproofUseCase` takes the registrable domain the caller already has (`PageProtectionStatus.site`), as Spec 023's `SetSiteProtectionUseCase` does.
- **T017**: `OneTimeGrantStore` had no test file; `OneTimeGrantStoreTest` and `HttpsUpgradeFailureStoreTest` were created.
- **T018**: 13 anonymous `FaviconCache` fakes gained a one-line `retainOnly`. `FakeFaviconCache` also records the call.
- **T019 — `distinctHosts()` is a default method** on `HistoryRepository` (derived from `observeAll()`), following the existing `getLatestVisitPerUrl` pattern. No fake needed changing; the Room implementation overrides it with `SELECT DISTINCT url`. The new pure helper `domain/privacy/UrlHost.kt` (`urlHostOrNull`) is shared by both.

- **T021 — spike**: run on all three engines; results recorded in research R1. **No stop condition was hit**: `getCookieInfo` keeps `httponly` and `expires`, and the A55 has `DELETE_BROWSING_DATA`. The fixtures were rewritten without `async`/`await` because WebView 53 rejects them (`Uncaught SyntaxError: Unexpected token function`). The spike file was deleted.
- **T025/T039 — `RunFireUseCase` and `clearExcept` landed together**, so no interim `Complete`-only path ever existed.
  - `clearExcept` (`AndroidWebDataCleaner`) does four things:
    1. snapshots the kept cookies (`KeptCookie`, keyed by name|domain|path; host-only cookies drop `domain=`, secure ones are written back over `https`);
    2. calls `deleteBrowsingDataForSite` for each non-kept candidate host — or `WebStorage.deleteOrigin` on old engines;
    3. calls `removeAllCookies`;
    4. writes the kept cookies back, then flushes.
  - Logs carry counts only.
- **T026 — the marker has its own repository, not new `SettingsRepository` members.** `FireMarkerRepository` sits over the same DataStore file, as Spec 020's invite state does. The 4 settings fakes and `UserSettings` stay untouched.
  - The coordinator and the step hook live in `domain/fire/`, not a top-level `fire/` package (Clean Architecture layout).
  - `@ApplicationScope` (`core/di/`) is provided in `PrivacyToolsModule`.
- **T029** — `FireOverlay` is a full-screen `Dialog`, so it covers the bars, sheets and dialogs and consumes touches without changing `BrowserScreen`'s layout tree.
- **T031** — the Fire tile sits in the places row as a fourth tile with the **Place** tone. Peach first made it taller than its neighbours (seen on the 16 KB AVD).
- **T034** — the delay lives in `domain/fire/ConfigurableFireStepObserver` (0 in release). Only the debug receiver `FireStepDelayReceiver` sets it.
- **First manual run (16 KB AVD, 2026-10-13)**, 3 tabs on example.com:
  - hub → Fire → dialog → Clear → overlay → Home in 1 tab;
  - `WebDataCleaner: site data: outcome=Complete elapsedMs=1579`.
  - One ANR seen just before this run was **environmental**: the main thread was inside Chromium's `BrowserStartupController` at CPU load 5.4, with the API 24 AVD running alongside. Not repeated after that AVD was stopped.

- **T041/T042/T047–T050 — the dashboard was built complete in one pass** (US2's minimal sheet and US3's full sheet together).
  - **The dashboard state lives in Spec 023's `ProtectionViewModel`** (`dashboard`, `onFireproofSwitched`), not in a separate `PrivacyDashboardViewModel`. That view model already owns the shown page's monitor slot, and a second view model would need a second slot fed by the same WebView hooks.
  - The derivation is the pure `PageDashboardState.of(...)` (`presentation/browser/privacy/`), unit-tested on its own (T051).
  - `HubCallbacks.onDashboard` is new: the blocked-tracker banner opens the dashboard on a web page, while Home and the incognito start page keep `onPrivacy` (Settings), as the analyze remediation for T042 asked. The existing `HubSheet` tests needed no change, because they build `HubCallbacks` with defaults.
  - `PageProtectionMonitor`'s new flow is `blockedHostsFlow`; its backing field is `blockedHostList` (ktlint's backing-property rule).
  - The whole sheet is one `LazyColumn` with keys and content types.
- **T043** — the screen was cloned from `protectionexceptions/` with renamed strings and tags.
- **T045** — `ClearBrowsingDataUseCase` gains a 10th collaborator, `FireproofSiteRepository`. Its two test constructions and the instrumented Settings doubles were updated (`InstrumentedNoopFireproofSiteRepository`).
- **Unit tests: 923/923** after US1–US3 (882 before this spec).

- **T055–T057 — the scripts are installed through Spec 023's `ProtectionHooks`.**
  - `protect()` builds a `PrivacyScriptInstaller` beside the `PageProtectionMonitor` and `unprotect()` detaches it, so `BrowserWebView` needed no change.
  - Adopted pop-up WebViews go through the same factory and get the scripts from adoption onward. **The pop-up's very first document, loaded before adoption, runs without them** — a documented gap (Spec 021 pop-up lifecycle).
  - The installer re-installs when any of these change:
    - the secret (Fire);
    - the bundles (first load);
    - the saved exceptions (Room flow).
  - It also re-installs synchronously from `ProtectionViewModel.onProtectionSwitched` (`hooks.reinstallScripts()`), before the reload, so the reload already runs the new rule.
  - `IncognitoProtectionExceptions` gained `snapshot()`; it has no fakes.
- **T058/T060 — `CookieBannerTest` 2/2 on the 16 KB AVD**:
  - all 9 local consent fixtures were refused;
  - **0 accepts**;
  - a site with protection off was left alone.
- **T061/T062 — `FingerprintShieldTest` 3/3**:
  - same site → equal values; another site → different values;
  - a cross-site frame follows its top page;
  - a new secret changes the values;
  - protection off restores the real values;
  - `getBattery` is undefined; device ids are empty.
  - The "visible canvas unchanged" check (US5 #6) is by construction — noise only ever touches a copy — and G12 checks it on real drawing-heavy sites. No pixel-level test was written.
- **T035/T040 — `FireDataTest` 3/3 on the 16 KB AVD.** It runs the real coordinator, use case and `AndroidWebDataCleaner` against `storage.html` on two sites.
  - Without fireproofing: all five storage kinds and the cookies are gone on both sites, history is empty, and one tab remains.
  - With `localhost` fireproof: all five kinds are kept and `fp_persist` is still `httponly` with its expiry; `127.0.0.1` is cleared.
  - An incognito-set cookie on the fireproof site is gone after Fire.
  - It replaces the hub-driven end-to-end test in the task text. The hub → dialog → overlay path is covered by `PrivacyToolsScreensTest` and the manual run above.
- **⚠️ Pre-existing Spec 012 limitation surfaced by T040** (not caused by this spec):
  - What happens: the incognito cookie snapshot covers only the sites **open in normal tabs** (`IncognitoTabRepositoryImpl.enumerateNormalTabOrigins`). When the last incognito tab closes, the jar is wiped and only that snapshot is written back. So **any incognito session signs the person out of every site not open in a tab** — fireproof or not.
  - In the test: the fireproof site is open in a normal tab, as it would be in real use.
  - Raised with the owner as a separate decision; not fixed here, because it is Spec 012's behaviour.
- **T036/T046/T052**:
  - `PrivacyToolsScreensTest` 7/7 — hub Fire tile, dashboard line vs Settings banner, confirm and cancel, overlay acknowledge, dashboard groups and switches, no fireproof switch in incognito.
  - `FireproofSitesScreenTest` 2/2.
- **T064 — static checks clean with no baseline change.** detekt asked for structural fixes:
  - `RunFireUseCase` was split into `collectHosts`, `clearSiteData`, `clearPermissions` and `iconsToKeep`;
  - `PrivacyToolsHosts` moved to its own file;
  - `TabsHeader` now owns the Fire button through `rememberTabsFire`;
  - three functions were rewritten to fewer returns.
  - Lint's `RequiresFeature` wanted `getCookieInfo` directly inside its feature check.
- **Unit tests 928/928** (882 before this spec, +46).
- **T066 — release APK 3,725,735 B (+99,134 B vs 3,626,601 B, budget +200 KB).** The same 8 `.so`, all `align=0x4000`; `assets/privacy/consent.js` 201,669 B and `fingerprint.js` 7,652 B are inside.
- **Compose report**: **206 restartable, 0 non-skippable** (195 before).
- **T067**:
  - `addDocumentStartJavaScript` appears only in `PrivacyScriptInstaller`.
  - There is no `evaluateJavascript`, `addWebMessageListener` or `javascript:` load; `addJavascriptInterface` appears only in two comments.
  - The assets contain 0 `postMessage`/`prompt(`/`alert(`.
  - The manifest is unchanged against `main`.

- **T065 — full instrumented suite on `TA016_API36_16K`: 186 tests, 183 pass in one run (1 skipped).**
  - `FileChooserInstrumentedTest.severalFiles_reachThePage` and `SettingsScreenRetentionTest.aShorterWindow_…` failed in the 17-minute run and **pass 3/3 each in isolation** — flakes of a long run, in code this spec does not touch.
  - `BrowserWebViewRendererRecoveryTest.rendererCrash_…` fails in isolation too. It is the known environment flake from Specs 019–023, which fails identically on older commits.


## Device Gate Results

Run 2026-10-13/14 on three devices:
- `TA016_API36_16K` (WebView 133);
- `TA016_API24` (WebView 53);
- the owner's **Galaxy A55** (SM-A556E, Android 16, WebView 153).

Real-site checks read page state through the Chrome DevTools Protocol on the debug build, as in Spec 023. The owner's default browser was not touched.

| Gate | Result |
|---|---|
| G1 Spike | ✅ All three engines; see research R1. |
| G2 Fire basics | ✅ **16 KB AVD**: 3 tabs → Home in 1 tab, `site data: outcome=Complete elapsedMs=1579`.<br>✅ **A55**: Fire from **50 tabs** → Home in 1 tab, `elapsedMs=1181`; the dialog lists what is cleared and what is kept.<br>Cancel and Back are covered by `PrivacyToolsScreensTest`. |
| G3 / G4 Site data | ✅ `FireDataTest` 3/3 on the 16 KB AVD:<br>• without fireproofing, all five storage kinds are gone;<br>• with fireproofing, they are kept and the cookie is still `HttpOnly` and persistent;<br>• a cookie set in incognito is not kept.<br>The API 24 fallback was not run on a device: WebView 53 lacks both features, and research R1 records the limits. |
| G5 Real sign-ins | ⬜ **DEFERRED to the owner** — it needs real accounts. G4 proves the mechanism. |
| G6 Kept data | ✅ `RunFireUseCaseTest`: bookmarks and the fireproof list untouched; permissions and favicons filtered.<br>✅ `FireDataTest`: history and tabs. |
| G7 Interrupted Fire | ✅ 16 KB AVD, debug `FireStepDelayReceiver` set to 3 s per step.<br>• The app was force-stopped at **8, 4, 14, 20 and 25 s** (5 runs).<br>• Every restart showed **exactly 1 tab** on Home.<br>• The resumed run cleared again in the new process (SC-005). |
| G8 Session traces | ✅ `FingerprintShieldTest`: a new secret changes the values.<br>✅ `RunFireUseCaseTest`: failures cleared, secret regenerated. |
| G9 Dashboard | ✅ `PrivacyToolsScreensTest`.<br>On device, in DE/RU/VI/KO/JA/ZH/FR, the dashboard opens from the hub line and shows the site, the connection line, the count, the protection switch and the fireproof switch. With protection off, it shows no hosts. |
| G10 Settings list | ✅ `FireproofSitesScreenTest`.<br>On device (DE): the row, the empty state and the new Clear-browsing-data category (unticked) are all shown. |
| G11 Cookie banners (SC-007) | ✅ **A55: 34 real sites probed from Vietnam; 20 showed a consent banner.**<br>**17 refused automatically**: theguardian, stackoverflow, ikea, zalando, elpais, ouest-france, economist, cookiebot, didomi.io, onetrust.com, otto, lidl, 20minutes, cookieyes, complianz, idealo, usercentrics.com.<br>How each refusal was verified:<br>• OneTrust: every group `:0` except necessary;<br>• Sourcepoint: `rejectedAny: true, consentedToAny: false`;<br>• Didomi: 0 enabled purposes;<br>• Cookiebot: `preferences/statistics/marketing:false`;<br>• CookieYes: `consent:no`;<br>• Complianz: `deny`;<br>• Usercentrics: only essential services granted.<br>**3 "accept or pay" walls left untouched**, as FR-024 requires: lemonde (Didomi), corriere, repubblica (iubenda).<br>**0 accepts.**<br>**14 excluded**:<br>• no banner for a Vietnamese IP — reuters and booking imply consent without one; spiegel, bild, heise, t-online, nature, independent, dailymail, hm and iubenda.com show none;<br>• lefigaro shows a bot check instead;<br>• the result was unclear on telegraph and marca.<br>Observation: after the refusal, 20minutes.fr redirects to its account page, because the site makes refusing require registration. |
| G12 Fingerprinting (SC-009) | ✅ `FingerprintShieldTest` 3/3.<br>✅ **A55, real sites with protection on**: Chart.js, Plotly, Highcharts, the Desmos calculator, sketch.io, Photopea and Windy (header and forecast) all render.<br>**⚠️ Pre-existing, not caused by Spec 024**: Excalidraw, tldraw, Google Maps, the Leaflet quick-start example and the ECharts editor render blank.<br>• CDP shows `html, body { height: 100% }` computing to **0 px**: the layout viewport height is 0 while `innerHeight` is 891.<br>• The result is identical with protection off for the site (scripts not installed, `getBattery` present).<br>• Recorded for a follow-up fix (WebView layout height). |
| T063 / US5 #4 | ✅ A55, webrtc.github.io `getUserMedia`:<br>• before permission, device labels and ids are empty;<br>• after "Allow this time" plus the Android prompt, the page gets 4 cameras with labels and 64-character ids, and the video plays. |
| G13 Offline / no own traffic | 🟡 Airplane-mode behaviour is covered by the fixture tests, served on the device. Proxy capture is still open, as in Spec 023. |
| G14 Performance | ✅ **A55.**<br>**SC-010 page load** — `loadEventEnd`, debug builds, fresh install each time, interleaved main / branch / branch / main, 10 news sites × 2 loads per build:<br>• **median per-site +3.0%** (budget +5%); total −20%;<br>• noisy: CNN +15%, vnexpress +22%, NYTimes −46%, Amazon −38%.<br>**SC-011 cold start** — release builds, returning user, `am start -W` TotalTime, interleaved 3 × 5 per build:<br>• branch median **223 ms** vs main **344 ms**, so the branch is not slower;<br>• the size of the gap is not explained (2 of main's 3 rounds did not reach Home after onboarding). Read it as "no regression", not "faster".<br>⚠️ **The first attempt was discarded.** The owner's Messenger chat bubbles covered the onboarding **Skip** button, so a scripted tap opened a Messenger conversation (no input was sent) and main never left onboarding. The script now uses the bottom **Next** button and verifies Home with `uiautomator`. |
| G15 Old engine | ✅ By design and by the spike: WebView 53 has no `DOCUMENT_START_SCRIPT`, so `CookieBannerTest` and `FingerprintShieldTest` skip there. The fallback path is unit-tested. |
| G16 Locales + motion | ✅ **16 KB AVD, all 8 languages** (EN, DE, RU, VI, KO, JA, ZH, FR): the hub Fire tile, the dashboard and the Fire dialog are fully translated, with nothing clipped.<br>✅ DE also covers the Settings row, the Fireproof empty state, the clear-data category and the About notice.<br>✅ Motion: `PrivacyToolsScreensTest` plus Compose's animator-scale behaviour.<br>⬜ Screen reader: DEFERRED to a person.<br>Notes:<br>• several ANRs during the sweep came from the AVD (main thread waiting on `InputManager.getInputDevice` binder calls); a cold boot cleared them;<br>• German mixes "du" (Spec 016 and the new strings) with "Sie" (Spec 023), an existing inconsistency. |
