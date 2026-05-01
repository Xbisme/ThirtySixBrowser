# Tasks: Navigation Controls

**Input**: Design documents from `/specs/008-navigation-controls/`
**Prerequisites**: [plan.md](plan.md) (✅), [spec.md](spec.md) (✅), [research.md](research.md) (✅), [data-model.md](data-model.md) (✅), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md) (✅), [quickstart.md](quickstart.md) (✅)

**Tests**: REQUIRED per Constitution §VI (Testing Discipline). Unit tests for ViewModel logic, instrumented Compose tests for bottom-bar interaction. Predictive-back animation visual is deferred to manual gate (mirrors Specs 004/006/007 pattern).

**Organization**: 4 user stories per spec. US1 + US2 are P1 (back/forward + system gesture); US3 + US4 are P2 (Reload/Stop + Home). Foundational phase covers state-plumbing, manifest, strings, and the `WebViewActionsHandle` pattern shared by all four stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps to user story from [spec.md](spec.md): `[US1]`, `[US2]`, `[US3]`, `[US4]`
- All file paths are absolute from repo root

## Path Conventions

- Source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/...`
- Manifest: `app/src/main/AndroidManifest.xml`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Instrumented tests: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline before any change. Spec 008 introduces zero new packages, so no Gradle / version-catalog changes are needed.

- [X] T001 Run baseline verification: `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` — confirm Spec 007 baseline is green (94 unit tests pass, zero lint warnings, detekt baseline unchanged) before any Spec 008 file is touched.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: All four user stories depend on these state-plumbing, manifest, and string changes. Each task here is a prerequisite for at least one user story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Constants & DI

- [X] T002 Add `HOME_URL` constant in [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppDefaults.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppDefaults.kt): `const val HOME_URL: String = "https://www.google.com/"` with KDoc explaining v1.0 fixed value + Spec 016 override path.

- [X] T003 Update [app/src/main/kotlin/com/raumanian/thirtysix/browser/di/UrlConfigModule.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/di/UrlConfigModule.kt) to provide `AppDefaults.HOME_URL` via `@Named("default_home_url")` (replaces the inline `"https://example.com"` literal from Spec 007).

### State surface

- [X] T004 [P] Extend `BrowserUiState` in [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt) with two new fields: `val canGoBack: Boolean = false`, `val canGoForward: Boolean = false`. Defaults preserve binary compatibility for existing callers.

- [X] T005 [P] Extend `BrowserWebViewCallbacks` in [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebViewCallbacks.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebViewCallbacks.kt) with two new fields: `val onCanGoBackChange: (Boolean) -> Unit`, `val onCanGoForwardChange: (Boolean) -> Unit`. Total field count = 6 (still ≤ Detekt `LongParameterList.functionThreshold = 6`).

- [X] T006 Add `WebViewClient.doUpdateVisitedHistory(view, url, isReload)` override in [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) that calls `callbacks.onCanGoBackChange(view.canGoBack())` and `callbacks.onCanGoForwardChange(view.canGoForward())`. Depends on T005.

- [X] T007 Add `fun onCanGoBackChanged(canGoBack: Boolean)` and `fun onCanGoForwardChanged(canGoForward: Boolean)` methods on [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt); each calls `_uiState.update { it.copy(...) }`. Depends on T004.

### WebView side-channel + Scaffold restructure

- [X] T008 Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/WebViewActionsHandle.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/WebViewActionsHandle.kt): a class holding `var goBack: () -> Unit`, `var goForward: () -> Unit`, `var reload: () -> Unit`, `var stopLoading: () -> Unit`, `var loadHome: () -> Unit` — each defaulting to `{}` no-op. Populated in `BrowserWebView.factory { }` block; consumed by `NavigationBottomBar` click handlers + `PredictiveBackHandler`.

- [X] T009 Wire `WebViewActionsHandle` into [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) — accept handle as parameter; in the `AndroidView` `factory { webView -> ... }` block, assign each handle field to a lambda calling the corresponding `webView.*` method (`webView.goBack()`, `webView.goForward()`, `webView.reload()`, `webView.stopLoading()`, `webView.loadUrl(homeUrl)`). Depends on T008.

- [X] T010 Restructure [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) to wrap content in M3 `Scaffold(bottomBar = { /* placeholder */ })` so that the WebView is in `content` slot with `Modifier.padding(scaffoldPadding)`. Bar slot stays empty until T020. Verify rotation test (Spec 007 `BrowserScreenRotationTest`) still passes with the new Scaffold.

- [X] T011 Pass `BrowserUiState.canGoBack`/`canGoForward`/`loadingState` from `BrowserScreen` into `BrowserWebView` callbacks: wire `onCanGoBackChange = viewModel::onCanGoBackChanged`, `onCanGoForwardChange = viewModel::onCanGoForwardChanged` in the `BrowserWebViewCallbacks` constructor inside `BrowserScreen.kt`. Depends on T006, T007, T010.

### Localization

- [X] T012 Add 5 new keys to [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) (EN baseline): `browser_action_back="Back"`, `browser_action_forward="Forward"`, `browser_action_reload="Reload page"`, `browser_action_stop="Stop loading"`, `browser_action_home="Home"`. Must follow Spec 004 lint enforcement (`MissingTranslation`/`ExtraTranslation` at error severity).

- [X] T013 [P] Translate the 5 keys in [app/src/main/res/values-vi/strings.xml](app/src/main/res/values-vi/strings.xml): `Lùi / Tiến / Tải lại trang / Dừng tải / Trang chủ`. Depends on T012.

- [X] T014 [P] Translate the 5 keys in [app/src/main/res/values-de/strings.xml](app/src/main/res/values-de/strings.xml): `Zurück / Vorwärts / Seite neu laden / Laden anhalten / Startseite`. Depends on T012.

- [X] T015 [P] Translate the 5 keys in [app/src/main/res/values-ru/strings.xml](app/src/main/res/values-ru/strings.xml): `Назад / Вперёд / Обновить страницу / Остановить загрузку / Домой`. Depends on T012.

- [X] T016 [P] Translate the 5 keys in [app/src/main/res/values-ko/strings.xml](app/src/main/res/values-ko/strings.xml): `뒤로 / 앞으로 / 페이지 새로 고침 / 로드 중지 / 홈`. Depends on T012.

- [X] T017 [P] Translate the 5 keys in [app/src/main/res/values-ja/strings.xml](app/src/main/res/values-ja/strings.xml): `戻る / 進む / ページを再読み込み / 読み込みを停止 / ホーム`. Depends on T012.

- [X] T018 [P] Translate the 5 keys in [app/src/main/res/values-zh/strings.xml](app/src/main/res/values-zh/strings.xml): `返回 / 前进 / 重新加载 / 停止加载 / 主页`. Depends on T012.

- [X] T019 [P] Translate the 5 keys in [app/src/main/res/values-fr/strings.xml](app/src/main/res/values-fr/strings.xml): `Précédent / Suivant / Recharger la page / Arrêter le chargement / Accueil`. Depends on T012.

### Bottom-bar shell

- [X] T020 Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt) skeleton: M3 `BottomAppBar` containing 4 placeholder `IconButton`s in left-to-right order — Back / Forward / Reload-or-Stop / Home — all currently disabled with `enabled = false`. Parameters: `canGoBack`, `canGoForward`, `isLoading`, `onBack`, `onForward`, `onReloadOrStop`, `onHome` (all `() -> Unit` lambdas). Use `MaterialTheme.colorScheme.*` for colors; no inline tints. **Per Constitution §III No-Hardcode Rule and matching project pattern from [BrowserLoadingIndicator.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserLoadingIndicator.kt) / [BrowserErrorState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserErrorState.kt), declare 4 file-top test-tag constants exposed for instrumented tests**: `const val TEST_TAG_NAV_BACK: String = "nav_back"`, `TEST_TAG_NAV_FORWARD = "nav_forward"`, `TEST_TAG_NAV_RELOAD_STOP = "nav_reload_stop"`, `TEST_TAG_NAV_HOME = "nav_home"`. Each `IconButton` references these via `Modifier.testTag(TEST_TAG_NAV_*)` — no inline string literals. Depends on T012 (uses `stringResource` for content descriptions).

- [X] T021 Wire `NavigationBottomBar` into `BrowserScreen` Scaffold's `bottomBar` slot: pass `uiState.canGoBack`, `uiState.canGoForward`, `uiState.loadingState is LoadingState.Loading`, plus 4 lambdas wired to `webViewActions::goBack`, `webViewActions::goForward`, `webViewActions::reload` / `webViewActions::stopLoading` (conditional), `webViewActions::loadHome`. Depends on T020, T010, T009.

### Foundational test baseline updates

- [X] T022 Update existing [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) tests that construct `BrowserUiState(...)` to use named arguments OR the new defaults — confirm Spec 007's 10 baseline ViewModel tests still pass after T004's field addition.

- [X] T023 Update existing instrumented tests in [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/) (`BrowserScreenInstrumentedTest`, `BrowserScreenOfflineErrorTest`) so they account for the new Scaffold wrapper and bottom-bar presence. Verify each test still passes its original assertion. Depends on T010, T021.

- [X] T023a **Decouple `BrowserScreenInstrumentedTest` from the production URL value** (REQUIRED before T002/T003 land — otherwise `containsString("Example Domain")` assertions on lines 58 + 103 will fail when production URL flips to `https://www.google.com/`). Apply Spec 007's offline-error pattern: add `@UninstallModules(UrlConfigModule::class)` to the test class, declare a nested `@Module @InstallIn(ViewModelComponent::class) FakeUrlConfigModule { @Provides @Named("default_home_url") fun provideTestUrl(): String = "https://example.com/" }`, so the assertions on the `<h1>Example Domain</h1>` element continue to work deterministically (unchanged by T002/T003). Depends on T002, T003.

