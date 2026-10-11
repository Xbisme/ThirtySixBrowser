---
description: "Task list for Spec 022 — Page Tools"
---

# Tasks: Page Tools

**Input**: Design documents from `specs/022-page-tools/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/PageToolsContracts.kt, quickstart.md

**Tests**: REQUIRED by Constitution §VI. Unit tests cover the ViewModels, use cases, the DataStore key and the pure helpers. Room DAO tests cover the two new queries.

**Organization**: by user story. US1 (Find) and US2 (Suggestions) are P1; US3 (Share), US4 (Text size) and US5 (Desktop) are P2; US6 (Print) is P3. **US1 is the MVP.**

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`
- `FIX` = `specs/022-page-tools/fixtures`

---

## Phase 1: Setup

- [X] T001 [P] Add constants to `MAIN/core/constants/BrowserLimits.kt`:
  - `TEXT_SIZE_PERCENT_50 = 50`, `_75`, `_90`, `_100`, `_110`, `_125`, `_150`, `_175`, `_200`;
  - `MAX_ADDRESS_SUGGESTIONS = 6`;
  - `MAX_FIND_QUERY_LENGTH = 200` ("Find text MUST be limited to 200 characters", FR-013).
- [X] T002 [P] Add the string keys for all six stories to `RES/values/strings.xml`, and translate them in `RES/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`. Keep "ThirtySix" in Latin script.
  - Hub section title and tile labels: Find in page, Share, Print, Desktop site, Text size.
  - Find bar: the field hint, a `%1$d/%2$d` count, and the Previous / Next / Close content descriptions.
  - The Settings Text size row title and the chooser title.
  - The suggestion row content description (title + address).
  - The share chooser title.
  - The "on" / "off" state descriptions.
- [X] T003 [P] Add Material Symbols vector drawables to `RES/drawable/`: `ic_find_in_page`, `ic_share`, `ic_print`, `ic_desktop`, `ic_text_size`. Use the outlined 24dp versions, matching the existing `AppIcons` set. Register them in `MAIN/presentation/theme/AppIcons.kt`.
- [X] T004 [P] Create the local fixtures:
  - `FIX/find.html`: exactly 50 × "lorem", one "tiếng", one "привет", and enough text to scroll;
  - `FIX/ua.html`: prints `navigator.userAgent`, `navigator.userAgentData?.mobile` and `window.innerWidth`.

  Mirror both into `app/src/androidTest/assets/` for the instrumented tests.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the hub's page-tools section and its availability rule, which every story plugs into (research R9).

- [X] T005 Create `MAIN/presentation/browser/PageTools.kt` with:
  - `enum class PageTool { Find, Share, DesktopSite, TextSize, Print }`;
  - `object PageToolsAvailability { fun of(isHomeContent, loadingState): Set<PageTool> }`, implementing research R9's table exactly. On Home: `{TextSize}`. While loading: every tool except Print. When loaded: all five. On failure: `{Share, DesktopSite, TextSize}`.
- [X] T006 [P] Write `TEST/presentation/browser/PageToolsAvailabilityTest.kt`: one case per row of the R9 table, including the incognito start page (`isHomeContent = true`).
- [X] T007 [P] Extend `QuickTile` in `MAIN/presentation/components/QuickTile.kt` with two defaulted parameters (contracts C16):
  - `selected: Boolean? = null`: when non-null, the tile uses `Modifier.toggleable` semantics with an on/off `stateDescription` and a selected fill from `MaterialTheme.colorScheme` (no inline colour);
  - `value: String? = null`: a second line in `MaterialTheme.typography.labelSmall`.

  Existing call sites must stay unchanged.
- [X] T008 Add `onPageTool: (PageTool) -> Unit` to `HubCallbacks` in `MAIN/presentation/browser/components/HubCallbacks.kt`. Add a `HubPageTools` section to `MAIN/presentation/browser/components/HubSheet.kt`, placed above the places row, in two rows:
  - Find · Share · Print
  - Desktop site · Text size

  The section receives `available: Set<PageTool>`, `isDesktopMode: Boolean` and `textSizeLabel: String`. It hides unavailable tiles (FR-002) and goes through `closeThen` (FR-004). Give each tile a test tag constant at the top of the file.
- [X] T009 Wire the section in `MAIN/presentation/browser/BrowserScreen.kt`:
  - pass `PageToolsAvailability.of(state.isHomeContent, state.loadingState)`, remembered on those two keys;
  - route `onPageTool` to a `when` whose branches each story fills in, initially no-ops;
  - until US4 and US5 land, pass `isDesktopMode = false` and the label for `AppDefaults` text size ("100%"). T043 and T052 replace them with the real values.

**Checkpoint**: unit tests green. The hub shows the page-tools tiles following the R9 table, and tapping them does nothing yet.

