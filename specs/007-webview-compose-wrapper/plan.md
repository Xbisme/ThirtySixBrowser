# Implementation Plan: WebView Compose Wrapper

**Branch**: `007-webview-compose-wrapper` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/007-webview-compose-wrapper/spec.md`

## Summary

Spec 007 ships the first feature-bearing UI in ThirtySixBrowser: a Compose-wrapped `android.webkit.WebView` that loads a hardcoded default URL (`https://example.com`) on app open. The wrapper handles the WebView lifecycle (pause/resume/destroy tied to Composable disposal), surfaces a Material3 top-anchored `LinearProgressIndicator` driven by `WebChromeClient.onProgressChanged`, and renders a localized error state when load fails. Architecture follows Clean Architecture + MVVM (Constitution §IV) — `BrowserScreen` Composable + `BrowserViewModel` (StateFlow<BrowserUiState>) under `presentation/browser/`. No persistence (in-memory only); cookies persist via Android system `CookieManager` default. WebView is locked down per Constitution §I+§II: 4 file-access settings disabled, `addJavascriptInterface` forbidden, mixed content `NEVER_ALLOW`, all web-origin runtime permissions silently denied. An instrumented Espresso-based test on emulator API 29+ verifies the page loads and contains the expected DOM text "Example Domain". The previously-disabled `instrumented-test` CI job is re-enabled (uncommitted change carried from `main`).

## Technical Context

**Language/Version**: Kotlin 2.3.21 (existing) / Java 11 / AGP 9.1.1
**Primary Dependencies**: Compose BOM 2026.04.01 (existing) — `androidx.compose.material3:material3` for `LinearProgressIndicator`; `androidx.compose.ui:ui` for `AndroidView`; `androidx.lifecycle:lifecycle-runtime-compose` (transitively via `lifecycle-viewmodel-compose` 2.10.0, already present); `androidx.webkit:webkit` — **NOT NEEDED** for v1 (forced-dark, COEP/COOP, etc. all out of scope; deferred to a later spec).
**Storage**: N/A — Spec 007 is in-memory only. Cookies handled by Android system `CookieManager` (no app-level wrapper). No Room/DataStore touch.
**Testing**: JUnit 4.13.2 (unit) + Compose UI Test (`androidx.compose.ui:ui-test-junit4`, BOM-managed) + Espresso 3.7.0 (already wired) + **Espresso-Web 3.7.0** (`androidx.test.espresso:espresso-web` — NEW; `androidTestImplementation` only) for asserting WebView DOM content. Robolectric is NOT used for WebView (system WebView is unavailable in JVM); WebView assertions live in `app/src/androidTest/`.
**Target Platform**: Android 7.0+ (minSdk 24) → Android 16 (targetSdk 36). System WebView component versioned by OS.
**Project Type**: mobile-app (Android single-module Gradle project under `app/`)
**Performance Goals**: Cold-start to first paint of `example.com` ≤ 5 s on emulator API 29 / Wi-Fi (SC-001); loading indicator visible within 200 ms of any load start (SC-002); localized error UI visible within 5 s when offline (SC-003).
**Constraints**: minSdk 24; manifest STAYS at 3 permissions (INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS); zero `addJavascriptInterface` call sites; zero hardcoded URL string literals in `presentation/`/`domain/`; APK release size delta ≤ 200 KB vs Spec 006 baseline 1.56 MB; 16 KB native-lib alignment CI gate continues to pass (no new `.so` from this spec; system WebView is OS-provided).
**Scale/Scope**: Single WebView surface, single in-memory page state, one feature module (`presentation/browser/`), 4–6 Composable files + 1 ViewModel + 1 UiState + 1 constants file extension + 1 instrumented test class + 1–2 unit test classes.

## Constitution Check

*Gate: MUST pass before Phase 0. Re-checked after Phase 1.*