**Checkpoint**: Foundation ready — `BrowserScreen` renders Scaffold + WebView + empty/disabled bottom bar. State plumbing live. All baseline tests pass. User story implementation can now proceed (in parallel if desired).

---

## Phase 3: User Story 1 — Back/Forward through visited pages (Priority: P1) 🎯 MVP

**Goal**: User can tap Back to retreat through session history and Forward to re-advance, with affordances correctly enabled/disabled per WebView state.

**Independent Test**: Cold-start app → tap Home (T046 also satisfies this; or use the initial Google load) → tap a search result → tap Back twice → land on Google → tap Forward → land on result. Back/Forward enabled/disabled state correct at each step.

### Implementation for User Story 1

- [X] T024 [US1] In [NavigationBottomBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt), wire the Back `IconButton`: `enabled = canGoBack`, `onClick = onBack`, `Icons.AutoMirrored.Filled.ArrowBack`, `contentDescription = stringResource(R.string.browser_action_back)`, `Modifier.testTag(TEST_TAG_NAV_BACK)` (constant declared in T020).

- [X] T025 [US1] In `NavigationBottomBar.kt`, wire the Forward `IconButton`: `enabled = canGoForward`, `onClick = onForward`, `Icons.AutoMirrored.Filled.ArrowForward`, `contentDescription = stringResource(R.string.browser_action_forward)`, `Modifier.testTag(TEST_TAG_NAV_FORWARD)`.