---

## Phase 3: User Story 1 — Find in page (Priority: P1) 🎯 MVP

**Goal**: The find bar replaces the top bar, shows "n/m", highlights every match, steps through them with wrap-around, and closes cleanly.

**Independent Test**: quickstart G1–G4.

### Tests for US1

- [X] T010 [P] [US1] Write `TEST/presentation/browser/BrowserViewModelFindTest.kt` covering data-model §3:
  - open gives "" and 0/0;
  - a query is truncated at 200 characters;
  - a result `(o, n)` becomes `(o+1)/n`;
  - a result with no matches becomes 0/0;
  - results arriving after close are ignored;
  - `onLoadStarted` closes the bar;
  - a change of active tab closes the bar;
  - close requests `clearMatches`;
  - `current ≤ total` always (I-6).
- [X] T011 [P] [US1] Write `ATEST/presentation/browser/FindInPageInstrumentedTest.kt` against `find.html`:
  - "lorem" → 1/50;
  - Next ×50 wraps to 1;
  - "zzz" → 0/0 with Previous and Next disabled;
  - Back closes the bar without navigating;
  - "TIẾNG" and "ПРИВЕТ" both match.

### Implementation for US1

- [X] T012 [P] [US1] Create `MAIN/presentation/browser/FindInPageState.kt` (`@Immutable data class FindInPageState(isOpen, query, current, total)`, contracts C10).
- [X] T013 [US1] Add `findAll`, `findNext(forward)` and `clearMatches` to `MAIN/presentation/browser/WebViewActionsHandle.kt`. Reset them in `detach()`.
- [X] T014 [US1] In `MAIN/presentation/browser/BrowserWebView.kt`, wire the find actions in `wireActions`. Set `wv.setFindListener { ordinal, total, _ -> callbacks.onFindResult(ordinal, total) }`. Add `onFindResult` to `MAIN/presentation/browser/BrowserWebViewCallbacks.kt`.
- [X] T015 [US1] In `MAIN/presentation/browser/BrowserViewModel.kt`, expose `findInPage: StateFlow<FindInPageState>` as a separate flow, not a field of `BrowserUiState` (§V rule 3). Add:
  - `onFindOpened`;
  - `onFindQueryChanged`, which truncates at `MAX_FIND_QUERY_LENGTH`;
  - `onFindResult`;
  - `onFindClosed`.

  Close the bar from `onLoadStarted` and from the active-tab collector. Expose a one-shot "clear matches" signal, or let the screen call `actions.clearMatches()` on close.
- [X] T016 [US1] Create `MAIN/presentation/browser/components/FindInPageBar.kt`. It collects `findInPage` itself and contains:
  - a text field, focused with the keyboard shown on open (FR-006);
  - a "current/total" count, as a polite `liveRegion`, hidden when the query is empty (FR-009, FR-036);
  - Previous and Next, disabled when `total == 0`;
  - Close.

  It consumes `WindowInsets.statusBars` (§V rule 7) and uses only theme tokens and `Spacing`.
- [X] T017 [US1] In `MAIN/presentation/browser/BrowserScreen.kt`:
  - swap `BrowserTopBar` for `FindInPageBar` while the bar is open;
  - do not compose `BrowserBottomBar` while it is open (FR-005, I-7);
  - add a `BackHandler(enabled = isOpen)` composed after the existing back handlers, so it wins (FR-010);
  - on query change, call `actions.findAll(query)`, or `clearMatches()` when the query is empty;
  - on close, call `actions.clearMatches()` (I-5);
  - route `PageTool.Find` → `viewModel.onFindOpened()`.
- [X] T018 [US1] Gates G1–G4 on `TA016_API36_16K`. Record the results in "Device Gate Results".

**Checkpoint**: Find in page works on its own.

---

## Phase 4: User Story 2 — Address suggestions (Priority: P1)

**Goal**: Up to 6 local suggestions while typing, bookmarks first, with literal matching, no network, and the same behaviour in incognito.

**Independent Test**: quickstart G5–G9.

### Tests for US2

