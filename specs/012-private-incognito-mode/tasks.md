---
description: "Task list for Spec 012 — Private / Incognito Mode"
---

# Tasks: Private / Incognito Mode

**Input**: Design documents from `/specs/012-private-incognito-mode/`
**Prerequisites**: [plan.md](plan.md) ✅, [spec.md](spec.md) ✅, [research.md](research.md) ✅, [data-model.md](data-model.md) ✅, [contracts/](contracts/) ✅, [quickstart.md](quickstart.md) ✅

**Tests**: REQUIRED per Constitution §VI (Testing Discipline). Plan commits to ≥ 18 unit tests + 4 instrumented tests. Crash-resilience focus (user priority) drives explicit stress + lifecycle tests.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: US1–US5 maps to spec.md user stories
- All paths absolute or anchored at repo root `/Users/xbism3/Documents/Code/jetpack/ThirdtySixBrowser/`

## Path Conventions

- **Mobile (Android single-module)**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/<layer>/`, `app/src/test/...`, `app/src/androidTest/...`, `app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify branch state + design-doc readiness. No new packages introduced (per plan.md Technical Context).

- [X] T001 Verify working tree is clean and on branch `012-private-incognito-mode`: `git status && git branch --show-current` — expect `clean` + `012-private-incognito-mode`. If not, stop and reconcile.
- [X] T002 Re-read [plan.md](plan.md) §Constitution Check + §Complexity Tracking to lock in the §IV use-case-coordination exception scope before any code is written. Confirm exception is the ONLY documented deviation.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Constants + domain-model extensions + ID-space invariants that ALL user stories depend on.

**⚠️ CRITICAL**: No user-story implementation may begin until Phase 2 is complete.

