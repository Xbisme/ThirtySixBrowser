---
description: "Task list for Spec 023 — Privacy Protection"
---

# Tasks: Privacy Protection

**Input**: Design documents from `specs/023-privacy-protection/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R14), data-model.md, contracts/ProtectionContracts.kt, quickstart.md

**Tests**: REQUIRED by Constitution §VI, which names this spec's engines: host blocklist matcher, HTTPS upgrade, URL-parameter stripping and the per-site exception list. Each engine gets unit tests. The Room migration gets a migration test. Blocking, fallback and GPC get instrumented tests on local fixtures (research R14).

**Organization**: by user story.
- US1 (tracker blocking) and US2 (count + per-site switch) are P1.
- US3 (HTTPS upgrade) and US4 (tracking parameters) are P2.
- US5 (GPC) and US6 (referrer) are P3.
- **US1 is the MVP.**

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`
- `ASSETS` = `app/src/main/assets`
- `FIX` = `specs/023-privacy-protection/fixtures`

---

## Phase 1: Setup

- [X] T001 [P] Create `MAIN/core/constants/AssetPaths.kt`. It holds `PROTECTION_TRACKER_HOSTS = "protection/tracker_hosts.txt"`, `PROTECTION_PUBLIC_SUFFIXES = "protection/public_suffix_list.dat"`, `PROTECTION_TRACKING_PARAMS = "protection/tracking_params.txt"`, `LICENSE_CC_BY_SA_3 = "licenses/CC-BY-SA-3.0.txt"` and `LICENSE_MPL_2 = "licenses/MPL-2.0.txt"`.
- [X] T002 [P] Create `MAIN/core/constants/NetworkTimeouts.kt` with `PROTECTION_DATA_LOAD_TIMEOUT_MS = 3_000L` (research R10: a bounded wait that fails open).
- [X] T003 [P] Extend `MAIN/core/constants/UrlConstants.kt`:
  - `GPC_HEADER_NAME = "Sec-GPC"` and `GPC_HEADER_VALUE = "1"`;
  - `BLOCKED_RESPONSE_STATUS = 403`, `BLOCKED_RESPONSE_REASON = "Blocked"` and `BLOCKED_RESPONSE_MIME = "text/plain"`;
  - `SCHEME_HTTP = "http"` and `SCHEME_HTTPS = "https"`, if not already present.

  Also add `REGISTRABLE_DOMAIN_CACHE_SIZE = 2_048` to `MAIN/core/constants/BrowserLimits.kt`.
- [X] T004 Write the generator `tools/protection-lists/build_tracker_hosts.py` and a `tools/protection-lists/README.md` that explains how to refresh the list. The script runs by hand only — never from Gradle, never at runtime. It downloads EasyPrivacy and EasyList and applies research R1's rules:
  - keep `||host^` rules, with or without type options;
  - drop rules with `domain=`, `popup`, a path, `@@`, cosmetic rules, and the CNAME and adult sections;
  - drop any host on the never-block set from research R3;
  - reduce the EasyList ad-server hosts until the file fits the ≤ 300 KB gzip target (research R9).

  It writes `ASSETS/protection/tracker_hosts.txt` with this header: sources, generation date, "CC BY-SA 3.0, The EasyList authors (https://easylist.to/)", "modified: host-level subset".
- [X] T005 Run T004 and commit `ASSETS/protection/tracker_hosts.txt`. Record the host count and the gzip size in this file's Notes. **Stop and report if the gzip size is over 300 KB.**
- [X] T006 [P] Add `ASSETS/protection/public_suffix_list.dat`, the upstream file from https://publicsuffix.org/list/ with its MPL-2.0 header unchanged. Record the upstream commit/date in Notes.
- [X] T007 [P] Create `ASSETS/protection/tracking_params.txt` with research R8's seed list. Put one name per line; a trailing `*` marks a prefix (`utm_*`); lines starting with `#` are comments. **Exclude** `ref`, `source` and `cid`.
- [X] T008 [P] Add the licence texts `ASSETS/licenses/CC-BY-SA-3.0.txt` and `ASSETS/licenses/MPL-2.0.txt`. Confirm in `app/build.gradle.kts` that no `noCompress` entry covers `.txt` or `.dat`, so the assets are stored compressed (research R9).
- [X] T009 [P] Add every string key for the six stories to `RES/values/strings.xml`, and translate them in `RES/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`. Keep "ThirtySix" in Latin script:
  - hub section title;
  - a `<plurals>` for "%d trackers blocked" (including zero);
  - "Protection on/off for this site" (switch label + state descriptions);
  - "Connection not secure";
  - Settings row "Sites without protection" with its summary, screen title, empty-state title and body, remove-site content description, "Remove all" and its confirm dialog;
  - the Clear-data category label + description;
  - About "Open-source notices" heading and the two attribution sentences (licence names and URLs untranslated).
- [X] T010 [P] Create local fixtures, mirrored into `app/src/androidTest/assets/`:
  - `FIX/trackers.html`: 30 requests (img, script, iframe, fetch) to the 12 hosts `tracker-01.test` … `tracker-12.test`, plus 10 requests to same-origin `/ok-N.png`;
  - `FIX/headers.html`: shows the request headers the echo server returns;
  - `FIX/echo_headers.py`: a host-side echo server for G10/G11;
  - `FIX/params.html`: links carrying tracking parameters.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: rule data, the pure matchers, exception storage and the per-WebView monitor that every story plugs into.

### Domain models and pure rules (no Android imports)