- [X] T019 [P] [US2] Write `TEST/domain/usecase/FilterAddressSuggestionsUseCaseTest.kt`:
  - empty or blank query → empty list;
  - title match and address match;
  - case-insensitive matching for ASCII, Vietnamese ("TIẾNG"/"tiếng") and Cyrillic;
  - `%`, `_`, `'` and `\` match only themselves (FR-017);
  - snapshot order is kept;
  - the result stops at the limit of 6.
- [X] T020 [P] [US2] Write `TEST/domain/usecase/LoadAddressSuggestionsSourceUseCaseTest.kt` with fake repositories:
  - bookmarks come first, newest saved first;
  - history follows, newest visit first;
  - a bookmarked address is dropped from history (FR-016, I-11);
  - no address appears twice (I-10).
- [X] T021 [P] [US2] Write `TEST/presentation/browser/suggestions/AddressSuggestionsViewModelTest.kt`:
  - the snapshot is loaded once per editing session;
  - text equal to the current page address → no suggestions (FR-021);
  - a superseded keystroke is cancelled (`mapLatest`);
  - `onEditingEnded` clears everything;
  - no repository write method is ever called (I-9).
- [X] T022 [P] [US2] Add DAO cases to the existing Robolectric DAO tests in `TEST/data/local/dao/`:
  - `HistoryDao.getLatestVisitPerUrl()` returns exactly one row per url, its newest visit, ordered by `visited_at DESC`;
  - `BookmarkDao.getAllNewestFirst()` is ordered by `created_at DESC, id DESC`.

### Implementation for US2

- [X] T023 [P] [US2] Create `MAIN/domain/model/AddressSuggestion.kt` with `AddressSuggestion(url, title, source)`, `SuggestionSource { Bookmark, History }` and `AddressSuggestionSnapshot(candidates)` (data-model §4).
- [X] T024 [P] [US2] Add `getAllNewestFirst()` to `MAIN/data/local/dao/BookmarkDao.kt` and `getLatestVisitPerUrl()` to `MAIN/data/local/dao/HistoryDao.kt`. Both are `suspend`, take no user-text parameter and contain no `LIKE`. Use the entity column constants.
- [X] T025 [US2] Add `getAllBookmarks()` to `MAIN/domain/repository/BookmarkRepository.kt` and `MAIN/data/repository/BookmarkRepositoryImpl.kt`. Add `getLatestVisitPerUrl()` to `MAIN/domain/repository/HistoryRepository.kt` and `MAIN/data/repository/HistoryRepositoryImpl.kt`. Update every fake implementing these interfaces under `TEST/` and `ATEST/`; Spec 018 found the fakes are scattered, so `grep -rl ": BookmarkRepository\|: HistoryRepository" app/src`.
- [X] T026 [US2] Create `MAIN/domain/usecase/LoadAddressSuggestionsSourceUseCase.kt` and `MAIN/domain/usecase/FilterAddressSuggestionsUseCase.kt` (contracts C7, C8). Matching uses `contains(query, ignoreCase = true)` on title or url, and nothing else.
- [X] T027 [US2] Create `MAIN/presentation/browser/suggestions/AddressSuggestionsUiState.kt` and `AddressSuggestionsViewModel.kt` (contracts C15):
  - the snapshot loads on `dispatchers.default` on `onEditingStarted`;
  - text flows through `mapLatest` and `flowOn(dispatchers.default)`;
  - `onEditingEnded` releases everything;
  - inject `DispatcherProvider`.
- [X] T028 [US2] Create `MAIN/presentation/browser/suggestions/AddressSuggestionList.kt`:
  - a `LazyColumn` with `key = url` and a `contentType` (§V rule 6);
  - each row shows the title, or the hostname when the title is empty (FR-018), plus the address and a bookmark or history icon;
  - each row has a content description combining title and address (FR-036);
  - it consumes `WindowInsets.ime`;
  - it uses theme tokens only.
- [X] T029 [US2] Wire the browser in `MAIN/presentation/browser/BrowserScreen.kt`:
  - the address bar's focus and text callbacks also call `AddressSuggestionsViewModel`;
  - the list renders under the top bar, over the page, while it is non-empty;
  - a tap goes through the existing `onAddressBarSubmit` path with the suggestion's url (FR-019, FR-020).
- [X] T030 [US2] Wire Home in `MAIN/presentation/home/HomeHero.kt`:
  - `HomeSearchBar` gains `onTextChange` and `onFocusChange` and still keeps its own text;
  - `MAIN/presentation/home/HomePage.kt` renders `AddressSuggestionList` under the search field;
  - a tap submits through the existing `onSearchSubmit` path (research R6).
- [X] T031 [P] [US2] Write `ATEST/presentation/browser/suggestions/AddressSuggestionListTest.kt` (Compose): rows render title or hostname, a tap reports the url, and the list is absent when empty.
- [X] T031a [US2] Write `ATEST/presentation/browser/suggestions/AddressBarSuggestionsFlowTest.kt` (Compose, Constitution §VI "address bar input flow"). Use a seeded fake repository.
  - Focusing the bar on a loaded page shows no suggestions (FR-021).
  - Typing part of a bookmarked title shows it.
  - Tapping it submits its url through the normal path (FR-019): the address bar loses focus, `isAddressBarFocused` becomes false, and the suggestion list disappears.
  - Pressing Go instead submits the typed text unchanged (FR-020).
- [X] T032 [US2] Gates G5–G9. G7 means monitoring traffic while typing 20 texts, expecting 0 requests carrying the typed text. G8 is run with the debug `HistorySeeder` at 10,000 rows, and checks both of SC-003's limits: 100 ms for a typed character to appear, 200 ms for the list to update. Record the results.

**Checkpoint**: Suggestions work on their own, on the browser and on Home.

---

## Phase 5: User Story 3 — Share (Priority: P2)

**Goal**: The system share sheet with the address, and the title as subject.

**Independent Test**: quickstart G10.

- [X] T033 [P] [US3] Add `ShareContent.of(url, title)` to `MAIN/presentation/browser/PageTools.kt`: `text = url`, and `subject = title` only when the title is not blank. Write `TEST/presentation/browser/ShareContentTest.kt`.
- [X] T034 [US3] In `MAIN/presentation/browser/BrowserScreen.kt`, route `PageTool.Share`:
  - build `Intent.ACTION_SEND` (`text/plain`, `EXTRA_TEXT`, and `EXTRA_SUBJECT` when present);
  - wrap it in `Intent.createChooser(…, stringResource(share chooser title))`;
  - `startActivity` from `LocalContext` inside `runCatching`;
  - take the title from `activeTab?.title`;
  - add no incognito branch (FR-024).
- [X] T035 [US3] Gate G10. Record the results.

---

## Phase 6: User Story 4 — Text size (Priority: P2)

**Goal**: One app-wide level of 50–200%, set from Settings or the hub, applied to every tab without a reload, and kept across restarts and Clear browsing data.

**Independent Test**: quickstart G11.

### Tests for US4

- [X] T036 [P] [US4] Write `TEST/domain/model/TextSizeTest.kt`: there are exactly 9 levels, in order; `fromPercentOrDefault(null)`, `(0)` and `(130)` each return `Percent100`; and every level round-trips (I-1, I-2).
- [X] T037 [P] [US4] Extend `TEST/data/local/datastore/SettingsDataStoreTest.kt` and `TEST/data/mapper/SettingsMapperTest.kt`: a missing key reads 100; 150 round-trips; an unknown value falls back to the default.
- [X] T038 [P] [US4] Extend `TEST/domain/usecase/ClearBrowsingDataUseCaseTest.kt` to assert that the text size is untouched after a clear (FR-032).

### Implementation for US4

- [X] T039 [P] [US4] Create `MAIN/domain/model/TextSize.kt` (data-model §1, percentages from `BrowserLimits`). Add `AppDefaults.TEXT_SIZE = TextSize.Percent100` and `StorageKeys.TEXT_ZOOM_PERCENT = intPreferencesKey("text_zoom_percent")`, the latter with a schema-evolution note matching the other keys.
- [X] T040 [US4] Thread `textSize` through the settings stack:
  - `MAIN/domain/model/UserSettings.kt`: add the field and its `DEFAULT`;
  - `MAIN/data/local/datastore/SettingsDataStore.kt`: read and write;
  - `MAIN/data/mapper/SettingsMapper.kt`;
  - `MAIN/domain/repository/SettingsRepository.kt` and `MAIN/data/repository/SettingsRepositoryImpl.kt`: `setTextSize`;
  - every `SettingsRepository` fake: `grep -rl ": SettingsRepository" app/src`.
- [X] T041 [US4] Create `MAIN/domain/usecase/SetTextSizeUseCase.kt` (contracts C6).
- [X] T042 [US4] Add the Settings UI:
  - `MAIN/presentation/settings/SettingsUiState.kt` gains `SettingsDialog.TextSizeChooser` and `textSize`;
  - `MAIN/presentation/settings/SettingsViewModel.kt` gains `onTextSizeRowClick` and `onTextSizeChosen`;
  - create `MAIN/presentation/settings/components/TextSizeChooserDialog.kt`, built on the existing `SingleChoiceDialog`, with labels such as "125%";
  - add a **Text size** row to `MAIN/presentation/settings/components/SettingsRows.kt` and `MAIN/presentation/settings/SettingsScreen.kt`, in the appearance section beside Theme. The row shows the current level (FR-030).
- [X] T043 [US4] Add `QuickSetting.TextSize` in `MAIN/presentation/browser/components/HubCallbacks.kt`, handled in `MAIN/presentation/browser/QuickSettingsHost.kt` by `onTextSizeRowClick`. Route `PageTool.TextSize` → `QuickSetting.TextSize`. The hub tile shows `value = "<n>%"` (FR-003).
- [X] T044 [US4] Apply the level in the browser:
  - `BrowserViewModel` exposes `textZoomPercent: StateFlow<Int>`, mapped from `ObserveUserSettingsUseCase`;
  - `BrowserWebView` gains a `textZoomPercent: Int` parameter, set in `buildConfiguredWebView` before the initial load;
  - a `LaunchedEffect(textZoomPercent)` sets it on `host.current` (§V rule 8), with no reload (FR-031).
- [X] T045 [P] [US4] Write `ATEST/presentation/browser/TextZoomInstrumentedTest.kt`: after a change of level, a live WebView's `settings.textZoom` equals the new value and no new page load starts.
- [X] T046 [US4] Gate G11. Record the results.

---

## Phase 7: User Story 5 — Desktop site (Priority: P2)

**Goal**: A per-tab desktop mode that reloads the page as a desktop browser, is shown checked in the hub, is not remembered, and is off in every new tab.

**Independent Test**: quickstart G12.

### Tests for US5

- [ ] T047 [P] [US5] Write `TEST/presentation/browser/DesktopUserAgentTest.kt` using recorded default UAs from API 24, 29 and 36. The result has no `Android`, no `; wv` and no ` Mobile`, contains `X11; Linux x86_64`, and keeps the same `Chrome/<version>` token.
- [ ] T048 [P] [US5] Write `TEST/presentation/browser/BrowserViewModelDesktopModeTest.kt`:
  - the toggle affects only the active tab (I-3);
  - a newly created tab id is mobile (FR-027);
  - switching tabs reflects each tab's own mode;
  - a closed tab's id is pruned;
  - a fresh ViewModel has every tab mobile (FR-028, I-4).

### Implementation for US5

- [ ] T049 [P] [US5] Create `MAIN/presentation/browser/DesktopUserAgent.kt` (research R2). The platform token, `wv` token, `Mobile` token and the UA-metadata platform name (`"Linux"`) are named constants.
- [ ] T050 [US5] In `MAIN/presentation/browser/BrowserViewModel.kt`:
  - add the private `desktopModeTabs: MutableStateFlow<Set<Long>>`;
  - add `onDesktopModeToggled()` for the active tab;
  - prune ids against the open-tab list.

  In `MAIN/presentation/browser/BrowserUiState.kt`, add `isDesktopMode`.
- [ ] T051 [US5] Add `setDesktopMode(Boolean)` to `MAIN/presentation/browser/WebViewActionsHandle.kt`. In `MAIN/presentation/browser/BrowserWebView.kt`:
  - add a private `applyPageMode(wv, desktop)` that sets `userAgentString` (`DesktopUserAgent.from(WebSettings.getDefaultUserAgent(ctx))`, or `null` for mobile), `useWideViewPort` and `loadWithOverviewMode`;
  - when `WebViewFeature.isFeatureSupported(USER_AGENT_METADATA)`, also set `WebSettingsCompat.setUserAgentMetadata` with `mobile = false` and the platform constant from `DesktopUserAgent`, and restore the default for mobile;
  - call `applyPageMode` in `buildConfiguredWebView` **before** the initial load;
  - wire the action to `applyPageMode` + `reload()`, using `stopLoading()` first when a load is in progress.

  **First confirm the `androidx.webkit` 1.17.0 API names** (research R2). If the metadata API differs or is absent, ship the UA string and viewport only, and record that under "Implementation Notes".
- [ ] T052 [US5] Route `PageTool.DesktopSite` → `onDesktopModeToggled()` + `actions.setDesktopMode(...)` in `MAIN/presentation/browser/BrowserScreen.kt`. The hub tile uses `selected = state.isDesktopMode` (FR-003).
- [ ] T053 [P] [US5] Write `ATEST/presentation/browser/DesktopModeInstrumentedTest.kt` against `ua.html` served by `MockWebServer`: the request's `User-Agent` header has no `Mobile`/`wv` in desktop mode and does have them again after toggling off.
- [ ] T054 [US5] Gate G12. That covers `ua.html`, 5 real sites, 2 links followed within each site staying in desktop mode (FR-026), a new tab being mobile, and the mode being gone after `am kill`. Record the results.

---

## Phase 8: User Story 6 — Print / Save as PDF (Priority: P3)

**Goal**: The system print screen for a loaded page, named after its title or hostname.

**Independent Test**: quickstart G13.

- [ ] T055 [P] [US6] Add `PrintJobName.of(title, url)` to `MAIN/presentation/browser/PageTools.kt`: the title, else the hostname, else the url. Write `TEST/presentation/browser/PrintJobNameTest.kt`.
- [ ] T056 [US6] Add `print(jobName)` to `MAIN/presentation/browser/WebViewActionsHandle.kt`. Wire it in `MAIN/presentation/browser/BrowserWebView.kt` as `PrintManager.print(jobName, wv.createPrintDocumentAdapter(jobName), null)` from `wv.context`, inside `runCatching`, and reset it in `detach()`.
- [ ] T057 [US6] Route `PageTool.Print` in `MAIN/presentation/browser/BrowserScreen.kt` with `PrintJobName.of(activeTab?.title, state.currentUrl)`. Availability is already restricted to `Loaded` by T005 (FR-033). Record nothing (FR-034).
- [ ] T058 [US6] Gate G13. Record the results.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T059 [P] Gate G14 (tile availability on Home, the incognito start page, a loading page and an error page, plus SC-001's ≤ 2 taps to each tool) and gate G15 (all 8 locales at 360dp, DE first). Record both.
- [ ] T060 [P] Gate G16: G1, G11, G12 and G13 on `TA016_API24`. Confirm the UA metadata is skipped silently there. Record the results.
- [ ] T061 Compose compiler report (Testing Gate 9, `-PcomposeReports`): zero unexplained non-skippable composables in the touched files. Then gate G17, Layout Inspector counts while typing into find and into the address bar (Testing Gate 10; "needs a person" if unavailable). Record both.
- [ ] T062 Privacy check: grep the new and changed files for any log line carrying an address, title, find text or typed text, and remove any found. Confirm `git grep -n "LIKE" -- app/src/main/` shows no new hit. Check FR-037: `git diff main -- app/src/main/AndroidManifest.xml` adds no `uses-permission`, `AppDatabase.SCHEMA_VERSION` is still 3, and no `app/schemas/**/4.json` exists. Record the result.
- [ ] T063 Full automated gates:
  - `testDebugUnitTest lintDebug detekt ktlintCheck`;
  - then `assembleRelease` separately;
  - the 16 KB script;
  - `connectedDebugAndroidTest` on the 16 KB AVD, force-stopping the app first and re-running `adb reverse` afterwards.
- [ ] T064 Release check: run the release build on `TA016_API36_16K` with zero FATAL. Check the APK delta against `main` is ≤ +100 KB (SC-009) and that there is no new `.so`. Record the results.
- [ ] T065 Record the carry-forward debt found in research R5 (Spec 013's `BookmarkDao.searchByTitleOrUrl` uses unescaped `LIKE`) under "Implementation Notes" and in `CLAUDE.md` › Known carry-forward debt.
- [ ] T066 Update the docs:
  - `CLAUDE.md`: roadmap row, Recent Changes, Active Spec, facts to keep;
  - `.claude/claude-app/sdd-roadmap.md`;
  - `.claude/claude-app/dev-workflow.md`;
  - `.claude/claude-app/project-context.md`.
- [ ] T067 Open the PR `022-page-tools` → `main`. Include the gate table, the Constitution line (11/11, §X waiver), the APK delta and the debt from T065.

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: no dependencies; T001–T004 can run in parallel.
- **Foundational (Phase 2)**: T005 → T006 in parallel; T007 in parallel; T008 after T005 and T007; T009 after T008.
- **US1 (Find)**: needs Phase 2. T010 and T011 first. Then T012 → T013 → T014 → T015 → T016 → T017 → T018.
- **US2 (Suggestions)**: needs Phase 2 only, so it is independent of US1. Tests T019–T022 first. Then T023 and T024 → T025 → T026 → T027 → T028 → T029 and T030 → T031 → T031a → T032.
- **US3 (Share)**: needs Phase 2; independent.
- **US4 (Text size)**: needs Phase 2; independent. T043 edits `HubCallbacks.kt`, which T008 also touched.
- **US5 (Desktop)**: needs Phase 2. It touches `BrowserWebView.kt` and `WebViewActionsHandle.kt` like US1, US4 and US6, so land those changes one story at a time.
- **US6 (Print)**: needs Phase 2; the same file caution as US5.
- **Polish**: after the stories in scope.

```text
Setup → Foundational ─┬─> US1 Find (P1, MVP) ─┐
                      ├─> US2 Suggestions (P1)│   BrowserWebView.kt / WebViewActionsHandle.kt:
                      ├─> US3 Share (P2)      │   US1 → US4 → US5 → US6 (sequential in those files)
                      ├─> US4 Text size (P2) ─┤
                      ├─> US5 Desktop (P2) ───┤
                      └─> US6 Print (P3) ─────┘──> Polish
