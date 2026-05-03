# Implementation Plan: Tabs Management

**Branch**: `011-tabs-management` | **Date**: 2026-05-03 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/011-tabs-management/spec.md`

## Summary

Light up Spec 005's previously-unused `tabs` table by adding the first concrete consumer: a multi-tab browsing model with a grid switcher, persistence across app kill/restart, individual + bulk close, and a max-tabs cap. The active-tab pointer is derived from `MAX(last_active_at)` over the `tabs` table — no Room schema migration. The tab switcher reuses the placeholder `AppDestination.Tabs` route (already wired in [AppNavGraph.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt)) and replaces the current `TabsScreen` placeholder Composable with a real `LazyVerticalGrid` of `TabSwitcherCard`s. Entry to the switcher is a 5th `IconButton` added to Spec 008's `BottomAppBar`, single-tap = navigate to switcher route, **long-press = open a fresh home tab inline** (Q2 clarification). Inactive tabs do NOT keep a live `WebView` instance — switching to an inactive tab recreates the WebView and reloads the persisted URL (FR-027 / A6 — explicit memory-vs-fidelity trade-off). Thumbnails are deterministic text-only placeholders (Q1 clarification — no screenshot capture). Incognito tabs are explicitly out of scope (Spec 012).

This is the first spec to introduce a Repository → Repository dependency that crosses feature boundaries (`TabRepository` for state-of-truth, `BrowserViewModel` for the active tab's URL/title write-through). The pattern is documented as an accepted exception in [Complexity Tracking](#complexity-tracking) and mirrors Spec 010's precedent (`SearchEngineRepository → SettingsRepository`).

Estimated scope: 1 new core constant file, 4 new domain files (model + repository interface + 6 use cases), 2 new data files (mapper + repository impl), 1 new Hilt module, 1 new ViewModel + UiState, ~5 new Composables (switcher screen + card + new-tab card + close-all dialog + empty-state component), modifications to `BrowserScreen` + `BrowserViewModel` + `NavigationBottomBar*` (5th button + long-press + 2 new callbacks), 6 new string keys × 8 locales = 48 new translations, ~25 new unit tests + 3 new instrumented tests.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (existing project pin)
**Primary Dependencies**: Existing — Hilt 2.59.2 / KSP 2.3.7, Room 2.8.4 (entity + DAO already shipped in Spec 005), DataStore Preferences 1.2.1 (NOT used by this spec — active-tab pointer is derived from Room, NOT persisted in DataStore), Navigation Compose 2.9.8, Compose BOM 2026.04.01 (provides `LazyVerticalGrid` from `foundation`, `BadgedBox` from `material3`, `combinedClickable` from `foundation.ExperimentalFoundationApi`), kotlinx-coroutines + Flow, Material icons (Tabs/Add/Close already present in `material-icons-core` from Spec 007). **Zero new packages.**
**Storage**: Room v1 schema from Spec 005 (`TabEntity` table `tabs`, columns `id` PK auto, `url`, `title`, `position`, `created_at`, `last_active_at`; `INDEX_POSITION` already in place). **NO schema migration required** — A1 / A2 in spec.md commit to using the existing columns as-is. The active-tab pointer is derived at query time via `MAX(last_active_at)`, NOT stored as a separate column. New `BrowserLimits.MAX_TABS = 50` constant under `core/constants/BrowserLimits.kt`.
**Testing**: Existing — JUnit 4, kotlinx-coroutines-test, Turbine, Robolectric 4.16.1 (DAO-touching tests, pinned to SDK 33 per Spec 005's `app/src/test/resources/robolectric.properties`), Espresso + Espresso-Web (Spec 007), Hilt androidTest runtime (Spec 007), `HiltTestActivity` (Spec 007). Pure-JVM unit tests for the ViewModel + repository + use cases using a `FakeTabRepository` pattern matching Spec 010's `FakeSearchEngineRepository`. Instrumented tests for: tab switcher round-trip, process-death restoration, max-tabs limit gate.
**Target Platform**: Android API 24 (minSdk) → API 36 (targetSdk). Predictive back integration for the switcher overlay/route uses Spec 008's existing `PredictiveBackHandler` pattern; pre-Android-14 falls back to standard `BackHandler`.
**Project Type**: Mobile app (Android, single-module `app/`)
**Performance Goals**: Per spec — SC-005 switcher grid renders ≤ 200 ms for up to 50 tabs on Pixel 5+; SC-010 cold-start tab restoration ≤ 500 ms for up to 50 persisted tabs (active tab WebView begins loading immediately, inactive metadata hydrated lazily via Flow); 60 fps preserved on `BrowserScreen` and the new `TabsScreen` (Constitution §V). Tab switch latency on tap (close switcher → activate target tab) ≤ 100 ms perceived (Constitution §V interactive-tap budget).
**Constraints**: APK release-size delta ≤ 200 KB (SC-007). Zero new `.so` (SC-008; Constitution §IX). Zero new permissions (Constitution §II). All 162 existing unit tests preserve green (SC-006). Detekt baseline UNCHANGED (SC-009) — bundle expansion of `NavigationBottomBarCallbacks` 4→6 fields is exactly at `LongParameterList.functionThreshold = 6` and PASSES; if a future review tightens the threshold, callbacks will need re-bundling. NO regression in Spec 008's predictive back semantics for the BrowserScreen path.
**Scale/Scope**: 1 new domain interface (`TabRepository`), 1 new repository implementation (`TabRepositoryImpl`), 1 new mapper (`TabMapper`), 1 new domain model (`Tab`), 6 new use cases, 1 new Hilt module (`TabsModule`), 1 new ViewModel (`TabsViewModel`) + UiState, 1 new core constant file (`BrowserLimits.kt`), 5–7 new Composables (Switcher Screen rewrite + Card + New-tab card + Close-all dialog + Empty-state placeholder), modifications to 4 existing files (`BrowserScreen.kt`, `BrowserViewModel.kt`, `NavigationBottomBar.kt`, `NavigationBottomBarCallbacks.kt`). 6 new string resource keys × 8 locales = 48 new translations. ~25 new unit tests + ~3 new instrumented tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Note |
|---|-----------|--------|------|
| I | Privacy & Security First | ✅ PASS | All tab state stored on-device in the existing Room DB (Spec 005). NO new network surface. NO logging of URLs beyond what `TabEntity.url` already persists. NO analytics. The shared cookie-jar across non-incognito tabs is Android WebView's default behavior (Spec 007 lockdown applied per-WebView per FR-028). Incognito isolation is Spec 012's job; this spec correctly preserves the absence of `is_incognito` from `TabEntity`. |
| II | Google Play Compliance | ✅ PASS | NO new permissions. NO `addJavascriptInterface`. NO new manifest entries. The 5th BottomAppBar button is a standard `IconButton`. `LazyVerticalGrid` is a public Jetpack Compose API. `Modifier.combinedClickable` for the long-press gesture is in `foundation.ExperimentalFoundationApi` — annotation `@OptIn(ExperimentalFoundationApi::class)` is required at the call site, which is documented as an accepted Jetpack opt-in (not an unstable third-party API). |
| III | Code Quality & Safety | ✅ PASS | New constant file `core/constants/BrowserLimits.kt` per the No-Hardcode Rule "Magic numbers / limits" row. New string keys for "tabs_switcher_title" / "tabs_switcher_new_tab" / "tabs_switcher_close_all" / "tabs_switcher_close_all_confirm" / "tabs_card_close" / "tabs_default_title" externalized to all 8 locale `strings.xml`. NO inline magic numbers, NO inline strings, NO inline colors. Detekt baseline expected UNCHANGED — `NavigationBottomBarCallbacks` data class expands 4 → 6 fields, exactly at `LongParameterList.functionThreshold = 6` (passes). |
| IV | Clean Architecture (MVVM) | ✅ PASS w/ documented exception | New `TabRepository` interface in `domain/repository/`, impl in `data/repository/`. New `Tab` model in `domain/model/` (pure Kotlin, zero Android imports). New use cases in `domain/usecase/`. `TabsViewModel` is `@HiltViewModel` and depends on use cases only. **Repository → Repository exception**: `BrowserViewModel` (presentation-side) writes through `UpdateTabUrlAndTitleUseCase` — the use case bridges `BrowserViewModel`'s WebView callbacks into `TabRepository`. There is NO direct `Repository → Repository` chain in `data/`; the cross-feature glue lives at the use-case layer where Constitution §IV permits it (UseCases coordinate Repositories). See research.md R4. |
| V | Performance Excellence | ✅ PASS | Switcher grid uses `LazyVerticalGrid` with stable `key = tab.id` (Constitution §V — never index-only). Inactive tabs do NOT keep a live `WebView` (FR-027 / A6) → memory predictable even at 50 tabs. The 500 ms cold-start budget (SC-010) is comfortable: Room hydration of 50 rows from a 4-column table is sub-10ms; the active tab's WebView begins loading from the persisted URL immediately while the inactive list flows in via `TabDao.observeAll()`. The active-tab pointer query (`MAX(last_active_at)`) hits the existing PK ordering — sub-millisecond on 50 rows. |
| VI | Testing Discipline | ✅ PASS | 25 new unit tests planned (see Phase 1 contracts). 3 new instrumented tests: `TabSwitcherFlowTest` (open switcher → tap card → tab switches), `TabPersistenceProcessDeathTest` (force-restart restores tabs via Hilt-injected `TabRepositoryImpl` + Robolectric SDK 33), `MaxTabsLimitTest` (50 tabs → new-tab affordance disabled + localized message). Domain-layer coverage stays ≥ 70% per Constitution §VI. |
| VII | Offline-First Architecture | ✅ PASS | All tab CRUD writes through `TabDao` (Room WAL — Spec 005). Atomic writes via `suspend` DAO API. Process-death recovery is the spec's headline (US2). Constitution §VII §"Process death MUST not lose tab state" calls out exactly this guarantee — Spec 011 is its first implementation. |
| VIII | Localization & Accessibility | ✅ PASS | 6 new string keys × 8 locales = 48 translations, externalized before any Composable referencing them ships. All `IconButton`s in the new Switcher get `contentDescription` from `stringResource(...)`. Tab cards expose `Modifier.semantics { contentDescription = ... }` describing the tab's title + hostname for TalkBack. Touch targets meet 48×48dp baseline. WCAG AA contrast for active-tab indicator + close (×) affordance verified via Material Theme Builder (already done in Spec 003). |
| IX | Dependency Currency & 16KB | ✅ PASS | **Zero new packages.** All required Compose/Material APIs are on the classpath. 16 KB CI gate auto-passes by construction (no new `.so`). |
| X | Simplicity & Build Order | ✅ PASS | Spec 011 is the documented next step in `sdd-roadmap.md` (Phase 2 Core Browser, after 010). No skipping. No speculative code: every new use case has at least one production caller in this spec; the `position` column reuse from Spec 005 is YAGNI-compliant since it's a tiebreaker in v1.0 and explicitly reserved (FR-023) — not an unbuilt drag-to-reorder feature. Incognito tabs explicitly deferred to Spec 012 (FR-031). Drag-to-reorder, tab groups, "open in new tab from link", and undo-close all explicitly deferred to future specs (FR-033 / FR-034 / FR-035 / FR-037). |
| XI | Build Configuration | ✅ PASS | No new BuildConfig fields. No flavor changes. No signing changes. No manifest changes. |

**Result**: 11/11 PASS pre-design. One documented exception under Principle IV (Repository → Repository via UseCase coordination layer — see Complexity Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/011-tabs-management/
├── spec.md                              # /speckit-specify (done) + /speckit-clarify (done; 2 Q&A)
├── plan.md                              # This file
├── research.md                          # Phase 0 — 12 R-items (decisions + rationale + alternatives)
├── data-model.md                        # Phase 1 — Tab domain model + TabEntity reuse + TabsUiState shape
├── contracts/
│   ├── TabRepository.md                 # Phase 1 — domain interface contract
│   ├── TabsViewModel.md                 # Phase 1 — ViewModel API + UiState transitions
│   ├── BrowserViewModel.md              # Phase 1 — write-through coupling (URL / title / active-switch)
│   └── NavigationBottomBar.md           # Phase 1 — 5th button + long-press contract + callbacks expansion
├── quickstart.md                        # Phase 1 — verification checklist (5 manual gates)
├── checklists/
│   └── requirements.md                  # /speckit-specify output (updated post-clarify)
└── tasks.md                             # /speckit-tasks output (NOT created here)
```