- [X] T011 [P] Create `MAIN/domain/model/ProtectionData.kt` with `TrackerHostSet`, `PublicSuffixRules`, `TrackingParamRules` and `ProtectionData`, exactly as contracts C1 defines them. All four are immutable.
- [X] T012 [P] Create `MAIN/domain/model/PageProtectionStatus.kt` (contracts C2, with `NOT_A_WEB_PAGE`) and `MAIN/domain/model/SiteProtectionException.kt` (C3).
- [X] T013 Create `MAIN/domain/protection/PublicSuffixMatcher.kt` implementing data-model rule 1 (contracts C5):
  - lower-case the host;
  - an IP literal or single-label host is its own site;
  - exception rules beat wildcard rules, which beat normal rules; the longest match wins; an unknown TLD falls back to the implicit `*`;
  - a host that *is* a public suffix is its own site.

  Cache results in a bounded, thread-safe map sized `BrowserLimits.REGISTRABLE_DOMAIN_CACHE_SIZE`. Provide `isThirdParty`.
- [X] T014 [P] Write `TEST/domain/protection/PublicSuffixMatcherTest.kt` using a small rule fixture. Cover:
  - `example.com`, `www.example.co.uk`, `alice.github.io` vs `bob.github.io` (the private section);
  - a wildcard (`*.ck`) and an exception (`!www.ck`);
  - IPv4 and IPv6 literals, `localhost`, single-label hosts, a host equal to a suffix, an unknown TLD, upper-case input;
  - cache eviction.
- [X] T015 Create `MAIN/domain/protection/TrackerMatcher.kt` (contracts C6, data-model rule 3). It blocks only when protection is on, the request is third-party, no suffix of the host is on the never-block set, and the host or a parent up to its registrable domain is listed. Put the never-block set from research R3 in a named constant `NEVER_BLOCK_HOSTS` in the same file. Do no allocation beyond suffix iteration.
- [X] T016 [P] Write `TEST/domain/protection/TrackerMatcherTest.kt`. Cover:
  - a listed host and a listed parent;
  - the first-party case (`tracker.example` page loading `cdn.tracker.example`);
  - the never-block set (`accounts.google.com` under a listed `google.com`, if any);
  - `protectionOn = false`;
  - an unlisted host;
  - case-insensitivity.
- [X] T017 [P] Create `MAIN/domain/protection/TrackingParamStripper.kt` (contracts C7, data-model rule 4). Work on the raw query: compare names percent-decoded, keep the other raw pairs byte-identical and in order, keep the fragment, drop an emptied `?`, touch only http/https, and return the same instance when nothing changes.
- [X] T018 [P] Write `TEST/domain/protection/TrackingParamStripperTest.kt` with SC-004's 10 cases. Include:
  - mixed parameters with a fragment;
  - only tracking parameters → no `?`;
  - a `utm_*` prefix;
  - a percent-encoded kept value staying byte-identical;
  - a repeated kept key;
  - a tracking name inside the fragment left alone;
  - `ref=` kept;
  - a non-http URL untouched;
  - an empty value `?fbclid=`.
- [X] T019 [P] Create `MAIN/domain/protection/HttpsUpgradePolicy.kt` (contracts C8, data-model rule 5). It returns `null` for a non-http scheme, `localhost`, an IP literal, a single-label host, or a host in `failedHosts`. Port, path, query and fragment are preserved.
- [X] T020 [P] Write `TEST/domain/protection/HttpsUpgradePolicyTest.kt`. Cover a port, a query + fragment, IPv4/IPv6, `localhost`, `router`, a failed host, an https input and an `ftp:` input.
- [X] T021 Create `MAIN/domain/protection/NavigationPreparer.kt` (contracts C9, data-model rule 6). For an excepted destination site, the URL is returned unchanged. Otherwise it returns `Prepared(url = upgrade(strip(u)) ?: strip(u), upgradedFrom = original-http-or-null)`.
- [X] T022 [P] Write `TEST/domain/protection/NavigationPreparerTest.kt`. Cover strip + upgrade combined, an excepted site in normal and in incognito (the two lookups are separate), a failed host, and the **idempotence** invariant `prepare(prepare(u)) == prepare(u)`.

### Rule data loading

- [X] T023 Create `MAIN/data/local/protection/ProtectionAssetParser.kt` with three tolerant parsers: tracker hosts, PSL (ICANN + private sections; `!` and `*.` rules), and tracking parameters (`*` suffix = prefix). Each skips comments and blank lines, lower-cases hosts, and ignores malformed lines.
- [X] T024 [P] Write `TEST/data/local/protection/ProtectionAssetParserTest.kt` over small inline samples. Also add one test that parses the **real** committed assets from `ASSETS`, asserting a non-zero count, that none of the R3 never-block hosts appears in the tracker set, and that `utm_*` is a prefix.
- [X] T025 Create `MAIN/domain/repository/ProtectionDataRepository.kt` (contracts) and `MAIN/data/local/protection/ProtectionDataSource.kt`, a `@Singleton`. `preload()` starts parsing once on `DispatcherProvider.io`. `awaitData(timeoutMs)` blocks the calling **background** thread up to the timeout and returns `null` on timeout or failure (fail open). Add `currentDataOrNull()`, which never blocks, for main-thread callers (see T035 and T058). The main thread must never call `awaitData`. Implement the repository in `MAIN/data/repository/ProtectionDataRepositoryImpl.kt`. Log the parse time and entry counts once.
- [X] T026 [P] Write `TEST/data/local/protection/ProtectionDataSourceTest.kt`. Cover: concurrent `preload` parses once; `awaitData` before completion waits; a parse failure → `null`; a timeout → `null`.
- [X] T027 Wire `preload()` into `MAIN/ThirtySixApplication.kt` `onCreate`, beside the retention sweep, without waiting on it.

### Exception storage (Room v4) and in-memory stores

