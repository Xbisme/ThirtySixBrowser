---

description: "Task list for Spec 028 Tab History and Swipe Navigation"
---

# Tasks: Tab History and Swipe Navigation

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: the constitution (VIII) requires tests for every use case, repository, mapper, DAO and ViewModel, so test tasks are included. Every task that adds production code names the test file that goes with it.

**Paths**:

| Short | Path |
|---|---|
| `M` | `app/src/main/kotlin/com/raumanian/thirtysix/browser` |
| `T` | `app/src/test/kotlin/com/raumanian/thirtysix/browser` |
| `A` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser` |
| `B` | `M/feature/browser/presentation/browser` |
| `L` | `M/core/data/datasource/local` |

**Every commit** passes G1 (`detekt ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin`) and `python3 scripts/comment-check.py` on the touched files.

## Phase 1: Setup

- [X] T001 Record G0 on `main` `e89632d` in "Baseline" at the end of this file:
  - unit and instrumented counts;
  - release APK bytes;
  - Compose report;
  - 2 × 12 speed-compiled cold starts on `TA016_API36_16K`.

  Keep the `e89632d` debug and release APKs in the session scratchpad.
- [ ] T002 Run the spike on `TA016_API36_16K`, `TA016_API29_CI` and `TA016_API24`. Use a throwaway instrumented test or debug code that is never committed, and record the answers in research R2/R5.
  - **(a)** The value types and size of the bundle from `WebViewCompat.saveState(wv, b, 256 KiB, true)` after 5 navigations.
  - **(b)** The main-thread time of that call. If it is over 5 ms, T012 saves after loads with a 3 s debounce instead of 1 s. The release and stop saves stay immediate.
  - **(c)** Whether `WebView.onOverScrolled` fires with `clampedX` for:
    - a horizontal drag on a narrow page;
    - a drag on a horizontally scrolling `div`;
    - a drag on maps.google.com.

  If (c) fails anywhere, mark the R5 fallback as chosen before Phase 6.

## Phase 2: Foundational (blocks every story)

- [X] T003 Add the domain types:
  - `M/core/domain/model/TabSession.kt`: `data class TabSession(tabId: Long, state: ByteArray, savedAt: Long)`. `equals`/`hashCode` use `tabId` and `savedAt` only.
  - `M/core/domain/repository/TabSessionRepository.kt`, as in [contracts/tab-session-repository.md](contracts/tab-session-repository.md): `clears: StateFlow<Long>`, `get`, `save`, `delete`, `retainOnly`, `clearAll`.
- [X] T004 Add the Room table:
  - `L/entity/TabSessionEntity.kt`: table `tab_sessions`; `tab_id INTEGER NOT NULL` primary key with `FOREIGN KEY → tabs(id) ON DELETE CASCADE`; `state BLOB NOT NULL`; `saved_at INTEGER NOT NULL`.
  - `L/TabSessionDao.kt`: `get(tabId)`, `upsert`, `delete(tabId)`, `deleteNotIn(ids)`, `deleteAll`.
  - `L/migration/Migration5To6.kt`: additive `CREATE TABLE IF NOT EXISTS tab_sessions (...)`.
  - Register the entity and set `SCHEMA_VERSION = 6` in `L/AppDatabase.kt`.
  - Add the migration and provide the DAO in `M/core/di/DatabaseModule.kt`.
  - Export `app/schemas/.../6.json`.
- [X] T005 [P] Add the data tests:
  - `T/core/data/datasource/local/TabSessionDaoTest.kt`: upsert/get; `deleteNotIn`; deleting the tab row removes its session, which proves the cascade is on.
  - Extend `T/core/data/datasource/local/AppDatabaseMigrationTest.kt` with "migrating on to v6 adds an empty tab_sessions table and keeps every row".
- [X] T006 Add `M/feature/tabs/data/repository/TabSessionRepositoryImpl.kt` and bind it in `M/feature/tabs/di/`.
  - `tabId < 0` → an in-memory map only; such a save never reaches the DAO.
  - A state over `TabSessionLimits.MAX_STATE_BYTES = 256 * 1024` is rejected.
  - `get` swallows read errors and returns `null`.
  - `clearAll` empties both stores, then increments `clears`.
  - All work runs on the injected IO dispatcher.

  Test: `T/feature/tabs/data/repository/TabSessionRepositoryImplTest.kt`, which checks every rule in the contract table.
- [X] T007 [P] Add `T/core/testing/fake/FakeTabSessionRepository.kt`, which records saves and counts clears.
- [X] T008 [P] Add `B/TabStateCodec.kt`: `encode(Bundle): ByteArray?` and `decode(ByteArray): Bundle?`.
  - Format: a version byte, then length-prefixed `(key, ByteArray)` pairs.
  - A bundle with any non-`ByteArray` value encodes to `null`.
  - An unknown version or a malformed input decodes to `null`.

  Test: `T/feature/browser/presentation/browser/TabStateCodecTest.kt` (Robolectric) covers:
  - round trip;
  - an empty bundle;
  - a non-bytes value;
  - an unknown version;
  - truncated input.

  **Commit** `feat(028): tab session storage`.

## Phase 3: User Story 1, Back still works after reopening the app (P1) 🎯 MVP

**Goal**: FR-001, FR-002, FR-004, FR-005. **Independent test**: quickstart G3 step 1 (SC-001).

- [X] T009 [US1] *(as built: the 1 s batching lives in `TabSessionHooks.saveSoon`, where the engine state is captured, so busy single-page sites do not capture on every history change; `TabSessionAction.Save(tabId, state)` is written at once. `DownloadRequestViewModel` moved to `PageRequestHolders` to keep `rememberBrowserPageHolders` within 5 parameters)* [US1] Add the session holder:
  - `B/TabSessionViewModel.kt`, `B/TabSessionAction.kt` (`Save(tabId, state)`), `B/TabSessionEvent.kt` (`ClearLiveHistory`, Keep), `B/TabSessionUiState.kt` (`loadedTabId: Long?`).
  - **The bytes never enter UI state or composable parameters** (constitution IV: `ByteArray` is not stable). The holder keeps the loaded bytes privately, and exposes a read-only lookup property `savedState: SavedTabStateLookup`. That is a `fun interface` `(tabId: Long) -> ByteArray?`, the same pattern as `desktopMode`.
  - It reads the active tab from `CurrentPageRepository` and loads its session whenever the tab id changes. `loadedTabId` becomes that id once the read completes.
  - `Save` debounces per tab by `TabSessionLimits.SAVE_DEBOUNCE_MS = 1_000`; a flush is used for release and stop.

  Test: `T/feature/browser/presentation/browser/TabSessionViewModelTest.kt`.
- [X] T010 [US1] Wire the route in `B/BrowserRoute.kt`:
  - obtain `TabSessionViewModel` in `BrowserPageHolders` (`rememberBrowserPageHolders`);
  - collect its state;
  - pass `loadedTabId` into `BrowserScreenState`, and the `savedState` lookup into `BrowserScreenSources`;
  - add `TabSessionEventEffect` in `B/BrowserEffects.kt`.

  Also update `A/feature/browser/fixture/BrowserScreenHolders.kt`.
- [X] T011 [US1] Restore before the first load in `B/BrowserWebView.kt` and `B/BrowserContent.kt`:
  - `TabWebView` composes `BrowserWebView` only once `loadedTabId` equals the active tab id.
  - In `factory`, after `buildConfiguredWebView` wires the client and protections, read `sources.savedState(tabId)` and decode it with `TabStateCodec`. If `restoreState` returns non-null with entries, skip the initial `loadProtected` and set `skipNextLoad` for the tab switch's `loadUrl`. Otherwise load as today.
  - Adopted pop-ups never restore.
  - Renderer recovery (a new generation) restores from the last save first.
- [X] T012 [US1] Add the save triggers:
  - `B/BrowserWebView.kt`: after `onPageFinished` and `doUpdateVisitedHistory`, send `TabSessionAction.Save` with `WebViewCompat.saveState(wv, bundle, MAX_STATE_BYTES, includeForwardState = true)` when `WebViewFeature.SAVE_STATE` is supported, else `wv.saveState(bundle)`, encoded by the codec.
  - `B/WebViewLifecycle.kt`: save and flush on `ON_STOP`.
  - No save while the tab shows Home.
- [ ] T013 *(written; it runs a real WebView save → new WebView restore → Back/Forward, and waits for an AVD run)* [US1] Add the instrumented test `A/feature/browser/presentation/browser/TabHistoryRestoreTest.kt`. Using `AssetPageServer`, it loads pages A→B→C, recreates the activity with a fresh holder set over the same database, and asserts `canGoBack` twice back to A. A second case asserts Forward after going back.

  A fresh holder set over the same database stands in for a new process. The real process kill is covered by G3 step 1 (`am kill`).

  **Commit** `feat(028): restore tab history after reopening`.

## Phase 4: User Story 2, Switching tabs keeps each tab's history (P1)

**Goal**: FR-001 for tab switches, and FR-006 in memory. **Independent test**: quickstart G3 step 2 (SC-002).

- [X] T014 [US2] Save at once before release in `B/BrowserWebView.kt`'s `DisposableEffect.onDispose`, before `releaseWebView`. Skip this when the renderer is gone. Test: extend `TabSessionViewModelTest` so a flush bypasses the debounce.
- [ ] T015 *(the save-on-release and restore-on-create path is covered by `TabHistoryRestoreTest` with real WebViews and by `TabSessionViewModelTest`. The two-tab and incognito switch runs in the G3 walk)* [US2] Add the instrumented test `A/feature/browser/presentation/browser/TabSwitchHistoryTest.kt`:
  - two normal tabs, A→B in tab 1, switch to tab 2 and back: Back reaches A;
  - the same for an incognito tab, which never writes to the DAO (asserted through the repository's database).

  **Commit** `feat(028): keep history across tab switches`.

## Phase 5: User Story 3, Saved history disappears with the browsing data (P1)

**Goal**: FR-006 to FR-010a. **Independent test**: quickstart G3 steps 3–4 (SC-003, SC-004).

- [X] T016 [US3] Add `M/core/domain/usecase/ClearBrowsingHistoryUseCase.kt`, which calls `HistoryRepository.clearAll()` then `TabSessionRepository.clearAll()`. Test: `T/core/domain/usecase/ClearBrowsingHistoryUseCaseTest.kt`.
- [X] T017 [US3] Use the new use case in place of `history.clearAll()` in three places:
  - `M/feature/privacy/domain/usecase/RunFireUseCase.kt`, in the History step;
  - `M/feature/settings/domain/usecase/ClearBrowsingDataUseCase.kt`;
  - `M/feature/history/presentation/history/HistoryViewModel.kt`.

  Update their tests (`RunFireUseCaseTest`, `ClearBrowsingDataUseCaseTest`, `HistoryViewModelTest`) to assert that saved sessions are cleared.
- [X] T018 [US3] Make the live history follow the clear:
  - `TabSessionViewModel` turns each `clears` increment after the first into `TabSessionEvent.ClearLiveHistory`. The route effect calls `webViewActions.clearHistory()`, a new entry in `B/WebViewActionsHandle.kt` wired to `WebView.clearHistory()`.
  - The holder sweeps with `retainOnly(ids of ObserveAllTabsUseCase)` whenever the tab list changes.

  Tests are in `TabSessionViewModelTest`.
- [ ] T019 *(the unit side is covered: `RunFireUseCaseTest`, `ClearBrowsingDataUseCaseTest`, `ClearBrowsingHistoryUseCaseTest`, and `TabSessionRepositoryImplTest` for "incognito never reaches the database". The end-to-end checks run in G3 steps 3–4 on the AVD)* [US3] Add the instrumented test `A/feature/browser/presentation/browser/TabHistoryPrivacyTest.kt`:
  - closing a tab leaves no `tab_sessions` row;
  - Fire leaves none;
  - after "Clear browsing data → History" the open tab has no Back;
  - incognito browsing leaves no row and no incognito URL in the database.
- [X] T019a *(as built: both use cases take only `HistoryRepository`, so they cannot reach `TabSessionRepository` by construction; a unit test would assert nothing. The device check stays in G3)* [US3] Show that the retention period never trims a tab's saved history (FR-010a):
  - extend `T/feature/history/domain/usecase/PruneOldHistoryUseCaseTest.kt` and `T/feature/settings/domain/usecase/ChangeHistoryRetentionUseCaseTest.kt` with a case where a pre-filled `FakeTabSessionRepository` is unchanged after pruning;
  - in G3, set the shortest retention, trigger a prune, and check that an open tab still goes back.

  **Commit** `feat(028): saved tab history follows browsing data`.

## Phase 6: User Story 4, Swipe inside the page (P2)

**Goal**: FR-011 to FR-018. **Independent test**: quickstart G3 steps 7–8 (SC-007, SC-008).

- [X] T020 [US4] Add the setting:
  - DataStore key `swipe_navigation_enabled` (default `true`) in `L/datastore/SettingsDataStore.kt`;
  - `UserSettings.isSwipeNavigationEnabled` in `M/core/domain/model/UserSettings.kt`, with the default in `AppDefaults`;
  - `setSwipeNavigationEnabled` in `M/core/domain/repository/SettingsRepository.kt` and `M/feature/settings/data/repository/SettingsRepositoryImpl.kt`;
  - the mapper in `M/feature/settings/data/mapper/SettingsMapper.kt`;
  - `T/core/testing/fake/FakeSettingsRepository.kt`.

  Tests: the mapper and repository tests.
- [X] T021 *(as built: the indicator reuses `browser_action_back`/`browser_action_forward`, so only the two Settings strings were added)* [US4] Add the Settings row:
  - `SettingsAction.SwipeNavigationChanged(enabled)`, state field, and a switch row in `M/feature/settings/presentation/settings/` (`SettingsAction.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`);
  - strings `settings_swipe_navigation_title` and `settings_swipe_navigation_summary` in `app/src/main/res/values*/strings.xml` (8 locales).

  Tests: `SettingsViewModelTest`, plus an instrumented check of the row in `SettingsScreen*Test`.
- [X] T022 [US4] Rename `B/PageTextSizeViewModel.kt` to `B/PageSettingsViewModel.kt`, which exposes `StateFlow<PageSettingsUiState(textZoomPercent, swipeNavigationEnabled)>`. Update `BrowserToolHolders`, the route and the tests.
- [X] T023 [US4] Add the swipe state:
  - `B/SwipeNavigationState.kt`: a `@Stable` class with `direction`, a per-frame `progress`, and `available`;
  - `SwipeNavigationLimits` with a `THRESHOLD` dp, the 2:1 horizontal ratio and the 24 dp fallback edge.

  Test: `T/feature/browser/presentation/browser/SwipeNavigationStateTest.kt` covers progress clamping, cancel, and LTR/RTL direction mapping.
- [X] T024 *(as built: the gesture starts only when three things hold. The drag is mostly sideways, the page reported overscroll since touch-down, and `canScrollHorizontally` is false that way. The spike could not settle whether a real finger scrolls an inner carousel, because adb-injected drags did not scroll it; this is checked by hand in G3)* [US4] Add `B/BrowserPageView.kt`, a `WebView` subclass:
  - it watches touches in `dispatchTouchEvent` and overscroll in `onOverScrolled`, per [contracts/swipe-navigation.md](contracts/swipe-navigation.md) and the T002 result;
  - it reports start, progress, release and cancel through a `SwipeNavigationListener`.

  Use it in place of `WebView(context)` in `B/BrowserWebView.kt`, for both the page and pop-ups. Test the gesture logic, separated into a pure `SwipeGestureTracker`, in `T/.../SwipeGestureTrackerTest.kt`.
- [X] T025 [US4] Add `B/SwipeNavigationIndicator.kt` and the wiring:
  - **Indicator**: a circular arrow; offset and scale read only in `graphicsLayer {}`; primary color at the threshold and a disabled tint when unavailable; semantics "Back"/"Forward" from new strings `swipe_navigation_back` and `swipe_navigation_forward` (8 locales).
  - **Wiring**: `B/BrowserContent.kt` gets a `SwipeNavigationState` in `BrowserScreenUi`, and the gesture is enabled only per FR-015 and FR-018.
  - **Release**: calls `webViewActions.goBack()` or `goForward()`.
- [ ] T026 *(`SwipeNavigationTest` is written: real pointer events on a `BrowserPageView` for back and forward, a short swipe, a vertical drag, and swiping switched off. It waits for an AVD run)* [US4] Add the instrumented test `A/feature/browser/presentation/browser/SwipeNavigationTest.kt`:
  - injected horizontal swipes on an asset page with history go back and forward;
  - a vertical scroll and an asset carousel page do not;
  - with the setting off, nothing happens;
  - on Home, on the incognito start page, with the address bar focused, and with the find bar open, nothing happens (FR-015).

  Run the Compose report and attach the result. Record Layout Inspector recomposition counts in "Results" (constitution Quality Gate 6):
  - on the browser screen, during one full swipe: target 0 recompositions of `BrowserScreen`, `BrowserContent` and `TabWebView` while the finger moves;
  - on the Settings screen, while toggling the switch: only the row recomposes.

  **Commit** `feat(028): swipe to go back and forward`.

## Phase 7: Polish & Cross-Cutting

- [X] T027 Add `docs/adr/0028-tab-sessions-kept-by-the-engine.md`, following the template format. It covers:
  - the engine state and its bound;
  - the codec over `Parcel`;
  - Room with cascade, and incognito in memory;
  - the save and restore moments;
  - why a restore keeps the `loadProtected` rule (R4);
  - the swipe signal.

  **Commit** `docs(028): ADR 0028`.
- [ ] T028 Run G3 (quickstart steps 1–9) on `TA016_API36_16K` and a smoke on `TA016_API24`, including the instrumented suite and an upgrade over the `e89632d` build. Also:
  - repeat steps 7–8 with three-button navigation (`adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton`, restore gestural afterwards) for FR-016;
  - confirm `app/src/main/res/xml/data_extraction_rules.xml` and `backup_rules.xml` still exclude the database for FR-010.

  Record "Results".
- [ ] T029 Run G4: `lintDebug`, `assembleRelease`, 16 KB alignment, the Compose report with zero unexplained non-skippable composables, the Layout Inspector counts from T026, APK size vs T001, and cold start vs T001 (SC-006).
- [ ] T030 Push and open the PR with the results and the attribution footer. Merge with CI 6/6 once the owner agrees.
- [ ] T031 Write the memory note after the merge.

## Dependencies

- T001 → T002 → Phase 2.
- In Phase 2, T003 → T004 → T006. T005, T007 and T008 can run in parallel after T004.
- Phase 3 (US1) needs Phase 2.
- Phase 4 (US2) needs T011.
- Phase 5 (US3) needs T006 and T009.
- Phase 6 (US4) needs only Phase 1. It is independent of US1–US3 and can be done after them in any order.
- T027 comes after Phases 3–6. T028–T031 come after T027.

## Parallel examples

- After T004: T005 (DAO/migration tests), T007 (fake) and T008 (codec) touch different files.
- Within US4: T020/T021 (settings) and T023 (state) run in parallel. T024 needs the T002 result.

## Implementation Strategy

The MVP is Phases 1–3: the reported defect fixed for reopening the app. US2 adds tab switches, and US3 closes the privacy rules; both ship in the same PR. US4 is the gesture. It comes last because it depends only on the spike, and it can be cut to a follow-up PR if the owner prefers.

---

## Baseline (T001)

| Measure | Value |
|---|---|
| Unit / instrumented | 943 / 189 (PR #47 at `e89632d`) |
| Release APK | 3,758,431 B |
| Compose restartable / non-skippable | 225 / 0 |
| Cold start | measured in T029 against the kept `e89632d` release APK on an AVD, interleaved. It is not measured on the owner's phone, which holds their own release install |

## Results (T028–T029)

| Measure | Value | Pass |
|---|---|---|
| Unit tests | 987, 0 failed (943 at baseline) | yes |
| detekt, ktlint, lintDebug | clean | yes |
| Compose restartable / non-skippable | 228 / 0 (was 225 / 0) | yes |
| Release APK | 3,776,883 B (+18,452 B, +0.49 %), under the 4 MB budget | yes |
| 16 KB alignment | all native libs aligned | yes |
| Galaxy A55, by hand | pages A→B→C, force-stop, reopen: C, then Back to B and to A. Swipe right goes back, swipe left goes forward. A swipe pulled back under the threshold cancels. The arrow shows on the side of the swipe | yes |
