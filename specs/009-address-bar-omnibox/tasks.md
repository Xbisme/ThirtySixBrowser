---

description: "Task list for Spec 009 — Address Bar / Omnibox"
---

# Tasks: Address Bar / Omnibox (Spec 009)

**Input**: Design documents from `specs/009-address-bar-omnibox/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md), [quickstart.md](quickstart.md)

**Tests**: Tests are INCLUDED — Constitution §VI mandates unit tests for all business logic + Compose UI tests for critical user flows. The Spec 008 precedent (102/102 unit tests + `NavigationBottomBarTest` component-level UI tests) carries forward.

**Organization**: Tasks are grouped by user story (US1–US5) so each story can be implemented and tested independently. Foundational refactors that span stories live in Phase 2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks — safe to run in parallel
- **[Story]**: `[US1]`–`[US5]` for tasks tied to a specific user story; absent for Setup / Foundational / Polish

## Path Conventions

Single-module Android app. All source code paths below are absolute from repo root:

- Production source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/strings.xml`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Instrumented (Compose UI / Hilt) tests: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the spec-009 branch is current and the working tree is clean before structural changes begin.

- [x] T001 Verify on branch `009-address-bar-omnibox` with clean working tree: run `git status` and `git rev-parse --abbrev-ref HEAD`; abort if dirty or on a different branch.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add new constants, the two new `BrowserUiState` fields, the new `WebViewActionsHandle.loadUrl` lambda, and the 8-locale string keys. These are touched by every user story; doing them first keeps each story phase focused on its own logic. **No callback-bundle refactor here** — that lands in US3 since it co-locates naturally with the new `onUrlChange` callback US3 introduces.

**⚠️ CRITICAL**: No user story phase may begin until this phase is complete.

- [x] T002 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlPatterns.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlPatterns.kt) with `object UrlPatterns { const val URL_HEURISTIC_REGEX = "^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*|.*\\..*" ; const val ADDRESS_BAR_NEWLINE_REGEX = "[\\r\\n]+" }` and a KDoc explaining the simple `://` or `.` heuristic decision (research R2).
- [x] T003 [P] Extend [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/UrlConstants.kt) — add TWO new constants next to the existing `DEFAULT_HOME_URL`: (1) `const val GOOGLE_SEARCH_URL_TEMPLATE = "https://www.google.com/search?q=%s"` (KDoc: Spec 010 will replace inline use with `SearchEngineRepository`); (2) `const val HTTPS_SCHEME_PREFIX = "https://"` (KDoc: prepended by `AddressBarInputClassifier` when input matches the URL heuristic but lacks a scheme — Constitution §III "URLs (default home, search) → UrlConstants.kt" mapping covers scheme literals as well as full URLs).
- [x] T004 Add the two EN baseline keys to [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml): `browser_address_bar_hint` (e.g., "Search or enter URL") and `browser_address_bar_clear_cd` ("Clear address bar"). Use the project's existing `<!-- Spec 009 — address bar / omnibox -->` comment block style (mirrors Spec 008's strings comment block).
- [x] T005 [P] Translate the two new keys into the 7 non-EN locales — edit each of [values-vi/strings.xml](app/src/main/res/values-vi/strings.xml), [values-de/strings.xml](app/src/main/res/values-de/strings.xml), [values-ru/strings.xml](app/src/main/res/values-ru/strings.xml), [values-ko/strings.xml](app/src/main/res/values-ko/strings.xml), [values-ja/strings.xml](app/src/main/res/values-ja/strings.xml), [values-zh/strings.xml](app/src/main/res/values-zh/strings.xml), [values-fr/strings.xml](app/src/main/res/values-fr/strings.xml). 14 translations total. Verify with `./gradlew lintDebug` that `MissingTranslation` does NOT fire.
- [x] T006 Add `addressBarText: String = ""` and `isAddressBarFocused: Boolean = false` to the [BrowserUiState data class](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt). Update KDoc to mention Spec 009 added these as transient UI fields. Defaults preserve binary compatibility with all existing call sites (Spec 007 `BrowserScreenInstrumentedTest`, Spec 008 callers).
- [x] T007 Add `var loadUrl: (String) -> Unit = {}` field to [WebViewActionsHandle](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/WebViewActionsHandle.kt) alongside the existing 5 lambdas (`goBack`, `goForward`, `reload`, `stopLoading`, `loadHome`). Update class KDoc.
- [x] T008 Wire `loadUrl` inside [BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) `factory { webView -> ... }` block: `actionsHandle.loadUrl = { url -> webView.loadUrl(url) }`. Same pattern as the existing `goBack` / `reload` etc. lambda assignments.
- [x] T009 Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — assert default `BrowserUiState` from a freshly constructed `BrowserViewModel` has `addressBarText == ""` and `isAddressBarFocused == false`.

