# Tasks: Swipe the Address Bar to Switch Tabs

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/tab-swipe.md](contracts/tab-swipe.md), [quickstart.md](quickstart.md)

**Tests**: required by Constitution §VIII. Every new or changed use case, repository and ViewModel has a test, and so does the gesture tracker.

**Paths**:

| Short form | Path |
|---|---|
| `M/` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/` |
| `T/` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/` |
| `A/` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/` |
| `BR` | `feature/browser/presentation/browser` |

**Gate G1 after every task group**: `detekt ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin`, all green.

## Phase 1: Setup

- [X] T001 Baseline on `030-omnibar-tab-swipe` at `3feed8a` (code = `main` `8e5a0ca`): record the unit and instrumented test counts and the release APK size in "Gate Results" below

## Phase 2: Foundational (blocks all stories)

- [X] T002 [P] Add `TabNeighbours(previous: Tab?, next: Tab?)` in `M/core/domain/model/TabNeighbours.kt`
- [X] T003 [P] Add `TabOrder` in `M/core/domain/util/TabOrder.kt`:
  - `inOpeningOrder(tabs)` sorts by `position ASC, createdAt ASC, |id| ASC`;
  - `neighbours(tabs, activeId)` takes the same-`isIncognito` tabs of the active one in that order and returns the ones before and after. A `null` or unknown `activeId` gives `TabNeighbours(null, null)`.
- [X] T004 Tests in `T/core/domain/util/TabOrderTest.kt`:
  - ties on `position` broken by `createdAt`, then by id magnitude, for negative incognito ids too;
  - neighbours in the middle and at both ends;
  - a single tab;
  - normal and incognito mixed: neighbours never cross kinds;
  - an unknown active id.
- [X] T005 [P] Change the KDoc of `position` in `M/core/domain/model/Tab.kt` to say it is the order tabs are shown and swiped in. Add `docs/adr/0030-tabs-in-opening-order.md`: tabs are shown and swiped in opening order, "active" stays the newest `lastActiveAt`, and closing hands the active mark to the neighbour (research R1, R2).

## Phase 3: User Story 1 – Swipe to the neighbouring tab (P1) 🎯 MVP

**Goal**: drag the pill to reach the next or previous tab in a stable order; the Tabs screen and closes follow the same order. **Independent test**: quickstart G3.1 and G3.2.

**Order and close**

- [X] T006 [US1] Close heir in `M/feature/tabs/data/repository/TabRepositoryImpl.kt` `closeTab`, inside the existing `seedMutex` block:
  - read all rows;
  - `wasActive` = the closed row has the newest `lastActiveAt`;
  - delete the row;
  - if `wasActive` and other rows remain, the heir is the next row in `TabOrder.inOpeningOrder`, or the previous one if the closed row was last. The heir gets `lastActiveAt = max(heir.lastActiveAt, closed.lastActiveAt)`;
  - the empty-table home seed stays as it is.

  Update the `closeTab` KDoc in `M/core/domain/repository/TabRepository.kt`, which currently says "the most recently active survivor becomes active", to "the neighbour in opening order becomes active".
- [X] T007 [US1] The same heir rule in `M/feature/tabs/data/repository/IncognitoTabRepositoryImpl.kt` `closeTab`, inside its `mutex`. The session still ends at `1 → 0`. Replace `position = state.value.size` with a running `AtomicInteger` counter that is never reused, so positions never repeat after closes (research R1).
- [X] T008 [US1] Repository tests:
  - in `T/feature/tabs/data/repository/TabRepositoryImplTest.kt`:
    - closing the active middle tab activates the next;
    - closing the active last tab activates the previous;
    - closing a background tab leaves the active tab alone;
    - closing the only tab still seeds Home;
    - the order survives a reopen: close and reopen the in-memory database's repository and read A, B, C again (FR-005b);
  - in `IncognitoTabRepositoryImplTest.kt`:
    - the same three cases;
    - positions after open, close, open are distinct and increasing;
    - closing the last incognito tab still ends the session.
- [X] T009 [US1] Close-precedence test in a new `T/core/domain/usecase/ObserveAllTabsUseCaseTest.kt`, using the real repository implementations over in-memory Room and the incognito repository. With an incognito tab active, closing the active normal tab does not take over: the head stays the incognito tab.
- [X] T010 [US1] Tabs screen in opening order:
  - `M/feature/tabs/presentation/tabs/TabsViewModel.kt` maps `observeAllTabs()` through `TabOrder.inOpeningOrder` before the mode filter, and keeps `activeTabId` from the newest `lastActiveAt`;
  - `M/feature/tabs/presentation/tabs/TabsScreen.kt` scrolls the grid to the active card once, when the screen opens (`LaunchedEffect(Unit)` keyed on the first non-empty list).

  Update `T/feature/tabs/presentation/tabs/TabsViewModelTest.kt`: the order after several switches stays A, B, C. Update any other test that assumed most-recent-first in the Tabs screen.

**Neighbours and holder**

- [ ] T011 [P] [US1] Add `ObserveTabNeighboursUseCase` in `M/feature/browser/domain/usecase/ObserveTabNeighboursUseCase.kt`. It maps `ObserveAllTabsUseCase()` to `TabOrder.neighbours(tabs, tabs.maxByOrNull(Tab::lastActiveAt)?.id)` and applies `distinctUntilChanged`. Test it in `T/feature/browser/domain/usecase/ObserveTabNeighboursUseCaseTest.kt` with the core fakes: it follows a switch, it follows a close, and it never crosses kinds.
- [ ] T012 [P] [US1] Add `TabSwipeLimits` in `M/BR/TabSwipeLimits.kt`:
  - `COMMIT_FRACTION = 0.33f`
  - `FLICK_VELOCITY_DP_PER_S = 1_000f`
  - `EDGE_RESISTANCE = 0.25f`
  - `SIDEWAYS_RATIO = 2f`
  - `PREVIEW_HOLD_MAX_MS = 600L`
- [ ] T013 [US1] Add the holder's types:
  - `TabPreview(tabId, isIncognito, isHome, host, title)`, `@Immutable`, with `host` = "" for Home and incognito, and `title` = "" for incognito: never shown or spoken;
  - `TabSwipeUiState(previous, next, enabled)`;
  - `TabSwipeAction.SwitchTo(tabId, announce)`;
  - `TabSwipeEvent.Announce(preview)`.

  They go in `M/BR/TabSwipeUiState.kt`, `TabSwipeAction.kt` and `TabSwipeEvent.kt`.
- [ ] T014 [US1] Add `TabSwipeViewModel` in `M/BR/TabSwipeViewModel.kt` (`@HiltViewModel`):
  - `uiState` combines `ObserveTabNeighboursUseCase` and `SettingsRepository.observeSettings().map { it.isTabSwipeEnabled }`. Until T029 adds that field, use `true`;
  - `screenshotVersion` comes from `ScreenshotCache.version`, and `screenshotFor(tabId)` returns `null` for incognito;
  - `onAction(SwitchTo)` calls `SwitchToTabUseCase`, then sends `Announce` only when `announce` is true.

  Home is detected with `UrlConstants.HOME_PAGE_URL`, as `TabSwitcherCard` does.
- [ ] T015 [US1] Tests in `T/feature/browser/presentation/browser/TabSwipeViewModelTest.kt`, using Turbine and `MainDispatcherRule`:
  - neighbours become previews;
  - a Home neighbour has `isHome` set and an empty host;
  - `SwitchTo` switches through the fake repositories;
  - `Announce` is sent only for `announce = true`;
  - `screenshotFor` is `null` for incognito.

**Gesture and visuals**

- [ ] T016 [US1] Add `TabSwipeTracker` in `M/BR/TabSwipeTracker.kt`, pure Kotlin like `SwipeGestureTracker`:
  - inputs: `down(x, y)`, `move(x, y)`, `pointerAdded()`, `up(velocityX)`, `cancel()`, plus `canStart: Boolean` and the `hasPrevious`/`hasNext` flags captured at down;
  - it claims the gesture only after `touchSlop` with `|dx| ≥ SIDEWAYS_RATIO·|dy|`. A vertical-first move rejects it for the rest of the touch;
  - output: `Progress(offsetPx)`, damped by `EDGE_RESISTANCE` toward a side with no tab, then `Commit(side)` or `Return`;
  - commit when `|offset| ≥ COMMIT_FRACTION·laneWidth` or `|velocity| ≥ flick` toward a side with a tab;
  - RTL mirrors which side is next.
- [ ] T017 [US1] Tracker tests in `T/feature/browser/presentation/browser/TabSwipeTrackerTest.kt`:
  - no claim under slop;
  - no claim at a 1:1 diagonal;
  - a claim at 3:1;
  - commit at 0.34 of the width and return at 0.30;
  - commit on a short fast flick;
  - damping and return at an end;
  - a second pointer returns;
  - RTL;
  - `canStart = false` never claims.
- [ ] T018 [US1] Add `TabSwipeState` (`@Stable`) in `M/BR/TabSwipeState.kt`:
  - fields `offsetPx` (`mutableFloatStateOf`), `incoming`, `settling` and `holdPreview`;
  - `Animatable`-backed `settleTo(target, animate)`;
  - `rememberTabSwipeState()`.
- [ ] T019 [US1] Add `Modifier.tabSwipe(state, tracker inputs, neighbours, onCommit)` in `M/BR/TabSwipeModifier.kt`:
  - `pointerInput` with `awaitEachGesture`, reading `PointerEventPass.Initial`;
  - it feeds `TabSwipeTracker` and consumes changes only once the tracker has claimed;
  - `VelocityTracker` gives the release velocity;
  - it cancels if the incoming tab on the dragged side changes or disappears during the drag. Other list changes do not cancel (FR-011, research R5);
  - it ignores pointers that go down inside `WindowInsets.systemGestures`, and sets no gesture exclusion (FR-010);
  - one line of comment on why the Initial pass is used.
- [ ] T020 [US1] Add the lane and the preview:
  - **lane**, `M/BR/component/TabSwipeLane.kt`: a clipped `Box` holding the current pill, moved with `graphicsLayer { translationX = state.offsetPx }`, and a ghost pill for `incoming` one lane width away, showing the host or `browser_tab_swipe_home_label`;
  - **preview**, `M/BR/component/TabSwipePreview.kt`: draws the screenshot through `rememberFileImage`, or `SeededMeshArt` seeded from the host, or the Home look. Its position is set only in `graphicsLayer`.
- [ ] T021 [US1] Wire it into the browser:
  - `BrowserRoute.kt`: add `TabSwipeViewModel` to the holders and collect `uiState` once;
  - `BrowserScreenInputs.kt`: add `tabSwipe: TabSwipeUiState` to `BrowserScreenState`, and `onTabSwipeAction` to `BrowserScreenActions`;
  - `BrowserScreen.kt`: `rememberTabSwipeState()` goes into `BrowserFrame`;
  - `component/BrowserTopBar.kt`: wrap the `AddressBar` in `TabSwipeLane`, with `canStart = enabled && !isAddressBarFocused`;
  - `BrowserContent.kt`: offset the `TabWebView` box and the Home overlay by `offsetPx × screenWidth / laneWidth` in `graphicsLayer`, and place `TabSwipePreview` beside them;
  - on commit, send `SwitchTo(announce = false)`. Keep `holdPreview` until the new tab's `WebViewClient.onPageCommitVisible` (forward it as a `BrowserWebViewCallbacks` callback) or `PREVIEW_HOLD_MAX_MS`, then reset the offset when `activeTab.id` changes. Do not use `LoadStarted` or progress: they come before anything is drawn;
  - on the AVD, check whether `onPageCommitVisible` fires for a tab restored from saved state. If it does not, end the hold on the WebView's first draw after the re-key instead (research R4). Record the finding in "Implementation notes".
- [ ] T022 [US1] Home and the incognito start page: `HomeSearchBar` in `M/feature/browser/presentation/home/HomeHero.kt` takes a `modifier` from `HomeOverlay`. `BrowserContent.kt` passes `Modifier.tabSwipe(…)` there, with `canStart = enabled && !homeSearchFocused`.
- [ ] T023 [US1] Add `browser_tab_swipe_home_label` to `app/src/main/res/values*/strings.xml` in all 8 locales

**Checkpoint**: US1 works on the AVD (quickstart G3.1–G3.2, G3.5).

## Phase 4: User Story 2 – Normal and incognito stay apart (P1)

**Goal**: no swipe crosses kinds, and an incognito neighbour shows nothing of its page. **Independent test**: quickstart G3.3.

- [ ] T024 [US2] `TabSwipePreview` and the ghost pill draw `ThirtySixTheme.colors.surfaceIncognito` with `AppIcons.Incognito` for `isIncognito` previews. No file is read and no title is drawn. The lane in an incognito tab uses the incognito pill colours, as `addressWellStyle` does.
- [ ] T025 [US2] Tests:
  - in `TabSwipeViewModelTest`, with N1, N2 and I1, I2: from N2 there is no incognito neighbour, from I1 `previous` is `null` and `next` is I2, and an incognito preview has an empty title and host;
  - in `ObserveTabNeighboursUseCaseTest`: when the last incognito tab is closed, the neighbours fall back to the normal head's.

## Phase 5: User Story 3 – The address bar still works as before (P1)

**Goal**: taps, editing, the in-page swipe and scrolling behave as before. **Independent test**: quickstart G3.4.

- [ ] T026 [US3] Extra tracker tests in `TabSwipeTrackerTest`:
  - a tap that drifts 0.9× slop sideways is not claimed;
  - a drag that turns sideways only after a vertical start stays rejected;
  - `canStart = false` (focused, or find bar open) never claims.
- [ ] T027 [US3] Compose UI test in `T/feature/browser/presentation/browser/component/TabSwipeLaneTest.kt` (Robolectric):
  - `performClick` on the pill calls the field's focus and never `onCommit`;
  - `swipeLeft` on an unfocused pill calls `onCommit(next)`;
  - `swipeLeft` while focused does not.

## Phase 6: User Story 4 – Everyone can switch, and the setting can turn it off (P2)

**Goal**: accessibility actions, reduced motion and a Settings switch. **Independent test**: quickstart G3.6.

- [ ] T028 [P] [US4] Add `rememberAnimationsEnabled()`, which is false only when "Remove animations" is on (an animator duration scale of 0, FR-015), in `M/core/presentation/util/AnimationsEnabled.kt`. It uses `ValueAnimator.areAnimatorsEnabled()` on API 26+, and `Settings.Global.getFloat(ANIMATOR_DURATION_SCALE, 1f) != 0f` on API 24–25. `TabSwipeState.settleTo` snaps when it is false. Test both branches in `T/core/presentation/util/AnimationsEnabledTest.kt` (Robolectric `@Config(sdk = [25, 34])`).
- [ ] T029 [US4] Add the setting end to end:
  - `StorageKeys.TAB_SWIPE_ENABLED = booleanPreferencesKey("tab_swipe_enabled")`;
  - `AppDefaults.TAB_SWIPE_ENABLED = true`;
  - `UserSettings.isTabSwipeEnabled`;
  - `SettingsRepository.setTabSwipeEnabled`;
  - `SettingsDataStore` read and write;
  - the fake in `T/core/testing/fake/`.

  Then switch `TabSwipeViewModel` from the T014 placeholder to the real field.
- [ ] T030 [US4] The Settings row in `M/feature/settings/presentation/settings/SettingsScreen.kt`:
  - placed under the swipe navigation row, key `row_tab_swipe`, test tag `settings_tab_swipe_row`;
  - `SettingsAction.TabSwipeToggled(enabled)` is handled in `SettingsViewModel`;
  - add `settings_tab_swipe_title` and `settings_tab_swipe_summary` in all 8 locales;
  - test it in `T/feature/settings/presentation/settings/SettingsViewModelTest.kt`.
- [ ] T031 [US4] Accessibility actions:
  - in `TabSwipeLane` and on the Home search field, add `semantics { customActions = … }` with `browser_a11y_next_tab` when `next != null` and `browser_a11y_previous_tab` when `previous != null`. Each sends `SwitchTo(announce = true)`. They are present whatever the setting;
  - in `BrowserRoute`, collect `TabSwipeEvent.Announce` and call `LocalView.current.announceForAccessibility`:
    - a normal tab: `browser_a11y_switched_to_tab` with the title, else the host, else `browser_tab_swipe_home_label`;
    - an incognito tab: `browser_a11y_switched_to_incognito_tab`;
  - add these 4 strings in all 8 locales.
- [ ] T032 [US4] Tests:
  - in `TabSwipeLaneTest`, the custom actions are listed only for existing sides, and invoking one calls `SwitchTo(announce = true)`;
  - with the setting off, `swipeLeft` does nothing but the actions remain.

## Phase 7: Polish and gates

- [ ] T033 Instrumented `A/feature/browser/presentation/browser/BrowserTabSwipeTest.kt` on `MainActivity`, with the per-test DataStore module (AGENTS.md):
  - open three tabs, swipe the pill left and right, and check the active tab through the Tabs screen order;
  - a tap focuses the pill;
  - the custom action switches;
  - with the setting off, a swipe does nothing.
- [ ] T033a Recomposition count (gate 6, constitution 2.2.0) in `A/feature/browser/presentation/browser/component/TabSwipeRecompositionTest.kt`. Host `TabSwipeLane` and `TabSwipePreview` with `SideEffect` counters in the current-pill and page-content slots, run one full drag and release with `performTouchInput`, and assert at most one recomposition of each slot during the drag. Record the counts below.
- [ ] T034 Compose compiler report (`-PcomposeReports`): zero unexplained non-skippable composables in `TabSwipeLane`, `TabSwipePreview`, `BrowserTopBar`, `BrowserContent` and `SettingsScreen`. Record the result below.
- [ ] T035 Quality gates:
  - `detekt ktlintCheck testDebugUnitTest lintDebug`;
  - `assembleRelease` and the 16 KB script;
  - the APK size delta ≤ 40 KB;
  - `connectedDebugAndroidTest` on `TA016_API36_16K`.
- [ ] T036 Device walk, quickstart G3 (1–7), on `TA016_API36_16K` and `TA016_API24`, with the counts SC-001 (20 each way, ≥ 19), SC-003 (20 in-page swipes, 50 scrolls) and SC-004 (40 swipes, 0 crossings). Also check the system-Back edge drag, a rotation during a drag, and a tab opened at the end during a drag. TalkBack (SC-007) is the owner's pass; record it as such. Record the results below.
- [ ] T037 Measurements, quickstart G5:
  - SC-005 frame timing on the A55 release build (check `mCurrentFocus` first; restore the owner's state afterwards);
  - SC-008 cold start interleaved against `8e5a0ca`;
  - the recomposition counts from T033a.
- [ ] T038 PR with the gate results; update the project memory

## Dependencies

- Phase 2 blocks everything.
- Inside US1:
  - T006–T010 (order and close) are independent of T011–T022 (gesture), and can be done first and merged on their own;
  - T016 comes before T019;
  - T013 and T014 come before T021.
- US2 needs T020 and T021.
- US3 needs T016 and T020.
- US4:
  - T028 and T029 can start after Phase 2;
  - T031 and T032 need T020.
- Phase 7 comes last.

## Parallel opportunities

- T002 ∥ T003 ∥ T005.
- T011 ∥ T012 ∥ T016 once Phase 2 is done.
- T028 ∥ T029 ∥ the US1 gesture work.
- T023 ∥ T030 strings, which touch different keys in the same files: merge carefully.

## Implementation strategy

MVP = Phases 1–3: the swipe works with a stable order, and the Tabs screen matches. Then the incognito surfaces (US2), the non-interference tests (US3), and access and settings (US4). Commit once per task group, following the plan's step table.

## Gate Results

*(Filled during implementation: AVD, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Status | Result |
|---|---|---|
| T001 Baseline | ✅ 2026-10-17, code = `main` `8e5a0ca` | 1,040 unit tests, 0 failures; 204 instrumented `@Test`s; release APK 3,818,443 B (`741a393` build, app code identical) |
| G1/T035 Quality gates | — | |
| Size | — | |
| Cold start | — | |
| Compose report | — | |
| G3 Device walk | — | |
| SC-005 Frames | — | |
| Recomposition count (T033a) | — | |