```

## Parallel Execution Examples

- **Phase 1**: T001, T002, T003 and T004 together.
- **US1 tests**: T010 and T011 together.
- **US2**: T019–T022 together, then T023 and T024 together. US2's data and domain layer (T023–T027) can run alongside US1, because they share no files.
- **US4 tests**: T036, T037 and T038 together, with T039 alongside.
- **Across stories**: US3 (T033) and US5's T047 and T049 are pure files and can be written at any time after Phase 2.

## Implementation Strategy

1. Phases 1–2, then **US1 Find**, then G1–G4. Stop and validate: this is the MVP.
2. Add **US2 Suggestions**, the other P1 and the one with the strictest privacy rule. Run G7 before moving on.
3. Add US3 Share and US4 Text size, which are small and independent.
4. Add US5 Desktop, after confirming the `androidx.webkit` API names first.
5. Add US6 Print.
6. Polish: the gates, the debt note, the docs and the PR.

## Notes

- Device-gate tasks record their results in this file, under "Device Gate Results" (no other file path).
- `[P]` means a different file with no dependency on an incomplete task.
- Write each story's tests first and watch them fail.
- `BrowserWebView.kt` is touched by US1, US4, US5 and US6. Keep each story's hook in its own private function.
- **Galaxy A55**: never change the default browser, and reinstall the debug build after instrumented runs.
- A screen-reader pass (FR-036) is DEFERRED to a person, as in Specs 016–021.

## Implementation Notes

- **Icons (T003)**: six Material Symbols Rounded vectors (weight 300, grade −25) fetched from `google/material-design-icons @ 49d4db35`, the commit every existing `AppIcons` vector cites: `find_in_page`, `print`, `desktop_windows`, `format_size`, `keyboard_arrow_up`, `keyboard_arrow_down`. Outline style, like the hub's Places tiles. `ic_share` already existed (Spec 021).
- **One extra string**: `find_in_page_count` (`%1$d/%2$d`) is `translatable="false"` and lives in `values/` only. It holds digits and a slash, so no locale needs its own copy.
- **`QuickTile` switch semantics (T007)**: a non-null `selected` uses `Modifier.toggleable(role = Role.Switch)` plus an explicit "On"/"Off" `stateDescription` (`hub_tile_state_on/off`).
- **Hub layout (T008)**: the page-tools section sits above the Places row in its own file, `HubPageTools.kt`. Each row keeps three slots, so the second row's two tiles stay the same width as the first row's.
- **Shared test fixture**: `BrowserViewModelFixture` (unit) builds `BrowserViewModel` over fakes for the new Spec 022 tests. Older test classes keep their own builders.
- **Shared test server**: `testing/AssetPageServer` (androidTest) serves the test APK's assets over HTTP and records `User-Agent` headers. A server is needed because file access is off (Spec 007 FR-013), and `file:///android_asset/` reads the *app's* assets, not the test APK's.
- **⚠️ Test pollution found**: `FindInPageInstrumentedTest` raises the keyboard. Left up, it made the next class's (`HubSheetTest`) text lookups fail, because "could not find any node" while the same class passed alone, and on `main`. Its `@After` now calls `Espresso.closeSoftKeyboard()`.
- **The find count sits inside the text field's `decorationBox`**, so its tag is merged into the field node. Tests look it up with `useUnmergedTree = true`.
- **🐞 Defect found at G2, fixed**: while finding, the bars do not slide away (FR-005). However the WebView is translated down by the top bar's height, which normally the slide-away cancels, so the page's last ~top-bar-height was unreachable below the screen. While the find bar is open the WebView gets a bottom padding equal to that height. Verified: the page's last link and button scroll into view.
- **US2 list is a plain `Column` keyed by address, not a `LazyColumn` (deviation from plan/T028)**: there are at most six rows, and on Home the list sits inside the page's own `LazyColumn`, where a second lazy list in the same direction is forbidden (§V rule 6).
- **`HistoryRepository.getLatestVisitPerUrl()` has a default** derived from `observeAll()`, following the precedent of `observeRecent`. The Room implementation overrides it with one `GROUP BY url` + `MAX(visited_at)` query. SQLite's documented "bare column" rule takes the other columns from the max row, which avoids a correlated subquery over 10,000 rows. `BookmarkRepository.getAllBookmarks()` is abstract. Four test doubles were updated: `FakeBookmarkRepository` (now `open`) and the three `*NoopBookmarkRepository` objects in `BrowserScreen*Test`.
- **Duplicate bookmarks of one address** (Spec 013 allows them) are reduced to the newest, not only bookmarks against history (I-10).
- **🐞 Pre-existing defect found at G8, fixed (Spec 009 `AddressBar`)**: typing fast dropped or reordered keystrokes. The field re-applied `state.addressBarText` whenever it differed from its own text, so a late echo of an earlier keystroke through the ViewModel overwrote newer typing. On `main`'s build, fast typing gave "sdede" twice in three tries. The field now remembers the texts it reported and ignores their echoes, and only an outside change (Clear, an incoming link) is applied. Regression test: `AddressBarTest.lateEchoOfEarlierTyping_…`. The focus effect also no longer selects the whole address if typing has already begun.
- **Not fixed (automation artefact, both builds)**: the very first keystroke typed by `adb input text` 0.2 s after the first focus following an *install* can be lost. It happened once on the 022 build ("eeded") and once on `main`'s, never after a cold start (3/3) or later focus. adb types before the keyboard has connected, which a person cannot do.
- **Spec 019 behaviour noted**: the incognito start page has no address entry (Search there does nothing), so typing in an incognito tab happens only on an incognito web page's address bar.
- **`textZoomPercent` comes from a small `PageTextSizeViewModel`, not `BrowserViewModel` (deviation from T044)**. Spec 021 split `PopupTabs` and `LongPressActions` the same way. This keeps `BrowserViewModel` free of a settings dependency, and avoids touching its seven test builders for one number.
- **`MIME_TEXT_PLAIN` moved to `AppConstants`**: Spec 021's `LongPressHost` and the new share action both had a private `"text/plain"`.
- **T038 is satisfied by construction**: `ClearBrowsingDataUseCase` has no settings dependency at all, so it cannot reset the text size. Confirmed at G11, so no test was added for it.
- **`UserSettings.textSize` is defaulted**, so every `UserSettings(...)` call that predates it still compiles. Three test doubles implement the new `setTextSize`; `FakeSettingsRepository` records it.