### Tests for User Story 1

- [X] T026 [P] [US1] Add unit tests to [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) covering: (a) initial `uiState.canGoBack == false`, (b) `onCanGoBackChanged(true)` propagates to state, (c) `onCanGoBackChanged(false)` propagates, (d) `onCanGoForwardChanged` symmetric, (e) state independence (toggling one does not affect the other).

- [X] T027 [P] [US1] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarBackForwardTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarBackForwardTest.kt): Compose UI tests verifying (a) Back disabled when `canGoBack=false`, (b) Back enabled + click invokes `onBack` lambda when `canGoBack=true`, (c) Forward disabled/enabled symmetric, (d) content descriptions present in default locale.

- [ ] T028 [P] [US1] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenHistoryTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenHistoryTest.kt): integration test that loads a known multi-page sequence using a hilt-injected fake URL config, taps the in-app Back button via `composeTestRule.onNodeWithTag("nav_back").performClick()`, and asserts the URL display reverts. Use the same `@UninstallModules(UrlConfigModule::class)` + nested `FakeUrlConfigModule` pattern Spec 007 established.

**Checkpoint**: User Story 1 complete — Back/Forward affordances functional, disabled state correct, click dispatch verified.

---

## Phase 4: User Story 2 — System back gesture + predictive (Priority: P1)

**Goal**: System back gesture (edge-swipe / nav-bar back) navigates WebView history when present; falls through to screen-exit when at root. On Android 14+, predictive preview animates during gesture.

**Independent Test**: Navigate A → B → C, perform system back gesture → land on B; repeat → land on A; repeat → app exits. On Android 14+ device, slow gesture shows preview of destination during swipe.

### Implementation for User Story 2