- [X] T028 Create `MAIN/data/local/entity/SiteProtectionExceptionEntity.kt`: table `site_protection_exceptions`, `site TEXT PRIMARY KEY` ("Registrable domain, lower-case ASCII (IDN as punycode)"), `added_at INTEGER NOT NULL`, with companion column-name constants. Create `MAIN/data/local/dao/SiteProtectionExceptionDao.kt` as in contracts (`upsert`, `observeAll` ordered `added_at DESC`, `delete`, `deleteAll`).
- [X] T029 Bump `MAIN/data/local/database/AppDatabase.kt` to `SCHEMA_VERSION = 4` and add the entity and DAO. Create `MAIN/data/local/database/migrations/Migration3To4.kt`, an additive `CREATE TABLE` only, and register it in `MAIN/di/DatabaseModule.kt`. Build to export `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/4.json` and commit it.
- [X] T030 [P] Write `TEST/data/local/database/AppDatabaseMigration3To4Test.kt`, using the real `Room.databaseBuilder` pattern from Spec 015 (not `MigrationTestHelper`). Seed v3 rows in `site_permissions` and `history`, migrate, and assert they survive and the new table is empty. Then run `git grep fallbackToDestructiveMigration -- app/src/main/` and confirm it still has zero real call sites.
- [X] T031 Create `MAIN/domain/repository/ProtectionExceptionRepository.kt` (contracts), `MAIN/data/mapper/SiteProtectionExceptionMapper.kt` and `MAIN/data/repository/ProtectionExceptionRepositoryImpl.kt`. Use the `runIo` helper like `SitePermissionRepositoryImpl`. `currentSites()` reads `MAIN/data/local/protection/ProtectionExceptionSnapshot.kt`, a `@Singleton` that collects `observeAll` into a `StateFlow<Set<String>>` on the application scope.
- [X] T032 [P] Create `MAIN/data/local/protection/IncognitoProtectionExceptionStore.kt` and `MAIN/data/local/protection/HttpsUpgradeFailureStore.kt`. Both are `@Singleton` and in memory only, using a concurrent set. Neither may be persisted (FR-014, FR-020).
- [X] T033 Clear `IncognitoProtectionExceptionStore` in `MAIN/data/repository/IncognitoTabRepositoryImpl.kt`, on the 1 → 0 transition in `closeTab` and in `closeAll`, next to the existing cookie-snapshot restore.
- [X] T034 [P] Write `TEST/data/repository/ProtectionExceptionRepositoryImplTest.kt` (add / remove / clearAll / observe order, snapshot follows Room). Extend `TEST/data/repository/IncognitoTabRepositoryImplTest.kt`: closing the last incognito tab clears the store, and closing one of two does not.
- [X] T035 Create `MAIN/di/ProtectionModule.kt`. It binds both repositories, the stores and the snapshot. It also provides `MAIN/data/local/protection/ProtectionMatchersHolder.kt`, a `@Singleton` that builds `PublicSuffixMatcher`, `TrackerMatcher`, `TrackingParamStripper` and `NavigationPreparer` **once**, after the data loads. Callers use it in two ways:
  - `matchersOrNull()` never blocks; on the main thread, `null` means the data is not loaded yet;
  - `awaitMatchers(timeoutMs)` is for background threads only.

  `NavigationPreparer`'s `isExcepted` is `IsSiteProtectedUseCase` (T035a). Every `@Module` stays in `di/`.
- [X] T035a Create `MAIN/domain/usecase/IsSiteProtectedUseCase.kt`: `operator fun invoke(site: String, isIncognito: Boolean): Boolean`. It is synchronous and safe on any thread. Normal tabs read `ProtectionExceptionRepository.currentSites()`. Incognito tabs read **only** `IncognitoProtectionExceptionStore`, exposed through a small domain interface `IncognitoProtectionExceptions` that the data store implements (FR-014). This use case is the **only** way presentation code learns whether a site is protected (§IV). Add `TEST/domain/usecase/IsSiteProtectedUseCaseTest.kt` covering normal, incognito and the separation between the two.

### Per-WebView monitor

- [X] T036 Create `MAIN/presentation/browser/protection/PageProtectionMonitor.kt` (contracts). Its state per WebView:
  - an `AtomicReference` top URL and top host;
  - `protectionOn`;
  - a concurrent blocked-host set;
  - the pending upgrade;
  - a conflated `MutableStateFlow<PageProtectionStatus>`.

  **Per-page reset happens in `onMainFrameRequest`** (WebView IO thread), which runs before any of the new page's resources. It clears the blocked set and recomputes `protectionOn` with `IsSiteProtectedUseCase`, using the registrable domain of the new top URL and the tab's incognito flag. Same-document changes never reach it (FR-010).

  `onSubresource` uses `ProtectionMatchersHolder.awaitMatchers(NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS)` and `TrackerMatcher`. It returns `WebResourceResponse(BLOCKED_RESPONSE_MIME, "utf-8", BLOCKED_RESPONSE_STATUS, BLOCKED_RESPONSE_REASON, emptyMap(), empty stream)` and adds the host to the blocked set; otherwise it returns `null`. It never touches Room or the main thread.

  Add `blockedHosts(): Set<String>` for tests.
- [X] T037 [P] Write `TEST/presentation/browser/protection/PageProtectionMonitorTest.kt` (Robolectric for `WebResourceResponse`). Cover:
  - a listed third-party host is blocked, and a second request to it counts once;
  - first-party and unlisted hosts pass;
  - `protectionOn = false` passes;
  - `onPageChanged` resets the count;
  - data not loaded / timeout → fail open;
  - the status flow conflates.

**Checkpoint**: rules, data and storage exist; nothing is wired to the WebView yet.

---

## Phase 3: User Story 1 — Trackers are blocked without setup (Priority: P1) 🎯 MVP

**Goal**: third-party requests to listed hosts are refused silently; the page keeps loading (FR-004 – FR-009).
**Independent Test**: `trackers.html` with a Hilt-replaced list → 30 refused, 10 loaded (SC-001), also in airplane mode (SC-011).

- [X] T038 [US1] In `MAIN/presentation/browser/BrowserWebView.kt`, create one `PageProtectionMonitor` per WebView in `buildConfiguredWebView` and pass it to `BrowserWebViewClient`; dispose it with the WebView.
- [X] T039 [US1] Override `shouldInterceptRequest(view, request)` in `BrowserWebViewClient`:
  - for the main frame, call `monitor.onMainFrameRequest(url)` and return `null` (FR-005);
  - otherwise return `monitor.onSubresource(url)`.

  Downloads are untouched (FR-006). The callback must stay on its WebView IO thread with no main-thread hop.