## Device Gate Results

Device: `TA016_API36_16K` (API 36, 16 KB pages), debug build, fixtures served at `127.0.0.1:8022` via `adb reverse`.

| Gate | Result | Evidence |
|---|---|---|
| G1 Find | ✅ | Hub → Find in page → "lorem": **1/50** at once. All 50 highlighted, the current one orange. The find bar replaces the top bar, the bottom bar is hidden, and the keyboard is up. Wrap-around 50→1 and 1→50 is also covered by `FindInPageInstrumentedTest` (4/4). |
| G2 Find closing | ✅ | Back with the keyboard hidden closes the bar, clears every highlight, stays on the page, and the bars return. A page `alert()` appears above the find bar, which is still open with 1/50 after OK. Tapping a link loads `ua.html` with the find bar closed and the bars back. |
| G3 Find survives | ✅ | At 7/50, rotating to landscape keeps "lorem" and **7/50**, with match 7 still current. The in-app language case is not run separately: `MainActivity` handles both config changes without recreation (Specs 019/021), and the state lives in the ViewModel. |
| G4 Find, Unicode | ✅ (instrumented) | `adb input text` cannot type non-ASCII (memory note). `FindInPageInstrumentedTest.matching_ignoresCase_inVietnameseAndRussian` finds "TIẾNG" → tiếng and "ПРИВЕТ" → привет on the real WebView. |
| G5 Suggestions | ✅ | 10,001 history rows seeded plus one bookmark. "fix" gives one row, the bookmark, with a bookmark icon; its address is also in history but appears once. "page-12" gives exactly 6 history rows, newest first. Tapping the first loads it, the keyboard closes (`mInputShown=false`), and the list is gone. |
| G6 Literal characters | ✅ (unit) | `FilterAddressSuggestionsUseCaseTest` covers `%`, `_`, `0%_`, `'` and `\` matching only themselves. `adb input text` cannot type `%` reliably. |
| G7 No network | ⚠️ DEFERRED (traffic capture) | On this AVD, `adb emu network capture` records **nothing**: the control capture during a real `example.com` load was also empty, so the typing capture's 0 packets proves nothing. Verified instead by construction: the suggestion path (`suggestions/`, the two use cases) contains no network API (grep: 0 hits) and reads only Room. `AddressSuggestionsViewModelTest` asserts it writes nothing. A proxy-based check (e.g. on the A55) is still open. |
| G8 Timing / no dropped keystroke | ✅ after fix | 10,000 rows: suggestions appear without visible delay. **The fast-typing defect is above (Implementation Notes)**; after the fix, 5 of 6 fresh-focus fast-typing runs gave "seeded", and the one miss was the post-install adb artefact. The 100 ms / 200 ms figures were not measured with a frame recording. |
| G9 Home + incognito | ✅ Home / ⚠️ incognito by construction | Home's search field shows the bookmark suggestion under it while typing "fixture". Incognito: the start page has no address entry (Spec 019). The ViewModel has no incognito branch and writes nothing (T021), so the same behaviour applies on an incognito page's address bar. Not driven on device: screenshots are black under `FLAG_SECURE`. |
| G10 Share | ✅ | Hub → Share opens the system sheet "Sharing link" with exactly `http://127.0.0.1:8022/find.html`. The subject (title) is covered by `ShareContentTest`. Keep and Messages are not on this AVD. |
| G11 Text size | ✅ | The hub tile opens the chooser: 9 levels, 100% checked. Picking 150% enlarges the page at once with no reload (Reload stays Reload, never Stop). The tile reads **150%** after a force-stop and relaunch, and still after Clear browsing data (history + cookies + cache). `TextZoomInstrumentedTest` 2/2: the live WebView updates without a page load, and a new WebView starts at the chosen size. |