| # | Principle | Pre-Phase 0 | How Spec 007 satisfies | Notes |
|---|-----------|-------------|------------------------|-------|
| I | Privacy & Security First | ✅ PASS | FR-006 no `addJavascriptInterface`; FR-013 four file-access settings disabled; FR-016 cookies local-only (no upload); FR-017 silently deny web permissions; FR-018 `MIXED_CONTENT_NEVER_ALLOW`; no analytics; no PII collection. | Cookie persistence is local-only (Constitution §I prohibits *cloud* sync, not local cookies; explicitly stated in FR-016). |
| II | Google Play Compliance | ✅ PASS | Uses `android.webkit.WebView` (Play-approved); manifest unchanged at 3 permissions; no JS bridge; Tools/Productivity category preserved. | No new permissions. |
| III | Code Quality & Safety (No-Hardcode) | ✅ PASS | FR-010 default URL constant in `core/constants/UrlConstants.kt`; FR-009 strings via `stringResource`; FR-015 colors/typography/shapes via `MaterialTheme.*` and spacing via `Spacing.*`. New constants file additions documented in Phase 1 below. | Detekt MagicNumber + Android Lint + ktlint already gating in CI. |
| IV | Clean Architecture (MVVM) | ✅ PASS | FR-011 — `BrowserScreen` Composable in `presentation/browser/`, `BrowserViewModel` exposes `StateFlow<BrowserUiState>`, no DAO/DataStore from VM. No Repository in this spec (justified: no persistence). | Domain layer pure (no Android imports for any domain model added — none added in this spec). |
| V | Performance Excellence | ✅ PASS | SC-001 ≤ 5 s first paint is end-to-end (network + render). Constitution §V's 1.5 s cold-start refers to first **frame** (placeholder Compose Screen), not first **web paint**; both compatible. WebView pause/resume tied to lifecycle (FR-008). | No 60 fps regression — single WebView surface, no list scroll added. |
| VI | Testing Discipline | ✅ PASS | FR-012 instrumented test on emulator API 29+ asserts DOM contains "Example Domain"; unit tests for ViewModel state mapping. CI `instrumented-test` job re-enabled (uncommitted edit on this branch). | Detekt + ktlint + lint zero-violation gates already in place. |
| VII | Offline-First Architecture | ✅ PASS (with documented exception) | WebView page load is the explicit Constitution §VII exception ("the ONLY exception is WebView page load + outbound search query"). Settings/state in-memory v1 — no persistence. | Spec 011 will add tab state to Room. |
| VIII | Localization & Accessibility | ✅ PASS | All user-visible strings in `strings.xml` × 8 locales (FR-009); `MissingTranslation` lint = error (Spec 004 enforcement). TalkBack contentDescription on icons; touch target inheritance from M3 components; WCAG AA via theme. | New strings added in this spec MUST ship to all 8 `values-*/strings.xml` to keep lint green. |
| IX | Dependency Currency & 16KB | ⚠️ PASS w/ research | One new dependency: `androidx.test.espresso:espresso-web`. Version MUST be looked up at moment of `libs.versions.toml` addition (Constitution §IX). Espresso-Web is pure Java (no `.so`) — 16KB-safe by construction. System WebView is OS-provided (no `.so` in our APK). 16KB CI gate continues green. | Lookup procedure documented in research.md R1. |
| X | Simplicity & Build Order | ✅ PASS | Phase 2 first spec, follows mandatory order (001→002→{003,004,005,006}→007). YAGNI: no Repository, no persistence, no Settings flow — strictly the smallest viable WebView surface. | Out-of-scope items (navigation, address bar, tabs, downloads, incognito, retry button) explicitly enumerated in spec Assumptions. |
| XI | Build Configuration | ✅ PASS | No build-type changes; no flavor additions; no new `BuildConfig` field; no signing change. R8 + 16KB gate untouched. | — |

**Gate result**: 11/11 PASS. No violations to track in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/007-webview-compose-wrapper/
├── plan.md                # this file
├── spec.md                # /speckit-specify + /speckit-clarify output
├── research.md            # Phase 0 — Espresso-Web lookup, WebView lifecycle pattern, etc.
├── data-model.md          # Phase 1 — BrowserUiState + LoadingState + ErrorReason shapes
├── contracts/
│   └── browser-screen-contract.md  # Phase 1 — BrowserScreen public API, BrowserViewModel state contract
├── quickstart.md          # Phase 1 — verification gates 1..N
├── checklists/
│   └── requirements.md    # already exists from /speckit-specify
└── tasks.md               # Phase 2 (NOT created by /speckit-plan; comes from /speckit-tasks)
```

### Source Code (repository root) — files Spec 007 creates or extends

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   └── constants/
│       └── UrlConstants.kt                 # NEW or EXTEND — adds DEFAULT_HOME_URL = "https://example.com"
├── presentation/
│   └── browser/
│       ├── BrowserScreen.kt                # NEW — public Composable, hosts BrowserWebView + LinearProgressIndicator + ErrorState
│       ├── BrowserWebView.kt               # NEW — internal AndroidView wrapper around android.webkit.WebView
│       ├── BrowserViewModel.kt             # NEW — Hilt @HiltViewModel, exposes StateFlow<BrowserUiState>
│       ├── BrowserUiState.kt               # NEW — immutable data class
│       ├── LoadingState.kt                 # NEW — sealed class (Idle, Loading(progress), Loaded, Failed(reason))
│       ├── ErrorReason.kt                  # NEW — sealed class (NetworkUnavailable, DnsFailure, HttpError, SslError, Generic) + @StringRes mapping
│       └── components/
│           ├── BrowserLoadingIndicator.kt  # NEW — top LinearProgressIndicator wrapper
│           └── BrowserErrorState.kt        # NEW — full-screen error UI (icon + localized text)
└── presentation/navigation/
    └── AppDestination.kt                   # EXISTING — Browser destination already exists from Spec 002

app/src/main/res/values/strings.xml          # EXTEND — new keys: browser_loading_a11y, browser_error_title,
                                              #          browser_error_offline_hint, browser_error_generic
app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml  # EXTEND — same 4 keys × 7 locales

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/
└── presentation/browser/
    └── BrowserScreenInstrumentedTest.kt    # NEW — Espresso-Web assertion: page contains "Example Domain"
                                              #       (replaces the trivial ExampleInstrumentedTest.kt or runs alongside)

app/src/test/kotlin/com/raumanian/thirtysix/browser/
└── presentation/browser/
    ├── BrowserViewModelTest.kt             # NEW — state transitions (Idle → Loading → Loaded / Failed)
    └── ErrorReasonTest.kt                  # NEW — error code → @StringRes mapping table

gradle/libs.versions.toml                    # EXTEND — add espresso-web library entry (version inherited from existing espressoCore = 3.7.0)
app/build.gradle.kts                         # EXTEND — androidTestImplementation(libs.androidx.espresso.web)
.github/workflows/ci.yml                     # ALREADY EDITED — instrumented-test job re-enabled (uncommitted on this branch)
```