### Source Code (delta on top of existing repo layout)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   └── BrowserLimits.kt                                 # NEW — object BrowserLimits { const val MAX_TABS: Int = 50 }
├── domain/
│   ├── model/
│   │   └── Tab.kt                                       # NEW — pure Kotlin data class (id, url, title, position, createdAt, lastActiveAt)
│   ├── repository/
│   │   └── TabRepository.kt                             # NEW — interface: observeTabs() → Flow<List<Tab>>, getActiveTabId(), createTab(...), switchActiveTab(id), updateTabUrlAndTitle(...), closeTab(id), closeAllTabs(), getTabCount()
│   └── usecase/
│       ├── ObserveTabsUseCase.kt                        # NEW — exposes Flow<List<Tab>> for TabsViewModel
│       ├── ObserveActiveTabUseCase.kt                   # NEW — derived Flow<Tab?> = observeTabs().map { it.maxByOrNull(::lastActiveAt) }
│       ├── CreateTabUseCase.kt                          # NEW — guards MAX_TABS; returns Result.Error(MaxTabsReachedException) on cap
│       ├── SwitchActiveTabUseCase.kt                    # NEW — writes lastActiveAt = now()
│       ├── CloseTabUseCase.kt                           # NEW — durable close; if last tab → recreate fresh home tab
│       ├── CloseAllTabsUseCase.kt                       # NEW — wipe + recreate single fresh home tab atomically
│       └── UpdateActiveTabUrlAndTitleUseCase.kt         # NEW — write-through from BrowserViewModel WebView callbacks
├── data/
│   ├── mapper/
│   │   └── TabMapper.kt                                 # NEW — TabEntity ↔ Tab (Spec 005 mapper precedent: pure top-level functions)
│   └── repository/
│       └── TabRepositoryImpl.kt                         # NEW — class @Inject constructor(private val tabDao: TabDao, @Named("default_home_url") private val homeUrl: String, private val dispatchers: DispatcherProvider) {...}
├── di/
│   └── TabsModule.kt                                    # NEW — abstract class @Binds bindTabRepository(impl: TabRepositoryImpl): TabRepository (sibling of SettingsModule, SearchEngineModule)
├── presentation/
│   ├── browser/
│   │   ├── BrowserScreen.kt                             # MODIFY — bottom-bar 5th button slot; navController-passing for switcher route; reads activeTab.url from TabsViewModel via collectAsStateWithLifecycle to drive WebView reload on tab switch
│   │   ├── BrowserViewModel.kt                          # MODIFY — inject UpdateActiveTabUrlAndTitleUseCase; wire onUrlChanged + onLoadFinished (title) to write through; wire homeUrl from active tab when constructing BrowserUiState
│   │   └── components/
│   │       ├── NavigationBottomBar.kt                   # MODIFY — add 5th IconButton with combinedClickable (single-tap=onTabsSwitcherClick, long-press=onTabsSwitcherLongClick); BadgedBox showing tab count
│   │       └── NavigationBottomBarCallbacks.kt          # MODIFY — data class expands 4 → 6 fields (add onTabsSwitcherClick + onTabsSwitcherLongClick) — at LongParameterList threshold but PASSES
│   └── tabs/
│       ├── TabsScreen.kt                                # REWRITE — replace placeholder with LazyVerticalGrid of TabSwitcherCard + new-tab card + close-all dialog
│       ├── TabsViewModel.kt                             # NEW — @HiltViewModel; collects ObserveTabsUseCase + ObserveActiveTabUseCase; exposes TabsUiState; user actions delegate to use cases
│       ├── TabsUiState.kt                               # NEW — data class TabsUiState(tabs, activeTabId, isCloseAllDialogVisible, errorEvent)
│       └── components/
│           ├── TabSwitcherCard.kt                       # NEW — Composable card per tab (deterministic placeholder bg + first-letter glyph + title + hostname + close X + active-tab indicator)
│           ├── TabSwitcherNewTabCard.kt                 # NEW — Composable card for the "new tab" affordance inside switcher
│           ├── CloseAllTabsConfirmDialog.kt             # NEW — Material3 AlertDialog (FR-012)
│           └── TabPlaceholderColor.kt                   # NEW — pure-Kotlin object with deterministic hash(hostname) → ColorScheme role + glyph(hostname) → Char (NO inline Color literals; uses MaterialTheme.colorScheme palette indices)