**Checkpoint**: Foundation complete. New constants + state fields + action handle in place. User story implementation can begin.

---

## Phase 3: User Story 1 — Submit a URL to navigate (Priority: P1) 🎯 MVP

**Goal**: User taps the address bar, types a URL (with or without scheme), submits via the keyboard's Go action; the WebView navigates.

**Independent Test**: With the app on the home page, tap the address bar, type `developer.android.com`, press the keyboard Go action. The WebView loads `https://developer.android.com/...`. Verifiable on a real device via Quickstart Gate 7 steps 5–6 (or in CI via the `AddressBarTest` Compose UI test below using a stub `WebViewActionsHandle.loadUrl` that just records the URL).

### Tests for User Story 1 (write FIRST — must fail before implementation) ⚠️

- [x] T010 [P] [US1] Create [AddressBarInputClassifierTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/AddressBarInputClassifierTest.kt) with table-driven URL classification tests: `https://example.com` → `Url("https://example.com")`; `developer.android.com` → `Url("https://developer.android.com")`; `HTTPS://example.com` → `Url("HTTPS://example.com")` (preserve uppercase); `192.168.1.1` → `Url("https://192.168.1.1")`; `   developer.android.com   ` → `Url("https://developer.android.com")` (trim); `developer.android.com\n` → `Url("https://developer.android.com")` (newline strip); `""` → `Empty`; `   ` → `Empty`. Also assert `kotlin.coroutines` → `Url(...)` as a documented false-positive accepted in v1 (FR edge case).
- [x] T011 [P] [US1] Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — add tests for `onAddressBarTextChange("foo")` updates state; `onAddressBarFocusChange(true)` toggles flag; `onAddressBarSubmit()` returns `false` for empty/whitespace input and does NOT touch a stub `WebViewActionsHandle.loadUrl`; `onAddressBarSubmit()` returns `true` and calls `loadUrl("https://example.com")` for input `example.com`; `onAddressBarSubmit()` returns `true` and preserves explicit `http://example.com` scheme. **FR-013 cancel-previous-load assertion** (C1 remediation): set `addressBarText = "first.com"`, call `onAddressBarSubmit()`, then set `addressBarText = "second.com"`, call `onAddressBarSubmit()` — assert the stub `loadUrl` was invoked exactly twice in order with `https://first.com` then `https://second.com`. WebView platform contract guarantees the second `loadUrl` cancels the first; the test asserts the ViewModel correctly forwards both invocations rather than coalescing or skipping.

### Implementation for User Story 1

