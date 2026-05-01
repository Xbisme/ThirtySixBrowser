# Research: Navigation Controls (Spec 008)

> Verified 2026-05-01 against project context (Compose BOM 2026.04.01, Material3 1.4.0, Kotlin 2.3.21, AGP 9.1.1, minSdk 24, targetSdk 36).

This spec adds a bottom navigation bar + predictive-back integration to the existing `BrowserScreen` from Spec 007. No new third-party packages are introduced; all decisions below are about which APIs already on the classpath to use, and how to wire them.

## R1 — Compose component for the bottom bar

**Decision**: Use Material 3 `BottomAppBar(content: @Composable RowScope.() -> Unit)`.

**Rationale**:
- `BottomAppBar` is M3's first-class component for "actions related to the current screen" — exactly our four affordances.
- `NavigationBar` is M3's component for **top-level destination switching** (e.g., Tabs / Bookmarks / History / Downloads / Settings). Using it here would be semantically wrong and would conflict with the future Spec 016 settings tab structure if we ever add one.
- A custom `Row` reinvents the wheel: M3 default container color, tonal elevation, content padding, window-insets handling are all done correctly by `BottomAppBar` and would have to be re-implemented otherwise.
- `BottomAppBar` integrates with `Scaffold(bottomBar = { ... })` slot, which gives correct insets behavior with `WindowInsets.systemBars`.

**Alternatives considered**:
- `NavigationBar`: rejected — wrong semantic (destinations, not actions).
- `Surface { Row { ... } }`: rejected — duplicates BottomAppBar's tonal-elevation + content-padding logic.
- `BottomSheetScaffold`: rejected — overkill, persistent bar is required (FR-018), not a sheet.

**Reference**: Material 3 `BottomAppBar` overload that takes a single `RowScope` content slot is in Material3 1.4.0 (verified `developer.android.com/jetpack/androidx/releases/compose-material3` 2026-05-01).

## R2 — Predictive back gesture API in Compose

**Decision**: Use `androidx.activity:activity-compose` `PredictiveBackHandler(enabled, onBack: suspend (Flow<BackEventCompat>) -> Unit)`.

**Rationale**:
- `PredictiveBackHandler` is the Compose-friendly wrapper over the platform `OnBackInvokedDispatcher` (Android 13+) and replaces direct `Activity.onBackPressed` overrides. It composes cleanly into our screen and respects Compose lifecycle.
- The `enabled` parameter directly takes `canGoBack` from `BrowserUiState` — when `false`, the gesture is NOT consumed and the system performs default back behavior (exit screen / app), satisfying FR-011.
- The `onBack` lambda receives a `Flow<BackEventCompat>` of progress events. We don't need a custom preview animation in v1.0 — the system renders the predictive preview based on the manifest opt-in alone (see R3). We collect the flow only to detect commit (flow completes) vs cancel (flow throws `CancellationException`):

  ```kotlin
  PredictiveBackHandler(enabled = uiState.canGoBack) { progress ->
      try {
          progress.collect { /* no-op: system renders preview */ }
          viewModel.onBackClick()  // gesture committed
      } catch (e: CancellationException) {
          // gesture cancelled — no navigation
          throw e  // re-throw per coroutines convention
      }
  }
  ```

- Falls back to plain `BackHandler` semantics on API 24–32 (no preview animation, but the back action still routes through this handler).

**Alternatives considered**:
- `BackHandler { ... }` (basic): rejected — does not surface predictive progress, so the system cannot render the preview animation on API 34+ even if enabled in the manifest.
- Direct `OnBackInvokedDispatcher` registration via `LocalOnBackPressedDispatcherOwner`: rejected — bypasses Compose lifecycle, manual cleanup risk, identical net behavior to `PredictiveBackHandler`.

**Reference**: `PredictiveBackHandler` is in `androidx.activity:activity-compose` 1.8.0+ (verified `developer.android.com/jetpack/androidx/releases/activity` 2026-05-01). Project's Compose BOM 2026.04.01 transitively pulls 1.10.0+ (well above floor).

## R3 — Manifest opt-in for predictive back

**Decision**: Add `android:enableOnBackInvokedCallback="true"` to the `<application>` element in `AndroidManifest.xml`.