app/src/main/res/
├── values/strings.xml                                   # MODIFY — add 6 new keys
└── values-{vi,de,ru,ko,ja,zh,fr}/strings.xml           # MODIFY — add 6 translated strings each (7 files × 6 keys = 42 translations)

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── data/
│   ├── mapper/
│   │   └── TabMapperTest.kt                             # NEW — 4 round-trip tests (TabEntity ↔ Tab)
│   └── repository/
│       └── TabRepositoryImplTest.kt                     # NEW — 6 tests: observeTabs, MAX(last_active_at) active pointer, createTab cap-guard, closeTab last-tab recreate, closeAllTabs atomic recreate, updateTabUrlAndTitle write-through
├── domain/
│   ├── model/
│   │   └── TabTest.kt                                   # NEW — 1 test: equality + copy semantics for the data class
│   └── usecase/
│       └── TabUseCasesTest.kt                           # NEW — 6 tests: one per use case (delegation to FakeTabRepository)
└── presentation/tabs/
    └── TabsViewModelTest.kt                             # NEW — 8 tests: state mutators, dialog visibility, close-all flow, switch-active flow, error event surface

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/
└── presentation/tabs/
    ├── TabSwitcherFlowTest.kt                           # NEW — open switcher from BrowserScreen → tap a card → switcher closes → BrowserScreen shows the tapped tab's URL
    ├── TabPersistenceProcessDeathTest.kt                # NEW — create N tabs → ActivityScenario.recreate() → assert restoration
    └── MaxTabsLimitTest.kt                              # NEW — fill to MAX_TABS → assert affordance disabled + localized message
