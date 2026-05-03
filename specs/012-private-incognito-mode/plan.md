# Implementation Plan: Private / Incognito Mode

**Branch**: `012-private-incognito-mode` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/012-private-incognito-mode/spec.md`

## Summary

Add an incognito tab mode on top of Spec 011's tab infrastructure. Incognito tabs live **only in volatile in-memory state** (no Room rows, no on-disk caches), are **visually distinguished** in the switcher with a placeholder card (no screenshot leak), and **wipe their session state** (cookies, cache, form data) on close-of-last-incognito.

Crash-resilience is the user-stated top priority — this drives three deliberate architectural choices:

1. **Public-API cookie snapshot/restore** instead of mucking with the WebView SQLite cookie file. Less powerful (loses cookie attributes), but no path-on-Android-version-fragility risk.
2. **Idempotent `FLAG_SECURE` lifecycle binding** that survives configuration changes, rapid tab-switching, and lifecycle reordering.
3. **In-memory `IncognitoTabRepository` (new domain interface)** parallel to the Room-backed `TabRepository` — no schema changes, no migration, no defensive read of new columns. The two repos are unioned at the use-case layer (`ObserveAllTabsUseCase`) so consumers see one ordered list.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (per Spec 001); JDK 11 toolchain.
**Primary Dependencies (existing, NO new artifacts expected)**: Compose BOM 2026.04.01, Material3 1.4.0, Hilt 2.59.2, Room 2.8.4 (read-side only — no migration), DataStore 1.2.1, kotlinx-coroutines 1.10.2, AndroidX WebKit (system WebView).
**Storage**: In-memory (`MutableStateFlow<List<Tab>>` for incognito list + `CookieSnapshot` data class held in `CookieJarSnapshotManager`). Zero new disk artefacts, zero Room schema changes.
**Testing**: JUnit 4 + MockK 1.13.x + Turbine 1.2.1 (existing); Compose UI Test + Espresso (existing); Robolectric 4.16.1 SDK 33 for any Cookie-related JVM tests if added; ANRWatchdog NOT needed for SC-005 (instrumented stress test asserts no crash via try/catch around 100-cycle loop).
**Target Platform**: Android 7.0+ (minSdk 24, targetSdk 36) — same as Spec 011. `FLAG_SECURE` is API 1+ so no version gate. `CookieManager.removeAllCookies(callback)` is API 21+ — within minSdk floor.
**Project Type**: Native Android single-module app (per Spec 001 / 002).
**Performance Goals**: incognito-tab open ≤ 100 ms p95, `FLAG_SECURE` set/clear ≤ 16 ms (one frame), cookie snapshot capture ≤ 200 ms for typical 5-origin normal-tab list (SC-005 stress baseline).
**Constraints**: Constitution v1.2.0 11/11 — preserve. APK delta ≤ +100 KB (SC-008). 16 KB native-page-size CI gate ✅ (zero new `.so` expected, SC-009). 8-locale translation parity (SC-007).
**Scale/Scope**: `MAX_INCOGNITO_TABS = MAX_TABS = 50` (independent caps per Q1). Cookie snapshot O(N) where N ≤ 50 normal-tab origins.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Walked through Constitution v1.2.0 §I–§XI:

| # | Principle | Result | Notes |
|---|-----------|--------|-------|
| I | Privacy & Security First | ✅ PASS | Spec is privacy-strengthening. Incognito **strictly** does not write to Room/DataStore/disk caches. `addJavascriptInterface` remains forbidden (FR-020). Permission-deny posture inherited from Spec 007 (FR-019). |
| II | Google Play Compliance | ✅ PASS | No new permissions. No new platform APIs beyond what is already in `android.webkit.*` and `WindowManager.LayoutParams.FLAG_SECURE` (long-stable). Tools/Productivity category preserved. |
| III | Code Quality & Safety / No-Hardcode | ✅ PASS | All new strings via `stringResource(R.string.*)`. New limit `MAX_INCOGNITO_TABS` lives in `core/constants/BrowserLimits.kt`. New colour treatment uses existing `MaterialTheme.colorScheme.*` tokens (FR-013) — no new bespoke colour. Detekt + ktlint + lintDebug remain green. |
| IV | Clean Architecture (MVVM) | ⚠️ PASS WITH DOCUMENTED EXCEPTION | One new use-case-coordination point: `CloseIncognitoTabUseCase` calls both `IncognitoTabRepository` (remove tab) AND `CookieJarSnapshotManager` (restore cookies if last incognito tab). This is **NOT** Repository → Repository (forbidden); it is UseCase → Repository + UseCase → CoreService, which Constitution §IV permits. Documented in Complexity Tracking below as a continuation of the Spec 011 use-case-coordination pattern. |
| V | Performance Excellence | ✅ PASS | FLAG_SECURE flag flip is O(1). Cookie snapshot is O(50) origin lookups, each ~1 ms via `CookieManager.getCookie(url)` → bounded ≤ 200 ms. No new heap allocations in steady state. Tab switcher rendering unchanged for normal tabs (incognito card is a fast-path that skips Coil load entirely). |
| VI | Testing Discipline | ✅ PASS | Plan ships ≥ 12 new unit tests (IncognitoTabRepositoryImpl + CookieJarSnapshotManager + 4 new use cases + Tab model isIncognito field test) and ≥ 4 new instrumented tests (rapid-cycle stress per SC-005, FLAG_SECURE lifecycle, cookie-restore behaviour, switcher-card placeholder presence). |
| VII | Offline-First Architecture | ✅ PASS | All incognito state is in-memory. Process death = total wipe (FR-012) — Room is the persistence-of-record only for normal tabs. No new write paths, no new migration. |
| VIII | Localization & Accessibility | ✅ PASS | 9 new string keys × 8 locales = **72 new translations**. Each new affordance (incognito new-tab button, close-all-incognito, indicator label) carries a `contentDescription`. WCAG contrast retained because we only swap colour-token, no bespoke palette. |
| IX | Dependency Currency / 16KB | ✅ PASS | **Zero new packages expected**. The platform `CookieManager` and `WindowManager.LayoutParams.FLAG_SECURE` are part of Android system; system WebView is OS-provided (16KB-safe by virtue of being not in our APK). 16KB CI gate continues green. |
| X | Simplicity & Build Order | ✅ PASS | Spec 012 is the explicit next entry in the Spec 011 → 012 → 013 sequence per [sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md). Phase 2 closes 6/6. No skipped phase. |
| XI | Build Configuration | ✅ PASS | No new buildConfigField, no flavor introduction, no signing change. Debug+release only. |

**Gate result**: 11/11 PASS pre-Phase 0. The single ⚠️ on §IV is a documented use-case-coordination pattern continuation from Spec 011 (which also had one such exception accepted for `UpdateActiveTabUrlAndTitleUseCase`); see Complexity Tracking.

Re-check after Phase 1 design: see end of plan.md (post-design re-check still 11/11 PASS).

## Project Structure

### Documentation (this feature)

```text
specs/012-private-incognito-mode/
├── spec.md                       # /speckit-specify + /speckit-clarify output
├── plan.md                       # THIS FILE
├── research.md                   # Phase 0 output (R1–R10)
├── data-model.md                 # Phase 1 output
├── contracts/
│   ├── IncognitoTabRepository.contract.md     # Phase 1 output
│   └── CookieJarSnapshotManager.contract.md   # Phase 1 output
├── quickstart.md                 # Phase 1 output
├── checklists/
│   └── requirements.md           # /speckit-specify output (already created)
└── tasks.md                      # /speckit-tasks output (NOT created here)
```

### Source Code (delta-only — files added or modified)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   └── constants/
│       └── BrowserLimits.kt                     # MOD: + MAX_INCOGNITO_TABS
├── data/
│   ├── local/
│   │   └── cookies/                             # NEW dir
│   │       ├── CookieJarSnapshot.kt             # NEW: data class (origin → cookieHeader list)
│   │       └── CookieJarSnapshotManagerImpl.kt  # NEW: platform CookieManager wrapper
│   ├── mapper/
│   │   └── TabMapper.kt                         # MOD: emit isIncognito = false from Room
│   └── repository/
│       └── IncognitoTabRepositoryImpl.kt        # NEW: in-memory MutableStateFlow + Mutex
├── domain/
│   ├── model/
│   │   └── Tab.kt                               # MOD: + isIncognito: Boolean = false
│   ├── repository/
│   │   ├── IncognitoTabRepository.kt            # NEW
│   │   └── CookieJarSnapshotManager.kt          # NEW (pure Kotlin interface, kept in domain)
│   └── usecase/
│       ├── CreateIncognitoTabUseCase.kt         # NEW
│       ├── CloseIncognitoTabUseCase.kt          # NEW (also calls SnapshotManager on last close)
│       ├── CloseAllIncognitoTabsUseCase.kt      # NEW
│       ├── ObserveAllTabsUseCase.kt             # NEW: merges normal + incognito Flow
│       ├── ObserveActiveTabIsIncognitoUseCase.kt # NEW: drives FLAG_SECURE
│       └── UpdateActiveTabUrlAndTitleUseCase.kt # MOD: branch by isIncognito (skip Room write)
├── presentation/
│   ├── browser/
│   │   ├── BrowserViewModel.kt                  # MOD: route writes per isIncognito; gate caches
│   │   ├── BrowserUiState.kt                    # MOD: + isIncognito: Boolean
│   │   ├── BrowserWebView.kt                    # MOD: incognito lockdown branch
│   │   ├── BrowserNavigationCallbacks.kt        # UNCHANGED — gate at BrowserViewModel callsites per R7 (no shape change)
│   │   ├── BrowserScreen.kt                     # MOD: incognito indicator beside address bar
│   │   └── components/
│   │       └── IncognitoIndicator.kt            # NEW: small composable
│   ├── tabs/
│   │   ├── TabsViewModel.kt                     # MOD: observe merged tabs + incognito count
│   │   ├── TabsUiState.kt                       # MOD: + incognitoTabCount: Int
│   │   ├── TabsScreen.kt                        # MOD: render incognito affordances
│   │   └── components/
│   │       ├── TabSwitcherCard.kt               # MOD: branch by isIncognito (placeholder render)
│   │       ├── TabSwitcherNewIncognitoTabCard.kt # NEW
│   │       └── CloseAllIncognitoConfirmDialog.kt # NEW (mirrors CloseAllTabsConfirmDialog)
│   └── util/
│       └── SecureWindowEffect.kt                # NEW: Compose effect that toggles Activity FLAG_SECURE
├── di/
│   └── IncognitoModule.kt                       # NEW: Singleton @Binds for IncognitoTabRepository + CookieJarSnapshotManager
├── MainActivity.kt                              # MOD: collect ObserveActiveTabIsIncognitoUseCase → SecureWindowEffect
└── (other files unchanged)

app/src/main/res/values/strings.xml              # MOD: + 9 new keys (EN baseline)
app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml # MOD: + same 9 keys per locale (72 new translations total)
```