- [X] T029 [US2] Add `android:enableOnBackInvokedCallback="true"` to the `<application>` element in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml). Add `tools:targetApi="33"` if Lint flags `UnusedAttribute` (matches Spec 004's `localeConfig` pattern).

- [X] T030 [US2] Add `PredictiveBackHandler(enabled = uiState.canGoBack) { progress -> try { progress.collect {} ; webViewActions.goBack() } catch (e: CancellationException) { throw e } }` inside [BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt), placed at the top level of the composable (sibling of Scaffold). Per coroutines convention re-throw `CancellationException`. Depends on T029.

### Tests for User Story 2

- [ ] T031 [P] [US2] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenBackGestureTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenBackGestureTest.kt) — using Espresso `pressBack()`: (a) when `canGoBack=true`, pressing back navigates one history entry backward (assert by URL state delta); (b) when `canGoBack=false`, pressing back finishes the host activity (assert via `composeTestRule.activityRule.scenario.state == DESTROYED` or activity-finish observer).

- [ ] T032 [US2] **Manual gate (deferred to user device verification)**: predictive preview animation visible on Android 14+ device per [quickstart.md](quickstart.md) Gate 7a. Mirrors Spec 007 T042 / Spec 006 T043b pattern — cannot be reliably automated. Mark in tasks.md as ✅ when user confirms on real device.

**Checkpoint**: User Story 2 complete — system back gesture honors WebView history, exits screen at root, predictive preview verified on Android 14+.

---

## Phase 5: User Story 3 — Reload/Stop combined affordance (Priority: P2)

**Goal**: Single bottom-bar slot toggles between Stop (during loading) and Reload (otherwise). Click does the right action with no race.

**Independent Test**: Throttle network → tap a slow URL → mid-load tap the affordance → load aborts with Stop semantic visible. After page settles → tap same affordance → re-fetch with Reload semantic visible.

### Implementation for User Story 3

- [X] T033 [US3] In [NavigationBottomBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt), wire the combined Reload/Stop `IconButton`: read `isLoading` parameter; render `Icons.Filled.Close` (stop semantic) when true, `Icons.Filled.Refresh` (reload) otherwise; content description: `stringResource(R.string.browser_action_stop)` vs `R.string.browser_action_reload`. `onClick = onReloadOrStop` (single lambda; semantic decision lives in `BrowserScreen`). `Modifier.testTag(TEST_TAG_NAV_RELOAD_STOP)`.

- [X] T034 [US3] In [BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) bottom-bar `onReloadOrStop` lambda: `if (uiState.loadingState is LoadingState.Loading) webViewActions.stopLoading() else webViewActions.reload()`. Decision is made at click time from current state, not pre-computed. Depends on T033.

### Tests for User Story 3

- [X] T035 [P] [US3] Add Compose UI test to [NavigationBottomBarBackForwardTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarBackForwardTest.kt) (or create a new `NavigationBottomBarReloadStopTest.kt`): assert (a) Stop icon + "Stop loading" content description shown when `isLoading=true`, (b) Reload icon + "Reload page" content description shown when `isLoading=false`, (c) click invokes `onReloadOrStop` lambda exactly once regardless of which semantic is current.

- [ ] T036 [P] [US3] Add an integration test to [BrowserScreenHistoryTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenHistoryTest.kt) (or new file `BrowserScreenReloadStopTest.kt`): force `LoadingState.Loading(0.4f)` via test seam → assert Stop semantic via `onNodeWithTag("nav_reload_stop").assertContentDescriptionEquals(...)`; transition to `Loaded` → assert Reload semantic.

**Checkpoint**: User Story 3 complete — combined Reload/Stop affordance toggles correctly with no race.

---

## Phase 6: User Story 4 — Home button (Priority: P2)

**Goal**: One-tap escape to `https://www.google.com/` from any URL. Adds a normal entry to session history (Back returns to prior page).

**Independent Test**: From any page, tap Home → URL becomes `https://www.google.com/`. Tap Back → return to prior page (Home was a normal nav, not a stack reset).

### Implementation for User Story 4

- [X] T037 [US4] In [NavigationBottomBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt), wire the Home `IconButton`: always enabled, `Icons.Filled.Home`, `contentDescription = stringResource(R.string.browser_action_home)`, `onClick = onHome`, `Modifier.testTag(TEST_TAG_NAV_HOME)`.

- [X] T038 [US4] In [WebViewActionsHandle.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/WebViewActionsHandle.kt) and the `BrowserWebView.kt` factory wiring (T009), make `loadHome = { webView.loadUrl(homeUrl) }` where `homeUrl` is the `@Named("default_home_url")` Hilt-injected `String` already passed into `BrowserWebView` from `BrowserViewModel` / `BrowserScreen` per the Spec 007 wiring. Verify the wiring chain: ViewModel → BrowserScreen → BrowserWebView → handle.

