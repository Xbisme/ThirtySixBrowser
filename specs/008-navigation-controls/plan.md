# Implementation Plan: Navigation Controls

**Branch**: `008-navigation-controls` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/008-navigation-controls/spec.md`

## Summary

Wire a Material 3 bottom bar with four affordances — **Back · Forward · Reload/Stop · Home** — onto the `BrowserScreen` from Spec 007, plus integrate the Android predictive-back gesture so the system back swipe navigates WebView session history before falling through to screen exit. Implementation is a thin presentation-layer + `BrowserViewModel` extension over the existing `BrowserWebView`; no new domain entities, no new repository, no new persistence. Single new app-wide constant (`AppDefaults.HOME_URL = "https://www.google.com/"`, replacing the `https://example.com` placeholder Spec 007 shipped) and 5 new locale keys × 8 locales for the affordance content descriptions.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (existing project pin)
**Primary Dependencies**: Existing — Compose BOM 2026.04.01, Material3 1.4.0, `androidx.activity:activity-compose` (BOM-managed; `BackHandler` + `PredictiveBackHandler` already on the classpath), `androidx.compose.material:material-icons-core` (added in Spec 007). Hilt 2.59.2 / KSP 2.3.7. **No new dependency added by this spec.**
**Storage**: N/A — WebView session history is platform-managed in-memory (per Spec 008 spec, persistence deferred to Spec 011)
**Testing**: Existing — JUnit 4, kotlinx-coroutines-test, Turbine; Compose UI Test + Espresso-Web + Hilt-android-testing wired in Spec 007
**Target Platform**: Android API 24 (minSdk) → API 36 (targetSdk). Predictive back preview animation kicks in on API 34+; identical logical behavior on API 24–33 without the preview.
**Project Type**: Mobile app (Android, single-module `app/`)
**Performance Goals**: Per spec — SC-002 system back ≤ 300 ms commit-to-frame; SC-003 predictive preview start ≤ 100 ms; SC-004 reload begin / stop cancel ≤ 200 ms; 60fps during all transitions (Constitution §V).
**Constraints**: APK release-size delta ≤ 50 KB (SC-008). Zero new `.so` (SC-009; Constitution §IX). Zero new permissions (Constitution §II). All affordances localized in 8 locales (Constitution §VIII). 4.5:1 / 3:1 contrast on disabled vs enabled state (Constitution §VIII WCAG AA).
**Scale/Scope**: Single screen (`BrowserScreen`), single tab assumption. 4 affordances on the bar. ~5 new string keys × 8 locales = 40 translations. ~150 new lines of Kotlin (estimate).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Note |
|---|-----------|--------|------|
| I | Privacy & Security First | ✅ PASS | No data transmitted. WebView history stays in-memory on-device. Home URL load is a normal user-initiated network request to Google — equivalent to a user typing the URL. No tracking added. |
| II | Google Play Compliance | ✅ PASS | No new permissions. No `addJavascriptInterface`. Standard `android.webkit.WebView` history APIs only. Predictive back is a platform-standard gesture handler. |
| III | Code Quality & Safety | ✅ PASS (with planned constants) | New `AppDefaults.HOME_URL` constant (replaces Spec 007 placeholder). 5 new `R.string.browser_action_*` keys. Bottom bar uses `MaterialTheme.colorScheme` + `Spacing.*` tokens. No magic numbers. No inline strings. |
| IV | Clean Architecture (MVVM) | ✅ PASS | Pure presentation-layer + `BrowserViewModel` surface extension. WebView session history is platform state, not domain state — no Repository layer. State flows: WebView callback → ViewModel `update { }` → `StateFlow<BrowserUiState>` → bottom bar recomposition. |
| V | Performance Excellence | ✅ PASS (gated at impl) | Affordance state recomputation is O(1) per page commit. No layout thrash on rotation (pure Compose state). Benchmarks deferred to manual gate (mirrors Specs 004/006/007 manual UX gate pattern). |
| VI | Testing Discipline | ✅ PASS | New unit tests for ViewModel nav surface + Reload/Stop semantic toggle + canGoBack/canGoForward state derivation. New instrumented test for bottom-bar interaction + back gesture (skipping predictive-preview animation assertion which requires API 34+ device). |
| VII | Offline-First Architecture | ✅ PASS | No new persistence. Home tap when offline produces the existing Spec 007 `LoadingState.Failed(NetworkUnavailable)` UI — no special-case offline handling needed. |
| VIII | Localization & Accessibility | ✅ PASS | 5 new keys × 8 locales. All affordances expose `contentDescription`. Disabled state uses M3 default disabled alpha (≥ 3:1 contrast on light + dark). 48dp minimum touch target via M3 `IconButton`. |
| IX | Dependency Currency & 16KB | ✅ PASS | **Zero new packages.** All required APIs (`BackHandler`, `PredictiveBackHandler`, `BottomAppBar`, `IconButton`, `Icons.Filled.*`) are on the existing classpath. 16KB CI gate auto-passes by construction. |
| X | Simplicity & Build Order | ✅ PASS | Spec 008 is the natural Phase 2 successor to Spec 007 per `sdd-roadmap.md`. No skipping. No speculative code. Long-press history dropdowns + hard-reload + multi-tab back behavior all explicitly out of scope (deferred to later specs). |
| XI | Build Configuration | ✅ PASS | No new BuildConfig fields. No flavor changes. No signing changes. Manifest gains one attribute (`android:enableOnBackInvokedCallback="true"`) — required for predictive back per Android 13+ contract. |

