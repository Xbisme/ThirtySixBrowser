# Implementation Plan: Address Bar / Omnibox

**Branch**: `009-address-bar-omnibox` | **Date**: 2026-05-02 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/009-address-bar-omnibox/spec.md`

## Summary

Wire a single-line top address bar onto the existing `BrowserScreen` Scaffold (built in Spec 008) so the user can submit a URL or a free-form query and have the WebView navigate. The bar mirrors the WebView's current URL while unfocused (hostname-only display) and switches to the full URL with all-text-selected when focused. URL/query classification is a simple `://`-or-`.` heuristic; queries hard-code a Google search URL via the existing `UrlConstants` template (Spec 010 will refactor this into a `SearchEngineRepository`). Implementation is presentation-layer only — no new persistence, no new domain models, no new packages, no new permissions. New surface: 1 Composable (`AddressBar`), 1 small classifier utility (`AddressBarInputClassifier`), 4 new `BrowserViewModel` methods, 2 new `BrowserUiState` fields, 1 new `WebViewActionsHandle` action, and 2 new locale keys × 8 locales = 16 new translations.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (existing project pin)
**Primary Dependencies**: Existing — Compose BOM 2026.04.01, Material3 1.4.0 (`OutlinedTextField`, `IconButton`, `Icons.Filled.Clear`), `androidx.compose.ui:ui` (`SoftwareKeyboardController`, `FocusManager`, `BringIntoViewRequester`), `androidx.compose.foundation:foundation` text-field. Hilt 2.59.2 / KSP 2.3.7 (no new injection points). **Zero new packages.**
**Storage**: N/A — address-bar state is transient ViewModel state. `currentUrl` already lives on `BrowserUiState` (Spec 007); only two new transient fields are added.
**Testing**: Existing — JUnit 4, kotlinx-coroutines-test, Turbine; Compose UI Test + Espresso-Web + Hilt-android-testing wired in Specs 007/008. New tests follow the `NavigationBottomBarTest` precedent (component-level Compose UI tests; full instrumented integration deferred per Spec 008 pattern).
**Target Platform**: Android API 24 (minSdk) → API 36 (targetSdk). All Material3 / Compose APIs used are available from API 24 via the Compose BOM.
**Project Type**: Mobile app (Android, single-module `app/`)
**Performance Goals**: Per spec — SC-001 submit-to-WebView-load-start ≤ 1 s on Pixel 5+. 60 fps during focus/unfocus transitions and clear-button tap (Constitution §V). No work on the main thread beyond standard Compose recomposition + a single regex match per submit.
**Constraints**: APK release-size delta ≤ 50 KB (SC-007). Zero new `.so` (SC-008; Constitution §IX). Zero new permissions (Constitution §II). All visible text localized in 8 locales (Constitution §VIII). Clear button + IME submit reachable via TalkBack (SC-006).
**Scale/Scope**: Single screen extension (top of existing `BrowserScreen`). 1 new Composable (`AddressBar`), 1 new utility (classifier), ~5 new ViewModel methods, ~2 new state fields, 2 new string keys × 8 locales = 16 new translations. Estimated ~250 new lines of Kotlin (UI + classifier + tests).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Note |
|---|-----------|--------|------|
| I | Privacy & Security First | ✅ PASS | No data transmitted to any ThirtySix-controlled server. Submitting a query loads a Google search URL the user explicitly initiated by typing — equivalent to typing `google.com/search?q=...`. No query logging, no analytics. WebView lockdown from Spec 007 unchanged. |
| II | Google Play Compliance | ✅ PASS | No new permissions. No `addJavascriptInterface`. Standard Android `EditText`/Compose `TextField` IME submit. |
| III | Code Quality & Safety | ✅ PASS | New URL-detection regex lives in `core/constants/UrlPatterns.kt`. Google search template already in `core/constants/UrlConstants.kt` (created in Spec 008 home-URL flip). 2 new `R.string.browser_address_bar_*` keys. Composable uses `MaterialTheme.colorScheme` + `Spacing.*` tokens. No magic numbers. No inline strings. |
| IV | Clean Architecture (MVVM) | ✅ PASS | Pure presentation extension. Address-bar state is transient UI state, not persisted, not a business entity — correctly outside `domain/`. URL/query classification is a pure function (no I/O, no Android imports), placed in `presentation/browser/` next to the consumer rather than promoted to `domain/usecase/` per the project's incremental-scope preference and the fact that Spec 010 will refactor query handling into a dedicated `SearchEngineRepository`. |
| V | Performance Excellence | ✅ PASS (gated at impl) | Per-keystroke work is O(1) Kotlin string ops; classification regex runs once per submit, not per keystroke. Hostname extraction runs once per `currentUrl` change. No layout thrash. Manual UX timing gate deferred to a Spec 008-style on-device verification. |
| VI | Testing Discipline | ✅ PASS | New unit tests: classifier (URL vs query, scheme prepend, encoding), hostname extractor, `BrowserViewModel` submit / focus / clear methods. New component-level Compose UI test class for the address bar (mirrors `NavigationBottomBarTest`). Full instrumented integration with a real WebView deferred per the Specs 007/008 precedent. |
| VII | Offline-First Architecture | ✅ PASS | No new persistence. Submit while offline produces the existing Spec 007 `LoadingState.Failed(NetworkUnavailable)` UI — no special-case needed. Address-bar text and focus survive rotation via standard Compose `rememberSaveable` + `StateFlow` (FR-027). |
| VIII | Localization & Accessibility | ✅ PASS | 2 new keys × 8 locales = 16 translations. Address bar `contentDescription` localized. Clear button has its own localized `contentDescription`. M3 `OutlinedTextField` provides 48 dp touch target by default; clear `IconButton` enforces it explicitly. WCAG AA contrast inherited from theme. RTL not required (no RTL locale in supported set). |
| IX | Dependency Currency & 16KB | ✅ PASS | **Zero new packages.** All required APIs (`OutlinedTextField`, `IconButton`, `Icons.Filled.Clear`, `LocalSoftwareKeyboardController`, `FocusManager`, `BackHandler`) are on the existing classpath via Compose BOM 2026.04.01 + Material3 1.4.0 + `material-icons-core` (added in Spec 007). 16 KB CI gate auto-passes by construction. |
| X | Simplicity & Build Order | ✅ PASS | Spec 009 is the natural successor to Spec 008 per `sdd-roadmap.md`. No skipping. No speculative code. Suggestions / autocomplete / voice input / user-selectable search engines all explicitly out of scope (FR-028–031), deferred to later specs. |
| XI | Build Configuration | ✅ PASS | No new BuildConfig fields. No flavor changes. No signing changes. No manifest changes. |