- [x] T012 [P] [US1] Create [AddressBarInputClassifier.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/AddressBarInputClassifier.kt). Declare `internal sealed interface AddressBarSubmitResult { object Empty : AddressBarSubmitResult; data class Url(val target: String) : AddressBarSubmitResult; data class Query(val text: String) : AddressBarSubmitResult }`. Implement `internal fun classifyAddressBarInput(raw: String): AddressBarSubmitResult` that: (1) replaces newlines via `UrlPatterns.ADDRESS_BAR_NEWLINE_REGEX` then `trim()`; (2) returns `Empty` if blank; (3) matches against `Regex(UrlPatterns.URL_HEURISTIC_REGEX)`: if match — return `Url(target)` where `target = if (cleaned.contains("://")) cleaned else "${UrlConstants.HTTPS_SCHEME_PREFIX}$cleaned"`; else return `Query(cleaned)`. **NO inline `"https://"` literal** — use the constant added in T003 to satisfy Constitution §III No-Hardcode rule (D1 remediation).
- [x] T013 [US1] In [BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt), add three new methods per [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md): `fun onAddressBarTextChange(newText: String)` (updates state), `fun onAddressBarFocusChange(focused: Boolean)` (updates state), `fun onAddressBarSubmit(): Boolean` (calls `classifyAddressBarInput(_uiState.value.addressBarText)`; for `Empty` returns `false`; for `Url(target)` calls `actionsHandle.loadUrl(target)` and returns `true`; **`Query` branch is intentionally a stub `TODO()` until US2 — write a comment marking the location**). Inject `WebViewActionsHandle` into the ViewModel via constructor or pass per-call (decided at impl: keep aligned with Spec 008's pattern).
- [x] T014 [US1] Create [AddressBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBar.kt) Composable: `internal fun AddressBar(state: BrowserUiState, callbacks: AddressBarCallbacks, modifier: Modifier = Modifier)`. Use `OutlinedTextField` with single-line, `KeyboardOptions(imeAction = ImeAction.Go)`, `KeyboardActions(onGo = { … })`. The `onGo` lambda calls `callbacks.onSubmit()`; if returns `true`, call `softwareKeyboardController?.hide()` then `focusManager.clearFocus()` (research R5). `placeholder = { Text(stringResource(R.string.browser_address_bar_hint)) }`. NO clear button yet (US4); NO hostname-only display yet (US5) — bind the field directly to `state.addressBarText`. Define a sibling `internal data class AddressBarCallbacks(val onTextChange, val onFocusChange, val onSubmit)` in the same file or a separate `AddressBarCallbacks.kt` file (avoid `MatchingDeclarationName` detekt by separating if needed — see Spec 008 `NavigationBottomBarCallbacks` precedent). Add file-top `internal const val TEST_TAG_ADDRESS_BAR = "address_bar"` matching the project pattern from `NavigationBottomBar.kt`.
- [x] T015 [US1] Modify [BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — add `topBar = { AddressBar(state, addressBarCallbacks, modifier = Modifier.fillMaxWidth()) }` parameter to the existing `Scaffold`. Build `addressBarCallbacks` via `remember(viewModel) { AddressBarCallbacks(viewModel::onAddressBarTextChange, viewModel::onAddressBarFocusChange, viewModel::onAddressBarSubmit) }` (mirrors `rememberBottomBarCallbacks` from Spec 008).
- [x] T016 [US1] Create [AddressBarTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBarTest.kt) Compose UI test class. Drive the `AddressBar` Composable in isolation (no Hilt, no WebView). Tests: (1) initial render shows hint; (2) typing updates the bound state via callback; (3) focus toggle invokes `onFocusChange`; (4) IME Go action triggers `onSubmit` — assert via callback spy; (5) IME Go action when `onSubmit` returns `false` does NOT dismiss focus/keyboard (assert focus state unchanged). Mirror the structure of [NavigationBottomBarTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarTest.kt) including its `createComposeRule()` + `setContent` pattern.
- [x] T017 [US1] Run `./gradlew testDebugUnitTest` — confirm new classifier + ViewModel tests pass. Run `./gradlew connectedDebugAndroidTest --tests "*AddressBarTest"` — confirm Compose UI test passes (when emulator available).
- [ ] T018 [US1] Manual on-device verification — execute Quickstart Gate 7 steps 1, 5, 6: cold-start no-focus + `https://example.com` submit + scheme-prepend submit. **DEFERRED to user-device gate** (mirrors Spec 008 T032/T049 pattern; cannot run from automated agent).

**Checkpoint**: User Story 1 functional. URLs (with or without scheme) submit and load in the WebView. No suggestions, no hostname display, no clear button, no live URL sync — all reserved for later stories.

---

## Phase 4: User Story 2 — Submit a search query (Priority: P1)

**Goal**: User types a free-form query and submits; the WebView loads a Google search results page for that query.

**Independent Test**: Tap the address bar, type `kotlin coroutines`, press Go. The WebView loads `https://www.google.com/search?q=kotlin+coroutines` (or `%20`-encoded variant). Verifiable on a real device via Quickstart Gate 7 step 7 or in CI via the extended `BrowserViewModelTest`.

### Tests for User Story 2 ⚠️

- [x] T019 [P] [US2] Extend [AddressBarInputClassifierTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/AddressBarInputClassifierTest.kt) with query cases: `hello` → `Query("hello")`; `kotlin coroutines` → `Query("kotlin coroutines")`; `localhost` → `Query("localhost")` (no dot — accepted v1 trade-off per spec edge case); `   hello   ` → `Query("hello")` (trim); `hello\nworld` → `Query("hello world")` (newline strip).
- [x] T020 [P] [US2] Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) with query-path submit tests: `onAddressBarSubmit()` for input `kotlin coroutines` calls a stub `loadUrl` with exactly `https://www.google.com/search?q=kotlin+coroutines` (or `%20` — assertion must match the encoder's actual output); special chars like `c++` are properly encoded; Unicode like `안녕` is properly encoded; returns `true`.

### Implementation for User Story 2

- [x] T021 [US2] Replace the `TODO()` placeholder in [BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) `onAddressBarSubmit`'s `Query` branch: `val encoded = URLEncoder.encode(result.text, "UTF-8"); val searchUrl = String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, encoded); actionsHandle.loadUrl(searchUrl)`. Use `import java.net.URLEncoder`. Add `@Suppress("ImportOrdering")` only if ktlint complains (it should not).
- [x] T022 [US2] Run `./gradlew testDebugUnitTest` — confirm classifier + ViewModel query tests pass.
- [ ] T023 [US2] Manual on-device verification — Quickstart Gate 7 step 7. **DEFERRED user-device gate.**

**Checkpoint**: User Stories 1 + 2 both work independently. URL and query submits both navigate the WebView correctly.

---

## Phase 5: User Story 3 — Address bar stays in sync with the loaded page (Priority: P2)

**Goal**: When the WebView navigates by any means (link click, redirect, back/forward, programmatic), the address bar's `currentUrl` updates so the unfocused display (US5) and any future suggestions feature stays accurate. This phase carries the **callback-bundle refactor** (data-model.md Entity 3) — co-located with the new `onUrlChange` callback.

**Independent Test**: Load `https://example.com`, click an internal link, observe the `BrowserUiState.currentUrl` flow update via `BrowserViewModelTest` Turbine assertion. On-device — Quickstart Gate 7 step 9.

### Tests for User Story 3 ⚠️

- [x] T024 [P] [US3] Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — `onUrlChanged("https://example.com/about")` updates `currentUrl` and does NOT mutate `addressBarText` or `isAddressBarFocused`. Use Turbine to verify the StateFlow emits exactly one new value. **FR-019a immediacy assertion** (C2 remediation): wire a stub `WebViewActionsHandle.loadUrl` that synchronously invokes `viewModel.onUrlChanged(target)` (mirrors the WebView's `onPageStarted` callback contract). Then: (a) set `addressBarText = "example.com"` and call `onAddressBarSubmit()`, (b) immediately assert `uiState.value.currentUrl == "https://example.com"` — proves the bar reflects the submitted URL synchronously, before any `onLoadFinished` callback fires (FR-019a Q1 clarification). Document in the test KDoc that the real WebView fulfills this contract via `onPageStarted` firing on the same UI thread tick as `loadUrl(...)`.
- [x] T025 [P] [US3] Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — `onLoadStarted("https://example.com")` mutates only `loadingState` to `Loading(0f)`; `currentUrl` is unchanged from this method (responsibility moved to `onUrlChanged`). This test asserts the Spec 009 refactor of `onLoadStarted`.

### Implementation for User Story 3

- [x] T026 [P] [US3] Create [BrowserNavigationCallbacks.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserNavigationCallbacks.kt) — new `internal data class BrowserNavigationCallbacks(val onUrlChange: (String) -> Unit, val onCanGoBackChange: (Boolean) -> Unit, val onCanGoForwardChange: (Boolean) -> Unit)` with KDoc explaining the Spec 008→009 split rationale (detekt `LongParameterList` mitigation).
- [x] T027 [US3] Refactor [BrowserWebViewCallbacks.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebViewCallbacks.kt) — REMOVE `onCanGoBackChange` and `onCanGoForwardChange` fields (now in `BrowserNavigationCallbacks`). Keep the 4 load-lifecycle fields: `onLoadStarted`, `onProgressChanged`, `onLoadFinished`, `onLoadFailed`. Update KDoc to reference the new sister bundle.
- [x] T028 [US3] Update [BrowserWebView.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt) Composable signature — add `navigationCallbacks: BrowserNavigationCallbacks` parameter. Inside `WebViewClient`: (1) `onPageStarted(view, url, favicon)` → fire `loadCallbacks.onLoadStarted(url)` AND `navigationCallbacks.onUrlChange(url)`; (2) `doUpdateVisitedHistory(view, url, isReload)` → fire `navigationCallbacks.onUrlChange(url)` AND `onCanGoBackChange(view.canGoBack())` AND `onCanGoForwardChange(view.canGoForward())`. Verify with `./gradlew detekt` that `BrowserWebView` parameter count stays under `LongParameterList.functionThreshold = 6`.
- [x] T029 [US3] In [BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) — add `fun onUrlChanged(url: String) { _uiState.update { it.copy(currentUrl = url) } }`. Modify the existing `onLoadStarted(url)` to NO LONGER mutate `currentUrl` — it only transitions `loadingState` to `LoadingState.Loading(0f)`. Update KDoc on both methods to document the responsibility split.
- [x] T030 [US3] Modify [BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — construct a `BrowserNavigationCallbacks(viewModel::onUrlChanged, viewModel::onCanGoBackChanged, viewModel::onCanGoForwardChanged)` and pass to `BrowserWebView`. Bundle the existing 4 load-callbacks into the new (smaller) `BrowserWebViewCallbacks(viewModel::onLoadStarted, viewModel::onProgressChanged, viewModel::onLoadFinished, viewModel::onLoadFailed)`.
- [x] T031 [US3] Fix call sites in [BrowserScreenInstrumentedTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenInstrumentedTest.kt) and [BrowserScreenOfflineErrorTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenOfflineErrorTest.kt) for the new `BrowserWebView` signature (split callback bundles). Add `BrowserNavigationCallbacks(...)` to each test's `setContent`. Compile-fix only — no semantic test changes.
- [x] T032 [US3] Run `./gradlew testDebugUnitTest detekt` — confirm: (a) all new + existing unit tests pass, (b) detekt reports zero violations, (c) baseline file UNCHANGED.
- [ ] T033 [US3] Manual on-device verification — Quickstart Gate 7 steps 8 (cross-domain redirect live trace) + 9 (link click sync). **DEFERRED user-device gate.**

**Checkpoint**: User Stories 1 + 2 + 3 work independently. URL/query submit + live mirror across navigation transitions. Callback-bundle refactor complete. Detekt baseline still unchanged.

---

## Phase 6: User Story 4 — Clear input quickly (Priority: P2)

**Goal**: While the address bar is focused with non-empty text, a trailing X button appears; tapping it empties the field and keeps focus.

**Independent Test**: Tap address bar (full URL appears, all selected per US5 — but US5 not yet shipped, so just type something), tap X, observe field empties + focus retained + keyboard stays open. Quickstart Gate 7 step 4.

### Tests for User Story 4 ⚠️

- [x] T034 [P] [US4] Extend [BrowserViewModelTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) — `onAddressBarClear()` empties `addressBarText` to `""`; `isAddressBarFocused` is unchanged.

### Implementation for User Story 4

- [x] T035 [US4] Add `fun onAddressBarClear() { _uiState.update { it.copy(addressBarText = "") } }` to [BrowserViewModel.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt). Add a fourth callback to `AddressBarCallbacks`: `onClear: () -> Unit`. Wire `viewModel::onAddressBarClear` in `BrowserScreen.kt`'s `remember { ... }` block.
- [x] T036 [US4] Extend [AddressBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBar.kt) — add a `trailingIcon` to the `OutlinedTextField`: `IconButton(onClick = callbacks.onClear) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.browser_address_bar_clear_cd)) }`. Wrap the `IconButton` in a conditional: `if (state.isAddressBarFocused && state.addressBarText.isNotEmpty())` so the icon is invisible otherwise (FR-023 / FR-024). Add file-top `internal const val TEST_TAG_ADDRESS_BAR_CLEAR = "address_bar_clear"`.
- [x] T037 [US4] Extend [AddressBarTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBarTest.kt) — three clear-button visibility tests (unfocused empty: not visible; focused empty: not visible; focused non-empty: visible) + one tap test (tap clear → onClear callback fires + bar text becomes empty + focus retained).
- [ ] T038 [US4] Manual on-device verification — Quickstart Gate 7 step 4. **DEFERRED user-device gate.**

**Checkpoint**: User Stories 1 + 2 + 3 + 4 functional.

---

## Phase 7: User Story 5 — Compact hostname display when idle (Priority: P3)

**Goal**: When the address bar is unfocused and a URL with a host is loaded, display only the hostname; when focused, show the full URL with all text selected.

**Independent Test**: Load a URL with a path (`https://developer.android.com/jetpack/compose`); when bar is unfocused, only `developer.android.com` is visible. Tap bar — full URL appears with all text selected. Tap elsewhere — hostname-only returns. Quickstart Gate 7 steps 2–3.

### Tests for User Story 5 ⚠️

- [x] T039 [P] [US5] Create [UrlExtensionsTest.kt](app/src/test/kotlin/com/raumanian/thirtysix/browser/core/extensions/UrlExtensionsTest.kt) — covers `extractHostnameOrSelf` (or chosen function name): `https://developer.android.com/path?q=1` → `developer.android.com`; `https://www.example.com` → `www.example.com` (no www. stripping per research R8); `about:blank` → `about:blank` (no host fallback); `file:///sdcard/page.html` → the original string (no host fallback); `""` → `""`; `not a url` → `not a url`; `https://192.168.1.1:8080/path` → `192.168.1.1`. Robolectric is already configured project-wide via [robolectric.properties](app/src/test/resources/robolectric.properties) (Spec 005).
- [x] T040 [P] [US5] Extend [AddressBarTest.kt](app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBarTest.kt) — display tests: (a) unfocused with URL containing path → only hostname shown; (b) focused with URL → full URL shown + all text selected; (c) unfocused with `about:blank` → falls back to full URL; (d) unfocused with empty `currentUrl` → placeholder hint shown.

### Implementation for User Story 5

- [x] T041 [P] [US5] Create [UrlExtensions.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/core/extensions/UrlExtensions.kt) with `internal fun String.extractHostnameOrSelf(): String { if (isBlank()) return ""; val host = Uri.parse(this).host; return if (host.isNullOrBlank()) this else host }`. Import `android.net.Uri`. Add KDoc explaining the no-www-stripping decision and the about:blank/file:/// fallback semantics (research R4).
- [x] T042 [US5] Extend [AddressBar.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/AddressBar.kt) — switch the `OutlinedTextField`'s `value` parameter from a plain `String` to a `TextFieldValue` (provides selection control). Display-state derivation:
  - When `state.isAddressBarFocused == false`: `displayed = state.currentUrl.extractHostnameOrSelf()` (FR-014 / FR-016). When `state.currentUrl` is empty, display empty + the `placeholder` slot will render.
  - When `state.isAddressBarFocused == true`: bind directly to a local `remember { mutableStateOf(TextFieldValue(state.addressBarText)) }` driven by user input.
  - Add a `LaunchedEffect(state.isAddressBarFocused)` that, on `false → true` transition, copies `state.currentUrl` into both the local `TextFieldValue` AND the ViewModel via `callbacks.onTextChange(state.currentUrl)`, AND sets the selection to `TextRange(0, state.currentUrl.length)` (FR-018 select-all).
  - On `true → false` transition (e.g., user dismissed without submit), invoke `callbacks.onTextChange("")` to reset the bound state — the next render falls back to hostname display.
- [x] T043 [US5] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest --tests "*UrlExtensionsTest" "*AddressBarTest"` — confirm new tests pass.
- [ ] T044 [US5] Manual on-device verification — Quickstart Gate 7 steps 2–3. **DEFERRED user-device gate.**

**Checkpoint**: All 5 user stories functional. Spec 009 feature-complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Wire the FR-026 back-during-focus handler, run all CI gates, schedule the manual on-device verifications, prepare the post-merge documentation update.

- [x] T045 [P] In [BrowserScreen.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt), add `BackHandler(enabled = state.isAddressBarFocused) { focusManager.clearFocus() }` BEFORE the existing `PredictiveBackHandler(enabled = state.canGoBack) { … }`. This catches the niche path where keyboard is hidden but bar is focused (research R6); platform IME-dismiss-on-back handles the standard case automatically. Document the handler ordering in a code comment.
- [x] T046 [P] Run `./gradlew lintDebug` — confirm zero warnings; verify `MissingTranslation` and `ExtraTranslation` did not regress for the 8-locale string set.
- [x] T047 [P] Run `./gradlew detekt ktlintCheck` — confirm zero violations + detekt baseline UNCHANGED. If `BrowserViewModel`'s constructor parameter count grows over the threshold (Spec 008 noted `BrowserWebViewCallbacks` was already at 6), refactor parameters into a per-bundle parameter object inline.
- [x] T048 Run `./gradlew testDebugUnitTest` — confirm 127+ unit tests pass (Spec 008 baseline 102 + ~25 new — adjust expectation based on actual test count after T010, T011, T019, T020, T024, T025, T034, T039).
- [x] T049 Run `./gradlew connectedDebugAndroidTest` — confirm `AddressBarTest` + the existing instrumented suite pass (with emulator booted).
- [x] T050 Run `./gradlew assembleRelease && .specify/scripts/bash/verify-16kb-alignment.sh` — confirm: (a) BUILD SUCCESSFUL; (b) APK size ≤ 1.72 MB (Spec 008 baseline 1.67 + 50 KB SC-007 budget); (c) every native lib entry aligns `0x4000`; (d) zero new `.so`. Capture the exact APK size in the post-merge note.
- [ ] T051 Manual on-device verification — Quickstart Gate 7 step 10 (back-during-focus dismisses keyboard before predictive WebView-back consumes). **DEFERRED user-device gate.**
- [ ] T052 Manual on-device verification — Quickstart Gate 7 step 11 (rotation preserves text + focus). **DEFERRED user-device gate.**
- [ ] T053 Manual on-device verification — Quickstart Gate 7 steps 12 (empty submit no-op) + 13 (TalkBack sweep on EN + 1 non-Latin locale). **DEFERRED user-device gate.**
- [ ] T054 Manual on-device verification — Quickstart Gate 7 step 14 (8-locale visual sweep). **DEFERRED user-device gate** (mirrors Spec 008 T049 pattern).
- [x] T055 After all gates green and PR opened, update [CLAUDE.md](CLAUDE.md) SPECKIT block + [.claude/claude-app/project-context.md](.claude/claude-app/project-context.md) Recent Changes section with the Spec 009 implementation summary (mirror Spec 008's entry style).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Phase 1; BLOCKS all user-story phases.
- **US1 (Phase 3, P1) — MVP**: Depends on Phase 2.
- **US2 (Phase 4, P1)**: Depends on Phase 2 + the `onAddressBarSubmit` skeleton from US1 (T013). Can begin once T013 is in place; T021 fills the `Query` branch.
- **US3 (Phase 5, P2)**: Depends on Phase 2. Independent of US1/US2 in functionality but the callback-bundle refactor (T026–T031) must compile cleanly with T015's `BrowserScreen` topBar wiring already in place.
- **US4 (Phase 6, P2)**: Depends on Phase 2 + the `AddressBar` Composable from US1 (T014). Extends the same file.
- **US5 (Phase 7, P3)**: Depends on Phase 2 + Phase 5 (US3 — for `currentUrl` to be live via `onUrlChanged`) + the `AddressBar` Composable from US1 (T014). US5 modifies `AddressBar.kt` to add hostname-vs-full-URL display + `LaunchedEffect`.
- **Polish (Phase 8)**: Depends on all user-story phases user wishes to ship.

### User Story Dependencies (within Spec 009)

- US1 → US2: T021 fills the `Query` `TODO()` left by T013.
- US3 → US5: US5's hostname display relies on `currentUrl` being live, which requires US3's `onUrlChange` wiring + the `onLoadStarted` responsibility-narrowing (T029).
- US1 ↔ US3 ↔ US4: All three modify `AddressBar.kt` and `BrowserViewModel.kt` — sequential within the file; do US1 first to establish the structure, then US3 (callback refactor), then US4 (clear button), then US5 (display switching).

### Within Each User Story

- Tests written FIRST and FAILING before implementation (Constitution §VI + project workflow).
- Models / data classes before service / Composable consumers.
- Implementation before integration tests.

### Parallel Opportunities

- **Phase 2**: T002 + T003 + T005 are `[P]` — three different files, independent. T004 is sequential (it's a single file edit; T005 then validates the parity).
- **Phase 3 (US1)**: T010 + T011 are `[P]` test scaffolds. T012 is `[P]` with T013/T014 only as long as the test files reference the not-yet-existing classifier symbols (KSP/Kotlin compile catches that immediately, so practically T012 should be done before tests run, but the task ordering allows parallel writing).
- **Phase 5 (US3)**: T024 + T025 + T026 are `[P]`. The remaining tasks (T027–T031) form a single refactor chain on shared files.
- **Phase 7 (US5)**: T039 + T040 are `[P]`. T041 + T042 are sequential (T042 depends on T041's extension function).
- **Phase 8**: T045 + T046 + T047 are `[P]` (different files / independent gradle tasks).

---

## Parallel Example: User Story 1 startup

```bash
# Concurrent file creation (4 different files):
Task: "T010 — AddressBarInputClassifierTest.kt with URL classification table"
Task: "T011 — BrowserViewModelTest extension for address-bar method tests"
Task: "T012 — AddressBarInputClassifier.kt with classify() + sealed result"
Task: "T014 — AddressBar.kt Composable (depends on T012's classifier types being stub-importable; reorder if needed)"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 — both P1)

1. Phase 1 Setup (T001).
2. Phase 2 Foundational (T002–T009).
3. Phase 3 US1 (T010–T018).
4. Phase 4 US2 (T019–T023) — completes both P1 stories; URL + query both submit-and-load.
5. **STOP / VALIDATE**: Run Quickstart Gate 7 steps 5–7 on a real device. Demo / merge if happy.

### Incremental Delivery

1. MVP (above) → optional ship.
2. Add US3 (T024–T033) — live URL sync. Demo address bar updating on link click.
3. Add US4 (T034–T038) — clear button.
4. Add US5 (T039–T044) — hostname-only display + full-URL-on-focus polish.
5. Phase 8 polish (T045–T055) — back-during-focus handler + CI gates + manual-gate punch list.
6. Open PR.

### Solo-Developer Strategy (this project)

- Phases sequential (no parallelism between developers).
- Within a phase, do `[P]` tasks first to set up scaffolds, then consume them in implementation tasks.
- Run `./gradlew testDebugUnitTest detekt ktlintCheck` after every checkpoint to catch regressions early.

---

## Notes

- **`[P]` = different file + no dependency on incomplete task** — any task that touches `AddressBar.kt` (T014, T036, T042) is sequential within itself.
- **`[Story]` label** maps the task to its user story for traceability.
- **Tests first**: Constitution §VI and the project's spec workflow both require failing tests before implementation. The `Tests for User Story N` subsections list the test scaffolds; they are listed before the implementation tasks they validate.
- **Manual on-device gates DEFERRED**: T018, T023, T033, T038, T044, T051, T052, T053, T054 — eight tasks total — cannot be executed by an automated agent. User runs them on a real Android 14+ device. This mirrors the Spec 007 T042 / Spec 008 T032+T049 deferred-gate pattern.
- **Detekt baseline must stay UNCHANGED**: T032 + T047 explicitly verify this. The callback-bundle refactor and the new Composables are designed to pass detekt without baseline cover; if a violation surfaces, fix structurally (split bundle further, extract method) — do NOT add a baseline entry.
- **APK size budget**: SC-007 = +50 KB over Spec 008's 1.67 MB → cap at 1.72 MB. The new code is small (no new packages, no new resources beyond 16 short translations); expected actual delta is < 25 KB.

## Task count summary

| Phase | Tasks | [P] count | DEFERRED gates |
|---|---|---|---|
| 1 — Setup | 1 | 0 | 0 |
| 2 — Foundational | 8 | 3 | 0 |
| 3 — US1 (P1, MVP) | 9 | 3 | 1 |
| 4 — US2 (P1) | 5 | 2 | 1 |
| 5 — US3 (P2) | 10 | 3 | 1 |
| 6 — US4 (P2) | 5 | 1 | 1 |
| 7 — US5 (P3) | 6 | 3 | 1 |
| 8 — Polish | 11 | 3 | 4 |
| **Total** | **55** | **18** | **9** |

Suggested MVP scope: Phase 1 + Phase 2 + Phase 3 (US1) = 18 tasks. Adding US2 to round out both P1 stories: +5 tasks = 23 tasks for an end-to-end "type URL or query → page loads" demo.