- [X] T040 [US1] Keep the top URL right on history and back/forward restores. Refresh the monitor from `onPageStarted` and `doUpdateVisitedHistory`. Where `WebViewFeature.isFeatureSupported(NAVIGATION_LISTENER)`, also use `WebViewCompat.addNavigationListener` `onNavigationCompleted`, skipping `isSameDocument` (research R4).
- [X] T041 [US1] In `MAIN/presentation/browser/page/PageSession.kt`, call `monitor.onPageChanged(topUrl, isWebPage)` from `advance()`. This updates only UI-facing fields: `isWebPage` is false for `HOME_PAGE_URL` and the incognito start page, and `connectionSecure` is set as well. It also covers history and back/forward restores that issue no main-frame request: when the top URL differs from the one `onMainFrameRequest` saw, reset the count and recompute `protectionOn` there via `IsSiteProtectedUseCase`. Counting and the normal reset belong to `onMainFrameRequest` (T036). The IO-thread reset makes FR-010 hold even for resources that arrive before `onPageStarted`.
- [X] T042 [US1] Confirm `onReceivedHttpError` still ignores non-main-frame responses (it already does, research R4), so a blocked request never shows error UI (FR-007). Add a comment naming FR-007.
- [X] T043 [P] [US1] Create `ATEST/protection/FakeProtectionDataModule.kt`, a `@TestInstallIn` replacement providing a `ProtectionData` whose tracker set is `tracker-01.test` … `tracker-12.test`. Mark it per-class where needed, as with the Spec 007 `@UninstallModules` pattern. It also contains `127.0.0.1`, the resolvable tracker used by T044(b).
- [X] T044 [US1] Write `ATEST/protection/TrackerBlockingTest.kt` on `trackers.html` served by `AssetPageServer` on `localhost`. `*.test` hosts never resolve, so "did not load" alone proves nothing. The test proves blocking in two ways:
  - **(a)** `monitor.blockedHosts()` equals the 12 `tracker-NN.test` hosts, and the hub status reads 12;
  - **(b)** a **resolvable** tracker: the fake list (T043) also contains `127.0.0.1`, and the page loads `http://127.0.0.1:<port>/pixel.png` (a different site from `localhost` under the IP rule). The image's `onload` flag must stay **false** with protection on, and become **true** after the site is excepted (`ProtectionExceptionRepository.add("localhost")` + reload).

  Also assert that 10/10 same-origin resources load and that no error UI shows. Run it in both a normal and an incognito tab (FR-009).
- [X] T045 [US1] Write `ATEST/protection/TrackerBlockingOfflineTest.kt`: assertion (a) of T044 with the device network turned off (SC-011). `localhost` is served on the device, so assertion (b) still holds. If the emulator cannot toggle the network from a test, record this as a manual step of G1.

**Checkpoint**: US1 is shippable alone — blocking works with protection always on.

---

## Phase 4: User Story 2 — Count and per-site switch (Priority: P1)

**Goal**: the hub shows the distinct blocked hosts and a per-site switch; Settings lists sites without protection; Clear data has an opt-in category (FR-010 – FR-017, FR-016a).
**Independent Test**: quickstart G5, G12, G13; SC-007, SC-008.

- [X] T046 [P] [US2] Create the use cases in `MAIN/domain/usecase/`:
  - `ObserveProtectionExceptionsUseCase`;
  - `SetSiteProtectionUseCase(site, on, isIncognito)`: normal goes to the repository; incognito goes to `IncognitoProtectionExceptionStore` only, and **never reads or writes Room** (FR-014);
  - `RemoveProtectionExceptionsUseCase(site?)`: `null` = all.
- [X] T047 [P] [US2] Write `TEST/domain/usecase/SiteProtectionUseCasesTest.kt`. Cover: normal on/off persists, incognito on/off touches only the store, remove-one, remove-all.
- [X] T048 [US2] Create `MAIN/presentation/browser/protection/ProtectionViewModel.kt` (contracts). It exposes the active WebView monitor's `status` (switched with the active tab), and `onProtectionSwitched(on)` → `SetSiteProtectionUseCase` with the current page's registrable domain and `isIncognito`, then emits `reloadEvents`. Keep it separate from `BrowserViewModel` (Spec 022 `PageTextSizeViewModel` precedent). It learns the site's state only through `IsSiteProtectedUseCase` and the monitor's status, never from a data-layer store (§IV, analyze D1).
- [X] T049 [P] [US2] Write `TEST/presentation/browser/protection/ProtectionViewModelTest.kt`: status passthrough, switch off/on in normal and incognito, and one reload event per switch.
- [X] T050 [US2] Create `MAIN/presentation/browser/components/HubProtectionSection.kt`. It shows:
  - the plural count;
  - the "Connection not secure" line when `!connectionSecure` (used by US3, rendered here);
  - a switch row with toggle semantics and an on/off `stateDescription`.

  It is hidden when `!isWebPage` (FR-011). Use Spec 019 components and tokens only, with no inline colours or dimens.
- [X] T051 [US2] Wire the section into `MAIN/presentation/browser/components/HubSheet.kt`. **While a web page is shown, `HubProtectionSection` takes the place of the existing `PrivacyStageBanner`**, so the hub has one privacy block, not two. On Home and the incognito start page, the banner stays as it is (FR-011). Collect `ProtectionViewModel.status` **only inside the hub** (§V rule 3). In `MAIN/presentation/browser/BrowserScreen.kt`, close the hub on switch and reload the current page on `reloadEvents` (FR-012).
- [X] T052 [US2] Create the Settings screen `MAIN/presentation/settings/protectionexceptions/` (`ProtectionExceptionsScreen`, `ProtectionExceptionsUiState`, `ProtectionExceptionsViewModel`), mirroring `sitepermissions/`:
  - newest first;
  - remove one;
  - "Remove all" with confirm;
  - an empty state (FR-015);
  - `LazyColumn` with `key = site` and `contentType`.

  Add `AppDestination.ProtectionExceptions` (`settings/protection_exceptions`) in `MAIN/presentation/navigation/AppDestination.kt` + `AppNavGraph.kt`, and a "Sites without protection" `SettingsValueRow` in `SettingsScreen.privacySection` after Site permissions.