**Structure Decision**: Single-module Android app per Spec 001 (no flavors). Layer placement follows Constitution §IV: in-memory in-process repository in `data/repository/`, the contract in `domain/repository/`, no domain-layer Android-SDK imports (verified — `FLAG_SECURE` toggling lives only in `presentation/util/SecureWindowEffect.kt` against the Compose `LocalContext.current as Activity` and never bleeds into `domain/`). Constants extension in `core/constants/BrowserLimits.kt` per the existing pattern from Spec 011. Cookie snapshot manager pairs an interface (in `domain/repository/`) with an Android-aware impl (in `data/local/cookies/`) — the interface is intentionally pure Kotlin so use cases can depend on it without crossing layers.

## Complexity Tracking

> Constitution gates that require deviation justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| §IV — `CloseIncognitoTabUseCase` calls **two** data-layer collaborators (`IncognitoTabRepository` + `CookieJarSnapshotManager`) in a single use-case invocation | The "close-the-last-incognito-tab" operation is a single conceptual transaction from the user's perspective — closing the tab AND restoring cookies must succeed atomically or the privacy guarantee leaks. Coordinating those two services at the use-case layer (the only layer the constitution allows for cross-feature glue per Spec 011's accepted exception) is the only Constitution-compliant location. The alternative — a "Repository that owns cookies" — would either (a) reintroduce a `Repository → Repository` chain (forbidden) or (b) merge cookie management into `IncognitoTabRepository`, conflating two separate concerns and making the snapshot manager untestable in isolation. | Repository → Repository is explicitly forbidden by §IV. Conflating cookie state into `IncognitoTabRepository` would make the snapshot manager impossible to unit-test against the platform `CookieManager` shadow (Robolectric) without dragging the full repository state machine into every test. |