```

**Structure Decision**: Existing single-module Android Clean Architecture layout (`core/data/domain/presentation/di`) per Constitution §IV. This spec adds **15 new production files** (1 constant file, 1 domain model, 1 repository interface, 6 use cases, 1 mapper, 1 repository impl, 1 Hilt module, 1 ViewModel + UiState, 4 new Composable component files) + **rewrites 1 placeholder file** (`TabsScreen.kt`) + **modifies 4 existing files** (`BrowserScreen.kt`, `BrowserViewModel.kt`, `NavigationBottomBar.kt`, `NavigationBottomBarCallbacks.kt`) + **modifies 8 locale `strings.xml`** files. New Hilt module file is named `TabsModule.kt` — sibling to `SettingsModule.kt` (Spec 006) and `SearchEngineModule.kt` (Spec 010) — keeping the discoverability pattern.

The tab switcher reuses the **already-wired** `AppDestination.Tabs` route in [AppNavGraph.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt) — no new route definition, no `AppDestination` change. The switcher button single-tap calls `navController.navigate(AppDestination.Tabs.route)`; long-press dispatches `CreateTabUseCase` directly via `TabsViewModel` (or a hoisted callback at the `MainActivity` / `AppNavGraph` level — finalized in research R6).

## Phase 0 — Research

See [research.md](research.md) for the full record. Summary of items resolved:

| # | Topic | Outcome |
|---|-------|---------|
| R1 | Active-tab pointer mechanism (in-session vs persisted vs derived) | Derived from `MAX(last_active_at)` over the `tabs` table at query time. Mutated in-session by writing the about-to-leave tab's `last_active_at = System.currentTimeMillis()` immediately BEFORE the switch (FR-005). Avoids a Room schema migration for a new `is_active` column. The "current tab" pointer in `TabsUiState` is computed by `ObserveActiveTabUseCase` as `tabs.maxByOrNull { it.lastActiveAt }`. |
| R2 | Tab switcher = route vs modal overlay | **Route** — reuses the placeholder `AppDestination.Tabs` already wired in [AppNavGraph.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt). Matches the project pattern (Bookmarks / History / Downloads / Settings are all routes). NavController back-stack handles predictive back natively. Trade-off vs overlay: switcher loses the `BrowserScreen` bottom-bar context, but the switcher screen owns its own UI (LazyVerticalGrid + new-tab card + close-all FAB), so this is acceptable. |
| R3 | Tab thumbnail rendering (deterministic placeholder algorithm) | Hash hostname → palette index in a fixed-size MaterialTheme-derived color list (uses `MaterialTheme.colorScheme.{primary,secondary,tertiary,error,primaryContainer,secondaryContainer,tertiaryContainer,errorContainer}`); first letter of hostname (uppercased, non-alphanumeric stripped, fallback `?`) as the foreground glyph using `MaterialTheme.typography.headlineMedium`. NO inline `Color(0xFF...)` literals (Constitution §III) — palette indices map into theme tokens. Pure function `TabPlaceholderColor.forHostname(hostname: String): Pair<Color, Char>` exposed for unit testing. |
| R4 | Repository → Repository exception (BrowserViewModel write-through) | Avoid direct `Repository → Repository` chain. The bridge from `BrowserViewModel` WebView callbacks (`onUrlChanged`, `onLoadFinished` for title) into `TabRepository` lives at the **use-case layer** (`UpdateActiveTabUrlAndTitleUseCase`). UseCases coordinating Repositories is explicitly permitted by Constitution §IV — only `Repository → Repository` is forbidden. `BrowserViewModel` injects the use case; `TabRepositoryImpl` only depends on `TabDao` + `DispatcherProvider` + the home-URL `@Named` constant for the auto-create-home-tab default (FR-019). |
| R5 | Empty-state auto-create-home-tab — where does it live? | Inside `TabRepositoryImpl.observeTabs()` — if Room emits an empty list on cold start, the impl performs a one-time atomic insert of a fresh home tab (idempotent via a `Mutex`-guarded check `if (tabDao.count() == 0) tabDao.insert(...)`). Subsequent emissions show the seeded tab. Same logic on the last-tab-close path inside `CloseTabUseCase` (when `tabDao.count()` would drop to 0 after the close, recreate atomically in the same transaction). |
| R6 | NavigationBottomBarCallbacks expansion (4 → 6 fields) | Spec 008 set the bundle at 4 fields under detekt's `LongParameterList.functionThreshold = 6`. Spec 011 adds 2 fields: `onTabsSwitcherClick` + `onTabsSwitcherLongClick`. Bundle becomes 6 — exactly at the threshold (PASSES). The 5th button uses `Modifier.combinedClickable(onClick = callbacks.onTabsSwitcherClick, onLongClick = callbacks.onTabsSwitcherLongClick)` requiring a file-level `@OptIn(ExperimentalFoundationApi::class)`. The single-tap callback (typically `{ navController.navigate(AppDestination.Tabs.route) }`) is hoisted from `BrowserScreen` (which gets the `NavController` passed in from `AppNavGraph`); the long-press callback dispatches `CreateTabUseCase` via a small bridge on `BrowserViewModel` (`fun onLongPressNewTab() { viewModelScope.launch { createTab() } }`) so the BrowserScreen does not need to inject the use case directly. |
| R7 | BrowserViewModel ↔ TabsViewModel coupling | `BrowserViewModel` does NOT inject `TabRepository` directly. Instead: (a) it injects `ObserveActiveTabUseCase` to seed `BrowserUiState.currentUrl` from the active tab on cold start (replaces the current `@Named("default_home_url")` injection with a use-case-driven flow that falls back to home URL when no active tab exists — auto-create handles the "no tab" case via R5); (b) it injects `UpdateActiveTabUrlAndTitleUseCase` for write-through on `onUrlChanged` + `onLoadFinished`; (c) it injects `CreateTabUseCase` for the long-press new-tab path (R6). Switching tabs is initiated from `TabsScreen` via `SwitchActiveTabUseCase` + `navController.popBackStack()`; the active-tab Flow then re-emits, BrowserScreen recomposes, and `WebViewActionsHandle.loadUrl(activeTab.url)` is invoked via a `LaunchedEffect(activeTab.id)`. |
| R8 | Inactive-tab WebView lifecycle (FR-027 / A6) | One `WebView` instance lives at any time — the active tab's. On tab switch via `SwitchActiveTabUseCase`, the existing WebView is disposed (`DisposableEffect` cleanup from Spec 007 fires when `activeTab.id` changes), and a fresh WebView is instantiated for the new tab via the `LaunchedEffect(activeTab.id)` triggering `loadUrl(activeTab.url)`. Per-tab in-session state (scroll position, form input) is NOT preserved across tab switches in v1.0 — explicit memory-vs-fidelity trade-off. |
| R9 | Process-death restoration test strategy | Robolectric-backed Hilt instrumented test using `androidx.test.core.app.ActivityScenario.recreate()` after seeding 3 tabs via a Hilt-injected `TabRepository` test override. Asserts: post-recreate, `ObserveTabsUseCase` emits the same 3 tabs in the same order, the active tab matches the pre-kill active tab. Pure `force-stop` is not testable in JVM — `recreate()` is the closest portable proxy; full process-death verification is on the manual user-device gate list (US2 Independent Test). |
| R10 | Hilt module placement — TabsModule new file | New file `app/.../di/TabsModule.kt` sibling to `SettingsModule.kt` and `SearchEngineModule.kt`. Keeps the discoverability pattern (one module per feature surface). Single `@Binds` line for `TabRepository` impl; `@InstallIn(SingletonComponent::class)`; `@Singleton` scope (matches `SettingsRepository` / `SearchEngineRepository`). |
| R11 | Tab title fallback ("New tab") string key | New string key `tabs_default_title` localized in all 8 locales. Used: (a) when a tab is just-created and `WebViewClient.onReceivedTitle` has not yet fired, (b) when restoration loads a persisted row whose `title` column is empty. The fallback is applied at the `TabSwitcherCard` Composable layer (display-time), NOT at write-time (the persisted `title` may legitimately be empty until the WebView resolves it). |
| R12 | Test fakes (FakeTabRepository pattern) | Hand-rolled `FakeTabRepository` class implementing the interface, backed by a `MutableStateFlow<List<Tab>>` for `observeTabs()` and inline `MutableMap<Long, Tab>` for state. Pattern matches Spec 010's `FakeSearchEngineRepository`. No mocking framework required. Lives under `app/src/test/.../testdoubles/` (new package) for reuse across `TabsViewModelTest` and `TabUseCasesTest`. |

## Phase 1 — Design & Contracts

### Data model

See [data-model.md](data-model.md) for the full record. Summary:

- **Domain `Tab`** (NEW, pure Kotlin): `data class Tab(val id: Long, val url: String, val title: String, val position: Int, val createdAt: Long, val lastActiveAt: Long)`. Pure-Kotlin, zero Android imports.
- **`TabEntity`** (Spec 005, unchanged): existing 6 columns (`id`, `url`, `title`, `position`, `created_at`, `last_active_at`). NO migration. NO new column. NO `is_incognito` (Spec 012 keeps incognito in-memory-only).
- **`TabMapper`** (NEW, top-level pure functions): `fun TabEntity.toDomain(): Tab` + `fun Tab.toEntity(): TabEntity`. Spec 005's mapper precedent (none yet — Spec 011 introduces the first `data/mapper/` file in the project; pattern documented in research R12).
- **`TabsUiState`** (NEW): `data class TabsUiState(val tabs: List<Tab>, val activeTabId: Long?, val isCloseAllDialogVisible: Boolean, val errorEvent: TabsErrorEvent?)`. `TabsErrorEvent` is a sealed type with `MaxTabsReached` (FR-016) plus a `consumed()` reducer for one-shot snackbar / toast surfaces.
- **`BrowserUiState`** (Spec 007/008/009/010): unchanged — still the single source of truth for `currentUrl` etc. Tab-level state lives in `TabsUiState`; `BrowserViewModel` reads the active tab's URL from `ObserveActiveTabUseCase` to seed `BrowserUiState.currentUrl` on cold start AND on tab switch (via `LaunchedEffect`).
- **`BrowserLimits`** (NEW): `object BrowserLimits { const val MAX_TABS: Int = 50 }`. New file under `core/constants/`.

### Contracts

See [contracts/TabRepository.md](contracts/TabRepository.md), [contracts/TabsViewModel.md](contracts/TabsViewModel.md), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md), [contracts/NavigationBottomBar.md](contracts/NavigationBottomBar.md). Summary:

```kotlin
// domain/repository/TabRepository.kt
interface TabRepository {
    fun observeTabs(): Flow<List<Tab>>
    suspend fun createTab(url: String): Result<Tab>     // Result.Error(MaxTabsReachedException) when at cap
    suspend fun switchActiveTab(tabId: Long)            // writes lastActiveAt = now() on the leaving tab
    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)
    suspend fun closeTab(tabId: Long)                   // recreates fresh home tab if last
    suspend fun closeAllTabs()                          // atomic wipe + single fresh home tab
    suspend fun getTabCount(): Int
}