### Tests for User Story 4

- [X] T039 [P] [US4] Add Compose UI test (extend `NavigationBottomBarBackForwardTest.kt` or new file `NavigationBottomBarHomeTest.kt`): assert Home button (a) is always enabled regardless of `canGoBack`/`canGoForward` state, (b) click invokes `onHome` lambda, (c) content description is "Home" in default locale.

- [ ] T040 [P] [US4] Add integration test to [BrowserScreenHistoryTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenHistoryTest.kt) (or new file `BrowserScreenHomeTest.kt`): use `FakeUrlConfigModule` with a controlled URL → after initial load, tap Home → assert WebView's `currentUrl` (via `BrowserUiState.currentUrl`) becomes the configured home URL.

**Checkpoint**: All 4 user stories complete. Bottom bar fully functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final quality gates + documentation.

- [X] T041 Run [quickstart.md](quickstart.md) Gate 2 (`./gradlew testDebugUnitTest`) — verify ≥ 100 tests pass (Spec 007 baseline 94 + ~10 new from Spec 008).

- [X] T042 Run [quickstart.md](quickstart.md) Gate 3 (`./gradlew lintDebug`) — verify zero warnings; specifically `MissingTranslation` and `ExtraTranslation` checks PASS for the 5 new keys × 8 locales.

- [X] T043 Run [quickstart.md](quickstart.md) Gate 4 (`./gradlew detekt ktlintCheck`) — verify zero violations and detekt baseline UNCHANGED from Spec 007.

- [X] T044 Run [quickstart.md](quickstart.md) Gate 5 (`./gradlew assembleRelease` + `.specify/scripts/bash/verify-16kb-alignment.sh`) — confirm APK ≤ 1.62 MB and all 24 native lib entries `align=0x4000`.

- [ ] T045 Run [quickstart.md](quickstart.md) Gate 6 (`./gradlew connectedDebugAndroidTest`) — emulator/device required; verify all instrumented tests pass (Spec 007 baseline + new NavigationBottomBar* + BrowserScreenHistory + BrowserScreenBackGesture).

- [X] T046 Update CLAUDE.md `## Recent Changes` and `<!-- SPECKIT START -->` Active Spec block — mark Spec 008 as ✅ Done with implementation summary (test counts, APK size, 16KB result, manual-gate status).

- [X] T047 Update [.claude/claude-app/project-context.md](.claude/claude-app/project-context.md) Spec 008 section: change status from 🔄 plan → ✅ implemented; add concrete test counts + APK size delta + any decisions discovered at impl time (e.g., quirk in `doUpdateVisitedHistory` firing for fragment navs — discovered during T028).

- [X] T048 Update [.claude/claude-app/sdd-roadmap.md](.claude/claude-app/sdd-roadmap.md) Spec 008 row: status from `🔄 Specified` → `✅ Done <date>`.

- [ ] T049 **Manual gate (deferred to user device verification)**: Run [quickstart.md](quickstart.md) Gate 7 (visual + interaction sweep across all 8 locales, Android 14+ predictive preview, TalkBack disabled-state announcement). Mark ✅ in tasks.md when user confirms on physical device.

- [X] T050 [P] Add a regression test for **FR-017** rapid-tap / config-change resilience: instrumented test in [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenRapidTapTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenRapidTapTest.kt) that (a) navigates A → B → C in the FakeUrlConfigModule-controlled WebView, (b) calls `composeRule.onNodeWithTag(TEST_TAG_NAV_BACK).performClick()` 10 times in tight succession, (c) asserts the activity is still alive (no crash), `BrowserUiState.canGoBack == false`, `canGoForward == true`, current URL is page A. Also asserts that triggering rotation mid-sequence does not lose the navigation state. Closes the FR-017 + Edge Case bullet 3 + Edge Case bullet 7 coverage gap surfaced by `/speckit-analyze` C1.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1. **BLOCKS all user stories.** Within Phase 2, ordering:
  - T002 → T003 (DI provides constant)
  - T004 + T005 parallel
  - T006 depends on T005
  - T007 depends on T004
  - T008 independent (new file)
  - T009 depends on T008
  - T010 depends on (none in particular but conceptually after T009)
  - T011 depends on T006, T007, T010
  - T012 → T013–T019 parallel
  - T020 depends on T012
  - T021 depends on T020 + T010 + T009
  - T022 depends on T004
  - T023 depends on T010, T021