**Rationale**:
- Without this attribute, Android 13+ continues to dispatch back via the legacy `onBackPressed` path and `OnBackInvokedDispatcher` callbacks (including `PredictiveBackHandler`) are NOT invoked. The system **also** does not render the predictive preview animation.
- Setting it `true` is application-wide; on API 24–32 it is silently ignored.
- This is the platform-recommended path per [Predict back gesture migration guide](https://developer.android.com/guide/navigation/predictive-back-gesture).
- Lint rule `OnBackInvokedCallback` may flag missing opt-in if any code uses `OnBackPressedCallback` — currently the project has no such code, so the attribute is safe to add only when this spec lands (which is also when `PredictiveBackHandler` first appears).

**Alternatives considered**:
- Leave attribute `false` (default): rejected — predictive preview animation never renders on API 34+, breaking the FR-012 acceptance criterion.

## R4 — Observing `canGoBack` / `canGoForward` changes from WebView

**Decision**: Re-read `webView.canGoBack()` / `webView.canGoForward()` inside `WebViewClient.doUpdateVisitedHistory(...)` and surface the new values via `BrowserWebViewCallbacks.onCanGoBackChange(Boolean)` and `onCanGoForwardChange(Boolean)`.

**Rationale**:
- `android.webkit.WebView` does NOT expose a direct callback for "history changed". The closest hook is `WebViewClient.doUpdateVisitedHistory(view, url, isReload)`, which fires AFTER each successful page commit (including back/forward navigation, fragment changes, and `pushState`).
- Re-reading `canGoBack()` / `canGoForward()` on every `doUpdateVisitedHistory` is O(1) — these are lightweight platform state checks. No performance concern.
- Surfacing through the existing `BrowserWebViewCallbacks` data class keeps the `BrowserWebView` parameter count stable (Spec 007's Detekt-driven 4-callback bundle pattern). Add 2 new fields → 6 total callbacks, still ≤ `LongParameterList.functionThreshold`.
- `onPageFinished` is NOT used because it does not fire for fragment navigations or `pushState` — `doUpdateVisitedHistory` is the more reliable signal.
- Initial-load case: `BrowserWebViewCallbacks.onPageStarted` already fires on the first load; we issue an initial `false`/`false` for both flags from the ViewModel default state — first commit triggers `doUpdateVisitedHistory` and updates correctly.

**Alternatives considered**:
- Polling on a `tickerFlow`: rejected — wastes CPU; introduces O(1Hz) recomposition overhead; lag between actual change and UI reflection.
- Subclassing `WebChromeClient.onProgressChanged`: rejected — fires too often during a single page load, no semantic alignment with "history changed".
- Using `WebView.copyBackForwardList()` to inspect history depth: rejected — heavier API, returns a snapshot list including title/url for each entry; we only need two booleans.

## R5 — Reload/Stop combined affordance state derivation

**Decision**: The bottom-bar component reads `BrowserUiState.loadingState` (already exposed by Spec 007) and renders Stop when `loadingState is LoadingState.Loading`, Reload otherwise. The icon and content description switch atomically with the `loadingState` value.

**Rationale**:
- `LoadingState` from Spec 007 is a sealed class with four states: `Idle` (no URL submitted yet), `Loading(progress: Float)`, `Loaded`, `Failed(reason: ErrorReason)`. Stop semantic is correct ONLY during `Loading`; in all other three states, Reload is correct.
- Single state source → no race between bar's internal state and WebView state.
- The button click handler dispatches to `BrowserViewModel.onReloadOrStopClick()`, which internally inspects the same `loadingState` and routes to either `webView.stopLoading()` or `webView.reload()`. This avoids any "wrong action executes due to stale UI snapshot" race.

**Alternatives considered**:
- Two separate buttons that toggle visibility: rejected — wastes bottom-bar real estate; introduces a layout-shift artifact during state transitions.
- Reload-with-cache-bypass option: explicitly out of scope per spec assumption.

## R6 — Disabled state visual + accessibility treatment

**Decision**: Use M3 `IconButton(enabled = canGoBack, ...)` and the M3 default disabled `LocalContentColor` (32% alpha on the content). Content descriptions remain present in disabled state so TalkBack announces the button label + "disabled" state per Android accessibility framework defaults.

**Rationale**:
- M3 `IconButton` automatically dims its content when `enabled = false` and disables click handling — single-source-of-truth, no manual alpha math.
- Disabled-state alpha (32%) applied to `LocalContentColor` against the BottomAppBar surface meets WCAG AA 3:1 contrast for icons (verified by Material design spec; not a per-color check needed).
- TalkBack announces "Back, button, dimmed" or equivalent locale string when the button is disabled — preserves accessibility per Constitution §VIII.

**Alternatives considered**:
- Hiding disabled buttons entirely: rejected — causes layout reflow + breaks user muscle memory; spec explicitly says "presented in a disabled (non-actionable) state" (FR-003 / FR-004), not "hidden".
- Custom contrast-boosted disabled color: rejected — over-engineering; M3 default is the project standard per Spec 003.

## R7 — Home button behavior + URL constant placement

**Decision**: New constant `AppDefaults.HOME_URL = "https://www.google.com/"` (added to `core/constants/AppDefaults.kt`, which Spec 006 already created). The existing Spec 007 `UrlConfigModule` (`@Named("default_home_url")`) is updated to provide `AppDefaults.HOME_URL` instead of the literal `"https://example.com"` placeholder.

**Rationale**:
- One constant serves both the initial-load URL (Spec 007 surface) and the Home tap action (Spec 008 surface). Semantically these have always been the same value — they were artificially split by the placeholder/scaffold timing of Spec 007.
- `AppDefaults` is the right namespace per Constitution §III table row "Default values": `searchEngine ?: AppDefaults.SEARCH_ENGINE`. The home URL is a default user-overridable value (Spec 016 will later expose the override surface).
- Hilt `UrlConfigModule` continues to inject the URL via `@Named("default_home_url")` — keeping the same DI seam allows Spec 016 to swap in a user-preference-backed provider later without touching `BrowserViewModel`.
- The Hilt test override pattern from Spec 007 (`@UninstallModules(UrlConfigModule::class)` + nested `FakeUrlConfigModule`) continues to work for offline-error tests.

**Alternatives considered**:
- Hardcode `"https://www.google.com/"` inline: rejected — Constitution §III §No-Hardcode Rule.
- Put the URL in `UrlConstants.kt`: rejected — `UrlConstants` is for fixed templates and protocol patterns; user-overridable defaults belong in `AppDefaults.kt`.
- Two separate constants (`INITIAL_URL` and `HOME_URL`): rejected — semantic duplication; both should always equal the user's "home" page.

## R8 — Test strategy for predictive back

**Decision**: Unit-test the ViewModel logic (`onBackClick` / `canGoBack` derivation) extensively; instrumented-test the **non-predictive** back path on any API level via Espresso `pressBack()`; **DO NOT** automate-assert the predictive preview animation itself.

**Rationale**:
- The predictive preview is rendered by the Android system (WindowManager + system UI), not by the app. Its visual behavior is platform-level and is not deterministic to assert from inside the app's instrumented-test JVM.
- What IS app-controlled and IS testable:
  - The `canGoBack` flag flips correctly as history changes (unit test on ViewModel).
  - `pressBack()` (Espresso) navigates back when `canGoBack = true` and falls through to screen-finish when `canGoBack = false` (instrumented test).
  - The `PredictiveBackHandler.enabled` parameter receives the correct boolean value from `BrowserUiState` (verifiable via Compose `composeTestRule.onNodeWithTag(...).performBackNavigation()` or by inspecting state).
- Manual gate (mirrors Specs 004 / 006 / 007): visual verification of the preview animation on a physical Android 14+ device, deferred to user device verification per project pattern.

**Alternatives considered**:
- UIAutomator-driven gesture simulation: rejected — flaky across emulator versions; the gesture coordinates differ on different navigation modes (3-button, 2-button, gesture).
- Robolectric shadow for `OnBackInvokedDispatcher`: rejected — the shadow does not faithfully simulate predictive-progress flow.

## R9 — APK release-size budget

**Decision**: Target ≤ 50 KB delta (SC-008). Budget breakdown estimate:
- Kotlin source (~150 LOC): negligible (< 5 KB after R8)
- 5 string keys × 8 locales (40 strings, all short): ~2 KB in `resources.arsc`
- 4 Material icon references (already in `material-icons-core` from Spec 007): 0 KB net
- Bottom bar Compose code: built-in (Material3 already in classpath)

**Total expected**: ~7 KB net. Well under the 50 KB SC-008 budget.

**Reference**: Spec 007 baseline 1.61 MB. Spec 008 expected 1.61–1.62 MB.

## R10 — 16KB CI gate readiness

**Decision**: Auto-passes by construction. Zero new packages → zero new `.so` files. The existing CI script `.specify/scripts/bash/verify-16kb-alignment.sh` continues to inspect the same 24 native lib entries from Spec 007 baseline (all `align=0x4000` ✅).

**Rationale**: Constitution §IX requires verification at merge time, not at plan time. The plan documents the expectation; the CI gate confirms.

---

## Summary

| ID | Question | Decision | Risk |
|----|----------|----------|------|
| R1 | Bottom-bar Compose component | M3 `BottomAppBar` | None — well-trodden M3 path |
| R2 | Predictive back API | `PredictiveBackHandler` from `activity-compose` | None — already on classpath |
| R3 | Manifest opt-in | Add `enableOnBackInvokedCallback="true"` | None — application-wide, ignored on older APIs |
| R4 | `can*Back` observation | `WebViewClient.doUpdateVisitedHistory` callback | Low — well-documented API; verify on fragment nav at impl time |
| R5 | Reload/Stop semantic | Read `loadingState`, dispatch in click handler | None |
| R6 | Disabled state | M3 `IconButton(enabled = ...)` default | None |
| R7 | Home URL constant | New `AppDefaults.HOME_URL`; update `UrlConfigModule` | Low — coordinated edit with Spec 007 module |
| R8 | Predictive-back test strategy | Unit + Espresso non-predictive; manual gate for animation | Low — matches Spec 007 manual-gate pattern |
| R9 | APK size budget | ~7 KB net (~50 KB cap) | None |
| R10 | 16KB gate | Auto-pass (zero new `.so`) | None |

All NEEDS-CLARIFICATION resolved. Ready for Phase 1 design artifacts.