**Structure Decision**: Single-module Android project (existing). All Spec 007 code lives under `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/` (with sub-package `components/` for reusable UI atoms) plus a thin extension to `core/constants/UrlConstants.kt`. No data/domain/repository layers — this spec has no persistence, so no Repository or Mapper or Domain model is justified (per Memory: "Incremental scope — files/packages added only when current spec needs them, not pre-built skeletons"). Following Clean Architecture §IV, `BrowserUiState` and the sealed `LoadingState` / `ErrorReason` types live in the **presentation** layer (not `domain/model/`), because they describe UI-screen state (Loading-with-progress is a UI concern), not portable domain entities. If future specs need to expose page state across screens, those types will be promoted to `domain/model/`.

## Phase 0 — Research output

See [research.md](research.md). Six research items resolved:

- **R1** — Espresso-Web version & 16KB compliance lookup procedure (Constitution §IX)
- **R2** — WebView lifecycle integration pattern in Compose `AndroidView` (DisposableEffect cleanup; no leak)
- **R3** — WebView state preservation across configuration change (rememberSaveable + WebView's own saveState/restoreState)
- **R4** — Consolidated WebView security settings cluster (file-access, JS bridge, mixed content, permissions) — implementation contract
- **R5** — Loading progress event flow (`WebChromeClient.onProgressChanged` 0..100 → `LoadingState.Loading(progress: Float)`)
- **R6** — Error event taxonomy (`WebViewClient.onReceivedError` API 23+ vs deprecated overload, `onReceivedHttpError`, `onReceivedSslError`) → `ErrorReason` mapping

## Phase 1 — Design & Contracts

Phase 1 artifacts:

- [data-model.md](data-model.md) — UI state shapes
- [contracts/browser-screen-contract.md](contracts/browser-screen-contract.md) — `BrowserScreen` Composable public API + `BrowserViewModel` state contract
- [quickstart.md](quickstart.md) — verification gates

### Agent context update

CLAUDE.md "Active Spec" block (between `<!-- SPECKIT START -->` and `<!-- SPECKIT END -->`) updated post-plan to reference [specs/007-webview-compose-wrapper/plan.md](specs/007-webview-compose-wrapper/plan.md). Update happens at end of `/speckit-plan` execution per skill instructions.

## Constitution Check (Post-Phase 1)

Re-evaluating the 11 principles with the additional design detail produced by Phase 0/1:

| # | Principle | Post-Phase 1 | Notes after design |
|---|-----------|--------------|--------------------|
| I | Privacy & Security First | ✅ PASS | R4 confirms the 6-setting WebView lockdown produces zero `addJavascriptInterface`, all 4 file-access disables, mixed-content blocked, permissions denied. Cookies remain `setAcceptCookie(true)` default — local-only. |
| II | Google Play Compliance | ✅ PASS | No manifest delta. |
| III | No-Hardcode | ✅ PASS | R5/R6 surface 2 magic numbers (progress 100, hide-delay) and 4–5 string keys — all named constants in `core/constants/UrlConstants.kt` extension + `core/constants/AnimationDurations.kt` (existing) + `strings.xml`. ErrorReason → @StringRes table is a function, not literal scatter. |
| IV | Clean Architecture | ✅ PASS | Decision documented: presentation-layer types only this spec; promotion to `domain/model/` deferred until cross-screen reuse is required. |
| V | Performance | ✅ PASS | WebView pause/resume in `DisposableEffect` (R2) prevents background CPU; no recomposition storm — `collectAsStateWithLifecycle` already in place. |
| VI | Testing | ✅ PASS | Espresso-Web assertion path verified in R1; unit tests for ViewModel state logic listed in source layout. |
| VII | Offline-First | ✅ PASS | WebView remains the documented exception. |
| VIII | Localization | ✅ PASS | 4 new string keys × 8 locales = 32 entries; lint will fail on any missing. |
| IX | Dep Currency & 16KB | ✅ PASS | Espresso-Web is pure Java (R1) — 16KB-safe by construction; CI gate passes. |
| X | Simplicity | ✅ PASS | No Repository/Mapper/Domain model added — incremental scope respected. |
| XI | Build Config | ✅ PASS | No change. |

**Post-Phase 1 gate result**: 11/11 PASS. Plan ready for `/speckit-tasks`.

## Complexity Tracking

> Empty — no Constitution violations to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none)    | (none)     | (none)                               |