- **Phase 3 (US1) + Phase 4 (US2) + Phase 5 (US3) + Phase 6 (US4)**: All parallel-eligible after Phase 2 completes (no inter-story dependencies; each story extends the bar shell from T020 in its own slot).
- **Phase 7 (Polish)**: Depends on all desired user stories complete.

### User Story Dependencies

- **US1 (P1)**: Independent after Phase 2. Touches Back + Forward affordance slots in `NavigationBottomBar`.
- **US2 (P1)**: Independent after Phase 2. Touches manifest + adds `PredictiveBackHandler` in `BrowserScreen`. Does NOT modify `NavigationBottomBar`.
- **US3 (P2)**: Independent after Phase 2. Touches Reload/Stop affordance slot in `NavigationBottomBar`.
- **US4 (P2)**: Independent after Phase 2. Touches Home affordance slot + `loadHome` in `WebViewActionsHandle`.

### Parallel Opportunities

- **Foundational locale translations** (T013–T019): all 7 parallel after T012.
- **Foundational state plumbing** (T004 + T005): parallel.
- **All four user stories**: parallel after Phase 2 if developer capacity allows. Each modifies a distinct slot inside `NavigationBottomBar` (T024 Back / T025 Forward / T033 Reload-Stop / T037 Home), so concurrent edits do not conflict at the file level — but coordinated commit messages help.
- **Tests within a story**: all marked [P] (different files or independent tests within same file).

---

## Parallel Example: Foundational locale translations

```text
# After T012 (English baseline) lands, run all 7 translations in parallel:
T013 [P] vi locale (file values-vi/strings.xml)
T014 [P] de locale (file values-de/strings.xml)
T015 [P] ru locale (file values-ru/strings.xml)
T016 [P] ko locale (file values-ko/strings.xml)
T017 [P] ja locale (file values-ja/strings.xml)
T018 [P] zh locale (file values-zh/strings.xml)
T019 [P] fr locale (file values-fr/strings.xml)
```

## Parallel Example: User Story 1 tests

```text
# Tests for US1 are independent and can run in parallel:
T026 [P] [US1] Unit tests in BrowserViewModelTest.kt
T027 [P] [US1] Compose UI tests in NavigationBottomBarBackForwardTest.kt
T028 [P] [US1] Integration test in BrowserScreenHistoryTest.kt
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 — both P1)

1. Phase 1 (Setup) — T001
2. Phase 2 (Foundational) — T002–T023
3. Phase 3 (US1 — Back/Forward affordances) — T024–T028
4. Phase 4 (US2 — System back gesture) — T029–T032
5. **STOP and VALIDATE**: bottom bar back-forward functional + system gesture works → MVP shippable.
6. Run automated gates (T041–T045) on a feature-gated branch slice.

This MVP delivers FR-001, FR-002, FR-003, FR-004, FR-010, FR-011, FR-012 + relevant SCs.

### Incremental P2 delivery

7. Phase 5 (US3 — Reload/Stop combined) — T033–T036.
8. Phase 6 (US4 — Home) — T037–T040.
9. Run automated gates again (T041–T045).
10. Phase 7 polish + docs (T046–T048).
11. **Manual user-device gates** (T032, T049) — single user verification session covering both deferred items together.
12. Open PR.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks.
- Tests REQUIRED per Constitution §VI; no skipping unless explicit constitution-amendment.
- Verify each Phase 2 task incrementally (`./gradlew assembleDebug`) — early breakage in foundational state plumbing cascades to all user-story tasks.
- Commit after each phase boundary OR after each user-story completion (smaller commits for easier review).
- Avoid: cross-story dependencies that would break independence; same-file conflicts within `NavigationBottomBar.kt` between US1/US3/US4 tasks (each touches a distinct slot, so commits are file-line-disjoint, but rebasing is still simpler when phases land sequentially).
- `Modifier.testTag(...)` strings live in production code (the Composable file), so per Constitution §III they MUST be `const val` declared at the top of the same file and exposed for tests. Project convention (see [BrowserLoadingIndicator.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserLoadingIndicator.kt) + [BrowserErrorState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserErrorState.kt)): `const val TEST_TAG_<NAME>: String = "..."` at file top + `private const val TEST_TAG: String = TEST_TAG_<NAME>` for use inside the file. T020 declares the 4 NavigationBottomBar tag constants up-front.
- Predictive-back animation cannot be unit-tested. Manual gate (T032 + T049) is the project's pattern.