**Result**: 11/11 PASS pre-design. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-address-bar-omnibox/
├── spec.md                              # /speckit-specify (done) + /speckit-clarify (done; 3 Q&A)
├── plan.md                              # This file
├── research.md                          # Phase 0 — component choice, classifier, hostname, focus + back, www. policy
├── data-model.md                        # Phase 1 — BrowserUiState delta + AddressBarSubmitResult sealed shape
├── contracts/
│   └── BrowserViewModel.md              # Phase 1 — public ViewModel surface delta (4 new methods + state delta)
├── quickstart.md                        # Phase 1 — verification checklist
├── checklists/
│   └── requirements.md                  # /speckit-specify output (already updated post-clarify)
└── tasks.md                             # /speckit-tasks output (NOT created here)
```

### Source Code (delta on top of existing repo layout)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   ├── UrlPatterns.kt                                # NEW — file does not yet exist; declare URL_HEURISTIC regex + ADDRESS_BAR_NEWLINE_REGEX
│   └── UrlConstants.kt                               # MODIFY — add GOOGLE_SEARCH_URL_TEMPLATE (used inline by Spec 009; Spec 010 will move into a repository)
├── presentation/browser/
│   ├── BrowserScreen.kt                              # MODIFY — add topBar slot to existing Scaffold; pass AddressBar callbacks
│   ├── BrowserViewModel.kt                           # MODIFY — add onAddressBarTextChange / onAddressBarFocusChange /
│   │                                                 #          onAddressBarSubmit / onAddressBarClear; URL submit path
│   │                                                 #          calls existing WebViewActionsHandle.loadUrl
│   ├── BrowserUiState.kt                             # MODIFY — add addressBarText: String, isAddressBarFocused: Boolean
│   ├── WebViewActionsHandle.kt                       # MODIFY — add loadUrl(String) lambda alongside the existing
│   │                                                 #          goBack/goForward/reload/stopLoading/loadHome lambdas
│   ├── AddressBarInputClassifier.kt                  # NEW — pure function `classifyInput(raw: String): AddressBarSubmitResult`
│   │                                                 #        + sealed `AddressBarSubmitResult{Empty, Url(String), Query(String)}`
│   └── components/
│       └── AddressBar.kt                             # NEW — Material3 OutlinedTextField wrapper:
│                                                     #        single-line, IME action Go, leading icon (search/globe),
│                                                     #        trailing clear IconButton (visible when focused & non-empty),
│                                                     #        hostname-vs-full URL display logic via focus state
│
└── di/                                               # NO CHANGE — UrlConfigModule from Spec 008 untouched

app/src/main/res/values/strings.xml                   # MODIFY — add browser_address_bar_hint, browser_address_bar_clear_cd
app/src/main/res/values-vi/strings.xml                # MODIFY — translate
app/src/main/res/values-de/strings.xml                # MODIFY — translate
app/src/main/res/values-ru/strings.xml                # MODIFY — translate
app/src/main/res/values-ko/strings.xml                # MODIFY — translate
app/src/main/res/values-ja/strings.xml                # MODIFY — translate
app/src/main/res/values-zh/strings.xml                # MODIFY — translate
app/src/main/res/values-fr/strings.xml                # MODIFY — translate

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── presentation/browser/
│   ├── BrowserViewModelTest.kt                       # MODIFY — add address-bar method tests
│   └── AddressBarInputClassifierTest.kt              # NEW — URL vs query, scheme prepend, encoding, edge cases
└── core/extensions/
    └── HostnameExtractionTest.kt                     # NEW — if hostname helper lives in core/extensions/UrlExtensions.kt

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/
└── components/
    └── AddressBarTest.kt                             # NEW — Compose UI test (focus toggle, clear button, hostname display, IME submit, click-to-focus)
```

**Structure Decision**: Single-module Android app, mobile project type. Address-bar UI lands in `presentation/browser/components/AddressBar.kt` parallel to Spec 008's `NavigationBottomBar.kt`. Classifier lives next to the ViewModel that consumes it (`presentation/browser/AddressBarInputClassifier.kt`), keeping a future Spec 010 refactor (move query handling to `domain/usecase/`) trivially mechanical. Two new constants files: extend `UrlConstants.kt` with the Google search template and create `UrlPatterns.kt` (declared in CLAUDE.md project structure but not yet created — this spec creates it). **No `data/`, no new `domain/`, no new top-level `di/`, no manifest changes.**

## Complexity Tracking

> No Constitution Check violations — Complexity Tracking N/A for Spec 009.