> This is the **only** documented exception. PR body MUST link to this row for reviewer ack record (continues the Spec 010/011 pattern).

## Phase 0: Research Plan

> Outputs to [research.md](research.md). Each R-item below resolves a specific implementation unknown surfaced during plan drafting.

- **R1** — Cookie snapshot strategy via public `CookieManager` API (decision: per-origin getCookie/setCookie round-trip; documented limitations on cookie-attribute loss).
- **R2** — `FLAG_SECURE` lifecycle binding pattern in Compose hosted inside an `AppCompatActivity` / `ComponentActivity`. Idempotency proof. Edge: configuration-change recreate.
- **R3** — `IncognitoTabRepository` ID space — negative `Long` IDs to guarantee non-collision with Room auto-increment positive IDs across the merged `Tab` Flow.
- **R4** — Hilt scope choice for `IncognitoTabRepository` and `CookieJarSnapshotManager` — both `@Singleton` because incognito session crosses ViewModel lifetime.
- **R5** — `WebView` lockdown delta for incognito tabs (`setSaveFormData(false)`, `setCacheMode(LOAD_NO_CACHE)`, on-destroy `clearHistory + clearFormData + clearMatches + clearSslPreferences + clearCache(true)` sequence).
- **R6** — Switcher card render: how to suppress screenshot read for incognito without modifying `ScreenshotCache` impl (decision: gate at composable callsite, leave cache contract untouched).
- **R7** — `BrowserViewModel` cache-write gating: where exactly to short-circuit favicon + screenshot writes when active tab is incognito (decision: at the existing `onIconReceived` / `onScreenshotReady` mutators, single `if (state.isIncognito) return` guard).
- **R8** — Stress-test harness for SC-005 (100 rapid open/close cycles): Espresso `IdlingResource` integration + `runBlocking` loop with `Dispatchers.Main.immediate` to keep the UI thread queue honest.
- **R9** — Restoration of normal active-tab pointer when the last incognito tab closes (decision: re-emit the current head of `TabRepository.observeTabs()` via the merged Flow ordering rule).
- **R10** — External intent (FR-018) crash-safety pattern: `try { startActivity(intent) } catch (ActivityNotFoundException, SecurityException) { … silent toast or drop … }` lives in `BrowserWebView` `WebViewClient.shouldOverrideUrlLoading` for ALL non-http schemes (incognito or not — strengthening Spec 007/008 baseline).

