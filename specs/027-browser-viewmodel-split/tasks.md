---

description: "Task list for Spec 027 Browser View Model Split"
---

# Tasks: Browser View Model Split

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/page-store.md](contracts/page-store.md), [quickstart.md](quickstart.md)

**Tests**: existing suites are the safety net. The new tasks only add tests for the store and move each holder's tests with its concern, keeping every assertion (FR-009).

**Paths**:

| Short | Path |
|---|---|
| `F` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/feature/browser` |
| `P` | `F/presentation/browser` |
| `T` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/feature/browser` |
| `A` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/feature/browser` |

**Every extraction task** (T005–T009) moves the listed actions, state and private functions out of `P/BrowserViewModel.kt` into the new holder:

- The holder has `onAction`, an `XAction`, an `XEvent` (only when it has effects, Keep kind as today) and a `UiState`.
- It reads the shared values from `CurrentPageRepository` and writes them only as [contracts/page-store.md](contracts/page-store.md) allows.
- `BrowserRoute` obtains the holder as a default parameter, adds its fields to the assembled `BrowserUiState`, and adds an `EventEffect` for its events. Callbacks call the holder's `onAction`.
- The tests for that concern move to `T/presentation/browser/<Holder>Test.kt` over `BrowserHoldersFixture`, with their assertions unchanged.

Then run G1 and **commit** `refactor(027): <concern> holder`.

## Phase 1: Setup

- [X] T001 Record G0 on `90cd024` in "Baseline" at the end of this file:
  - unit and instrumented counts
  - release APK bytes
  - Compose report
  - `wc -l P/*.kt`
  - size-suppression count in `F`

  Keep the `90cd024` debug and release APKs in the session scratchpad.

## Phase 2: Foundational (blocks every extraction)

- [X] T002 Create the store:
  - `F/domain/model/CurrentPage.kt` with `url`, `title`, `activeTab: Tab?`, `isIncognito`, `generation: Long`.
  - `F/domain/repository/CurrentPageRepository.kt` with `page: StateFlow<CurrentPage>`, `setUrl`, `setTitle`, `setActiveTab`, `setIncognito`, `startNewPage()`. `startNewPage()` bumps the generation and clears the title. A change of active tab id also bumps the generation and clears the title.
  - `F/data/repository/CurrentPageRepositoryImpl.kt`, a `MutableStateFlow` that starts at the `@Named("default_home_url")` URL.
  - An `@ActivityRetainedScoped` binding in `F/di/`.
- [X] T003 [P] `T/data/repository/CurrentPageRepositoryImplTest.kt` covering:
  - the initial state
  - each setter is visible synchronously
  - `startNewPage` bumps the generation and clears the title
  - a new tab id bumps the generation, while the same tab id does not
- [X] T004 Route `BrowserViewModel` through the store with no other change:
  - `currentUrl`, `currentTitleCache`, `activeTabFlow` and incognito are read from and written to `CurrentPageRepository`. Its tab-flow collectors write `setActiveTab` and `setIncognito`.
  - `onLoadStarted` calls `startNewPage()`.
  - The find bar closes on `generation` changes instead of the direct calls.
  - `BrowserViewModelFixture` and the `BrowserViewModel(` constructions in tests build an impl instance.

  G1. **Commit** `refactor(027): browser page store`.

## Phase 3: User Story 2, one small owner per concern (P1)

**Goal**: each concern in its own holder (FR-003, FR-004). **Independent test**: G2 shape checks, and each concern's tests build only its collaborators.

- [X] T005 [US2] **Find in page**: `P/FindInPageViewModel.kt` and `P/FindInPageAction.kt` (`Opened`, `QueryChanged`, `Closed`, `Result`; `Page.FindResult` moves here).
  - It closes when `page.generation` changes.
  - `rememberFindCallbacks` and the find bar use it, and the WebView's `onFindResult` sends `FindInPageAction.Result`.
  - Tests: `BrowserViewModelFindTest` → `T/presentation/browser/FindInPageViewModelTest.kt`, plus "closes on a new load" and "closes on a tab switch" (moved from the browser tests).
- [X] T006 *(no download test existed on the browser holder; two new ones added)* [US2] **Download requests**: `P/DownloadRequestViewModel.kt`, `P/DownloadRequestAction.kt` (`Requested`, `StoragePermissionDenied`) and `P/DownloadRequestEvent.kt` (`Download(DownloadSnackbarEvent)`, Keep).
  - Page downloads and long-press downloads both send to it.
  - Move the download tests.
- [X] T007 [US2] **Page actions**: `P/PageActionsViewModel.kt`, `P/PageActionsAction.kt` (`StarTapped`, `ToggleDesktopMode`, `LongPressNewTab`), `P/PageActionsEvent.kt` (`Bookmark`, `TabLimit`, `SetDesktopMode`, Keep) and `P/PageActionsUiState.kt` (`isBookmarked`, `isDesktopMode`).
  - `isBookmarked` is derived from `page.url`, and the toggle reads `page.url` and `page.title`.
  - Desktop mode uses `page.activeTab` and keeps the `desktopMode` lookup.
  - Tests: StarToggle, DesktopMode and the long-press cases.
- [X] T008 [US2] **Address bar and Home**: `P/AddressBarViewModel.kt`, `P/AddressBarAction.kt` (`TextChange`, `FocusChange`, `Submit`, `Clear`, `HomePressed`, `HomeOverlayDismissed`, `HomeSubmit`, `NavigateFromHome`, `BackWithoutHistory`), `P/AddressBarEvent.kt` (`LoadUrl`, Keep) and `P/AddressBarUiState.kt` (`text`, `isFocused`, `homeOverlay`, `cameFromHome`, `isHomeContent`).
  - Back-without-history writes the Home URL, clears the title and persists the tab as today.
  - Tests: submit, query, Home and Back cases, including the cross-concern "submit then `UrlChanged`" case, which drives this holder and the page holder over one store.
- [X] T009 [US2] **Page**: rename what is left of `BrowserViewModel` to `P/PageViewModel.kt` with `P/PageAction.kt` (11 cases) and `P/PageUiState.kt`.
  - Delete `BrowserViewModel.kt`, `BrowserAction.kt` and `BrowserEvent.kt`.
  - The remaining tests become `PageViewModelTest`, `PageViewModelHistoryRecordSequenceTest` and `PageViewModelIncognitoCacheGateTest`.
  - `A/presentation/browser/BrowserScreen*Test.kt` pass `pageViewModel = …`.
  - Check SC-004 for every holder. *(Page 9 deps / 11 actions, AddressBar 3 / 9, PageActions 5 / 3, FindInPage 1 / 4, DownloadRequest 1 / 2. The browser screen tests build the store-sharing holders through `A/fixture/BrowserScreenHolders.kt`, which replaces their three copies of the no-op repositories.)*
- [X] T010 [US2] Split `P/BrowserScreen.kt` into `BrowserRoute.kt`, `BrowserScreen.kt`, `BrowserContent.kt`, `BrowserHubHost.kt`, `BrowserBackHandlers.kt` and `BrowserCallbacks.kt`, moving each composable as is.
  - Remove the size suppressions on these files, on `BrowserScreenInputs.kt` and on the holders (grouping inputs where a list is still too long).
  - Run G2.
  - **Commit** `refactor(027): browser screen files`.

  *Done:* the route takes three holder groups (`BrowserPageHolders`, `BrowserToolHolders`, `PageRequestHolders`), and the screen takes `state, sources, actions, hosts`. What the screen keeps for itself (focus, bar heights, the bars' scroll, open sheets) is in `P/BrowserScreenUi.kt`. Size suppressions in `F` went from 26 to 21. `PageViewModel` keeps its own, since each of its functions handles one WebView callback. Largest file: `BrowserScreen.kt` at 201 lines.

## Phase 4: User Story 3, no dead shared code (P3)

- [X] T011 [US3] Delete `core/presentation/util/BaseViewModel.kt` and `T/../core/presentation/util/BaseViewModelTest.kt`. G1. **Commit** `refactor(027): remove unused BaseViewModel`.

## Phase 5: Governance

- [ ] T012 `docs/adr/0027-browser-state-split-over-a-page-store.md` (template format) covering:
  - the store and its scope
  - the five holders
  - the writers table
  - why `BrowserUiState` stays a screen-level value

  `python3 scripts/comment-check.py` on touched files, then G1. **Commit** `docs(027): ADR 0027`.

## Phase 6: User Story 1, nothing changes for the person (P1, verification)

- [ ] T013 [US1] G2 shape results in "Results".
- [ ] T014 [US1] G3 on `TA016_API36_16K`:
  - instrumented suite
  - upgrade over the seeded `90cd024` debug build
  - 20+ interleaved cold starts
  - behaviour walk: Home and address submit, star across rotation, desktop site, find bar closing after a new load and after a tab switch, download request, Back to Home, history rows after a redirect, nothing written in incognito
  - a smoke on `TA016_API24`
- [ ] T015 [US1] Full G1, the Compose report and the release APK size vs T001.

## Phase 7: Delivery

- [ ] T016 Push, then open the PR with the steps, Baseline vs Results and the attribution footer. Merge with CI 6/6 once the owner agrees.
- [ ] T017 Memory note after the merge.

## Dependencies

- T001 → T002 → T003 ∥ T004.
- T004 → T005 → T006 → T007 → T008 → T009, sequential because they all edit `BrowserViewModel.kt` and `BrowserRoute`.
- T009 → T010.
- T011 can run any time after T004.
- T012 after T010.
- T013–T015 after T012.
- T016 after T015.

## Implementation Strategy

MVP is T002–T005: the store plus the first extracted holder proves the pattern on the smallest concern. Then the remaining holders follow one commit each.

---

## Baseline (T001)

| Measure | Value |
|---|---|
| Unit / instrumented | 940 / 189 (from Spec 026 at the same code) |
| Release APK | 3,758,431 B |
| Compose restartable / non-skippable | 217 / 0 |
| `BrowserViewModel.kt` / `BrowserScreen.kt` | 514 / 615 lines |
| Size suppressions in `feature/browser` | 26 |

## Results (T013–T015)

| Measure | Value | Pass |
|---|---|---|
