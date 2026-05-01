# Tasks: WebView Compose Wrapper (Spec 007)

**Input**: Design documents from `specs/007-webview-compose-wrapper/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
**Tests**: Both unit (`testDebugUnitTest`) and instrumented (`connectedDebugAndroidTest`) tests included — Constitution §VI mandates instrumented WebView coverage; FR-012 explicitly requires the latter.
**Organization**: Tasks grouped by user story (US1 P1 / US2 P2 / US3 P3) to enable incremental MVP shipping.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on incomplete tasks)
- **[Story]**: US1 / US2 / US3 (Setup / Foundational / Polish phases have no story label)
- File paths are absolute-relative to repo root.

## Path Conventions (mobile)

- Production code: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Instrumented tests: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/`
- Build wiring: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.github/workflows/ci.yml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the single new dependency, the URL constant, and the EN-baseline string keys before any feature code lands.

- [X] T001 Add Espresso-Web library to [gradle/libs.versions.toml](gradle/libs.versions.toml) — new entry `androidx-espresso-web = { group = "androidx.test.espresso", name = "espresso-web", version.ref = "espressoCore" }` (no new `[versions]` key — piggybacks on existing `espressoCore = "3.7.0"`). Add inline comment per project convention: `# Spec 007 — Espresso-Web (verified central.sonatype.com YYYY-MM-DD). Pure Java, zero .so → 16KB-safe.` Verify version is still latest 3.x stable at moment of edit per Constitution §IX.
- [X] T002 Wire Espresso-Web into [app/build.gradle.kts](app/build.gradle.kts) — add `androidTestImplementation(libs.androidx.espresso.web)` next to the existing `androidx.espresso.core` line (~line 202).
- [X] T003 Add `DEFAULT_HOME_URL` to [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt) — create the file as `object UrlConstants { const val DEFAULT_HOME_URL: String = "https://example.com" }` if it does not yet exist; otherwise extend the existing object with the constant.
- [X] T004 [P] Add 4 new EN string keys to [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml): `browser_loading_a11y` ("Loading page"), `browser_error_title` ("Page didn't load"), `browser_error_offline_hint` ("Check your internet connection and try again."), `browser_error_generic` ("Something went wrong. Try opening the page later.").

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Translate all 4 new string keys into the 7 non-EN locales so `MissingTranslation = error` lint gate (Spec 004) does not block downstream tasks. Each locale file is an independent edit → 7-way parallelization.

⚠️ **CRITICAL**: T005–T011 MUST all complete before any US1/US2/US3 task. Lint will fail the build if any locale lacks any of the 4 keys.

- [X] T005 [P] Add 4 keys (`browser_loading_a11y`, `browser_error_title`, `browser_error_offline_hint`, `browser_error_generic`) to [app/src/main/res/values-vi/strings.xml](app/src/main/res/values-vi/strings.xml) with VI translations.
- [X] T006 [P] Add 4 keys to [app/src/main/res/values-de/strings.xml](app/src/main/res/values-de/strings.xml) with DE translations.
- [X] T007 [P] Add 4 keys to [app/src/main/res/values-ru/strings.xml](app/src/main/res/values-ru/strings.xml) with RU translations.
- [X] T008 [P] Add 4 keys to [app/src/main/res/values-ko/strings.xml](app/src/main/res/values-ko/strings.xml) with KO translations.
- [X] T009 [P] Add 4 keys to [app/src/main/res/values-ja/strings.xml](app/src/main/res/values-ja/strings.xml) with JA translations.
- [X] T010 [P] Add 4 keys to [app/src/main/res/values-zh/strings.xml](app/src/main/res/values-zh/strings.xml) with ZH (Simplified) translations.
- [X] T011 [P] Add 4 keys to [app/src/main/res/values-fr/strings.xml](app/src/main/res/values-fr/strings.xml) with FR translations.
- [X] T012 Run `./gradlew lintDebug` and confirm zero `MissingTranslation` / `ExtraTranslation` errors. Verify the 32 entries (4 keys × 8 locales) round-trip via `grep` Gate 6 from [quickstart.md](specs/007-webview-compose-wrapper/quickstart.md).