- [X] T053 [P] [US2] Write `TEST/presentation/settings/protectionexceptions/ProtectionExceptionsViewModelTest.kt`: list order, remove, remove-all confirm/cancel, empty state.
- [X] T054 [US2] Add `SitesWithoutProtection` to `MAIN/domain/model/ClearBrowsingDataCategory.kt`. **Do not** add it to `DEFAULT_SELECTION`. Handle it in `MAIN/domain/usecase/ClearBrowsingDataUseCase.kt`: repository `clearAll()` plus `IncognitoProtectionExceptionStore.clear()`. Add its label and description in `MAIN/presentation/settings/components/ClearBrowsingDataDialog.kt`. `ClearBrowsingDataUseCase` already has 7 dependencies; take the two new ones behind a single `PerSiteSettingsCleaner` domain interface if detekt's `LongParameterList` fires — never by adding to the baseline.
- [X] T055 [P] [US2] Extend `TEST/domain/usecase/ClearBrowsingDataUseCaseTest.kt`: the default selection leaves exceptions intact; selecting the category clears Room and the incognito store; other categories are unaffected.
- [X] T056 [US2] Write `ATEST/protection/ProtectionSwitchTest.kt` (per-test DataStore and Room, `@UninstallModules(SettingsDataStoreProviderModule)` as in Specs 020/022). Steps: Steps 2–4 use activity recreation; real process restarts (SC-007's "3 restarts") and the 2-tap count are covered by device gate G5.
  1. Open `trackers.html` → the hub shows **12** (SC-001).
  2. Switch off → reload → count 0, trackers load.
  3. Recreate the activity → still off.
  4. Remove from Settings → on again.
  5. Repeat in incognito: off in incognito leaves normal on, and closing the last incognito tab clears it (SC-008).
- [X] T057 [P] [US2] Write `ATEST/settings/ProtectionExceptionsScreenTest.kt`: rows, remove, remove-all, empty state.

---

## Phase 5: User Story 3 — HTTPS upgrade (Priority: P2)

**Goal**: app-issued and page-initiated `http://` main-frame GETs try `https://` first and fall back silently; the hub shows "not secure" on http pages (FR-018 – FR-023, FR-022a).
**Independent Test**: quickstart G6, G7, G8; SC-003.

- [X] T058 [US3] In `MAIN/presentation/browser/BrowserWebView.kt`, route **both** app `loadUrl` call sites through `NavigationPreparer.prepare(url, isIncognito)`: the `actions.loadUrl` lambda and the initial load. Skip `HOME_PAGE_URL` as today. This covers typed addresses, Home, bookmarks, history, Spec 020 intents and tab switches (research R7).

  These call sites run on the main thread, so they must never block (analyze I1):
  - if `ProtectionMatchersHolder.matchersOrNull()` is ready, prepare synchronously and load;
  - otherwise launch a coroutine that awaits the matchers on `DispatcherProvider.io`, bounded by `NetworkTimeouts.PROTECTION_DATA_LOAD_TIMEOUT_MS`, then prepares and calls `loadUrl` on the main thread;
  - on timeout, load the URL unprepared (fail open).

  Only a cold start straight into a link can hit the await path. Call `monitor.beginUpgrade(prepared)` when `upgradedFrom != null`.
- [X] T059 [US3] In `BrowserWebViewClient.shouldOverrideUrlLoading`, handle main-frame `http`/`https` requests that are not redirects:
  - if `ProtectionMatchersHolder.matchersOrNull()` is `null`, return `false` and do not rewrite: this method must answer synchronously, and the page that issued the link has already loaded, so this is practically unreachable;
  - otherwise `prepared = prepare(url)`;
  - if it differs, `beginUpgrade` when upgraded, then `view.loadUrl(prepared.url, gpcHeadersIfNeeded)` and return `true`;
  - never re-issue an identical URL (invariant 5).

  Keep today's handling of other schemes.
- [X] T060 [US3] Implement fallback in `BrowserWebViewClient`:
  - `onReceivedError` (main frame), and `onReceivedSslError` where `error.url` matches the pending https URL → `monitor.onMainFrameFailure(url)`. If it returns the http URL, add the host to `HttpsUpgradeFailureStore`, `loadUrl(http)`, and **do not** call `onLoadFailed` (invariant 4).
  - In `shouldOverrideUrlLoading` with `WebResourceRequestCompat.isRedirect` (feature `WEB_RESOURCE_REQUEST_IS_REDIRECT`) to `http://` on the pending host → mark it failed and return `false`, so the redirect is allowed with no loop.
  - `onReceivedHttpError` is never a fallback trigger (FR-021).
- [X] T061 [US3] Set `connectionSecure` from the top URL scheme in `PageProtectionMonitor.onPageChanged` / `onCommitted`, so the hub line from T050 appears on every http page (FR-022a).
- [X] T062 [P] [US3] Extend `PageProtectionMonitorTest`: the pending-upgrade lifecycle; a failure for a non-pending URL returns `null`; a redirect downgrade is detected; commit clears pending; `connectionSecure` is false on http.
- [X] T063 [US3] Write `ATEST/protection/HttpsUpgradeTest.kt` with local addresses: `http://localhost:<port>/` and `http://127.0.0.1:<port>/` are **not** upgraded (FR-022) and load, and the hub shows "not secure". Real-site upgrade and fallback are device gate G6, since a local TLS server cannot be trusted.

---

## Phase 6: User Story 4 — Tracking parameters removed (Priority: P2)

**Goal**: main-frame navigations drop listed parameters before the request; the address bar, history and Share see the cleaned address (FR-024 – FR-027).
**Independent Test**: quickstart G9; SC-004.

- [X] T064 [US4] Verify that T058/T059 already apply `strip` for every source in FR-024 (the shared `prepare`). Add one comment at each call site naming FR-024. Redirect hops (`isRedirect`) are **not** stripped (FR-027).
- [X] T065 [US4] Write `ATEST/protection/ParamStrippingTest.kt` on `params.html` (local server). Tap a link `?a=1&utm_source=x&b=2&fbclid=y#top`. Assert that the server received `?a=1&b=2`, that `BrowserUiState.currentUrl` is the cleaned address, and that the recorded history entry matches (FR-026). Then repeat on an excepted site: the parameters are kept.

---

## Phase 7: User Story 5 — Global Privacy Control (Priority: P3)

**Goal**: `Sec-GPC: 1` on every request where the engine supports app-wide headers; otherwise on every app-issued page load (FR-030, FR-031).
**Independent Test**: quickstart G10; SC-009.

- [X] T066 [US5] Create `MAIN/presentation/browser/protection/GpcHeaderInstaller.kt`. When `MULTI_PROFILE` and `CUSTOM_REQUEST_HEADERS` are both supported, call `WebViewCompat.getProfile(webView).addCustomHeader(CustomHeader(GPC_HEADER_NAME, GPC_HEADER_VALUE, setOf("*")))` once per process. If the rule is rejected, retry with `setOf("https://*", "http://*")`. Return whether app-wide headers are active. Call it from `buildConfiguredWebView`.
- [X] T067 [US5] When app-wide headers are **not** active, pass `mapOf(GPC_HEADER_NAME to GPC_HEADER_VALUE)` to every `loadUrl` the app issues (T058, T059, T060 fallback, reload fallback). GPC ignores per-site exceptions (FR-016, invariant 8).
- [X] T068 [US5] Write `ATEST/protection/GpcHeaderTest.kt` with a local header-echo endpoint in `AssetPageServer`. Check both the page request and an image request in a normal tab, an incognito tab and an excepted site. When `CUSTOM_REQUEST_HEADERS` is unsupported, assert the page request only and **skip** the resource assertion with `assumeTrue`.

---

## Phase 8: User Story 6 — Referrer (Priority: P3)

**Goal**: cross-site requests send at most the origin; the app never loosens the default, and its re-issued navigations send none (FR-028, FR-029).
**Independent Test**: quickstart G11.

- [X] T069 [US6] Audit `MAIN/presentation/browser/BrowserWebView.kt`: no `Referer` header is ever added, no referrer-related setting is changed, and the re-issued `loadUrl` calls (T059/T060) carry no referrer. Add a comment naming FR-028.
- [X] T070 [US6] Write `ATEST/protection/ReferrerTest.kt` with two local origins (`localhost` vs `127.0.0.1`, which are different sites under the matcher's IP rule). Assert:
  - a cross-site link and image get at most the origin;
  - a same-site link gets the full path;
  - a re-issued (stripped) navigation gets no `Referer`.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T071 [P] Add "Open-source notices" to `MAIN/presentation/settings/components/AboutSection.kt`, with the EasyList/EasyPrivacy (CC BY-SA 3.0) and Public Suffix List (MPL-2.0) attributions (FR-003, research R13). Add a `NOTICE.md` at the repo root with the same text and links to `ASSETS/licenses/`.
- [X] T072 [P] Write `ATEST/settings/AboutNoticesTest.kt`: both attributions are visible.
- [X] T073 Run the automated gates in quickstart.md: `testDebugUnitTest`, `lintDebug` (8-locale parity), `detekt`, `ktlintCheck`, `connectedDebugAndroidTest` on `TA016_API36_16K`, and `assembleRelease`. Record:
  - the APK delta against 3,394,072 B (**SC-010: ≤ 500 KB**);
  - no new `.so`;
  - `4.json` committed;
  - `unzip -l` listing the three protection assets;
  - `git diff main -- app/src/main/AndroidManifest.xml` adds no `uses-permission` (FR-034);
  - no setting anywhere turns all protections off (FR-017; check in review).
- [X] T074 Run the Compose compiler report (`-PcomposeReports=true`). Expect zero unexplained non-skippable composables in the touched files.
- [ ] T075 Device gates G1–G16 from quickstart.md on `TA016_API36_16K`, `TA016_API24` and the Galaxy A55 (never change its default browser). Record each result in "Device Gate Results" below:
  - G14: release build, interleaved runs, list-parse time and heap;
  - G15: traffic capture through a proxy.
- [X] T076 Update the docs: `CLAUDE.md` (Recent Changes, roadmap row, Active Spec, facts to keep), `.claude/claude-app/sdd-roadmap.md`, `.claude/claude-app/dev-workflow.md` and `.claude/claude-app/project-context.md`.
- [X] T077 Open the PR into `main`. The body must carry: the gate results, the APK delta, the Constitution Check line, the design waiver, the list sources and licences, and the documented limitations — WebSockets and service-worker requests are not blocked; a page's own referrer policy wins; GPC on old engines covers app-issued loads only; POST is never upgraded or stripped.

---

## Dependencies & Execution Order

- **Phase 1** (T001–T010) has no dependencies. T005 needs T004.
- **Phase 2** (T011–T037) needs Phase 1 and **blocks every story**:
  - the matchers (T013–T022) come before the parser and the data source (T023–T027);
  - Room (T028–T030) comes before the repository (T031–T034);
  - DI (T035) comes before the monitor (T036–T037).
- **US1** (T038–T045) needs Phase 2.
- **US2** (T046–T057) needs US1, which provides the monitor wiring.
- **US3** (T058–T063) needs Phase 2, plus T050 for the hub line.
- **US4** (T064–T065) needs T058/T059.
- **US5** (T066–T068) needs T058–T060 for the fallback header path.
- **US6** (T069–T070) needs T059–T060.
- **Polish** (T071–T077) comes after every story.

### Parallel opportunities

- **Phase 1**: T001, T002, T003, T006, T007, T008, T009 and T010 together.
- **Phase 2**: T014/T016/T018/T020/T022, each with its own implementation; T017, T019 and T032 together; T024, T026, T030 and T034 once their code exists.
- **US2**: T047, T049, T053 and T055 once their units exist; T057 alongside T056.
- **US3 ∥ US2** once T050 lands. **US5** and **US6** after US3/US4.

## Implementation Strategy

1. **MVP**: Phases 1–3. Trackers are blocked with protection always on. Demo this on real sites before continuing.
2. **Add US2**: make on-by-default safe with the per-site switch and visible with the count.
3. **Add US3 and US4** together; they share `prepare`.
4. **Add US5 and US6**, mostly verification on top of US3/US4.
5. Polish, then the gates, then the PR.

## Notes

- Constitution §IX: no dependency is added. If a step seems to need one, stop and record why first.
- Stop before T029 if a migration seems to touch an existing table. `MIGRATION_3_4` is additive only.
- Never run `build_tracker_hosts.py` from Gradle or CI; the committed asset is the source of truth for a release.

## Implementation Notes

Deviations from the task text, each deliberate:

- **T004/T005 — tracker list trim.** EasyPrivacy + EasyList's ad-server and third-party sections came to 54,786 hosts and **461 KB gzip**, so the script stopped as T005 requires. Three changes were made:
  - EasyList's ad-server section is now kept only for domains in the Tranco top-1M. Tranco is used as a membership filter at build time only; no Tranco data is written to the asset.
  - EasyList's third-party section and EasyPrivacy's tracking-server and third-party sections are kept in full.
  - Six major ad hosts that EasyList blocks only through path or popup rules were added as our own list (`OWN_HOSTS` in the script): `doubleclick.net`, `googlesyndication.com`, `adservice.google.com`, `appsflyer.com`, `ads-twitter.com`, `snap.licdn.com`.

  Result: **11,012 hosts, 75,144 B gzip**. `google-analytics.com`, `googletagmanager.com`, `doubleclick.net`, `scorecardresearch.com`, `criteo.com`, `taboola.com`, `outbrain.com` and `adnxs.com` are all listed, and no never-block host is.
- **T006 — PSL** upstream `VERSION: 2026-10-07_07-28-19_UTC`, `COMMIT: 3929462652695bad04f0a27afb600974014a3c8b`. IDN rules are converted to punycode while parsing, because WebView reports hosts in ASCII.
- **T030** extends the existing `AppDatabaseMigrationTest`, which builds a v1 file and migrates through every step, instead of adding a new file. The v1 → v4 chain covers 3 → 4.
- **T031** — the snapshot lives inside `ProtectionExceptionRepositoryImpl`, bound as a singleton. It is filled from Room once, then updated by every write, so no separate `ProtectionExceptionSnapshot` class exists.
- **T035** — `ProtectionMatchersHolder` sits in `domain/protection/`, not `data/local/protection/`. It only combines domain types, and the data layer must not depend on a use case.
- **T036/T041** — the per-page reset happens in `onMainFrameRequest` (IO thread), as analyze U1 asked. `onPageStarted` covers redirects (the count is kept) and history restores that issue no request (the count is reset). `PageSession` is untouched. The hub hides the section on Home through `state.isHomeContent`, so the monitor does not need to know about Home.
- **T043/T044** — the tests host `BrowserWebView` directly with a real `ProtectionEngine` over an in-memory list (`TestProtection`), following the Spec 022 pattern, instead of a Hilt test module. The page is served at `localhost`; the resolvable tracker is `127.0.0.1` on the same server.
- **T050/T051** — on a web page the privacy banner's text becomes the count, followed by the switch row and the "not secure" row. Long texts sit in `ListRow`'s `below` slot, because the subtitle is cut to one line.
- **T056** is replaced by narrower tests. Blocking and exceptions are covered by `TrackerBlockingTest`; the switch and the incognito separation by `ProtectionViewModelTest`, `SiteProtectionUseCasesTest` and the `IncognitoTabRepositoryImplTest` additions; the UI by `ProtectionScreensTest`. The end-to-end hub path through `BrowserScreen` is device gate G5.
- **T066** — lint's `RequiresFeature` check accepts `addCustomHeader` only directly inside an `isFeatureSupported(CUSTOM_REQUEST_HEADERS)` branch.
- **GPC on the 16 KB AVD (WebView 133)**: `CUSTOM_REQUEST_HEADERS` is not available there, so `GpcHeaderInstaller.appWide` is false. GPC rides only on app-issued loads, the documented fallback. The resource-level assertion is skipped there by `assumeTrue`.
- **T045** — no separate test file. `TrackerBlockingTest` (3/3) was re-run with the AVD in airplane mode (`cmd connectivity airplane-mode enable`); everything it loads is served on the device.
- **🐞 Defect found on the AVD and fixed — HTTPS fallback never ran.** `http://neverssl.com` showed the engine's "ERR_EMPTY_RESPONSE" page for `https://neverssl.com/`.
  - Cause: the app issues two loads in a row (the initial load, then the tab-switch load). The first, cancelled, still reports `onPageFinished`, and that cleared the pending attempt before the real one failed.
  - Fix: a pending attempt now ends only after its page has *started* and then finished without an error. The fallback load is also posted, so it is not swallowed by the error page being committed.
  - Regression test: `PageProtectionMonitorTest` "a cancelled load that never started does not end the attempt".
  - Retest: NeverSSL loads over http and the hub shows "Connection not secure".
- **🐞 Second HTTPS-fallback defect, found on the A55 (2 of 6 runs)**: the posted fallback `loadUrl` could still be replaced by the error page's commit (logcat: fallback decided, no load followed). The fallback address is now remembered (`fallbackDue`) and loaded from the error page's own `onPageFinished`, which always follows `onReceivedError`; that `onPageFinished` is no longer reported as a finished visit (no history row for the failed attempt). 8/8 runs pass after the change. Regression test: "the http fallback waits for the error page to finish and is handed out once".
- **CI (API 29, WebView 74) found a referrer gap**: engines before Chromium 85 send the full address as a cross-site referrer (`no-referrer-when-downgrade` default), which the app cannot change for page resources. Recorded in FR-029 and research R6; `NavigationProtectionTest` now checks the cross-site referrer only on WebView ≥ 85 (13/13 with 2 skipped on the API 29 AVD).
- **Unit tests**: 882/882 (808 before, +74). lint, detekt and ktlint are clean with **no baseline change**. Release APK **3,626,601 B (+232,529 B, budget 500 KB)**, no new `.so`.

## Device Gate Results

Run 2026-10-12 on `TA016_API36_16K` (Android 16, WebView 133) and, once connected, on the owner's **Galaxy A55** (SM-A556E, Android 16, WebView 153). The owner's default browser was not touched; Spec 023's release build now sits on the phone (data kept, DB v4).

| Gate | Result |
|---|---|
| Automated | unit ✅ **882/882** · instrumented ✅ **168/169** on the 16 KB AVD (known `rendererCrash_…` flake, 2/2 alone) and **13/13 Spec 023 tests on the A55** · lint/detekt/ktlint ✅ · Compose report **195 restartable, 0 non-skippable** · release APK **3,626,601 B (+232,529 B)** · no new `.so` · `4.json` committed |
| G1 Blocking, fixture | ✅ `TrackerBlockingTest` 3/3, also in airplane mode |
| G2 Real sites | ✅ **A55 (WebView 153), 2026-10-12**: CNN 25 · NYTimes 6 · eBay 6 · vnexpress 7 · dantri 4 · tuoitre 4 · IMDb 4 · YouTube 2 · Amazon 1 · Reddit 1 blocked; all 10 render and work (screenshot grid). Excluded with cause: **bbc.com resolves to 127.0.0.1 on this network** (DNS block, `ERR_CONNECTION_REFUSED` before any request of ours); theguardian.com loads no trackers before consent; bing.com's trackers are Microsoft first-party (FR-008) |
| G3 Never-block | ✅ A55: a page embedding a YouTube video plays it; `accounts.google.com` sign-in opens, 0 blocked |
| G4 Count reset | ✅ by unit tests (`PageProtectionMonitorTest`); on device, each new site showed its own count |
| G5 Per-site switch | ✅ vnexpress.net: switch off → page reloads, count 0, "Off for vnexpress.net" → survives force-stop → listed in Settings → Turn on → empty state |
| G6 HTTPS upgrade | ✅ A55: `http://` wikipedia.org, github.com, www.vnexpress.net, example.org end on https (no not-secure line). NeverSSL via a page link: after the second fix below, 8/8 runs fall back to http (two were still loading at the 20 s snapshot and completed). ⚠️ **Limitation**: each failed HTTPS attempt leaves a history entry, so Back from NeverSSL's subdomain took 3 presses instead of 2 (the entries reload and show the page, never an error) — research R7's case; the experimental `navigate(replaceCurrentEntry)` API is not adopted |
| G7 Not upgraded | ✅ `NavigationProtectionTest` (localhost) |
| G8 Not-secure line | ✅ NeverSSL page: hub shows "Connection not secure" and its body text |
| G9 Parameters | ✅ `NavigationProtectionTest` (typed/initial and page link); intent case ✅ above |
| G10 GPC | ✅ A55 (WebView 153, `CUSTOM_REQUEST_HEADERS` present): `NavigationProtectionTest` 13/13, **0 skipped** — `Sec-GPC: 1` on page **and** resource requests. 16 KB AVD (WebView 133): page loads only, as designed |
| G11 Referrer | ✅ `NavigationProtectionTest`: cross-site = origin, same-site = full, re-issued = none |
| G12 Incognito | ✅ unit (`SiteProtectionUseCasesTest`, `ProtectionViewModelTest`, `IncognitoTabRepositoryImplTest`); `TrackerBlockingTest` incognito run |
| G13 Clear data | ✅ unit (`ClearBrowsingDataUseCaseTest`); dialog on device open |
| G14 Performance | ✅ A55. **SC-005** page load (`loadEventEnd`, CDP, cache disabled, 3 runs each, debug build): on is faster on tracker-heavy pages (median CNN 3.4 s vs ≥7.9 s off with 2 timeouts, vnexpress 1.6 vs 5.7 s, tuoitre 0.7 vs 2.6 s, NYTimes 6.7 vs 10.0 s, dantri 2.5 vs 3.2 s, eBay 1.9 vs 1.4 s); Amazon/Reddit/YouTube/IMDb re-measured with **off first** to remove an order effect: on ≤ off on all four. **SC-006** cold start, release, returning user, interleaved 3×5 per build: main **324 ms** vs 023 **313 ms** median (−3.4%) — indicative only: main's runs hit the v4 database and crash *after* first frame (`migration from 4 to 3` — expected, no destructive fallback). First-run not measured (needs `pm clear` of the owner's data). List parse **97–114 ms** off the main thread; app Java heap 19.4 MB |
| G15 Own traffic | ⬜ open (needs a proxy) |
| G16 Locales | 🟡 lint 8-locale parity ✅; visual sweep at 360dp open |

Observation (not attributed to Spec 023): during the scripted run Chrome's first-run screen opened once, probably from a blind tap at (540, 210) on a vnexpress.net ad link with an `intent:` scheme. Spec 023 does not change how non-web schemes are handled.