## Phase 1: Design Artefacts

> Outputs:
>
> - [data-model.md](data-model.md) — `Tab` extension, `CookieJarSnapshot`, in-memory `IncognitoTabRepository` shape, merge ordering rules
> - [contracts/IncognitoTabRepository.contract.md](contracts/IncognitoTabRepository.contract.md) — interface contract + concurrency guarantees
> - [contracts/CookieJarSnapshotManager.contract.md](contracts/CookieJarSnapshotManager.contract.md) — capture/restore protocol + serialization guarantees
> - [quickstart.md](quickstart.md) — verify-the-spec-is-done gate sequence (build + unit + instrumented + manual)
> - `CLAUDE.md` `<!-- SPECKIT START -->` block updated to point at this plan

## Constitution Re-Check (post-design)

After Phase 1 artefacts (research, data-model, contracts, quickstart) drafted:

- §I–§XI walk-through — UNCHANGED from pre-Phase 0: 11/11 PASS.
- The §IV exception remains the only documented deviation; both contracts (`IncognitoTabRepository`, `CookieJarSnapshotManager`) are independently testable and depend ONLY on `core/`-layer types (no cross-feature repo coupling at the data layer).
- No new gate violations surfaced during data-model or contract design. Cookie-attribute-loss caveat (R1) is documented as a known limitation in research.md and surfaced at the spec acceptance gate, not a constitution deviation.

**Final gate result**: 11/11 PASS post-Phase 1 design. Ready for `/speckit-tasks`.