**Checkpoint**: All locales translated → US1/US2/US3 implementation can start.

---

## Phase 3: User Story 1 — First Page Renders (Priority: P1) 🎯 MVP

**Goal**: User opens the app, the Browser screen replaces the placeholder Composable from Spec 002, and `https://example.com` is rendered inside a Compose-wrapped `android.webkit.WebView`. No loading indicator yet (US2), no error UI yet (US3) — just the happy path.

**Independent Test**: Install debug APK on emulator API 29 with Wi-Fi → tap launcher → within 5 s the page heading "Example Domain" is visible. Verified end-to-end by [BrowserScreenInstrumentedTest](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) (T021).

**Out of scope for this phase**: progress bar UI, error state UI, error event handling, error string mapping. Those live in US2/US3.

### Implementation tasks

- [X] T013 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/LoadingState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/LoadingState.kt) — sealed class with cases `Idle` (object), `Loading(progress: Float)` (data class with `init { require(progress in 0f..1f) }`), `Loaded` (object). **Do NOT add `Failed` yet — US3 introduces it in T028.**
- [X] T014a [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/di/UrlConfigModule.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/di/UrlConfigModule.kt) — Hilt `@Module @InstallIn(ViewModelComponent::class) object UrlConfigModule { @Provides @Named("default_home_url") fun provideDefaultHomeUrl(): String = UrlConstants.DEFAULT_HOME_URL }`. This indirection is what allows the instrumented offline-error test (T036) to swap in an invalid URL via `@TestInstallIn` without touching production code.
- [X] T014 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt) — `data class BrowserUiState(val currentUrl: String, val loadingState: LoadingState)`. **No `DEFAULT` companion** — the initial state is constructed inside `BrowserViewModel` from the Hilt-injected URL (T015), so different injected URLs produce different initial states (production = `example.com`; instrumented test = invalid host).
- [X] T015 [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) — `@HiltViewModel class BrowserViewModel @Inject constructor(@Named("default_home_url") private val defaultHomeUrl: String) : ViewModel()`. Field `private val _uiState = MutableStateFlow(BrowserUiState(currentUrl = defaultHomeUrl, loadingState = LoadingState.Idle))`; expose `val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()`. Implement only the THREE happy-path entry methods per [contracts/browser-screen-contract.md](specs/007-webview-compose-wrapper/contracts/browser-screen-contract.md): `onLoadStarted(url)` → updates currentUrl + transitions to Loading(0f); `onProgressChanged(newProgress: Int)` → clamps to 0..100, maps to `progress / 100f`, transitions to Loading(p) or Loaded if p=100; `onLoadFinished(url)` → idempotent transition to Loaded. **`onLoadFailed` is NOT added in US1** — it lands in T030 (US3) together with `ErrorReason` (T028) and `LoadingState.Failed` (T029) so the type ordering compiles in a single phase. Use `_uiState.update { ... }` exclusively for mutation per Constitution §III.
- [X] T016 [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) — internal `@Composable fun BrowserWebView(state: BrowserUiState, onLoadStarted: (String) -> Unit, onProgressChanged: (Int) -> Unit, onLoadFinished: (String) -> Unit, modifier: Modifier = Modifier)`. Body: `AndroidView(factory = { ctx -> WebView(ctx).apply { applySecuritySettings(this); webViewClient = BrowserWebViewClient(...); webChromeClient = BrowserChromeClient(...); loadUrl(state.currentUrl) } }, modifier = modifier)`. Pause/resume via `LocalLifecycleOwner.current` `LifecycleEventObserver` on ON_PAUSE → `webView.onPause()`, ON_RESUME → `webView.onResume()`. `DisposableEffect(Unit) { onDispose { webView.stopLoading(); webView.loadUrl("about:blank"); webView.removeAllViews(); webView.destroy() } }` per [research.md R2](specs/007-webview-compose-wrapper/research.md#r2--webview-lifecycle-integration-in-compose-androidview). Implement `applySecuritySettings(webView)` private helper inside this file per [research.md R4](specs/007-webview-compose-wrapper/research.md#r4--consolidated-webview-security-settings-cluster) — sets `javaScriptEnabled = true`, all 4 file-access flags `false`, `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW`, `domStorageEnabled = true`. **Zero call sites of `addJavascriptInterface` (FR-006).**
- [X] T017 [US1] In [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt), implement private nested classes `BrowserWebViewClient` (override `onPageStarted(view, url, favicon)` → `onLoadStarted(url)`; `onPageFinished(view, url)` → `onLoadFinished(url)`) and `BrowserChromeClient` (override `onProgressChanged(view, newProgress)` → `onProgressChanged(newProgress)`; override `onPermissionRequest(request)` → `request.deny()`; override `onGeolocationPermissionsShowPrompt(origin, callback)` → `callback.invoke(origin, false, false)` per FR-017). Error overrides for US3 land in T030.
- [X] T018 [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — `@Composable fun BrowserScreen(modifier: Modifier = Modifier, viewModel: BrowserViewModel = hiltViewModel())`. Body: `val state by viewModel.uiState.collectAsStateWithLifecycle()`; render `BrowserWebView(state = state, onLoadStarted = viewModel::onLoadStarted, onProgressChanged = viewModel::onProgressChanged, onLoadFinished = viewModel::onLoadFinished, modifier = modifier.fillMaxSize())`. No progress bar, no error UI yet (US2/US3 add).
- [X] T019 [US1] Wire `BrowserScreen` into [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt) — replace the placeholder Composable currently mounted at `AppDestination.Browser` route (Spec 002) with `BrowserScreen()`. Other 6 placeholder destinations remain unchanged.
- [X] T020 [P] [US1] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — pure JVM unit tests using Turbine for StateFlow assertions. Cover the 5 happy-path scenarios from [contracts/browser-screen-contract.md "Test contract"](specs/007-webview-compose-wrapper/contracts/browser-screen-contract.md#test-contract) that do NOT involve Failed state: initial DEFAULT state, full happy path (Idle → Loading(0) → Loading(0.5) → Loading(1) → Loaded), load-via-onPageFinished without progress(100), idempotent finish, progress clamp newProgress=150 → 1f. **Skip the Failed-state scenario** — moved to T034 (US3).
- [X] T021 [US1] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) — `@RunWith(AndroidJUnit4::class)` test using Espresso-Web. Launch a `ComponentActivity` (or use existing `MainActivity`) hosting `BrowserScreen()`, then `onWebView().withElement(findElement(Locator.TAG_NAME, "h1")).check(webMatches(getText(), containsString("Example Domain")))`. Test method asserts FR-012 + SC-005. Idle/wait via Espresso-Web's `forceJavascriptEnabled` is implicit since FR-005 enables JS. Use `ActivityScenarioRule` for state cleanup.
- [X] T021b [US1] Extend [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) with a rotation/recreation test asserting **SC-004**: capture the URL from `BrowserViewModel.uiState.value.currentUrl` after initial load → call `activityScenario.recreate()` (simulates rotation / configuration change) → assert `currentUrl` is unchanged. ViewModel survival is provided by `@HiltViewModel` + `ViewModelStore`; this test prevents a future regression where someone introduces an `Activity-scoped` recreation that drops the state.
- [X] T022 [US1] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest` locally to confirm US1 phase fully green: ViewModel unit tests pass + instrumented tests (T021 page-render + T021b rotation) both green. Cold-start timing observation noted (target ≤ 5 s for SC-001).

**US1 Checkpoint** ✅: User can install the app, launch it, see `example.com` rendered. MVP shippable. Loading indicator absent (blank screen during load), error state shows nothing useful — those are US2/US3.

---

## Phase 4: User Story 2 — Loading Feedback (Priority: P2)

**Goal**: While the page is loading, a Material3 `LinearProgressIndicator` is visible at the top edge of the Browser screen with determinate progress 0..1, hidden as soon as the page finishes loading. Improves UX on slow networks (US1 alone leaves a blank screen during load).

**Independent Test**: With US1 in place, throttle emulator network to 2G → re-launch app → top progress bar appears within 200 ms with visible progress < 1f → bar disappears when page renders. Verified manually (Gate 7 step 3 in [quickstart.md](specs/007-webview-compose-wrapper/quickstart.md)) plus an instrumented assertion (T026).

### Implementation tasks

- [X] T023 [P] [US2] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserLoadingIndicator.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserLoadingIndicator.kt) — `@Composable fun BrowserLoadingIndicator(progress: Float, modifier: Modifier = Modifier)` wrapping Material3 `LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth().semantics { contentDescription = ... })`. Use `stringResource(R.string.browser_loading_a11y)` for `contentDescription`. Spacing/padding strictly via theme tokens — zero inline `.dp`. Add tag/testTag for instrumented assertion: `Modifier.testTag("browser_loading_indicator")`.
- [X] T024 [US2] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — wrap `BrowserWebView` in a `Box` (or `Column` aligned top-start). When `state.loadingState is LoadingState.Loading`, render `BrowserLoadingIndicator(progress = (state.loadingState as LoadingState.Loading).progress, modifier = Modifier.align(Alignment.TopCenter))` overlaying the WebView's top edge (does NOT cover content per FR-003). Indicator absent in `Idle` and `Loaded` states.
- [X] T025 [P] [US2] Extend [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) with one additional test: `onProgressChanged` with values 0, 25, 50, 99 each surface a distinct `LoadingState.Loading(progress)` emission with the expected float (0f, 0.25f, 0.5f, 0.99f); progress 100 transitions to `Loaded`.
- [X] T026 [US2] Extend [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) with **two** Compose UI assertions enforcing SC-002 directly:
  1. **First-show within 200 ms**: launch `BrowserScreen`, then `composeTestRule.waitUntilExactlyOneExists(hasTestTag("browser_loading_indicator"), timeoutMillis = 200L)`. Failure → indicator did not appear within the 200 ms budget; SC-002 fails.
  2. **Hide on load completion**: after the WebView fires `onPageFinished` (idle on the Espresso-Web atom from T021), `composeTestRule.waitUntilDoesNotExist(hasTestTag("browser_loading_indicator"), timeoutMillis = 5_000L)`. Timeout aligned to SC-001's 5 s end-to-end budget.
- [X] T027 [US2] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest` to confirm US2 phase green. Manually verify the indicator behavior under throttled emulator network per Gate 7 step 3.

**US2 Checkpoint** ✅: Users see a top progress bar while page loads on slow networks. Error state still missing — US3 handles.

---

## Phase 5: User Story 3 — Error State (Priority: P3)

**Goal**: When the WebView fails to load (no internet, DNS failure, HTTP ≥ 400, SSL error), the user sees a localized full-screen error UI with a hint to check connectivity — never a blank screen, never raw `net::ERR_*` strings. Completes the spec's pipeline-verification scope.

**Independent Test**: Airplane mode ON → re-launch app → within 5 s the localized error UI replaces the WebView area, with `browser_error_title` heading and `browser_error_offline_hint` body text in the device's locale (verify across all 8 locales per Gate 7 step 5 in [quickstart.md](specs/007-webview-compose-wrapper/quickstart.md)).

### Implementation tasks

- [X] T028 [P] [US3] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ErrorReason.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ErrorReason.kt) — sealed class with cases `NetworkUnavailable` (object), `DnsFailure` (object), `HttpError(val statusCode: Int)` (data class), `SslError` (object), `Generic` (object). Same file: top-level extension function `@StringRes fun ErrorReason.toUserMessageRes(): Int` mapping per [research.md R6](specs/007-webview-compose-wrapper/research.md#r6--error-event-taxonomy-mapping): `NetworkUnavailable | DnsFailure → R.string.browser_error_offline_hint`; all others → `R.string.browser_error_generic`.
- [X] T029 [US3] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/LoadingState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/LoadingState.kt) — add fourth case `data class Failed(val reason: ErrorReason) : LoadingState()`.
- [X] T030 [US3] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) — **add** the new entry method `fun onLoadFailed(reason: ErrorReason) { _uiState.update { it.copy(loadingState = LoadingState.Failed(reason)) } }`. The method does not exist before this task (US1's T015 deliberately omitted it because `ErrorReason` was not yet declared). Depends on T028 + T029.
- [X] T031 [US3] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) — add a fourth parameter `onLoadFailed: (ErrorReason) -> Unit` to the `BrowserWebView` Composable signature, then in the nested `BrowserWebViewClient` override:
  - `onReceivedError(view, request, error)` — when `request.isForMainFrame` is true, map `error.errorCode` per [research.md R6](specs/007-webview-compose-wrapper/research.md#r6--error-event-taxonomy-mapping): `ERROR_HOST_LOOKUP → DnsFailure`; `ERROR_CONNECT | ERROR_TIMEOUT | ERROR_IO → NetworkUnavailable`; else → `Generic`. Then call `onLoadFailed(reason)`. Sub-frame errors filtered out (do nothing).
  - `onReceivedHttpError(view, request, errorResponse)` — when `request.isForMainFrame`, call `onLoadFailed(ErrorReason.HttpError(errorResponse.statusCode))`.
  - `onReceivedSslError(view, handler, error)` — call `handler.cancel()` (NEVER `proceed()` — Constitution §I), then `onLoadFailed(ErrorReason.SslError)`.
  - `BrowserScreen` (T033) wires the new parameter to `viewModel::onLoadFailed`.
- [X] T032 [P] [US3] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserErrorState.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/BrowserErrorState.kt) — `@Composable fun BrowserErrorState(reason: ErrorReason, modifier: Modifier = Modifier)`. Renders a centered Column with: an icon (use `Icons.Outlined.CloudOff` or similar M3 icon — `Icons.Default.WifiOff` if more semantic), a title `Text(stringResource(R.string.browser_error_title), style = MaterialTheme.typography.titleLarge)`, body `Text(stringResource(reason.toUserMessageRes()), style = MaterialTheme.typography.bodyMedium)`. All padding via `Spacing.*` tokens, all colors via `MaterialTheme.colorScheme.*`. Add `Modifier.testTag("browser_error_state")` for instrumented assertion.
- [X] T033 [US3] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — when `state.loadingState is LoadingState.Failed`, render `BrowserErrorState(reason = (state.loadingState as LoadingState.Failed).reason, modifier = Modifier.fillMaxSize())` instead of `BrowserWebView`. Loading indicator hidden when in Failed state. Wire the new `onLoadFailed = viewModel::onLoadFailed` parameter from T031 through to `BrowserWebView`.
- [X] T034 [P] [US3] Extend [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) with state-transition tests for the Failed branch: (a) `Idle → Loading → Failed(NetworkUnavailable)` after `onLoadStarted` + `onLoadFailed(NetworkUnavailable)`; (b) `Loaded → Failed(SslError)` after `onLoadStarted` + `onLoadFinished` + `onLoadStarted` + `onLoadFailed(SslError)`; (c) recovery `Failed → Loading(0f)` after `onLoadStarted` post-failure.
- [X] T035 [P] [US3] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ErrorReasonTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/ErrorReasonTest.kt) — table-driven JUnit test asserting `toUserMessageRes` returns the correct `R.string.*` ID for each of the 5 `ErrorReason` cases per [research.md R6](specs/007-webview-compose-wrapper/research.md#r6--error-event-taxonomy-mapping). 5 test methods OR 1 parameterized test.
- [X] T036a [US3] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/di/TestUrlConfigModule.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/di/TestUrlConfigModule.kt) — `@Module @TestInstallIn(components = [ViewModelComponent::class], replaces = [UrlConfigModule::class]) object TestUrlConfigModule { @Provides @Named("default_home_url") fun provideDefaultHomeUrl(): String = "https://this-domain-does-not-exist-thirtysix.invalid" }`. Replaces the production `UrlConfigModule` (T014a) only inside instrumented-test runs; this is the standard Hilt instrumented-test override pattern.
- [X] T036 [US3] Extend [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) with an offline-error test that uses the `TestUrlConfigModule` override from T036a (annotate the test class or method with `@HiltAndroidTest`). The unresolvable host triggers `WebViewClient.onReceivedError(ERROR_HOST_LOOKUP)` → `ErrorReason.DnsFailure` → `LoadingState.Failed`. Assert `composeTestRule.waitUntilExactlyOneExists(hasTestTag("browser_error_state"), timeoutMillis = 5_000L)` (SC-003). **Do NOT use `adb shell svc wifi disable`** — the Hilt-override path runs deterministically on any emulator without shell privileges and avoids polluting the device network state across test runs.
- [X] T037 [US3] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest` to confirm US3 phase green. Manually verify error UI in all 8 locales per Gate 7 step 5.

**US3 Checkpoint** ✅: Full Spec 007 user-visible behavior delivered (page renders, loading bar, localized error). Pipeline verified end-to-end.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run all 8 quickstart gates, capture release-build observations, update documentation, hand off the manual gate to user.

- [X] T038 Run Gate 1 — `./gradlew clean assembleDebug assembleRelease --stacktrace`. Record release APK size; assert delta vs Spec 006 baseline 1.56 MB is ≤ 200 KB (SC-008). Verify the `release built with DEBUG signature — NOT for distribution` warning still surfaces when no release keystore is configured.
- [X] T039 Run Gate 4 — `./gradlew lintDebug detekt ktlintCheck`. Confirm zero warnings/violations across all three. Detekt `MagicNumber` baseline must be UNCHANGED — if the new code introduces violations, address structurally (extract constants), do not add baseline entries.
- [X] T040 Run Gate 5 sub-greps (5a–5e from [quickstart.md](specs/007-webview-compose-wrapper/quickstart.md)) — verify zero hardcoded URL literals in `presentation/`/`domain/`, zero `addJavascriptInterface` call sites, exactly 3 manifest permissions, all 4 file-access settings present in `BrowserWebView.kt`, `MIXED_CONTENT_NEVER_ALLOW` present once.
- [X] T041 Run Gate 8 — `./gradlew assembleRelease` then `.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk`. Confirm 0 exit code; all `.so` LOAD entries `0x4000`+. No new `.so` introduced (Espresso-Web is `androidTestImplementation`, system WebView OS-provided).
- [X] T042 Mark T034 (Gate 7 manual emulator UX verification) as **DEFERRED to user device verification**. Add a note in [tasks.md](specs/007-webview-compose-wrapper/tasks.md) and quickstart matching the Spec 004 / Spec 006 manual-gate pattern. Spec is implementation-complete from automated-CI standpoint once T038–T041 pass.
- [X] T043 Update [CLAUDE.md "Recent Changes"](CLAUDE.md) with Spec 007 entry: instrumented-test job re-enable confirmation, Espresso-Web verified version + 16 KB attestation, observed cold-start timing for `example.com`, APK release size delta, count of new unit tests + instrumented tests, any post-impl deviations from plan/research.
- [X] T044 Update [CLAUDE.md SPECKIT block](CLAUDE.md) (between `<!-- SPECKIT START -->` and `<!-- SPECKIT END -->`) — set `Active Spec` to "None active" (Spec 007 done), promote Spec 007 to `Previous`, mark Spec 008 (`navigation-controls`) as suggested next.
- [X] T045 Verify `git diff` shows the [.github/workflows/ci.yml](.github/workflows/ci.yml) `instrumented-test` re-enable change is part of the branch's commit set (carried from `main` as uncommitted edit at branch creation). If still uncommitted, stage it for the Spec 007 commit so the CI gate ships in the same PR as the test that exercises it.

---

## Dependency Graph

```
Phase 1 (Setup):           T001 → T002, T003, T004 (T002/T003/T004 sequential — different files but small)
                            ↓
