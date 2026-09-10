# ThirtySixBrowser Android — Project Context & Progress

> Cập nhật lần cuối: 2026-05-08 — **✅ Specs 001–012 done. 🟡 Spec 013 production code done (PR #14 merged). ✅ Spec 014 production code complete (US1–US5 + Polish; 103/113 tasks). Phase 1 6/6 + Phase 2 6/6 done; Phase 3 2/3 production-ready.**
> Dùng để Claude hiểu ngữ cảnh dự án qua các cuộc hội thoại.
> **QUAN TRỌNG**: Đọc file này + sdd-roadmap.md + dev-workflow.md + constitution.md khi bắt đầu hội thoại mới.

## Trạng thái dự án: ✅ Phase 1 done + ✅ Specs 007–012 done (Spec 012 PR #13 merged into `main` 2026-05-03) + ✅ Spec 013 merged (PR #14, 2026-05-07) + ✅ Spec 014 production code complete 2026-05-08 (manual device gates + PR remain) — 14/19 shipped/in-flight

Foundation phase tiến độ:
- **Spec 001** ✅ — Gradle Kotlin DSL + version catalog + 16KB-ready build (AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01)
- **Spec 002** ✅ — Clean Architecture skeleton + Hilt DI (Hilt 2.59.2 / KSP 2.3.7), base utilities (`Result<T>`, `AppError`, `DispatcherProvider`, `BaseViewModel.launchSafely`)
- **Spec 003** ✅ — Material3 theme + typography + dark mode (Deep Teal seed + Cyan tertiary, Poppins + Inter bundled local, 5-token spacing scale, light/dark/system + dynamic color Android 12+, cold-start window flash fix)
- **Spec 004** ✅ — Multi-Language Localization Foundation: 8 locales (EN/VI/DE/RU/KO/JA/ZH/FR), Android 13+ system per-app picker via `locales_config.xml` + manifest, lint enforcement at error severity for translation completeness, brand "ThirtySix" preserved in Latin across non-Latin scripts. Zero Kotlin code change.
- **Spec 005** ✅ — Room database schema (Bookmark/BookmarkFolder/HistoryEntry/Tab) + DAOs + AppDatabase + Hilt DatabaseModule + WAL + strict-no-destructive migration + DB excluded from Auto Backup. Room 2.8.4 + Turbine 1.2.1 + Robolectric 4.16.1 (test-only). 29 new unit tests (51 total). APK release 1.4 MB (down from Spec 004 baseline 1.49 MB after R8 re-shrink). 16KB CI gate green (no new `.so`).
- **Spec 006** ✅ — DataStore Preferences settings persistence + first complete Clean Architecture data slice. DataStore Preferences 1.2.1, pure Kotlin/Java zero `.so`. 4 settings keys (theme_mode, language_override sealed, search_engine enum, is_onboarding_completed). ThemeMode relocated `presentation/theme/` → `domain/model/`. MainActivity `MutableState<ThemeMode>` replaced with `@Inject ObserveUserSettingsUseCase` + `collectAsStateWithLifecycle(UserSettings.DEFAULT)`. Backup posture asymmetric (settings INCLUDED, DB EXCLUDED) documented inline in both XML files. 2 clarifications applied (Q1 Result<Unit> setters, Q2 LanguageOverride sealed type).
- **Spec 007** ✅ — **First feature-bearing UI**. **T042 manual UX gate verified by user on device 2026-05-01**: cold-start ≤ 5s ✅, 200ms loading first-show ✅, error UI in 8 locales ✅, rotation no-flash ✅ — closes the last deferred gate. WebView Compose Wrapper: `BrowserScreen` → `BrowserWebView` (`AndroidView` wrapping `android.webkit.WebView`) → loads `https://example.com`. State: `BrowserUiState` + sealed `LoadingState{Idle, Loading(Float), Loaded, Failed(ErrorReason)}` + sealed `ErrorReason{NetworkUnavailable, DnsFailure, HttpError, SslError, Generic}` with `@StringRes` mapping. UI: top Material3 `LinearProgressIndicator` (US2) + full-screen localized error UI (US3, 4 string keys × 8 locales = 32 translations). **WebView lockdown**: 4 file-access settings off (FR-013), zero `addJavascriptInterface` (FR-006), `MIXED_CONTENT_NEVER_ALLOW` (FR-018), all web-origin permissions denied silently (FR-017), cookies persist via Android default (FR-016, local-only). **Lifecycle**: `DisposableEffect(Unit)` + `loadUrl("about:blank") + removeAllViews() + destroy()` for the WebView 116+ native-resource race. **Hilt URL injection** via `UrlConfigModule` (`@InstallIn(ViewModelComponent::class)`); test override per-class via `@UninstallModules` + nested `FakeUrlConfigModule` inside `BrowserScreenOfflineErrorTest` (chosen over global `@TestInstallIn` after analysis surfaced that the latter would break happy-path tests). New deps: `espresso-web` (androidTest, version pinned to existing `espressoCore = 3.7.0`, pure-Java JAR), `hilt-android-testing` + `kspAndroidTest(hilt-compiler)`, `material-icons-core` (Compose-BOM-managed, used by `BrowserErrorState`). Custom `HiltTestRunner` boots `HiltTestApplication`; `HiltTestActivity` provides minimal `@AndroidEntryPoint` Compose host. Manifest expanded to 2 permissions (`INTERNET`, `ACCESS_NETWORK_STATE`); POST_NOTIFICATIONS reserved for Spec 015. **CI**: `instrumented-test` job re-enabled in `.github/workflows/ci.yml` (uncommitted edit carried from `main` ships in this PR). 4 clarifications applied (Q1 cookies persist, Q2 deny permissions, Q3 top linear progress, Q4 mixed content NEVER_ALLOW). 4 post-analyze remediations (C1 type ordering, M1 rotation test T021b, M2 200ms first-show enforcement, M3 deterministic offline trigger via Hilt). 15 new unit tests (94 total: 79 baseline + BrowserViewModelTest 10 + ErrorReasonTest 5). APK release **1.61 MB** (delta +50 KB vs Spec 006 baseline 1.56 MB; SC-008 ≤ 200 KB ✅; most of delta = `material-icons-core`). 16KB CI gate green: 24/24 entries `align=0x4000`, zero new `.so`. Detekt fix: 4-callback bundle in `BrowserWebViewCallbacks` keeps `BrowserWebView` parameter count under `LongParameterList.functionThreshold = 6`. `browser_screen_placeholder` removed from all 8 locales (Spec 002 placeholder → real screen). Constitution Check 11/11 PASS pre + post-implementation. **T042 manual emulator UX gate (cold-start ≤ 5s, loading 200ms first-show, error UI in 8 locales, rotation no-flash) DEFERRED to user device verification** — mirrors Spec 004/006 manual-gate pattern.

**Phase 2 done** (6/6 ship-ready): 008 / 009 / 010 / 011 / 012 all merged into `main`.
**Phase 3 in progress** (2/3 production-ready): Spec 013 merged into `main` via PR #14; **✅ Spec 014 production code complete on branch `014-history-view`** (103/113 tasks; 10 remaining are 8 manual user-device gates G1–G8 + `connectedDebugAndroidTest` + PR open).

### Spec 014 — History View (production code complete 2026-05-08, US1–US5 + Polish)

Branch `014-history-view`. Shipped in two passes: the **MVP pass** (US1 — auto-record + grouped list + tap-to-replace) on 2026-05-08, then the **completion pass** (US2 search · US3 per-entry actions · US4 clear-all · US5 empty-state verification · Phase 8 polish) the same day.

- **Final task state: 103/113.** The 10 open tasks are all device- or human-bound: 8 manual user-device gates (G1–G8 per `specs/014-history-view/quickstart.md`), `connectedDebugAndroidTest` inside T102, and T111 (open the PR).
- **New production files (completion pass, 6)**: `DeleteHistoryEntryUseCase`, `ClearAllHistoryUseCase`, `ClipboardWriter` interface + `AndroidClipboardWriter` impl (`data/local/clipboard/`), `di/ClipboardModule.kt`, and 3 Composables — `NoHistoryMatchesState`, `HistoryActionSheet`, `ClearAllHistoryConfirmDialog`. Plus the 18 files from the MVP pass (2 domain models, day-bucket helper, mapper, repository interface + impl, Hilt module, 2 use cases, ViewModel, UiState, ErrorEvent, 5 Composables).
- **Modified in the completion pass (5)**: `HistoryViewModel` (search pipeline via `combine(entries, searchQueryFlow)`, one-shot `historySnackbarEvent: SharedFlow` with `replay=0 / capacity=1 / DROP_OLDEST` mirroring Spec 013's channel policy, 9 new handlers), `HistoryScreen` (3-way list branch + action sheet + confirm dialog + `SnackbarHost`), `HistoryTopBar` (rebuilt as `Column { TopAppBar; OutlinedTextField }` so the FR-023 clear-all action and the always-visible search field coexist), `HistoryUiState` / `HistoryErrorEvent` KDoc, `AppConstants` (+`CLIPBOARD_URL_LABEL`).
- **Clipboard seam instead of `Context` in the ViewModel** — tasks.md T067 called for `@ApplicationContext Context` injection. Implemented as a `ClipboardWriter` interface + Hilt-bound Android impl instead, mirroring Spec 011's `FaviconCache` pattern. Keeps every `HistoryViewModel` test on the plain JVM (no Robolectric) and Android imports out of `presentation/`. Platform calls are `runCatching`-wrapped per Spec 012's system-service crash-resilience posture.
- **Search semantics**: filtering is gated at `BrowserLimits.SEARCH_MIN_CHARS = 2` with **no debounce** (Q4), matches case-insensitive substrings of title **or** URL, and treats `%` / `_` / `'` literally because the filter is an in-memory `String.contains` — never SQL (FR-013). Queries are truncated at `MAX_HISTORY_QUERY_LENGTH = 200`. `entries` stays unfiltered in state so the screen can distinguish "no history at all" (`EmptyHistoryState`) from "no matches" (`NoHistoryMatchesState`).
- **Open-in-new-tab is always a normal tab** (Q2 / FR-019) regardless of whether the active tab is incognito; only `MAX_TABS` is consulted, and a cap-reached result surfaces Spec 011's existing message via `HistoryErrorEvent.TabCapReached` rather than introducing a new error type.
- **Icon set stayed core-only** (zero new packages): `material-icons-core` ships no `DeleteSweep` or content-copy glyph, so clear-all falls back to `Icons.Filled.Delete` and the "Copy URL" sheet row carries no leading icon — the same precedent `BookmarkActionSheet` set for its icon-less rows.
- **i18n**: 15 new EN keys × 8 locales = **120 new translations** (7 US1 keys shipped in the MVP pass). `history_action_sheet_dismiss_content_description` from T071 was deliberately **not** added — `ModalBottomSheet` dismisses natively with no describable affordance, and an unused key is build-blocking under `warningsAsErrors = true` + Lint `UnusedResources`.
- **Debug-only 10K-row seeder** (T103a): `app/src/debug/kotlin/.../dev/HistorySeeder.kt`, an `@AndroidEntryPoint BroadcastReceiver` registered in `app/src/debug/AndroidManifest.xml`. Living in the `debug` source set is a stronger guarantee than a `BuildConfig.DEBUG` check — the class simply does not exist in the release artifact. Drive it with `adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_HISTORY -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.HistorySeeder`.
- **Quality gates ALL GREEN**: `testDebugUnitTest` ✅ **369/369** (Spec 013 baseline 341 → +28: `HistoryViewModelTest` 23 / `DeleteHistoryEntryUseCaseTest` 3 / `ClearAllHistoryUseCaseTest` 2) · `lintDebug` ✅ (incl. `MissingTranslation` + `ExtraTranslation` at error severity — 22 `history_*` keys present in all 8 locales) · `detekt` ✅ baseline UNCHANGED · `ktlintCheck` ✅ · `assembleRelease` ✅ · 16 KB gate ✅ (every `.so` entry `align=0x4000`; zero new native libs).
- **APK release 2.36 MB** vs Spec 013 baseline 2.38 MB → **delta −20 KB** (R8 reshrink absorbed the new Composables). Comfortably inside the SC-008 +200 KB budget, and −60 KB better than the +80 KB the MVP pass had banked.
- **4 instrumented test files written and compile-verified** (`compileDebugAndroidTestKotlin` ✅) but **NOT executed** — no device or emulator was attached: `HistoryRecorderIntegrationTest` (real Room/SQLite through `RecordHistoryEntryUseCase` → repo → mapper → DAO, incl. incognito suppression + repeat-visit separation + delete + clear-all), `HistoryScreenBrowseTest`, `HistoryScreenSearchTest`, `HistoryScreenLongPressTest`. A shared `HistoryScreenTestDoubles.kt` supplies in-memory repositories so the three Compose tests drive `HistoryViewModel` directly, avoiding the Hilt + WebView setup the project documents as flaky.
- **Bookkeeping correction**: T028 (`HistoryViewModelTest`) had been marked `[X]` in the MVP pass without the file existing — which is why the claimed "48/113" reconciled to 44 actual checkboxes. The file now exists with 23 cases spanning US1–US5.
- **Constitution Check 11/11 PASS** post-implementation. `HistoryViewModel` carries `@Suppress("TooManyFunctions", "LongParameterList")` with the same ViewModel-level use-case-coordination rationale Spec 013's `BookmarksViewModel` documented (history domain + Spec 011 tabs domain; no Repo→Repo dependency).

- **Pass 3 — emulator verification (2026-05-08)**: ran the debug build on `Medium_Phone_API_36.1` (API 36) and drove the feature by hand. Found a **real FR-001/FR-003 recorder defect** the unit tests had missed: `onLoadFinished` gated recording on `loadingState is Loaded && currentUrl == url`, but Chromium delivers `doUpdateVisitedHistory` → `onPageStarted` → progress → `onProgressChanged(100)` → `onPageFinished`, so the guard was already satisfied on genuine navigations (nothing recorded) while failed loads — state `Failed`, not `Loaded` — slipped through and *were* recorded. Replaced with an explicit per-navigation token (`pendingHistoryUrl`, armed in `onLoadStarted`, consumed in `onLoadFinished`, cleared in `onLoadFailed`). Also fixed stale titles: `onLoadStarted` clears `currentTitleCache`, and a title arriving after page-finish is back-filled onto the row through new `HistoryDao.updateTitle` / `HistoryRepository.updateTitle` / `UpdateHistoryEntryTitleUseCase` (repository contract 5 → 6 methods, documented deviation). New `BrowserViewModelHistoryRecordSequenceTest` (6 cases) replays the real ordering — all 6 failed before, pass after. Unit tests **375/375**; `connectedDebugAndroidTest` **59/60**, the one failure being a pre-existing Spec 007 flake that reproduces identically on `main`. Device re-verification confirmed: 3 navigations → 3 correctly-titled rows, incognito → zero rows, tap-to-replace, search, long-press delete/copy, and clear-all cancel/confirm all behave per spec.

- **Pass 4 — manual gates, performance, and the memory bound (2026-05-08)**: ran G2/G4/G5/G6/G7/G8 on emulators and closed all six; Spec 014 is now **110/113**. Found a **second real defect**: `groupedEntries` was derived inside `combine(...).onEach { }.launchIn(viewModelScope)`, and `viewModelScope` dispatches on `Dispatchers.Main.immediate`, so filtering + day-bucketing 10 000 rows ran on the UI thread on every emission *and every keystroke* — typing p99 frame was 200 ms with 70 % janky frames. Moving the derivation to `.map { }.flowOn(dispatchers.default)` cut typing p99 to **53 ms**. **SC-005 remains undemonstrated**: the target is p99 ≤ 16 ms on Pixel 5-class hardware and a debug build on an emulator still measures 350 ms on initial open (scrolling, the steady state, is fine at 81 ms p99 / 9.2 % janky) — T103b needs a release-build measurement on a real device. **Memory**: heap measurement showed ~300 B/row (10 001 rows ≈ 2.9 MB) against a completely unbounded table — ~30 MB at 100 000 rows, reachable in under two years of heavy use, on minSdk-24 devices with ~96 MB heap caps. Paging 3 was considered and rejected for v1.0 (it forces search into SQL `LIKE`, whose `%`/`_` wildcards would break FR-013's literal-matching guarantee, and would mean rewriting US1+US2 plus most of `HistoryViewModelTest` for two new dependencies). Chose to bound the **table** instead: `BrowserLimits.MAX_HISTORY_DAYS = 90` + `PruneOldHistoryUseCase` + `HistoryRepository.pruneOlderThan` over the pre-existing `HistoryDao.deleteInRange`, swept once per process start from `ThirtySixApplication` — a ViewModel-hosted sweep would never run for the user who never opens History, i.e. exactly the user at risk. Pruning beats `LIMIT`-capping because it keeps "absent from the list" equal to "absent from the database", so search cannot lie. Verified on the **API 24** AVD (minSdk, 2 GB RAM): 201 rows over 200 days → 91 after restart, the 110 out-of-window rows removed; the same run re-confirmed `java.time` desugaring on API 24. Unit tests **379/379**.

- **Pass 5 — full instrumented suite green (2026-05-08)**: Spec 014 is now **111/113**. `connectedDebugAndroidTest` runs **60/60**, three times back-to-back on the `TA016_API24` AVD (Android 7.0 / minSdk, arm64), with no flakes. Closing T102 required fixing the **pre-existing Spec 007 flake** `BrowserScreenInstrumentedTest.loadingIndicator_appearsAndHidesOnFinish`: it asserted the loading indicator appears during a live `example.com` load, so once a sibling test warmed the WebView HTTP cache the `Loading` window closed faster than the assertion could see it — order-dependent, and confirmed pre-existing by reproducing it on `main` (`954742a`) in a clean worktree on both API 24 and API 36 while it passes 3/3 in isolation. The test now waits for the real page to settle, then drives `onLoadStarted` / `onProgressChanged` / `onLoadFinished` explicitly, which is the Compose binding it actually guards; live-load coverage remains in `pageRenders_assertsDomContainsExampleDomain`. This edits a Spec 007 file from the Spec 014 branch — flagged in tasks.md and for the PR body. The API 36 AVD could not be re-run (its `/data` is 96 % full and refuses installs). **Only T103b (SC-005 on Pixel 5-class hardware) and T111 (PR) remain.**

- **Pass 6 — Spec 007 flake properly fixed (2026-05-08)**: pass 5's attempt was not a real fix — it passed on local arm64 API 24/36 but CI (API 29) still failed at the same assertion, because waiting for the page to settle does not stop a settled WebView from re-firing `onProgressChanged` / `onPageFinished` and flipping the synthetic `Loading` back to `Loaded`; CI's `-gpu swiftshader_indirect` emulator widens that window enough to lose every time. Fixed properly by removing the WebView from the equation: seed the ViewModel to `LoadingState.Failed` before `setContent`, which makes `BrowserWebView` skip its initial `loadUrl` (the seam `BrowserScreenOfflineErrorTest` already relies on), so it emits no callbacks and the state machine can be driven deterministically. New `BrowserScreenLoadingIndicatorTest` (2 cases); the racy method removed from `BrowserScreenInstrumentedTest`. Validated by booting the local AVD with CI's own flags: `main`'s original test fails there (first local reproduction of the CI failure), this branch runs **61/61 three times back-to-back**. `connectedDebugAndroidTest` is now green, leaving only T103b (SC-005 on real hardware) and T111 (PR).

**Next**: install the debug build on a real Android device → run manual gates **G1–G8** from `specs/014-history-view/quickstart.md` (G8 uses the new `HistorySeeder`) → run `connectedDebugAndroidTest` → open PR `014-history-view → main`. Spec 015 (`downloads-manager`) is parallel-available now.

### Spec 013 — Bookmarks CRUD (production code done 2026-05-07)

- **28 new production files**: 4 domain models (`Bookmark`, `BookmarkFolder`, `BookmarkSearchResult`, `BookmarkDescendantCount`) + `BookmarkUrlValidator` (pure Kotlin via `java.net.URI`) + `BookmarkException` sealed exception family + 15 use cases (`AddBookmark`, `UpdateBookmark`, `DeleteBookmark`, `MoveBookmark`, `ToggleBookmark`, `IsUrlBookmarked`, `ObserveBookmarksByFolder`, `ObserveFolderPath`, `ObserveAllFolders`, `SearchBookmarks`, `CreateFolder`, `RenameFolder`, `MoveFolder`, `DeleteFolder`, `CountFolderDescendants`) + 2 mappers + `BookmarkRepositoryImpl` (combined repo per R1, owning both DAOs, cascade delete via depth-first DFS in `database.withTransaction { }`, cycle prevention via ancestor walk) + `BookmarkModule` Hilt @Binds + `BookmarksViewModel` + `BookmarksUiState` (with `PendingBookmarkDialog` sealed family for at-most-one-dialog-at-a-time) + `BookmarksErrorEvent` + 13 Composables (Screen + TopBar + BreadcrumbBar + BookmarkRow / FolderRow / BookmarkSearchResultRow + EmptyBookmarksState / NoSearchMatchesState + 4 dialogs + 2 action sheets).
- **6 modified production files**: `BrowserUiState` (+`isBookmarked` +`bookmarkSnackbarEvent`); `BrowserViewModel` (+`IsUrlBookmarkedUseCase` + `ToggleBookmarkUseCase` injections + `onStarTapped` + `consumeBookmarkSnackbarEvent` + bookmark observer in init); `BrowserScreen` (top-bar `Favorite` / `FavoriteBorder` star toggle + bottom-bar `Icons.Filled.Star` 5th button); `NavigationBottomBar` + `NavigationBottomBarCallbacks` (+`onBookmarksClick` field, 7 fields total — exceeds detekt LongParameterList=6, suppressed); both DAOs (`BookmarkDao` +6 methods, `BookmarkFolderDao` +3 methods); `BrowserLimits` (+5 constants `MAX_BOOKMARK_TITLE_LENGTH=200`, `MAX_BOOKMARK_URL_LENGTH=2048`, `MAX_FOLDER_NAME_LENGTH=100`, `MAX_BOOKMARKS=10000`, `MAX_FOLDERS=1000`); `AppNavGraph` (passes nav controller into BookmarksScreen).
- **i18n**: 33 new EN string keys + 1 plurals × 8 locales ≈ 272 string-resource entries. Removed obsolete `bookmarks_screen_placeholder` (Spec 002 leftover) from all 8 locales — mirrors how Spec 007 retired `browser_screen_placeholder`.
- **Icon set pivot**: `material-icons-core` does NOT contain `Icons.Filled.Bookmark` / `BookmarkBorder`. Star toggle uses `Favorite` / `FavoriteBorder` pair (filled-vs-outline same family, both in -core); navigation entry uses `Icons.Filled.Star`. Zero new packages preserved.
- **4 clarifications applied** via `/speckit-clarify`: Q1 search scope = global w/ folder path inline · Q2 bookmark tap = replace active tab · Q3 folder delete = cascade w/ deep descendant count · Q4 star toggle = canonical-most-recent-by-URL (manual duplicates untouched). All marked ✅ resolved in spec.md.
- **3 MEDIUM + 7 LOW analyze findings remediated**: G10 deep-tree perf gate added to quickstart, FR-010a incognito-tap explicitly verified in T054, "13 → 14 use cases" plan.md count fix, three task-description count off-by-ones reconciled, T031 snackbar channel locked to dedicated `bookmarkSnackbarEvent`, T047 row icon locked to `Icons.Filled.FavoriteBorder` tinted primary, string-key estimate reconciled to ~33 keys + 1 plurals × 8 = ~272 entries.
- **4 mid-implementation user-feedback fixes** beyond original spec scope:
  1. Bookmarks entry-point added to **bottom bar** (5th button between Home and Tabs, `Icons.Filled.Star`) — original implementation routed `AppDestination.Bookmarks` but had no UI affordance, so user couldn't reach the screen.
  2. `FolderPickerSheet` rewritten to render hierarchical depth-indented list of all folders (originally only showed a synthetic "Root" entry). New `BookmarkRepository.observeAllFolders()` + `ObserveAllFoldersUseCase` + `BookmarksUiState.allFolders` field. Picker accepts `excludeFolderIdAndDescendants` for the Move-folder cycle-prevention UX guardrail.
  3. `AddOrEditBookmarkDialog` embedded folder selector — tap "Folder: <path>" row → opens `FolderPickerSheet` overlay → pick → state updates + dialog persists. Originally locked to caller-provided `parentId` with no UI to change it.
  4. `BookmarksScreen` back navigation: `ArrowBack` icon now **always visible** in top bar (was hidden at root). At root tap pops back to BrowserScreen; in sub-folder tap walks up to parent. `BackHandler` mirrors the same logic for system-back gesture.
- **Constitution Check 11/11 PASS** post-implementation. `BookmarkRepository` interface 18 methods (combined repo per [research.md R1](../../specs/013-bookmarks-crud/research.md#r1-repository-shape-combined-vs-split) — NOT a §IV Repo→Repo violation, it's one repo with two DAO data sources; `@Suppress("TooManyFunctions")` documented). `BookmarksViewModel` injects `UpdateActiveTabUrlAndTitleUseCase` from Spec 011 for the FR-010 "tap → load into active tab" path — use-case-level coordination, allowed by §IV.
- **All automated quality gates GREEN**: `testDebugUnitTest` ✅ (~30 new unit tests across `BookmarkUrlValidatorTest`, `BookmarkMapperTest`, `BookmarkFolderMapperTest`, `BookmarkDaoSpec013Test`, `BookmarkFolderDaoSpec013Test`, `BookmarkRepositoryImplTest`, 7 use case test files including cycle prevention + cascade delete + descendant count + canonical-most-recent star toggle + global search w/ folder path, `BookmarksViewModelTest`, `BrowserViewModelStarToggleTest`); `lintDebug` ✅; `detekt` ✅ baseline UNCHANGED, ~10 targeted `@Suppress` for known thresholds (LongParameterList, LongMethod, ReturnCount, TooManyFunctions) with rationale comments; `ktlintCheck` ✅; `assembleRelease` ✅; 16 KB CI gate ✅ 8/8 native lib entries `align 2**14` (zero new `.so`).
- **APK release size**: **2.38 MB** vs Spec 012 baseline 2.1 MB → **delta +280 KB**, **exceeds SC-008 +100 KB budget by 180 KB**. Overrun documented as feature scope (~13 Composables ≈ 100 KB Compose bytecode + ~272 i18n entries ≈ 10 KB + 15 use cases ≈ 30 KB + repo impl ≈ 40 KB + sealed events + R8-survived metadata). PR body MUST disclose the overrun explicitly; user already accepted "feature value > 180 KB extra" trade-off in conversation.
- **1 new test fake**: `FakeBookmarkRepository` under `app/src/test/.../testdoubles/` — in-memory map-backed implementation honoring the full repo contract (cycle prevention + cascade delete logic mirrored from production impl).
- **Tasks deferred** (production code is correct; these are remaining instrumented tests + manual gates + PR): T034/T055/T073/T083/T088/T099/T105/T110 (instrumented test files — emulator-bound), T035/T056/T100/T106/T113–T117a (10 manual user-device gates G1–G10), T121 (PR open). 19 tasks remain — none block the production code being correct.

**Next**: install on real Android device → run G1–G10 manual gates from `specs/013-bookmarks-crud/quickstart.md` → write deferred instrumented test files (Copilot Pro / Claude on fresh `/speckit-implement` pass) → open PR `013-bookmarks-crud → main` referencing the SC-008 APK overrun in body. Once merged, Spec 014 (`history-view`) + Spec 015 (`downloads-manager`) are parallel-available.

### Spec 012 — Private / Incognito Mode (PR #13 merged into `main` 2026-05-03)

- 11 new production files: `IncognitoTabRepository` interface + impl (`@Singleton`, `MutableStateFlow<List<Tab>>` + `Mutex` + negative-Long ID space per R3); `CookieJarSnapshotManager` interface + impl (public-API per-origin getCookie/setCookie round-trip, every platform call wrapped in `runCatching` for crash-safety); `CookieJarSnapshot` data class; 5 new use cases (`Create/Close/CloseAll IncognitoTab`, `ObserveAllTabs`, `ObserveActiveTabIsIncognito`); `SecureWindowEffect` Composable (idempotent FLAG_SECURE binding via `DisposableEffect(secure)`); `IncognitoIndicator` + `TabSwitcherNewIncognitoTabCard` + `CloseAllIncognitoConfirmDialog`; `IncognitoModule` Hilt @Binds.
- 17 modified production files: `Tab` (+`isIncognito: Boolean = false`); `TabMapper` (Room→always `false`, `toEntity()` defensively rejects incognito via `require(!isIncognito)`); `BrowserViewModel` + cache-write gates in `onIconReceived` / `onScreenshotReady`; `BrowserUiState` (+`isIncognito`); **`BrowserWebView`** (incognito lockdown branch with `saveFormData=false` + `LOAD_NO_CACHE` + 9-step destroy: stopLoading → clearHistory → clearFormData → clearMatches → clearSslPreferences → clearCache(true) → loadUrl(blank) → removeAllViews → destroy; **plus** `shouldOverrideUrlLoading` external-intent crash-safety wrapper covering FR-018 universally — both normal and incognito); `BrowserScreen` (extracted `BrowserTopBar` to keep under detekt LongMethod=60); `TabsScreen` ("Close all incognito" affordance visible iff `incognitoCount > 0`, new-incognito-tab card always rendered, helpers extracted); `TabsViewModel` (replaced `ObserveTabsUseCase` with `ObserveAllTabsUseCase`, branched `onCloseTab` by `tabId<0L`, added `onNewIncognitoTabClick` + `onCloseAllIncognito*` triplet); `TabsUiState` (+`incognitoTabCount`, +`isCloseAllIncognitoDialogVisible`); `TabsErrorEvent` (+`MaxIncognitoTabsReached`); `TabSwitcherCard` (branches preview area + title row by `tab.isIncognito` — placeholder lock glyph + tertiary container colour, hostname suppressed for privacy); `MainActivity` (FLAG_SECURE wiring); `UpdateActiveTabUrlAndTitleUseCase` (+`isIncognito` parameter routes to either repo); `ObserveActiveTabUseCase` (now sources from `ObserveAllTabsUseCase`); `TabRepositoryImpl` (defensive `runCatching` per-row mapping for FR-021 forward-compat); `BrowserLimits` (+`MAX_INCOGNITO_TABS = 50`).
- **9 new EN keys + 63 translations × 7 non-EN locales = 72 string-resource entries**. PluralsCandidate suppressed for the 2 plural-only-in-practice strings (cap=50 always > 1) with `tools:ignore="PluralsCandidate"` + rationale comment.
- **2 new test fakes**: `FakeIncognitoTabRepository` + `FakeCookieJarSnapshotManager` under `app/src/test/.../testdoubles/`.
- 3 clarifications applied via `/speckit-clarify`: Q1 independent caps `MAX_INCOGNITO_TABS = MAX_TABS = 50`; Q2 `FLAG_SECURE` enabled while incognito active (Chrome behaviour, blocks recents thumbnail + screenshots); Q3 cookie snapshot/restore strategy (capture before first incognito tab, restore on close of last).
- Crash-resilience focus (user priority #1) drove three architecture choices: (a) public-API cookie snapshot/restore (NO SQLite-file fragility), (b) idempotent FLAG_SECURE via `DisposableEffect(secure)`, (c) **separate in-memory `IncognitoTabRepository`** parallel to Room-backed `TabRepository`, merged at use-case layer (`ObserveAllTabsUseCase`). Plus mutex-guarded transitions, every CookieManager call in `runCatching`, 9-step incognito WebView destroy, defensive `runCatching` per-row mapping, negative-Long ID space (zero collision with Room PKs).
- **All automated quality gates GREEN**: testDebugUnitTest ✅ (Spec 011 baseline 201 tests still pass after Tab + UpdateActiveTabUrlAndTitleUseCase + ObserveActiveTabUseCase signature changes — 3 test fixtures patched: `TabUseCasesTest`, `BrowserViewModelTest`, `TabsViewModelTest`); lintDebug ✅; detekt ✅; ktlintCheck ✅; assembleRelease ✅ APK = **2.1 MB** (identical to Spec 011 baseline; well under SC-008 +100 KB budget — actual delta = 0); 16 KB CI gate ✅ 8/8 native lib entries `align 2**14` (zero new `.so`); Constitution Check **11/11 PASS** post-implementation with one documented §IV exception (`IncognitoTabRepositoryImpl` reads from `TabRepository` for origin enumeration in cookie snapshot capture — covered by plan.md Complexity Tracking; mirrors Spec 011 use-case-coordination ack pattern).
- **Documented limitation** (research R1): cookie attributes (`Domain`, `Path`, `Expires`, `Secure`, `HttpOnly`, `SameSite`) NOT preserved by `getCookie → setCookie` round-trip — restored cookies revert to default attributes. Acceptable trade-off for v1.0 because the alternative (SQLite-file copy) is platform-version-fragile and crash-resilience is the user's stated priority.
- **Tasks deferred** (production code is correct; these are remaining tests + manual gates + PR): T008/T009/T010/T010a/T011/T036/T037/T040a/T042/T043/T046/T047/T048/T055/T057/T058/T064/T066/T067/T068 (test files + instrumented suites — contracts in `specs/012-private-incognito-mode/contracts/` enumerate every test case explicitly, ideal for Copilot Pro per `dev-workflow.md` split) + T035/T073/T074/T075/T076/T077/T077a (manual user-device gates G1–G6) + T080/T081 (post-impl docs updates already partially done in this entry; PR open).

**Next**: run G1–G6 manual gates on a real device → write deferred test files (Copilot Pro / Claude on a fresh `/speckit-implement` pass) → update CLAUDE.md to ✅ DONE → open PR `012-private-incognito-mode → main` linking to plan.md Complexity Tracking row.

### Tổng quan dự án

- **App Name**: ThirtySixBrowser
- **Concept**: Android browser tối giản, lấy cảm hứng từ DuckDuckGo Browser nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn (WebView, DownloadManager, Room, DataStore)
- **Platform**: Android (phone + tablet)
- **Category**: Productivity / Tools (Google Play)
- **Developer**: Xbism3 (solo)
- **Business Model**: Free (chưa có Pro tier)
- **Package**: `com.raumanian.thirtysix.browser`

---

## Chiến lược sản phẩm

### Vấn đề cần giải quyết
- Đa số browser bên thứ ba trên Android nặng nề, có cloud sync, bắt đăng ký tài khoản
- DuckDuckGo Browser tốt nhưng nhiều tính năng phức tạp (App Tracking Protection, Email Protection, Fireproof) — không dễ tự build
- Người dùng cần một trình duyệt nhẹ, riêng tư, offline-first, chỉ làm browser cơ bản tốt

### Core Differentiators
1. **Tối giản, không tài khoản** — không sync, không cloud, không tracking
2. **Native Compose UI** — Material3, dynamic color, dark/light/system
3. **Đa ngôn ngữ ngay từ đầu** — 8 locale (VN/EN/DE/RU/KR/JA/ZH/FR)
4. **16KB page size ready** — tương thích Android 15+/16+ devices

### Positioning statement
*"Trình duyệt Android tối giản, không tài khoản, không sync, không tracking — chỉ là browser tốt."*

---

## Tech Stack

### Core
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36 (release with minorApiLevel 1)
- **Java**: 11
- **Architecture**: Clean Architecture + MVVM
- **State Management**: ViewModel + StateFlow / SharedFlow
- **DI**: Hilt
- **Navigation**: Navigation Compose
- **Theming**: Material3 ColorScheme + dynamic color (Android 12+) + light/dark/system
- **Persistence**: Room (bookmarks, history, tabs) + DataStore Preferences (settings)
- **Async**: Coroutines + Flow
- **WebView**: `android.webkit.WebView` bọc trong `AndroidView` Compose
- **Download**: Android `DownloadManager`
- **Splash**: `androidx.core:core-splashscreen`
- **Test**: JUnit + Compose UI Test + Espresso

### Build types
- `debug` — development
- `release` — production
- **KHÔNG dùng build flavors** (dev/staging/prod) — quyết định gọn

### Locales hỗ trợ
EN (default), VI, DE, RU, KO, JA, ZH, FR — total 8 locales

---

## ⚠️ Ràng buộc 16KB page size (CRITICAL)

Android 15 (API 35) đã giới thiệu hỗ trợ 16KB memory page size. Android 16 (API 36) làm cho thiết bị 16KB phổ biến hơn. App có native library (`.so`) align 4KB sẽ **KHÔNG load được** trên thiết bị 16KB → app crash khi mở.

### Bắt buộc khi thêm bất kỳ thư viện nào:

1. **Verify library hỗ trợ 16KB page size** trước khi thêm vào `libs.versions.toml`
2. **NDK version**: dùng NDK r27+ (build với 16KB alignment mặc định)
3. **AGP version**: tối thiểu AGP 8.5+ (recommend AGP 8.7+)
4. **Gradle property**:
   ```properties
   android.bundle.enableUncompressedNativeLibs=true
   ```
5. **Check `.so` alignment** sau build:
   ```bash
   unzip -p app-release.apk lib/arm64-v8a/lib*.so | \
     objdump -p - | grep LOAD | awk '{print $NF}'
   # Tất cả phải là 0x4000 (16KB) hoặc lớn hơn
   ```
6. **Thư viện chỉ Kotlin/Java** (không có `.so`) → không bị ảnh hưởng — vẫn cần check transitive deps
7. **Thư viện có `.so` cần verify**: WebView (system, OK), Room (Kotlin only, OK), bất kỳ thư viện ảnh, video, native crypto, Lottie, ML Kit, Tensorflow Lite, v.v.

### Quy trình mỗi khi thêm package mới:
1. Lookup phiên bản mới nhất trên `central.sonatype.com` hoặc Maven repo
2. Đọc release notes / GitHub README để xác nhận hỗ trợ 16KB
3. Nếu thư viện có `.so` mà chưa rõ → ưu tiên version release sau 2024-Q4 (khi Google bắt đầu push 16KB mạnh)
4. Sau khi build local → verify `.so` alignment bằng `objdump`
5. Document version + 16KB-verified date trong `project-context.md`

### Reference
- https://developer.android.com/guide/practices/page-sizes
- https://android-developers.googleblog.com/2024/08/adding-16-kb-page-size-to-android.html

---

## Architecture

### Pattern: Clean Architecture
```
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── base/                # BaseViewModel, BaseUseCase
│   ├── di/                  # Hilt qualifiers, dispatcher modules
│   ├── error/               # AppError sealed class, Result wrapper
│   ├── extensions/          # Context, Flow, String extensions
│   ├── result/              # Result<T> wrapper
│   ├── dispatcher/          # DispatcherProvider
│   └── constants/           # AppConstants, UrlConstants, StorageKeys
├── data/
│   ├── local/
│   │   ├── database/        # Room AppDatabase, Daos
│   │   ├── entity/          # BookmarkEntity, HistoryEntity, TabEntity
│   │   └── datastore/       # SettingsDataStore (DataStore Preferences)
│   ├── repository/          # *RepositoryImpl
│   └── mapper/              # Entity ↔ Domain model
├── domain/
│   ├── model/               # Bookmark, HistoryEntry, Tab (pure Kotlin)
│   ├── repository/          # *Repository interfaces
│   └── usecase/             # GetBookmarksUseCase, AddHistoryUseCase, ...
├── presentation/
│   ├── theme/               # Color, Typography, Shape, AppTheme
│   ├── navigation/          # AppNavGraph, AppDestination sealed class
│   ├── browser/             # BrowserScreen, BrowserViewModel, components
│   ├── tabs/                # TabsScreen, TabsViewModel
│   ├── bookmarks/           # BookmarksScreen, BookmarksViewModel
│   ├── history/             # HistoryScreen, HistoryViewModel
│   ├── downloads/           # DownloadsScreen, DownloadsViewModel
│   ├── settings/            # SettingsScreen, SettingsViewModel
│   └── onboarding/          # OnboardingScreen, OnboardingViewModel
├── di/                      # Hilt modules: DatabaseModule, RepositoryModule, ...
├── ThirtySixApplication.kt  # @HiltAndroidApp
└── MainActivity.kt          # @AndroidEntryPoint, splash, navigation host
```

### Implementation Sequence (bắt buộc)
1. Project init + Gradle Kotlin DSL + version catalog + buildTypes (Spec 001)
2. Clean Architecture skeleton + Hilt + base classes (Spec 002)
3. Theme + Typography + Dark/Light/System (Spec 003)
4. Localization 8 locales (Spec 004)
5. Room schema + DataStore (Specs 005, 006)
6. WebView wrapper + nav controls + omnibox + search (Specs 007–010)
7. Tabs + incognito (Specs 011, 012)
8. Bookmarks + History + Downloads (Specs 013–015)
9. Settings + Splash + Onboarding (Specs 016–018)
10. (Optional) Tracker blocker (Spec 019)

---

## Theme & Design System

### Color
- Material3 ColorScheme — `lightColorScheme()` / `darkColorScheme()`
- Dynamic color (Android 12+) — `dynamicLightColorScheme(LocalContext.current)`
- Theme mode: Light / Dark / System (mặc định System)
- Không hardcode `Color(0xFF...)` trong UI code

### Typography
- Material3 default Typography (chưa custom font)
- Có thể bổ sung custom font sau qua bundled `assets/fonts/`
- Nếu thêm font → bundle local, KHÔNG fetch runtime

### Shape
- Material3 default Shapes

### Iconography
- Material Icons Extended (`androidx.compose.material:material-icons-extended`)

---

## Storage

### Room Database
- File: `thirtysix_browser.db`
- Tables (planned, có thể đổi khi vào Spec 005):
  - `bookmarks` — `id`, `title`, `url`, `parent_folder_id?`, `created_at`, `sort_order`
  - `bookmark_folders` — `id`, `name`, `parent_id?`, `created_at`
  - `history` — `id`, `url`, `title`, `visited_at`, `favicon_url?`
  - `tabs` — `id`, `url`, `title`, `is_incognito`, `position`, `created_at`, `last_active_at`

### DataStore Preferences
- File: `thirtysix_settings`
- Keys (planned):
  - `theme_mode` — Light/Dark/System
  - `language_code` — locale code
  - `search_engine` — Google/DuckDuckGo/Bing (default Google)
  - `default_home_url`
  - `is_onboarding_completed`
  - `tracker_blocker_enabled` (nếu làm Spec 019)

### Không dùng
- ❌ SharedPreferences (legacy)
- ❌ Cloud sync
- ❌ Account / login
- ❌ Analytics SDK
- ❌ Crash reporting tự động (cân nhắc opt-in sau)

---

## Performance Targets

- App cold start (splash → ready): ≤ 1.5s trên Pixel 5+
- WebView page load: phụ thuộc network, không phải metric chính
- UI: 60fps animation + scroll trên Pixel 5+
- Tab switcher: ≤ 200ms để hiển thị grid preview (10 tabs)
- App APK size: ≤ 15MB (debug), ≤ 10MB (release với R8)
- AAB upload size: ≤ 8MB

---

## Packages dự kiến (sẽ verify version + 16KB tại thời điểm thêm)

### Version Catalog (`gradle/libs.versions.toml`)

| Package | Mục đích | 16KB safe? |
|---------|----------|-----------|
| `androidx.core:core-ktx` | Kotlin extensions | ✅ |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle | ✅ |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel + Compose | ✅ |
| `androidx.activity:activity-compose` | Activity + Compose | ✅ |
| `androidx.compose:compose-bom` | Compose BOM | ✅ |
| `androidx.compose.ui:ui` | Compose UI | ✅ |
| `androidx.compose.material3:material3` | Material 3 | ✅ |
| `androidx.compose.material:material-icons-extended` | Material Icons | ✅ |
| `androidx.navigation:navigation-compose` | Navigation | ✅ |
| `com.google.dagger:hilt-android` | DI | ✅ (Kotlin only) |
| `androidx.hilt:hilt-navigation-compose` | Hilt + Nav | ✅ |
| `androidx.room:room-runtime` | Room | ✅ |
| `androidx.room:room-ktx` | Room + Coroutines | ✅ |
| `androidx.room:room-compiler` | KSP | ✅ |
| `androidx.datastore:datastore-preferences` | DataStore | ✅ |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines | ✅ |
| `androidx.webkit:webkit` | WebView extensions | ✅ |
| `androidx.core:core-splashscreen` | Splash API | ✅ |
| `androidx.appcompat:appcompat` | Locale switching | ✅ |
| `coil-kt:coil-compose` | Favicon loading (TBD) | ⚠️ verify khi thêm |

> Mọi version cụ thể sẽ được lookup tại thời điểm thêm. Không hardcode version từ trí nhớ.

---

## Key Decisions Log

### 2026-05-01 — Spec 005 hoàn tất

**Room database schema shipped** (branch `005-room-database-schema`, pre-PR; 33/33 tasks complete).

**Versions chốt** (verified `developer.android.com/jetpack/androidx/releases/room` + `central.sonatype.com` + `github.com/cashapp/turbine/releases` + `github.com/robolectric/robolectric/releases` 2026-05-01):

- **Room 2.8.4** (released 2025-11-19) — `room-runtime` + `room-ktx` (impl) + `room-compiler` (ksp) + `room-testing` (testImpl). Pure Kotlin/Java, **zero `.so`** → 16KB CI gate auto-passes. minSdk floor 23 (project 24 above floor).
- **Turbine 1.2.1** (released 2025-06-11) — Cash App Flow testing library. Pure JVM JAR, pulls `kotlinx-coroutines-core 1.10.2` transitively.
- **Robolectric 4.16.1** (released 2026-01-21) — required to provide Android Context to Room in JVM unit tests. Robolectric DOES ship native code BUT it loads only into JVM test process — NOT packaged into release APK; Constitution §IX 16KB gate inspects release APK only → does NOT apply. **Pinned to SDK 33** via `app/src/test/resources/robolectric.properties` because SDK 34+ requires JDK 17 and SDK 36 requires JDK 21; project Kotlin toolchain is JDK 11. Room behaviour is SDK-independent for our entity / DAO test surfaces (CRUD + observers + FK SET NULL + WAL).

**Schema decisions** (per spec.md 11 clarifications):

- 4 entities: `BookmarkEntity` / `BookmarkFolderEntity` / `HistoryEntryEntity` / `TabEntity`. Auto-increment Long primary keys. Timestamps stored as `Long` epoch millis (no `TypeConverter` v1.0).
- Bookmark folder support shipped (self-FK on `BookmarkFolderEntity.parent_id`). FK `ON DELETE SET NULL` on both `bookmark.parent_folder_id` and `folder.parent_id` — orphan-to-root preserves user data.
- `favicon_url` deferred (no Coil/loading infra yet).
- `is_incognito` NOT in `TabEntity` — incognito = in-memory only (Spec 011/012 enforces persistence-by-omission).
- History = chronological log (each visit = new row); same URL multiple visits → multiple rows distinguished by `visited_at`. No UNIQUE constraint on bookmark URL either.
- 3 indexes shipped at v1: `history.visited_at`, `bookmark.parent_folder_id`, `tab.position`. Designed for power-user envelope (10K bookmarks / 100K history / 500 tabs) per Q5 clarification.
- Strict-no-destructive migration: NO `fallbackToDestructiveMigration*`. Schema export wired to `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` (committed to git).

**Backup exclusion** (Q4 clarification — stricter than Constitution §VII baseline which permits opt-in backup): `backup_rules.xml` (Android <12) and `data_extraction_rules.xml` (Android 12+) both exclude `thirtysix_browser.db` + `thirtysix_browser.db-wal` + `thirtysix_browser.db-shm` from cloud backup AND device-to-device transfer. Privacy-first stance — DB never leaves device.

**Hilt module placement**: `app/src/main/kotlin/.../di/DatabaseModule.kt` (NEW top-level `di/` package). `core/di/DispatcherModule.kt` from Spec 002 untouched. Per Constitution §IV "ALL @Module annotations in `di/` package" — both placements valid; chose top-level for application-scoped composites (parallel to upcoming `RepositoryModule` Specs 013/014, `NetworkModule` Spec 007).

**Out-of-scope (per incremental scope preference)**: Repository / Mapper / Domain model / `core/constants/` deferred to consuming specs (011/013/014). Encryption-at-rest, ContentProvider, data seeding, retention/pruning, full-text search, paging — all out of v1.0.

**Test coverage**: 29 new unit tests across 7 test files. Total project: 51 unit tests (was 22 after Spec 003+004 baseline).

| Test file | Tests | Covers |
|---|---|---|
| `BookmarkDaoTest` | 8 | CRUD + observer (Turbine) + FK orphan-to-root + same-URL-multi-row |
| `BookmarkFolderDaoTest` | 4 | nested folder + self-FK orphan + observeChildren ordering |
| `HistoryDaoTest` | 6 | chronological log invariant + range delete + Flow emission |
| `TabDaoTest` | 6 | maxPosition + position ordering + lastActiveAt update |
| `WalConcurrencyTest` | 1 | SC-006 — two coroutines concurrent insert under WAL |
| `AppDatabaseConfigTest` | 2 | FR-007 PRAGMA journal_mode + SC-004 negative-path migration |
| `DatabaseModuleSmokeTest` | 2 | SC-010 structural smoke — module providers + DAO round-trip |

`WalConcurrencyTest` uses `runBlocking` (real time) instead of `runTest` (virtual time) because the concurrency claim is a real-wall-clock invariant; 1 s budget relaxed to 5 s for Robolectric-JVM SQLite shadow overhead — production-device 1 s target documented in spec.

**Quality gates result** (2026-05-01 local run):

- `./gradlew assembleDebug` ✅
- `./gradlew testDebugUnitTest` ✅ 51/51 pass (29 new from Spec 005)
- `./gradlew lintDebug` ✅ zero warnings
- `./gradlew detekt` ✅ zero violations, baseline UNCHANGED
- `./gradlew ktlintCheck` ✅ zero violations
- `./gradlew assembleRelease` ✅ APK = 1.4 MB (DOWN from 1.49 MB Spec 004 baseline — R8 re-shrink absorbed Room runtime)
- 16KB CI script ✅ all 12 native lib entries aligned 0x4000 (only `libandroidx.graphics.path.so` from Compose; Room added zero natives)
- `git grep "fallbackToDestructiveMigration|allowMainThreadQueries()"` in `app/src/main/` = zero matches ✅

**Files created** (16 production + test):

- `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkEntity.kt`
- `.../entity/BookmarkFolderEntity.kt`
- `.../entity/HistoryEntryEntity.kt`
- `.../entity/TabEntity.kt`
- `.../data/local/dao/BookmarkDao.kt`
- `.../dao/BookmarkFolderDao.kt`
- `.../dao/HistoryDao.kt`
- `.../dao/TabDao.kt`
- `.../data/local/database/AppDatabase.kt`
- `.../di/DatabaseModule.kt`
- `app/src/test/.../dao/TestDatabaseFactory.kt`
- `app/src/test/.../dao/{Bookmark,BookmarkFolder,History,Tab}DaoTest.kt` (4)
- `app/src/test/.../database/WalConcurrencyTest.kt`
- `app/src/test/.../database/AppDatabaseConfigTest.kt`
- `app/src/test/.../di/DatabaseModuleSmokeTest.kt`
- `app/src/test/resources/robolectric.properties`
- `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` (auto-generated, committed)

**Files modified** (4):

- `gradle/libs.versions.toml` (+ room, turbine, robolectric versions + 6 library coords)
- `app/build.gradle.kts` (+ Room deps + KSP `room.schemaLocation` arg + Robolectric `testOptions.unitTests.isIncludeAndroidResources = true`)
- `app/src/main/res/xml/backup_rules.xml` (DB + WAL sidecars excluded)
- `app/src/main/res/xml/data_extraction_rules.xml` (DB + WAL sidecars excluded under both `<cloud-backup>` and `<device-transfer>`)

Constitution Check 11/11 PASS pre- and post-design. Stricter than minimum on §I + §VII (default-off backup vs constitutional "opt-in" baseline).

---

### 2026-05-01 — Spec 004 hoàn tất

**Multi-Language Localization Foundation shipped** (branch `004-localization-multi-language`, pre-PR; 24/24 task complete except manual emulator gates 3-7 deferred to user device verification).

**Locales config strategy** (verified 2026-05-01):

- Single canonical `app/src/main/res/xml/locales_config.xml` with 8 `<locale>` children; standard Android 13+ schema (no AGP `app_locales` Gradle DSL — explicit XML form keeps the supported-locales contract reviewable in version control).
- `AndroidManifest.xml` `<application>` declares `android:localeConfig="@xml/locales_config"` PLUS `tools:targetApi="33"` to suppress `UnusedAttribute` lint (attribute requires API 33+ but minSdk = 24; original [research.md R1](../../specs/004-localization-multi-language/research.md#r1--locales-config-xml-schema-for-android-13) said no `tools:targetApi` needed but real lint flagged it — fixed during implementation).
- Reach split: per-app picker on Android 13+ only; API 24–32 users get device-wide locale via standard resource qualifier matching. Accepted tradeoff per Constitution VIII.

**Chinese locale tag form**: bare `values-zh/` (no region qualifier), Simplified Chinese. Android resource resolver matches all `zh-*` device locales (zh-CN, zh-TW, zh-HK, zh-Hans, zh-Hant) → fallback to `values-zh/`. Trade Traditional readability for broader fallback coverage in v1.0; future spec may add `values-zh-rTW/` if Traditional becomes a priority.

**Brand-name translation rule** (per [research R6](../../specs/004-localization-multi-language/research.md#r6--app_name-translation-strategy-across-8-locales-q1-clarification-implementation)): "ThirtySix" stays in Latin script across all 8 locales; only descriptor "Browser" / "Navigator" is translated. Industry convention (Chrome, Firefox, Edge) — translating numerals would create 8 different brand names harming searchability + Play Store discoverability.

Per-locale `app_name`:
- en: `ThirtySix Browser` (canonical)
- vi: `Trình duyệt ThirtySix`
- de: `ThirtySix Browser` (loanword)
- ru: `ThirtySix Браузер`
- ko: `ThirtySix 브라우저`
- ja: `ThirtySix ブラウザ`
- zh: `ThirtySix 浏览器`
- fr: `Navigateur ThirtySix`

**Lint severity wiring** (Q2 clarification + FR-008 / SC-005): Added `error += listOf("MissingTranslation", "ExtraTranslation")` to `app/build.gradle.kts` `android.lint { }` block, AFTER the existing `disable += listOf(...)` block. Belt-and-suspenders on top of `warningsAsErrors = true` — explicit error severity survives any future relaxation of the global flag. Both negative-path verifications passed locally (delete a key → `MissingTranslation` blocks build; add an extra key → `ExtraTranslation` blocks build).

**No `AppCompatDelegate.setApplicationLocales` in this milestone** (per [research R4](../../specs/004-localization-multi-language/research.md#r4--why-no-appcompatdelegatesetapplicationlocales-in-this-spec)): That API is for in-app language switching (Spec 016 Settings Screen). Spec 004 covers automatic device-locale detection (P1) and Android 13+ system per-app picker (P2) — both work entirely through Android's resource system + manifest `localeConfig`. The Constitution VIII intent ("locale switching takes effect without app restart") is satisfied by OS-driven `Activity.recreate()` triggered when locale changes; Compose recomposes with new resources.

**No `androidx.appcompat:appcompat` introduced**: original [project-context.md packages-dự-kiến](#packages-dự-kiến-sẽ-verify-version--16kb-tại-thời-điểm-thêm) wishlist entry for AppCompat is deferred to Spec 016 when in-app switcher is implemented. Spec 004 needs zero new packages.

**Late-discovery cleanup during implementation**:

- `app/build.gradle.kts` lint `tools:targetApi="33"` for `localeConfig` attribute — research originally claimed no annotation needed; lint disagreed at runtime.
- `settings.gradle.kts:31` had `rootProject.name = "ThirdtySixBrowser"` (typo replicated at Gradle root) → fixed to `"ThirtySixBrowser"`. Documented as out-of-scope-but-applied in research R8 update. Doc files retain historical typo references intentionally as decision-log entries.

**Quality gates result** (2026-05-01 local run):

- `./gradlew assembleDebug` ✅
- `./gradlew lintDebug` ✅ (positive path)
- `./gradlew lintDebug` ✅ FAIL on missing translation (T015 negative)
- `./gradlew lintDebug` ✅ FAIL on extra translation (T016 negative)
- `./gradlew testDebugUnitTest` ✅ 12/12 pass (Spec 003 baseline, no new tests added)
- `./gradlew detekt ktlintCheck` ✅ zero violations, baseline unchanged
- `./gradlew assembleRelease` ✅ APK = 1.49 MB (no significant delta vs Spec 003)
- 16KB CI script ✅ all 12 native lib entries aligned 0x4000 (3 ABIs × 4 entries from `libandroidx.graphics.path.so`)
- `git grep -i thirdty` (excluding `specs/`, `.claude/claude-app/`, `CLAUDE.md` doc references) = zero matches in source

**Files created** (10 new):
- `app/src/main/res/values-vi/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-ko/strings.xml`
- `app/src/main/res/values-ja/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values-fr/strings.xml`
- `app/src/main/res/xml/locales_config.xml`

**Files modified** (4):
- `app/src/main/res/values/strings.xml` (app_name typo fix + comment refresh)
- `app/src/main/AndroidManifest.xml` (`android:localeConfig` + `tools:targetApi="33"`)
- `app/build.gradle.kts` (lint `error += listOf("MissingTranslation", "ExtraTranslation")`)
- `settings.gradle.kts` (rootProject.name typo fix)

**Manual gates DEFERRED to user device verification** (cannot run from local Mac without Android emulator):
- Gate 3 (system per-app picker shows 9 entries on Android 13+)
- Gate 4 (per-locale visual sweep across 8 locales)
- Gate 5 (device-wide locale fallback on Android 7–12)
- Gate 6 (unsupported-locale fallback to English)
- Gate 7 (regional variant fallback fr-CA → fr, zh-TW → zh, etc.)

User to run these on a real Android 13+ emulator/device before marking PR-ready.

---

### 2026-05-01 — Spec 003 hoàn tất

**Theme system shipped** (PR #4 merged, commit `856f0bc`):

- `presentation/theme/` migrated from `ui/theme/` (rename via diff — preserves intent, not history-preserving rename since template-era `ui/` package was wrong layer per Constitution §III).
- `ThirtySixTheme` Composable (renamed from template `ThirdtySixBrowserTheme` typo) wires Light/Dark/System + dynamic color (Android 12+) via `dynamicLightColorScheme(LocalContext.current)` / `dynamicDarkColorScheme(LocalContext.current)`. Static fallback uses pinned `LightColorScheme` / `DarkColorScheme`.
- Theme persistence: in-memory `MutableState` in `MainActivity` for Spec 003 — DataStore wire deferred to **Spec 006**, UI toggle deferred to **Spec 016**.

**Brand color decision** (verified via Material Theme Builder export 2026-05-01):

- Primary seed: **Deep Teal** `#0F766E` (light primary) / `#5EEAD4` (dark primary) — semantic of "calm, trustworthy, productive" matches Tools/Productivity Play category.
- Tertiary seed: **Cyan** `#0891B2` (light) / `#67E8F9` (dark) — provides visual contrast for accent CTAs without competing with primary.
- Full M3 ColorScheme: ~30 roles × 2 schemes pinned by hand in `Color.kt` (NOT via XML resources — Constitution §III "no hardcoded colors" exemption: hex literals here ARE the constants, all consumers reference via `MaterialTheme.colorScheme.*`).
- **File-level `@Suppress("MagicNumber")`** with rationale comment block — Detekt rule cannot model "this file IS the constants definition" semantics.

**Typography decision** (Poppins + Inter — bundled local, NOT downloadable):

- **Poppins** (heading): weight Medium 500 + SemiBold 600 — geometric grotesque, brand-recognizable.
- **Inter** (body/UI): weight Regular 400 + Medium 500 — high screen legibility at small sizes.
- 4 `.ttf` static files in [res/font/](app/src/main/res/font/) — `poppins_medium.ttf` (158KB), `poppins_semibold.ttf` (157KB), `inter_regular.ttf` (407KB), `inter_medium.ttf` (411KB). Total ~1.1MB raw (R8 compresses).
- Override only `fontFamily` + `fontWeight` on Material3 default `Typography()` — preserve M3 size/lineHeight/letterSpacing tokens via `.copy()`.
- Reason for bundled: Constitution §IV (offline-first) + privacy (no Google Fonts CDN call). Cost: ~1MB APK delta accepted.

**Spacing tokens** (5-token scale in `Spacing.kt`):

- `xs=4dp / sm=8dp / md=16dp / lg=24dp / xl=32dp` — Material 4dp baseline grid.
- For padding/margin/spacer ONLY. Icon/avatar sizing uses literal `.dp` per spec FR-010 (semantic difference: spacing vs. fixed sizing).

**Cold-start window flash fix** (FR-026/027 — clarification option A):

- `themes.xml` (light) + `values-night/themes.xml` (dark) set `windowBackground` per system theme → eliminates white flash when launching in dark mode before Compose renders first frame.
- Trade-off accepted vs. option B (bridging splash → theme): simpler, no new APIs, works on all minSdk 24+.

**WCAG SC-010 gate** (clarification option A):

- Material Theme Builder built-in checker verifies 24+ critical color pair contrast ≥ 4.5:1 (normal text) / 3:1 (large text + icons) **pre-export**.
- Reason: Detekt/Lint cannot enforce visual contrast; gate moved to design-tool stage (one-time, before each `Color.kt` rewrite).

**Detekt baseline cleanup**:

- 9 entries from Spec 001 (3 FunctionNaming + 6 MagicNumber on template theme colors) cleared — those files were rewritten in Spec 003 so the baseline cover is no longer needed. Detekt `FunctionNaming.ignoreAnnotated: ['Composable']` config (added Spec 002) handles Compose PascalCase structurally.

**No new packages introduced**:

- Compose BOM 2026.04.01 (Material3 1.4.0) supplies all required APIs: `dynamicLightColorScheme` / `dynamicDarkColorScheme` / `FontFamily(Font(R.font.*))`. 16KB risk = zero (no new `.so`).

**Quality gates result**: All 6 Gradle tasks pass (assembleDebug, assembleRelease, testDebugUnitTest 12/12 pass — 9 from Spec 002 + 3 new: SpacingTest, ThemeModeTest, TypographyTest, lintDebug, detekt, ktlintCheck). 16KB script: `libandroidx.graphics.path.so` still aligned 0x4000.

**NOT created** (deferred per scope discipline):

- Theme persistence (DataStore) — Spec 006
- UI theme toggle in Settings — Spec 016
- Custom shape tokens — Material3 default `Shapes()` retained, file is placeholder
- 7 locale string resources — Spec 004

---

### 2026-05-01 — Spec 002 hoàn tất

**Hilt + KSP wiring** (verified `central.sonatype.com` + GitHub releases 2026-05-01):

- **Hilt 2.59.2** (2026-02-20) — first AGP-9-compat release. KSP processor artifact = `com.google.dagger:hilt-compiler` (NOT `hilt-android-compiler` — that's kapt-era; using wrong ID with `ksp(...)` compiles silently but generates zero Hilt code).
- **KSP 2.3.7** (2026-04-22) — supports Kotlin 2.3.x. KSP versioning decoupled from Kotlin since KSP 2.3.0.
- **Day-1 metadata smoke test PASSED first-try** — no kapt/Kotlin-downgrade fallback needed. Risk #1 in research.md mitigated naturally.

**Navigation + Lifecycle**:

- `androidx.navigation:navigation-compose:2.9.8` (2026-04-22)
- `androidx.hilt:hilt-navigation-compose:1.3.0` (2025-09-10)
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` (2025-11-19) — NOT BOM-managed; Compose BOM only manages `androidx.compose.{animation,foundation,material,material3,runtime,ui}`. Lifecycle group pinned separately via `lifecycle = "2.10.0"` key.

**Test deps**: `kotlinx-coroutines-test:1.10.2` for `BaseViewModel.launchSafely` testing (`Dispatchers.setMain(StandardTestDispatcher())` pattern).

**Core utility decisions** (from spec.md clarifications Q1-Q4):

- `Result<T>` = 2 terminal states only (`Success<T>`, `Error`). Loading managed by `UiState.isLoading`.
- `BaseViewModel.launchSafely(onError, block)` — caller-provided `(AppError) -> Unit` callback.
- Exception → AppError mapping rule: `IOException → Network`, `SQLiteException → Database`, else `→ Unknown`. `CancellationException` re-thrown to preserve coroutine cancellation idiom.
- Constitution §III "ALL @Module annotations live in `di/` package" interpreted liberally: any folder named `di/` (including `core/di/`, future top-level `app/.../di/`). Spec 002 only creates `core/di/DispatcherModule.kt`.

**Detekt config addition**: `FunctionNaming.ignoreAnnotated: ['Composable']` — Compose PascalCase convention exempted at config level (cleaner than baseline cover). Baseline file UNCHANGED from Spec 001.

**Quality gates result**: 6/6 Gradle tasks pass (assembleDebug, assembleRelease, testDebugUnitTest 9/9 pass, lintDebug, detekt, ktlintCheck). 16KB script: all 4 ABIs of `libandroidx.graphics.path.so` align 0x4000 (no new `.so` introduced). APK release size delta vs Spec 001 = +88KB (SC-007 budget 1MB easy pass).

**Module structure created** (per "làm đến đâu tạo đến đó"):

```
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── ThirtySixApplication.kt          (@HiltAndroidApp)
├── MainActivity.kt                  (@AndroidEntryPoint)
├── core/{base,result,error,dispatcher,di}/      (5 dirs, 6 files)
└── presentation/{navigation,browser,tabs,bookmarks,history,downloads,settings,onboarding}/
                                     (8 dirs, 9 files: AppDestination + AppNavGraph + 7 placeholder Screens)
```

**NOT created** (deferred to later specs per scope discipline):
- `core/constants/*` (only Spec-001 stub `AppConstants.kt` exists; new constants per spec)
- `data/`, `domain/` directories — Spec 005/013
- Top-level `app/.../di/` — Spec 005 (DatabaseModule)
- Repository / UseCase / Entity / Mapper classes — per-feature spec
- `BaseUseCase.kt` — Spec 005 or 013
- 7 locale string resources (`values-{vi,de,...}/`) — Spec 004

---

### 2026-05-01 — Spec 001 hoàn tất

**Versions chốt** (verified `central.sonatype.com` + `developer.android.com` 2026-04-30, refined 2026-05-01):

- **AGP 9.1.1** — pinned vào 9.1.x range vì Android Studio chưa support 9.2.0. Re-evaluate khi IDE update.
- **Kotlin 2.3.21** — bundle Compose Compiler plugin (matches version).
- **Gradle wrapper 9.5.0** — `distributionUrl=gradle-9.5.0-bin.zip` (note: lookup phải dùng path `9.5.0` không phải `9.5`).
- **Compose BOM 2026.04.01** — resolves Compose UI 1.11.0 + Material3 1.4.0; KHÔNG pin individual Compose versions.
- **Detekt 1.23.8 / ktlint plugin 14.2.0** — Detekt CV-05 risk (built against Kotlin 2.0.21) chưa surface vấn đề thực tế trong template code.

**JDK requirements**:

- Launcher: **JDK 17+** (AGP 9.x daemon requirement).
- Compile target: JDK 11 via Gradle Toolchain (auto-provision).
- Local dev: dùng `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` (JBR JDK 21) hoặc bất kỳ JDK ≥ 17 nào.

**Signing**:

- Constitution v1.2.0 §XI two-scope rule: distribution = release keystore (chưa có), dev = debug keystore fallback với warning mandatory.
- Implementation: `app/build.gradle.kts` đọc `KEYSTORE_PATH/PASSWORD/ALIAS/KEY_PASSWORD` từ `local.properties` hoặc env vars; nếu thiếu → fallback `signingConfigs.debug` + `println` warning.

**Static analysis disable list** (documented):

- Lint: `OldTargetApi`, `GradleDependency`, `AndroidGradlePluginVersion` — AGP 9.x DSL false positives.
- ktlint: `function-naming` (Compose PascalCase) via `@file:Suppress` per file + `.editorconfig` safety net.
- Detekt baseline: `app/detekt-baseline.xml` accepts 9 template-only violations (3 FunctionNaming + 6 MagicNumber on theme colors). Spec 003 sẽ rewrite những file này, baseline sẽ được clear.

**16KB readiness verified**:

- Compose ships native lib `libandroidx.graphics.path.so` cho 4 ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) — TẤT CẢ aligned 0x4000 (16KB) ✅.
- Gradle property `android.bundle.enableUncompressedNativeLibs` đã removed (deprecated in AGP 8.1+, default true).
- CI script `.specify/scripts/bash/verify-16kb-alignment.sh` đã active strict mode (không phải fail-soft skeleton nữa).

**CI structure** (6 jobs):

- build (assembleDebug)
- unit-test (testDebugUnitTest)
- lint (lintDebug)
- static-analysis (detekt + ktlintCheck)
- verify-16kb (assembleRelease + 16KB script)
- instrumented-test (`if: false` đến Spec 007/011)

**Source migration**: `app/src/{main,test,androidTest}/java/` → `kotlin/` qua `git mv` (preserve history). Theme name `ThirdtySixBrowserTheme` (typo từ template) tạm giữ — sẽ rename ở Spec 003 khi rewrite theme.

---

## App Store Strategy

### Positioning
- **Category**: Tools / Productivity (Google Play)
- **Content Rating**: Everyone
- **Privacy**: No analytics, no crash reporting by default, zero data transmitted to ThirtySix servers
- **Permissions tối thiểu**: Internet, ACCESS_NETWORK_STATE (cho download), READ/WRITE_EXTERNAL_STORAGE chỉ khi cần (Scoped Storage cho Android 11+)

### Permissions cần
| Permission | Khi |
|------------|-----|
| `android.permission.INTERNET` | v1 (bắt buộc cho WebView) |
| `android.permission.ACCESS_NETWORK_STATE` | v1 (offline detection) |
| `android.permission.POST_NOTIFICATIONS` | v1 (download progress, Android 13+) |

---

## CI/CD

GitHub Actions hiện đã có sẵn workflow `.github/workflows/`. Chạy cho mỗi PR:

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew connectedDebugAndroidTest` (instrumented, optional)

Sẽ bổ sung step verify 16KB alignment cho `.so` files (nếu có) ở Spec 001.