// domain/usecase/CreateTabUseCase.kt
class CreateTabUseCase @Inject constructor(
    private val repository: TabRepository,
    @param:Named("default_home_url") private val homeUrl: String,
) {
    suspend operator fun invoke(url: String = homeUrl): Result<Tab> = repository.createTab(url)
}
```

`BrowserViewModel.onUrlChanged` and `onLoadFinished` write through `UpdateActiveTabUrlAndTitleUseCase` immediately so persistence reflects the live URL trace (FR-022). The existing synchronous return contract for `onAddressBarSubmit` (Boolean for focus/keyboard release per Spec 009 FR-013a) is preserved unchanged.

### Quickstart

See [quickstart.md](quickstart.md) for the full verification checklist. The 5 manual user-device gates align with US1 (multi-tab + switcher), US2 (process-death restore), US3 (close + close-all), SC-004 (max-tabs limit + 8-locale message), and SC-010 (50-tab cold-start budget).

### Agent context update

The `<!-- SPECKIT START -->` / `<!-- SPECKIT END -->` block in [CLAUDE.md](../../CLAUDE.md) will be updated by this command to point to Spec 011's plan.md (replacing the Spec 010 reference). See [agent context update step](#step-3-update-agent-context-claudemd) below.

## Constitution Check (post-design)

| # | Principle | Status | Re-check note |
|---|-----------|--------|----------------|
| I | Privacy & Security First | ✅ PASS | Design unchanged from pre-check. All tab data on-device. |
| II | Google Play Compliance | ✅ PASS | Confirmed: `Modifier.combinedClickable` is a public Compose `foundation` API gated by `@OptIn(ExperimentalFoundationApi::class)` — not a third-party unstable API. |
| III | Code Quality & Safety | ✅ PASS | All literals confirmed extracted to constants / theme tokens / strings. Detekt baseline expected unchanged. |
| IV | Clean Architecture | ✅ PASS w/ documented exception (R4) | The Repository → Repository concern is resolved by the use-case coordination layer (`UpdateActiveTabUrlAndTitleUseCase`). No direct cross-repository dependency in `data/`. See Complexity Tracking. |
| V | Performance Excellence | ✅ PASS | Active-tab Flow + LazyVerticalGrid stable keys + single-WebView strategy give predictable cold-start + memory profile. |
| VI | Testing Discipline | ✅ PASS | 25 unit + 3 instrumented tests planned; per-layer coverage maintained. |
| VII | Offline-First Architecture | ✅ PASS | All writes go through Room WAL (Spec 005). Atomic semantics via `suspend` DAO. |
| VIII | Localization & Accessibility | ✅ PASS | 6 keys × 8 locales planned; TalkBack semantics + 48dp touch targets covered. |
| IX | Dependency Currency & 16KB | ✅ PASS | Zero new packages confirmed in design. |
| X | Simplicity & Build Order | ✅ PASS | Design respects Spec 012 boundary (no incognito work) and Spec 016 boundary (no settings UI for tabs). |
| XI | Build Configuration | ✅ PASS | Design unchanged. |

**Result**: 11/11 PASS post-design.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Cross-feature use-case coordination — `UpdateActiveTabUrlAndTitleUseCase` is invoked from `BrowserViewModel` (presentation layer of the `browser/` feature) but writes to `TabRepository` (data layer of the `tabs/` feature). | The active tab's URL/title MUST be persisted in real time as the WebView reports them, otherwise process-death restoration loses the live URL trace and falls back to whatever was last persisted (could be the home page even after the user navigated to N other URLs). The bridging logic has to live somewhere; the choices are (a) cross-feature use case (chosen), (b) BrowserViewModel injects TabRepository directly (Constitution §IV violation — ViewModel-to-Repository is allowed but cross-feature ViewModel-to-Repository is the exact "feature isolation" smell), (c) shared `BrowserAndTabsViewModel` (couples two unrelated UI surfaces). | (a) Use-case coordination is the project precedent — Spec 010 documented `SearchEngineRepositoryImpl → SettingsRepository` as an accepted exception under the same Repository-Coordination intent. Constitution §IV explicitly says "Repository MUST NOT depend on other Repositories" and "UseCase pattern MUST be used for all business logic" — this entry honors both: TabRepositoryImpl depends only on TabDao + DispatcherProvider + the home-URL constant, and the cross-feature glue lives at the use-case layer where coordination is explicitly permitted. (b) Was rejected because it would make `BrowserViewModel` co-dependent on the tabs feature surface, breaking the "feature isolation enables incremental implementation" Constitution §IV rationale. (c) Was rejected because the two features have orthogonal lifecycles (BrowserScreen is the WebView host, TabsScreen is the switcher) and a shared ViewModel would conflate them. The PR body MUST link to this row so the reviewer ack is on the merge record (mirrors Spec 010 PR convention). |

---

### Step 3: Update agent context (CLAUDE.md)

The Spec 010 reference inside the `<!-- SPECKIT START -->` … `<!-- SPECKIT END -->` block of [CLAUDE.md](../../CLAUDE.md) will be replaced with a Spec 011 reference (active spec switched, scope summary updated, suggested next command updated to `/speckit-tasks`). This is performed inline by `/speckit-plan` and is the final step before reporting completion.