Phase 2 (Foundational):    T005..T011 [P×7 locales] → T012 (lint check)
                            ↓
                            ▼
Phase 3 (US1) — MVP shippable:
   T014a UrlConfigModule ──┐
   T013  [P] LoadingState ─┤
   T014  [P] BrowserUiState┤── T015 ViewModel ── T016 BrowserWebView ── T017 client/chrome ── T018 Screen ── T019 NavGraph
                           ┘                                                                                      ↓
                                                                                              T020  [P] unit test ↓
                                                                                              T021  instrumented  ↓
                                                                                              T021b rotation test ↓
                                                                                              T022  verify pass ◀─┘

Phase 4 (US2) — adds loading bar:
   T023 [P] LoadingIndicator ──┐
                                ├── T024 wire into Screen ── T025 [P] unit test ── T026 instrumented (200ms+5s) ── T027 verify
   (T024 depends on T023 + T018 from US1)                                                                            ↓
                                                                                                                      ▼
Phase 5 (US3) — adds error UI:
   T028  [P] ErrorReason ──────┐
   T029  LoadingState.Failed ──┤── T030 VM adds onLoadFailed ── T031 client error overrides ──┐
   T032  [P] ErrorState UI ────┘                                                                ├── T033 wire Screen
                                                                                                │
   T036a TestUrlConfigModule (test-only Hilt override) ─────────────┐                          │
   T034  [P], T035 [P], T036 instrumented offline ◀──────── all converge ────────────────────  ┘
                                                                                T037 verify pass