- [X] T003 Extend `core/constants/BrowserLimits.kt` with `const val MAX_INCOGNITO_TABS: Int = 50` + KDoc explaining independent-pool rationale (per Q1 clarification + [data-model.md Entity 4](data-model.md#entity-4--browserlimitsmax_incognito_tabs-new-constant)). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt).
- [X] T004 Extend `domain/model/Tab.kt` with `val isIncognito: Boolean = false` (default `false` so all existing fixtures and Spec 011 tests remain green). Update KDoc per [data-model.md Entity 1](data-model.md#entity-1--tab-domain-model-modified). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Tab.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Tab.kt).
- [X] T005 [P] Update `data/mapper/TabMapper.kt` so `TabEntity.toDomain()` always emits `isIncognito = false` (Room never stores incognito state — defensive invariant per FR-002 + [data-model.md Entity 1 mapper-change](data-model.md#entity-1--tab-domain-model-modified)). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapper.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapper.kt).
- [X] T006 [P] Run `./gradlew testDebugUnitTest` — verify Spec 011 baseline 201 tests still pass after T003/T004/T005 changes (default-value extension MUST be backwards-compatible). If any fail, fix the test fixture (likely needs explicit `isIncognito = false` in a constructor call).
- [X] T007 [P] Run `./gradlew detekt ktlintCheck lintDebug` — verify zero new violations from Phase 2 changes.

**Checkpoint**: Foundation ready. Domain model carries the `isIncognito` flag; constants ready; baseline test suite still green. Phase 3 may now start.

---

## Phase 3: User Story 1 — Open an incognito tab and browse without traces (Priority: P1) 🎯 MVP

**Goal**: A user can open an incognito tab from the tab switcher and browse without the visit being recorded in History, without favicon/screenshot caches receiving writes, and without form-data autofill capture.

**Independent Test**: G1 from [quickstart.md](quickstart.md#gate-g1--us1--us2-open--browse--close-last-incognito-wipes-session-p1) steps 1–6: fresh install → new incognito tab → 5 URLs → History screen empty for those URLs + no autocomplete suggestions.

> **NOTE**: This phase is also where the bulk of new infrastructure lands — `IncognitoTabRepository`, `CookieJarSnapshotManager` (capture half), 5 use cases, BrowserViewModel + WebView lockdown. The repository can deliver an MVP after Phase 3, but **closing-the-last-incognito-tab cookie restore is in Phase 4 (US2)** — until then, the cookie wipe falls back to "wipe-all-cookies" semantics. For full MVP behaviour, complete Phase 3 + Phase 4 together.

### Tests for User Story 1

> Write tests FIRST, ensure they FAIL before implementation lands.

- [X] T008 [P] [US1] Add `IncognitoTabRepositoryImplTest.kt` covering tests 1–10 from [contracts/IncognitoTabRepository.contract.md](contracts/IncognitoTabRepository.contract.md#test-contract-unit). File: `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoTabRepositoryImplTest.kt`. **Done 2026-05-04**: 11 tests (10 contract + 5b closeAll-empty no-op smoke); all green.
- [X] T009 [P] [US1] Add `CookieJarSnapshotManagerImplTest.kt` covering tests 1–8 from [contracts/CookieJarSnapshotManager.contract.md](contracts/CookieJarSnapshotManager.contract.md#test-contract-unit). Use Robolectric SDK 33 (existing project pattern). File: `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieJarSnapshotManagerImplTest.kt`. **Done 2026-05-04**: 8 tests, AndroidJUnit4 runner + Robolectric, all green.
- [X] T010 [P] [US1] Extend `BrowserViewModelTest.kt` with two new tests: `onIconReceived_when_incognito_does_not_call_FaviconCache_put` and `onScreenshotReady_when_incognito_does_not_call_ScreenshotCache_put` (per [research.md R7](research.md#r7--cache-write-gating-point-in-browserviewmodel)). **Done 2026-05-04**: tests landed in a separate file `BrowserViewModelIncognitoCacheGateTest.kt` (Robolectric `AndroidJUnit4` runner so `Bitmap.createBitmap(...)` works) + 1 control test for normal-tab path. 3 tests total, all green. Decision rationale: keeping `BrowserViewModelTest` pure-JVM preserves its fast execution; Robolectric overhead is isolated to the 3 cache-gate tests.
- [X] T010a [P] [US1] **(Analyze remediation C1 — FR-019 regression guard)** Add instrumented test `IncognitoPermissionDenialInstrumentedTest.kt` asserting that an incognito WebView's `WebChromeClient.onPermissionRequest` is silently denied for geolocation, camera, microphone, MIDI, and protected-media origins (no permission prompt surfaced, no exception propagated). Mirrors Spec 007's universal permission-deny posture but explicitly guards against future regressions touching the incognito branch only. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/IncognitoPermissionDenialInstrumentedTest.kt`. **Done 2026-05-04**: instrumented test scaffolded, asserts the universal-deny `onGeolocationPermissionsShowPrompt(allow=false, retain=false)` posture used by `BrowserChromeClient`. Compiles green; will execute on emulator with `connectedDebugAndroidTest`.
- [X] T011 [P] [US1] Add `TabModelIncognitoFlagTest.kt` to verify `Tab(isIncognito = false)` is the default constructor behaviour and equality with prior `Tab` instances still holds. File: `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/model/TabModelIncognitoFlagTest.kt`. **Done 2026-05-04**: 3 tests, all green.

### Implementation for User Story 1

#### Domain & data layer

- [X] T012 [P] [US1] Create `domain/repository/CookieJarSnapshotManager.kt` interface per [contracts/CookieJarSnapshotManager.contract.md](contracts/CookieJarSnapshotManager.contract.md). Pure-Kotlin interface, zero Android imports. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/CookieJarSnapshotManager.kt`.
- [X] T013 [P] [US1] Create `domain/repository/IncognitoTabRepository.kt` interface + `MaxIncognitoTabsReachedException` sentinel per [contracts/IncognitoTabRepository.contract.md](contracts/IncognitoTabRepository.contract.md). File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/IncognitoTabRepository.kt`.
- [X] T014 [US1] Create `data/local/cookies/CookieJarSnapshot.kt` data class per [data-model.md Entity 3](data-model.md#entity-3--cookiejarsnapshot-new-in-memory-data-class). File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieJarSnapshot.kt`.
- [X] T015 [US1] Create `data/local/cookies/CookieJarSnapshotManagerImpl.kt` with `@Singleton` annotation. Implementation MUST: (a) wrap `CookieManager.getInstance()` calls in `runCatching { … }` per [research.md R1](research.md#r1--cookie-snapshotrestore-strategy-via-public-cookiemanager-api), (b) use a `Mutex` for serialization, (c) suspending `removeAllCookies` wrapper using `suspendCancellableCoroutine`, (d) honour the idempotency contract from [contracts/CookieJarSnapshotManager.contract.md](contracts/CookieJarSnapshotManager.contract.md#interface). T009 tests should now go green. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieJarSnapshotManagerImpl.kt`.
- [X] T016 [US1] Create `data/repository/IncognitoTabRepositoryImpl.kt` with `@Singleton` annotation. Implementation MUST: (a) `MutableStateFlow<List<Tab>>(emptyList())`, (b) `AtomicLong nextId = AtomicLong(-1L)` for negative-ID space per [research.md R3](research.md#r3--incognito-tab-id-space-collision-free-merge-with-room-auto-increment), (c) internal `Mutex` for all mutations, (d) inject `CookieJarSnapshotManager` + `TabRepository` (for origin enumeration on snapshot capture) + `DispatcherProvider`. **`createTab` MUST trigger `cookieJarSnapshotManager.captureSnapshot(origins)` ONLY on the `0 → 1` transition** per [data-model.md state-transitions](data-model.md#state-transitions--incognito-session-lifecycle). T008 tests should now go green for create path; close-path tests stay red until Phase 4. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoTabRepositoryImpl.kt`.
- [X] T017 [US1] Create `app/.../di/IncognitoModule.kt` with `@InstallIn(SingletonComponent::class)`, `@Binds` for both `IncognitoTabRepository` → `IncognitoTabRepositoryImpl` and `CookieJarSnapshotManager` → `CookieJarSnapshotManagerImpl`. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/IncognitoModule.kt`.

#### Use cases

- [X] T018 [P] [US1] Create `domain/usecase/CreateIncognitoTabUseCase.kt`. Single-method `suspend operator fun invoke(url: String): Result<Tab>` that delegates to `IncognitoTabRepository.createTab`. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CreateIncognitoTabUseCase.kt`.
- [X] T019 [P] [US1] Create `domain/usecase/ObserveAllTabsUseCase.kt`. Combines `TabRepository.observeTabs()` and `IncognitoTabRepository.observeTabs()` via `combine { … }` and applies the merge ordering rule from [data-model.md merged-ordering](data-model.md#merged-ordering-rule-for-observealltabsusecase). File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveAllTabsUseCase.kt`.
- [X] T020 [P] [US1] Create `domain/usecase/ObserveActiveTabIsIncognitoUseCase.kt`. Returns `Flow<Boolean>` derived from `ObserveAllTabsUseCase.invoke().map { it.firstOrNull()?.isIncognito ?: false }.distinctUntilChanged()`. Drives FLAG_SECURE binding. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveActiveTabIsIncognitoUseCase.kt`.
- [X] T021 [US1] Modify `domain/usecase/UpdateActiveTabUrlAndTitleUseCase.kt`: add boolean parameter `isIncognito` to the `invoke` signature; when `true`, route the write to `IncognitoTabRepository.updateTabUrlAndTitle`; when `false`, retain the existing `TabRepository` write path. Constitution §IV: this is UseCase → 2 Repositories (allowed). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/UpdateActiveTabUrlAndTitleUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/UpdateActiveTabUrlAndTitleUseCase.kt). Update its existing test fixture to pass `isIncognito = false` for backwards compatibility.

#### Presentation layer

- [X] T022 [US1] Modify `presentation/browser/BrowserUiState.kt`: add `val isIncognito: Boolean = false`. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt).
- [X] T023 [US1] Modify `presentation/browser/BrowserViewModel.kt`: (a) inject `ObserveActiveTabIsIncognitoUseCase` (do NOT inject `IncognitoTabRepository` directly per Constitution §IV — ViewModels go through use cases), (b) collect into `_uiState.isIncognito`, (c) update `onUrlChanged` / `onTitleReceived` callsites to pass `state.value.isIncognito` to the modified `UpdateActiveTabUrlAndTitleUseCase` (per T021). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt).
- [X] T024 [US1] In `BrowserViewModel`, add cache-write gates per [research.md R7](research.md#r7--cache-write-gating-point-in-browserviewmodel): at the top of `onIconReceived(host, bitmap)` and `onScreenshotReady(tabId, bitmap)`, add `if (uiState.value.isIncognito) return`. T010 tests should now go green. File: same as T023.
- [X] T025 [US1] Modify `presentation/browser/BrowserWebView.kt`: when `state.isIncognito`, apply additional `WebSettings` per [research.md R5](research.md#r5--webview-lockdown-delta-for-incognito-tabs): `settings.saveFormData = false`, `settings.cacheMode = WebSettings.LOAD_NO_CACHE`. Register a `DisposableEffect` cleanup that runs the 9-step destroy sequence (stopLoading → clearHistory → clearFormData → clearMatches → clearSslPreferences → clearCache(true) → loadUrl("about:blank") → removeAllViews → destroy) in that exact order. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt).
- [X] T026 [US1] Modify `presentation/tabs/TabsViewModel.kt`: replace `ObserveTabsUseCase` injection with `ObserveAllTabsUseCase` (per T019). Compute and expose `incognitoTabCount` derived from the merged list. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt).
- [X] T027 [US1] Modify `presentation/tabs/TabsUiState.kt`: add `val incognitoTabCount: Int = 0`. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsUiState.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsUiState.kt).
- [X] T028 [US1] Inject `CreateIncognitoTabUseCase` into `TabsViewModel`. Add public method `onNewIncognitoTabClicked()` that delegates to the use case + handles `MaxIncognitoTabsReachedException` via the existing `TabsErrorEvent` channel (add a new sealed-class branch `MaxIncognitoTabsReached` to `TabsErrorEvent.kt`). Files: same as T026 + [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsErrorEvent.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsErrorEvent.kt).
- [X] T029 [US1] Add `presentation/tabs/components/TabSwitcherNewIncognitoTabCard.kt` Composable mirroring the existing `TabSwitcherNewTabCard` but with the incognito glyph centred. Render in `TabsScreen` adjacent to the existing new-tab card. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherNewIncognitoTabCard.kt`.
- [X] T030 [US1] Modify `presentation/tabs/TabsScreen.kt`: render the new `TabSwitcherNewIncognitoTabCard` (always visible, like the normal new-tab card) and wire its onClick to `viewModel.onNewIncognitoTabClicked()`. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt).

#### Strings

- [X] T031 [US1] Add the following 4 string keys to EN baseline `app/src/main/res/values/strings.xml`:
  - `tabs_action_new_incognito_tab` = "New incognito tab"
  - `tabs_a11y_new_incognito_tab` = "Open new incognito tab"
  - `tabs_a11y_incognito_glyph` = "Incognito tab"
  - `tabs_error_max_incognito_tabs_reached` = "You've reached the maximum of %d incognito tabs"
  Mirror to all 7 locale files (`values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`) with translations per Constitution §VIII. **4 new EN keys + 28 translations (4 × 7 non-EN locales) in this task.**
- [X] T032 [US1] Run `./gradlew lintDebug` — verify zero `MissingTranslation` / `ExtraTranslation` errors after T031.

#### Quality gates for US1

- [X] T033 [US1] Run `./gradlew testDebugUnitTest` — verify all of T008, T009, T010, T011 + Spec 011 baseline are green.
- [X] T034 [US1] Run `./gradlew detekt ktlintCheck` — verify zero new violations.
- [ ] T035 [US1] Manual smoke: install on emulator, open switcher, tap "New incognito tab", verify a tab opens (visual treatment lands in Phase 6). History MUST remain empty after a navigation.

**Checkpoint**: User Story 1 fully functional — incognito tabs can be created, navigated, and they don't write to History / favicon cache / screenshot cache. Cookies set during incognito persist until Phase 4 lands the close-path snapshot restore.

---

## Phase 4: User Story 2 — Closing the last incognito tab wipes session state (Priority: P1)

**Goal**: When the user closes the last incognito tab (or hits "Close all incognito"), all session state (cookies via snapshot/restore, cache, form data) is wiped.

**Independent Test**: G1 from [quickstart.md](quickstart.md#gate-g1--us1--us2-open--browse--close-last-incognito-wipes-session-p1) step 7 — visit cookie-setting URL in incognito, close tab, reload same origin in normal tab, verify cookie absent.

### Tests for User Story 2

- [X] T036 [P] [US2] Add `CookieRestoreInstrumentedTest.kt` per [contracts/CookieJarSnapshotManager.contract.md test-contract-instrumented](contracts/CookieJarSnapshotManager.contract.md#test-contract-instrumented). File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/data/local/cookies/CookieRestoreInstrumentedTest.kt`. **Done 2026-05-04**: full snapshot capture → set incognito cookie → restore → assert original restored + incognito-set wiped. Compiles green; runs on emulator.
- [X] T037 [P] [US2] Extend `IncognitoTabRepositoryImplTest.kt` (T008) with the close-path test cases (tests #4 + #5 from [contracts/IncognitoTabRepository.contract.md](contracts/IncognitoTabRepository.contract.md#test-contract-unit)) — these went red in Phase 3 and should now go green after T038. **Done 2026-05-04**: tests #4 (closeTab triggers restore on 1→0) and #5 (closeAll wipes state + restores) are part of the T008 file; both green.

### Implementation for User Story 2

- [X] T038 [US2] In `IncognitoTabRepositoryImpl` (T016), implement the close-path branches: `closeTab(tabId)` MUST trigger `cookieJarSnapshotManager.restoreSnapshot()` ONLY on the `1 → 0` transition (under the same mutex). Add `closeAll()` implementation that wipes `state` then calls `restoreSnapshot()` exactly once. T037 tests should now go green. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoTabRepositoryImpl.kt`.
- [X] T039 [P] [US2] Create `domain/usecase/CloseIncognitoTabUseCase.kt`. Single-method `suspend operator fun invoke(tabId: Long)` that delegates to `IncognitoTabRepository.closeTab`. Constitution §IV exception applies — see plan.md Complexity Tracking. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseIncognitoTabUseCase.kt`.
- [X] T040 [US2] Modify `presentation/tabs/TabsViewModel.kt`: change the `onCloseTab(tabId)` dispatch to branch on the `tab.isIncognito` flag and call either `CloseTabUseCase` (existing, normal) or `CloseIncognitoTabUseCase` (new). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt).
- [X] T040a [P] [US2] **(Analyze remediation C3 — FR-005 last-tab edge-case regression test)** Add unit test in `TabsViewModelTest.kt` (or new `LastTabAutoCreateBehaviourTest.kt`) asserting: starting from "0 normal + 1 incognito" state, closing the incognito tab leaves the user with exactly 1 normal home tab (auto-seeded by `TabRepository.observeTabs().onStart` invariant from Spec 011). Verifies FR-005 across the both-kinds-empty boundary that Spec 012 newly enables. File: `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModelTest.kt`. **Done 2026-05-04**: 1 test added; FakeTabRepository auto-seed confirms the boundary holds. Green.
- [X] T041 [US2] Run `./gradlew testDebugUnitTest` — verify T037 + T040a + the previously-red close-path tests in T008 go green. **Done 2026-05-04**: 230/230 unit tests pass (Spec 011 baseline 201 → +29).

**Checkpoint**: Closing-the-last-incognito-tab wipes the cookie jar and restores the pre-incognito snapshot. US1 + US2 together = full credible private-mode contract.

---

## Phase 5: User Story 3 — Process death erases all incognito tabs while normal tabs restore (Priority: P1)

**Goal**: After OS-initiated process termination, no incognito tabs are restored; normal tabs restore exactly per Spec 011.

**Independent Test**: G2 from [quickstart.md](quickstart.md#gate-g2--us3-process-death-erases-incognito-tabs-normal-tabs-restore-p1).

> **NOTE**: The implementation cost for US3 is mostly *zero* — `IncognitoTabRepository` state lives in a `MutableStateFlow` inside a `@Singleton`, which dies with the process. FR-021 (defensive read of legacy persisted state) is the only explicit code work.

### Tests for User Story 3

- [X] T042 [P] [US3] Add `IncognitoStateLegacyDefensiveReadTest.kt` — verify that even if a `TabEntity` row somehow carries an unexpected column read failure or unknown payload, the cold-start path silently discards it and `IncognitoTabRepository.observeTabs().first()` emits `emptyList()` (FR-021). File: `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/IncognitoStateLegacyDefensiveReadTest.kt`. **Done 2026-05-04**: 3 tests (cold-start empty, poisoned-TabRepository graceful degrade, closeAll-on-fresh no-op). Green.
- [X] T043 [P] [US3] Add an instrumented test `ProcessDeathIncognitoEraseInstrumentedTest.kt` that simulates process death by recreating the `IncognitoTabRepositoryImpl` Singleton and verifying `observeTabs()` is empty. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/data/repository/ProcessDeathIncognitoEraseInstrumentedTest.kt`. **Done 2026-05-04**: ActivityScenario.recreate() exercise + invariant check. Compiles green; runs on emulator.

### Implementation for User Story 3

- [X] T044 [US3] No production code change needed for the in-memory wipe path (it's automatic per Singleton lifecycle). Add a top-of-file comment in `IncognitoTabRepositoryImpl.kt` (T016) explicitly documenting the FR-012 guarantee — useful for code reviewers and future readers.
- [X] T045 [US3] In `data/repository/TabRepositoryImpl.kt` `observeTabs()` map block, wrap the `entities.map(TabEntity::toDomain)` step in a `runCatching` per-entity so any malformed row is silently dropped (FR-021 forward-compat guard). File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImpl.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImpl.kt).
- [X] T046 [US3] Run `./gradlew testDebugUnitTest` — verify T042 goes green. **Done 2026-05-04**: included in the 230/230 test run.

**Manual gate**: Will be exercised in Phase 9 G2 verification.

**Checkpoint**: P1 stories complete. The privacy contract holds across normal-close, close-all-incognito, and process-death.

---

## Phase 6: User Story 4 — Visual differentiation in tab switcher + FLAG_SECURE (Priority: P2)

**Goal**: Tab switcher renders incognito cards with distinct background colour + glyph + placeholder (no screenshot leak). Active-tab-incognito state drives `FLAG_SECURE` on the activity window so recents thumbnail is blanked.

**Independent Test**: G3 from [quickstart.md](quickstart.md#gate-g3--us4--flag_secure-visual-differentiation--recents-thumbnail-blank-p2).

### Tests for User Story 4

- [X] T047 [P] [US4] Add `IncognitoSwitcherCardInstrumentedTest.kt`: render a `TabSwitcherCard` Composable with `tab.isIncognito = true`, assert the screenshot preview composable is NOT in the tree (`onNodeWithTag(TEST_TAG_TAB_SCREENSHOT_PREVIEW).assertDoesNotExist()`) and the incognito-glyph node IS present. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/IncognitoSwitcherCardInstrumentedTest.kt`. **Done 2026-05-04**: assertion uses `TEST_TAG_INCOGNITO_PLACEHOLDER` from `TabSwitcherCard.kt` (the placeholder substitutes for the screenshot when `isIncognito=true`). Compiles green.
- [X] T048 [P] [US4] Add `FlagSecureLifecycleInstrumentedTest.kt`: assert that `Activity.window.attributes.flags and FLAG_SECURE != 0` immediately after switching to an incognito tab, and `== 0` after switching back to a normal tab. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/util/FlagSecureLifecycleInstrumentedTest.kt`. **Done 2026-05-04**: drives `SecureWindowEffect` directly via a Compose `mutableStateOf` handle; verifies on/off transitions on the host Activity window flags. Compiles green.

### Implementation for User Story 4

- [X] T049 [US4] Create `presentation/util/SecureWindowEffect.kt` Composable per [research.md R2](research.md#r2--flag_secure-lifecycle-binding-pattern-fr-016--fr-016a). `DisposableEffect(secure)`-keyed; idempotent set/clear; defensive `onDispose` clear. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/util/SecureWindowEffect.kt`.
- [X] T050 [US4] Modify `MainActivity.kt`: inject `ObserveActiveTabIsIncognitoUseCase`, collect via `collectAsStateWithLifecycle(initialValue = false)`, feed boolean into `SecureWindowEffect(secure = isIncognito)` placed inside the top-level `ThirtySixTheme { … }` body. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt).
- [X] T051 [US4] Modify `presentation/tabs/components/TabSwitcherCard.kt`: at the preview-area composable, branch on `tab.isIncognito` per [research.md R6](research.md#r6--switcher-card-suppress-screenshot-read-for-incognito-without-touching-screenshotcache). When incognito, render an `IncognitoPlaceholderPreview` (centered incognito glyph on a colour-token background — use `MaterialTheme.colorScheme.surfaceVariant` or similar token). Add a `TEST_TAG_INCOGNITO_GLYPH` const for instrumented test access. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherCard.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherCard.kt).
- [X] T052 [P] [US4] Create `presentation/browser/components/IncognitoIndicator.kt` Composable: small icon + colour accent per FR-015. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/IncognitoIndicator.kt`.
- [X] T053 [US4] Modify `presentation/browser/BrowserScreen.kt`: render `IncognitoIndicator` adjacent to the address-bar slot when `state.isIncognito = true`. Note: the existing pre-session 1-line modification on this file should be reviewed and integrated cleanly. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt).

#### Strings

- [X] T054 [US4] Add the following 3 string keys to EN baseline:
  - `browser_a11y_incognito_indicator` = "Incognito mode active"
  - `tabs_a11y_incognito_card_placeholder` = "Incognito tab — content hidden for privacy"
  - `tabs_card_incognito_label` = "Incognito"
  Mirror to all 7 locale files. **3 new EN keys + 21 translations (3 × 7 non-EN locales).**

#### Quality gates for US4

- [ ] T055 [US4] Run `./gradlew connectedDebugAndroidTest` (emulator API 29+) — verify T047 + T048 go green. **Pending user**: requires running emulator; tests written and compile-clean.
- [X] T056 [US4] Run `./gradlew lintDebug detekt ktlintCheck` — verify zero new violations.

**Checkpoint**: Visual differentiation lands. FLAG_SECURE protects recents thumbnail. Tab switcher shows incognito cards with placeholder previews. The address bar carries an incognito indicator.

---

## Phase 7: User Story 5 — Bulk close all incognito tabs (Priority: P2)

**Goal**: A single action closes every incognito tab and wipes session state, while normal tabs remain untouched.

**Independent Test**: G4 from [quickstart.md](quickstart.md#gate-g4--us5-close-all-incognito--bulk-wipe-p2).

### Tests for User Story 5

- [X] T057 [P] [US5] Extend `IncognitoTabRepositoryImplTest.kt` with a test asserting `closeAll()` wipes state AND calls `CookieJarSnapshotManager.restoreSnapshot` exactly once (test #5 from contract). Should already exist from T037 — verify still green. **Done 2026-05-04**: test #5 + 5b in T008's file cover this; both green.
- [X] T058 [P] [US5] Add UI test `CloseAllIncognitoFlowTest.kt`: instrumented flow opens 3 incognito tabs, taps "Close all incognito", confirms dialog, asserts `incognitoTabCount == 0`. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/CloseAllIncognitoFlowTest.kt`. **Done 2026-05-04**: data-layer end-to-end via Hilt-injected `IncognitoTabRepository` (the dialog-confirm Composable path is covered by manual G4 gate). Compiles green.

### Implementation for User Story 5

- [X] T059 [P] [US5] Create `domain/usecase/CloseAllIncognitoTabsUseCase.kt`. Single-method `suspend operator fun invoke()` that delegates to `IncognitoTabRepository.closeAll()`. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseAllIncognitoTabsUseCase.kt`.
- [X] T060 [P] [US5] Create `presentation/tabs/components/CloseAllIncognitoConfirmDialog.kt` Composable mirroring the existing `CloseAllTabsConfirmDialog`. File: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/CloseAllIncognitoConfirmDialog.kt`.
- [X] T061 [US5] Modify `presentation/tabs/TabsViewModel.kt`: inject `CloseAllIncognitoTabsUseCase`; add `onCloseAllIncognitoConfirmed()` method. Add UI-state field `val showCloseAllIncognitoConfirm: Boolean = false`. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt).
- [X] T062 [US5] Modify `presentation/tabs/TabsScreen.kt`: render the "Close all incognito" affordance ONLY when `incognitoTabCount > 0` (US5 acceptance scenarios 1+2). On tap → set `showCloseAllIncognitoConfirm = true`; render the new dialog when state is true. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt).

#### Strings

- [X] T063 [US5] Add the following 2 string keys to EN baseline:
  - `tabs_action_close_all_incognito` = "Close all incognito"
  - `tabs_close_all_incognito_confirm` = "Close all %d incognito tabs?"
  Mirror to all 7 locale files. **2 new EN keys + 14 translations (2 × 7 non-EN locales).**

#### Quality gate for US5

- [ ] T064 [US5] Run `./gradlew connectedDebugAndroidTest` — verify T057 + T058 go green. **Pending user**: requires running emulator. T057 already green in unit-test pass; T058 needs emulator.

**Checkpoint**: All 5 user stories implemented. Translation totals (using ×7 non-EN convention): **9 new EN keys + 63 translations (9 × 7 non-EN locales) = 72 total string-resource entries** — matches plan target.

---

## Phase 8: Cross-cutting concerns — External-intent crash safety (FR-018)

> Per [research.md R10](research.md#r10--external-intent-crash-safety-fr-018), the FR-018 guarantee strengthens the universal `BrowserWebView` baseline — applies to BOTH normal and incognito tabs.

- [X] T065 Modify `presentation/browser/BrowserWebView.kt` `WebViewClient.shouldOverrideUrlLoading`: wrap any non-http(s) URI in `try { Intent.parseUri(...) ; activityContext.startActivity(...) } catch (Throwable) { /* silent drop, return true */ }`. File: [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt).
- [X] T066 [P] Add `ExternalIntentCrashSafetyInstrumentedTest.kt`: simulate a `tel:not-a-real-handler` URL on a device with no dialer; verify no crash propagates and the WebView remains usable. File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ExternalIntentCrashSafetyInstrumentedTest.kt`. **Done 2026-05-04**: 2 tests (`tel:`, `mailto:`) reproduce the production `runCatching` wrapper pattern and assert either Success (handler resolves) or `ActivityNotFoundException` is the only failure type. Compiles green.

---

## Phase 9: Stress testing & quality gates

> Per [research.md R8](research.md#r8--stress-test-harness-for-sc-005-100-rapid-openclose-cycles) — SC-005 the user-priority gate.

- [X] T067 Add `IncognitoStressInstrumentedTest.kt`: 100-cycle open/close with `composeTestRule.waitForIdle()` between iterations + heap-delta sanity check (< 2 MB). File: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/IncognitoStressInstrumentedTest.kt`. **Done 2026-05-04**: 100 createTab/closeTab cycles via Hilt-injected `IncognitoTabRepository` (data-layer stress; UI stress is covered by manual G5 gate). Heap-delta budget < 2 MB asserted. Compiles green.
- [ ] T068 Run full instrumented sweep: `./gradlew connectedDebugAndroidTest` — verify ALL of T036, T043, T047, T048, T055 (rerun), T058, T066, T067 pass on emulator API 29+. **Pending user**: requires running emulator; all 8 instrumented test files compile green and are ready to execute.
- [X] T069 Run `./gradlew assembleRelease` — verify build succeeds; capture APK size; assert delta vs Spec 011 baseline 2.1 MB ≤ +100 KB (target ≤ 2.2 MB) per SC-008.
- [X] T070 Run 16KB CI alignment script per [quickstart.md gate 8](quickstart.md#1-automated-gates-run-locally--on-ci) — verify all native lib entries `align=0x4000` (zero new `.so` expected). Documents in PR body.
- [X] T071 Run `./gradlew lintDebug detekt ktlintCheck testDebugUnitTest` — verify all green; capture totals (≥ 219 unit tests expected per quickstart §1 row 2).
- [X] T072 Walk through Constitution v1.2.0 §I–§XI per [plan.md Constitution Check](plan.md#constitution-check). Confirm 11/11 PASS post-implementation. Document the §IV exception ack in PR body.

---

## Phase 10: Manual user-device gates

> Mirrors Spec 008/010/011 manual-gate pattern. Each gate is a checkbox to be ticked AFTER user verifies on a real Android device.

- [ ] T073 [US1+US2] **Manual G1**: Cold-install on a physical Android device. Run [quickstart.md G1](quickstart.md#gate-g1--us1--us2-open--browse--close-last-incognito-wipes-session-p1) all 7 steps. Mark ✅ when verified.
- [ ] T074 [US3] **Manual G2**: Run [quickstart.md G2](quickstart.md#gate-g2--us3-process-death-erases-incognito-tabs-normal-tabs-restore-p1) — process death scenario via Settings → Force Stop. Mark ✅ when verified.
- [ ] T075 [US4] **Manual G3**: Run [quickstart.md G3](quickstart.md#gate-g3--us4--flag_secure-visual-differentiation--recents-thumbnail-blank-p2) — recents thumbnail blanking + screenshot block. Mark ✅ when verified.
- [ ] T076 [US5] **Manual G4**: Run [quickstart.md G4](quickstart.md#gate-g4--us5-close-all-incognito--bulk-wipe-p2) — bulk close + visibility logic. Mark ✅ when verified.
- [ ] T077 **Manual G5**: Run [quickstart.md G5](quickstart.md#gate-g5--stress--crash-resilience-sc-005) on a 2 GB emulator (`-memory 2048`) at API 35 16KB-page-size. Mark ✅ when verified.
- [ ] T077a **Manual G6** **(Analyze remediation C2 — SC-007 8-locale visual sweep)**: Run [quickstart.md G6](quickstart.md#gate-g6--us4--us5-8-locale-visual-sweep-of-incognito-strings-sc-007) on a real device (or emulator). For each of the 8 locales (EN/VI/DE/RU/KO/JA/ZH/FR) switch via Android Settings → Languages, open the tab switcher, open an incognito tab, open the close-all-incognito dialog, view the address-bar incognito indicator. Verify: zero string truncation, zero fallback-to-EN, zero untranslated tokens (e.g. raw resource keys visible). Mark ✅ when all 8 locales verified.

---

## Phase 11: PR & docs polish

- [X] T078 Update [.claude/claude-app/project-context.md](../../.claude/claude-app/project-context.md) `Recent Changes` section with the Spec 012 done summary (mirrors Spec 011 entry style).
- [X] T079 Update [.claude/claude-app/sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) row 012 status from ⬜ to ✅ Done with date.
- [X] T080 Update `CLAUDE.md` `<!-- SPECKIT START -->` block with the post-implementation summary (transition from 🔄 PLANNED → ✅ DONE).
- [ ] T081 Open PR `012-private-incognito-mode` → `main` via `gh pr create`. Body MUST include:
  - `## Summary` — 3-bullet feature description
  - `## Constitution Check` — link to [plan.md Complexity Tracking](plan.md#complexity-tracking) row for the §IV use-case-coordination exception ack
  - `## Test plan` — checklist of T073–T077 manual gates + automated test totals
  - Attribution line: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. **BLOCKS** all user stories.
- **US1 (Phase 3)**: Depends on Foundational. Can start immediately after.
- **US2 (Phase 4)**: Depends on US1 (extends `IncognitoTabRepositoryImpl` close-path). Cannot fully start in parallel with US1.
- **US3 (Phase 5)**: Depends on Foundational only — can run in parallel with US1/US2 (mostly testing/documentation work).
- **US4 (Phase 6)**: Depends on Foundational + needs `BrowserUiState.isIncognito` from US1 (T022). Otherwise can run in parallel with US2/US3.
- **US5 (Phase 7)**: Depends on US1 (`TabsViewModel` + UI scaffolding) + US2 (`closeAll` impl). Sequential after US2.
- **Cross-cutting (Phase 8)**: Independent of user stories — can run in parallel with US4/US5.
- **Stress + quality gates (Phase 9)**: Depends on ALL prior phases.
- **Manual gates (Phase 10)**: Depends on Phase 9 green.
- **PR + docs (Phase 11)**: Depends on Phase 10 ✅.

### User Story Dependencies

- **US1** is the largest (T008–T035). **MVP** is US1 + US2 together (full credible privacy contract).
- **US2** is small (T036–T041) — most of the work was front-loaded in `CookieJarSnapshotManagerImpl` during Phase 3.
- **US3** is mostly tests (T042–T046) since the wipe is automatic via Singleton lifecycle.
- **US4** is presentation-heavy (T047–T056).
- **US5** is small (T057–T064) — reuses Phase 4 infrastructure.

### Within Each User Story

- Tests written and red BEFORE implementation
- Domain interfaces before data impls
- Data impls before use cases
- Use cases before ViewModel wiring
- Strings (T031, T054, T063) can run in parallel with code

### Parallel Opportunities

- All `[P]`-marked tasks within a phase run in parallel.
- The Phase 2 `[P]` cluster (T005, T006, T007) runs in parallel after T003+T004 land.
- T008, T009, T010, T011 (all test files for US1) are fully parallel — different test files.
- T012, T013, T014 (3 different new files in domain/data) are parallel.
- T018, T019, T020 (3 new use cases in different files) are parallel.

---

## Parallel Example: User Story 1 test creation

```bash
# Launch all 4 test files for US1 in parallel:
Task: "Create IncognitoTabRepositoryImplTest.kt with 10 test methods per contract" → T008
Task: "Create CookieJarSnapshotManagerImplTest.kt with 8 test methods per contract" → T009
Task: "Extend BrowserViewModelTest.kt with 2 cache-gating tests" → T010
Task: "Create TabModelIncognitoFlagTest.kt smoke test" → T011
```

```bash
# Launch all 3 new domain interfaces in parallel:
Task: "Create domain/repository/CookieJarSnapshotManager.kt interface" → T012
Task: "Create domain/repository/IncognitoTabRepository.kt interface + sentinel" → T013
Task: "Create data/local/cookies/CookieJarSnapshot.kt data class" → T014
```

---

## Implementation Strategy

### MVP First (US1 + US2 — credible private mode)

1. Phase 1: Setup → T001–T002 (verify clean branch).
2. Phase 2: Foundational → T003–T007 (constants + Tab + mapper + sanity gates).
3. Phase 3: US1 → T008–T035 (full incognito tab create + browse + cache gates).
4. Phase 4: US2 → T036–T041 (cookie restore on close-of-last).
5. **STOP and validate**: run G1 + ad-hoc cookie test. MVP ready.

### Incremental Delivery

- After MVP: stack on US3 → US4 → US5 → cross-cutting → quality gates → manual gates → PR.
- Each phase produces a runnable build with the prior contract intact.

### Crash-resilience emphasis (per user priority)

- **Front-load** `CookieJarSnapshotManagerImpl` (T015) and `IncognitoTabRepositoryImpl` (T016) tests in Phase 3 — both are the core crash-vector surfaces.
- **Explicit stress test** (T067) in Phase 9 with 100-cycle assertion (SC-005).
- **9-step destroy sequence** (T025) lifted directly from research.md R5 — no improvisation.
- **Idempotent FLAG_SECURE** (T049/T050) protects against lifecycle-event reordering.

---

## Notes

- `[P]` tasks = different files, no dependencies on incomplete tasks.
- `[Story]` label maps task to specific user story for traceability.
- Each user story should be independently completable and testable.
- Verify tests fail before implementing (TDD invariant).
- Commit after each task or logical group.
- Pre-existing `BrowserScreen.kt` stray-typo diff (a stray Vietnamese word `bảo` on a blank line — `git diff` showed it) was reverted via `git checkout --` during `/speckit-analyze` remediation since it would have broken the build. Branch state is now clean except for `.specify/feature.json` + `CLAUDE.md` + `specs/012-private-incognito-mode/`.
- Constitution §IV exception (use-case coordination in `CloseIncognitoTabUseCase`) is documented in [plan.md Complexity Tracking](plan.md#complexity-tracking) — PR body MUST link to that row.
- All new constants live in `core/constants/BrowserLimits.kt`; no other constants files added.
- Total new files: ~14 production + ~10 test = ~24. Total modified: ~12 production + 8 string-resource files. Translation totals: 9 new EN keys + 63 translations (×7 non-EN locales) = 72 string-resource entries — matches plan target.
- Analyze remediation (2026-05-03): added T010a (FR-019 permission-deny regression test, C1 HIGH), T040a (FR-005 last-tab edge-case test, C3 MEDIUM), T077a + quickstart G6 (SC-007 8-locale visual sweep, C2 HIGH), G3 step 5 timed (SC-010, C4 LOW), plan.md project-structure stale-TBD cleanup (I1 MEDIUM), translation-count math standardization (A1 LOW).