**Result**: 11/11 PASS pre-design. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-navigation-controls/
├── spec.md              # /speckit-specify (done) + /speckit-clarify (done)
├── plan.md              # This file
├── research.md          # Phase 0 — Compose component choice, predictive-back API, can*Back observation
├── data-model.md        # Phase 1 — BrowserUiState delta (no new domain entities)
├── contracts/
│   └── BrowserViewModel.md   # Phase 1 — public ViewModel surface (4 new methods + state delta)
├── quickstart.md        # Phase 1 — verification checklist
├── checklists/
│   └── requirements.md  # /speckit-specify output (already updated post-clarify)
└── tasks.md             # /speckit-tasks output (NOT created here)
```

### Source Code (delta on top of existing repo layout)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   └── AppDefaults.kt                            # MODIFY — add HOME_URL constant
├── presentation/browser/
│   ├── BrowserScreen.kt                          # MODIFY — wrap in Scaffold; add bottomBar slot;
│   │                                             #          add PredictiveBackHandler
│   ├── BrowserViewModel.kt                       # MODIFY — add onBackClick / onForwardClick /
│   │                                             #          onReloadOrStopClick / onHomeClick;
│   │                                             #          add can*Back state plumbing
│   ├── BrowserUiState.kt                         # MODIFY — add canGoBack: Boolean, canGoForward: Boolean
│   ├── BrowserWebView.kt                         # MODIFY — surface canGoBack/canGoForward changes
│   │                                             #          via BrowserWebViewCallbacks; expose
│   │                                             #          imperative reload/stop/goBack/goForward/home
│   ├── BrowserWebViewCallbacks.kt                # MODIFY — extend with onCanGoBackChange,
│   │                                             #          onCanGoForwardChange (callbacks fire from
│   │                                             #          WebViewClient.doUpdateVisitedHistory)
│   └── components/
│       └── NavigationBottomBar.kt                # NEW — Material3 BottomAppBar with 4 IconButtons
└── di/
    └── UrlConfigModule.kt                        # MODIFY — provided URL changes from
                                                  #          "https://example.com" → AppDefaults.HOME_URL

app/src/main/AndroidManifest.xml                  # MODIFY — add
                                                  # android:enableOnBackInvokedCallback="true" on <application>

app/src/main/res/values/strings.xml               # MODIFY — add 5 browser_action_* keys
app/src/main/res/values-vi/strings.xml            # MODIFY — translate
app/src/main/res/values-de/strings.xml            # MODIFY — translate
app/src/main/res/values-ru/strings.xml            # MODIFY — translate
app/src/main/res/values-ko/strings.xml            # MODIFY — translate
app/src/main/res/values-ja/strings.xml            # MODIFY — translate
app/src/main/res/values-zh/strings.xml            # MODIFY — translate
app/src/main/res/values-fr/strings.xml            # MODIFY — translate

app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/
└── BrowserViewModelTest.kt                       # MODIFY — add nav-action + state-derivation tests

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/
├── NavigationBottomBarTest.kt                    # NEW — Compose UI test for bar interactions
└── BrowserScreenBackGestureTest.kt               # NEW — system back integration test
```

**Structure Decision**: Single-module Android app, mobile-first project type. New code lands in `presentation/browser/` (slot for components: `components/NavigationBottomBar.kt`) plus a constant addition in `core/constants/AppDefaults.kt`. **No `data/`, `domain/`, or new top-level `di/` modules** — this spec sits entirely in the presentation tier per Constitution §IV (WebView session history is platform state, never persisted by the app, so it is correctly out of the domain layer).

## Complexity Tracking

> No Constitution Check violations — Complexity Tracking N/A for Spec 008.