Phase 6 (Polish):           T038 → T039 → T040 → T041 → T042 → T043 → T044 → T045
                            (mostly sequential — gating + doc updates)
```

**Critical path**: T001 → T002 → T012 → T014a → T015 → T016 → T017 → T018 → T019 → T021 → T021b → T024 → T026 → T030 → T031 → T033 → T036a → T036 → T038 → T041 → T044. ~21 sequential steps.

**Parallel batches**:

- **Phase 2**: T005..T011 (7 locale files) all run in parallel.
- **US1**: T013 + T014 in parallel; T020 in parallel with anything after T015.
- **US2**: T023 in parallel with leftover US1 finalization; T025 in parallel.
- **US3**: T028 + T032 in parallel; T034 + T035 in parallel.

---

## Implementation Strategy — Incremental MVP

The phase boundaries mean each phase produces a **shippable artifact**:

1. **After Phase 3 (US1)**: app compiles, runs, renders example.com end-to-end. Could merge as Spec 007.0 if scope shrunk.
2. **After Phase 4 (US2)**: + visible loading progress on slow networks. Spec 007.1.
3. **After Phase 5 (US3)**: + localized error state across 8 locales. Full Spec 007 as defined.
4. **After Phase 6**: documentation/CLAUDE.md current; manual Gate 7 handed off to user; PR-ready.

**Recommended approach**: implement all phases sequentially in a single branch (already on `007-webview-compose-wrapper`). Open the PR after Phase 6 is complete, with the working-tree-carried CI re-enable change included.

---

## Format Validation

Every task above has been verified against the strict checklist format:

- ✅ All start with `- [ ]` checkbox
- ✅ All have a `T###` task ID
- ✅ All non-Setup/non-Foundational/non-Polish tasks have a `[US1]` / `[US2]` / `[US3]` story label
- ✅ Parallelizable tasks have a `[P]` marker (T004, T005–T011, T013, T014, T020, T023, T025, T028, T032, T034, T035)
- ✅ Every task references at least one absolute-relative file path
- ✅ Setup phase: no story label (T001–T004)
- ✅ Foundational phase: no story label (T005–T012)
- ✅ Polish phase: no story label (T038–T045)

**Total tasks**: 48 (4 Setup + 8 Foundational + 12 US1 + 5 US2 + 11 US3 + 8 Polish). Increase from the original 45 reflects the post-`/speckit-analyze` remediation: T014a (UrlConfigModule), T021b (rotation test), T036a (TestUrlConfigModule). Renumbering kept stable via `Ta`-suffix convention.
**Tasks per story**: US1 = 12 / US2 = 5 / US3 = 11
**Suggested MVP scope**: complete Phases 1–3 (T001–T022) — page renders end-to-end with rotation-survival assertion; loading bar + error UI added incrementally in subsequent phases.

---

## Suggested next: `/speckit-analyze`

Run cross-artifact consistency analysis (spec ↔ plan ↔ tasks alignment) before `/speckit-implement`.
