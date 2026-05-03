# Research: Tabs Management (Spec 011)

**Date**: 2026-05-03 | **Branch**: `011-tabs-management`

This document records the technical decisions made during Phase 0 planning. Each item is a discrete decision with: **Decision** (what was chosen) / **Rationale** (why) / **Alternatives considered** (what else was evaluated). Spec 011 has 12 R-items.

---

## R1 — Active-tab pointer mechanism

**Decision**: The active tab is **derived** at query time as `MAX(last_active_at)` over the `tabs` table; in-session, switches mutate `lastActiveAt = System.currentTimeMillis()` on the about-to-leave tab BEFORE writing, so the leaving tab is always the most-recently-active among the non-currently-active set. No new column. No Room migration.

**Rationale**:
- Avoids a Room v1 → v2 schema migration. Spec 005 deliberately did NOT ship `is_incognito` precisely to keep v1 schema-stable until v1.0; introducing `is_active` here would force a migration for a single Boolean.
- The derived form is read-cheap: 50 rows × indexed `id` → sub-millisecond on Robolectric SQLite. Real-device cost is negligible.
- The "MAX(last_active_at)" semantics implicitly makes `last_active_at` doubly useful: (a) the active-tab pointer source of truth, (b) the switcher's most-recently-active-first ordering signal (FR-014). Both behaviors fall out of one column.
- In-session: when the user is on tab A and taps tab B's card, the use case writes `tabA.last_active_at = now()` first (so A becomes the most-recently-active *non-current*), then sets `tabB.last_active_at = now() + 1` (or simply `now()` after a coroutine yield) so B becomes the new max. Tie-breaks on `id` ASC if two writes hit the same millisecond — acceptable because the second-tap wins reliably (Room's `suspend` DAO serializes per-coroutine).

**Alternatives considered**:
- **Add `is_active: Boolean` column**: Forces Room v1 → v2 migration for a single field. Rejected — Spec 005's strict-no-destructive policy + the explicit "TabEntity reuse no-migration" assumption (A1) in spec.md commits us to the existing schema.
- **Persist active-tab ID in DataStore Preferences**: Splits the source of truth across two storage layers (Room for tab list, DataStore for active pointer). Race condition risk on cold start (one finishes loading before the other → which is correct?). Rejected — single source of truth wins.
- **In-memory active-tab pointer in `TabsViewModel` only, no persistence**: Forgets the active tab on process death. Fails US2 acceptance scenario #1 (the second-tab-active force-stop test). Rejected.

---

## R2 — Tab switcher = NavController route vs modal overlay

**Decision**: **NavController route** — reuse the existing `AppDestination.Tabs` route already wired in [AppNavGraph.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt:23) and [AppDestination.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppDestination.kt:6). The existing `TabsScreen.kt` placeholder Composable is rewritten in this spec.

**Rationale**:
- `AppDestination.Tabs` already exists in the project skeleton (Spec 002 reserved it). Honoring the existing route name + ViewModel pattern matches every other Phase-3 / Phase-4 screen (Bookmarks, History, Downloads, Settings) which are all routes, not overlays.
- Predictive back semantics are native to NavController — no manual `BackHandler` registration needed for the switcher's back. The switcher inherits Android 14+ predictive-back animation for free.
- The switcher screen can host its own top-bar tailored for switcher-specific affordances (close-all action button + a plural-aware count title per FR-015), without competing with `BrowserScreen`'s nav bar slot.
- No additional `composable(...)` registration cost — the line is already in `AppNavGraph`.

**Alternatives considered**:
- **Modal overlay inside `BrowserScreen`** (e.g., `AnimatedVisibility(showSwitcher)`): Pros — keeps `BrowserScreen` bottom bar visible, simpler back handler (just toggle `showSwitcher = false`). Cons — more state to manage in `BrowserViewModel`, breaks the project pattern where every other major screen is a route, harder to write isolated instrumented tests for the switcher (couples switcher tests to BrowserScreen + WebView setup). Rejected.
- **Bottom sheet** (`ModalBottomSheet`): Cons — too small for a 50-tab grid, awkward landscape ergonomics. Rejected.

---

## R3 — Tab thumbnail rendering (deterministic placeholder algorithm)

**Decision**: Each `TabSwitcherCard` renders a 16:9 (or 1:1, finalized in implementation) preview area as a solid background color + a foreground first-letter glyph. Both are derived deterministically from the tab's hostname. Pure-Kotlin function `TabPlaceholderColor.forHostname(hostname: String): TabPlaceholderStyle` returns a `(palette role index, glyph: Char)` pair; the Composable then resolves the palette role into an actual `Color` via `MaterialTheme.colorScheme` at draw time.

```kotlin
data class TabPlaceholderStyle(val paletteRoleIndex: Int, val glyph: Char)
object TabPlaceholderColor {
    private const val PALETTE_SIZE = 8
    fun forHostname(hostname: String): TabPlaceholderStyle {
        val hash = hostname.hashCode()
        val index = (hash.absoluteValue % PALETTE_SIZE)
        val glyph = hostname.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
        return TabPlaceholderStyle(paletteRoleIndex = index, glyph = glyph)
    }
}
```

The Composable maps `paletteRoleIndex` 0..7 to one of: `colorScheme.primary`, `secondary`, `tertiary`, `surfaceVariant`, `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `inversePrimary`. Glyph foreground uses the matching `onPrimary` / `onSecondary` / `onTertiary` / `onSurfaceVariant` / `onPrimaryContainer` / `onSecondaryContainer` / `onTertiaryContainer` / `inverseOnSurface` role for guaranteed WCAG-AA contrast (the M3 token system already enforces 4.5:1 for body text). **Excluded `error` + `errorContainer`** to avoid implying a tab has a failure state — semantic-warning colors are reserved for actual error UI per Material Design guidance.

**Rationale**:
- Deterministic — same hostname always renders the same placeholder across sessions and across devices. Visually stable for users who recognize their tabs by color.
- Constitution §III compliant — NO inline `Color(0xFF...)` literals; the function returns an INDEX into the theme palette, and the actual `Color` is resolved through `MaterialTheme.colorScheme.*` at draw time. Indexing into a palette is consistent with M3 design system idioms.
- Pure function — unit-testable without Compose. `TabPlaceholderColorTest` covers: deterministic output for the same input, fallback `'?'` glyph for non-alphanumeric hostnames, palette index always in `0..7`.
- Zero image / drawable resource cost — the placeholder is rendered with `Box(background = color) + Text(glyph, style = headlineMedium)` Composables.

**Alternatives considered**:
- **Material You dynamic color seeding from hostname**: Generate a unique color per hostname using `Color.fromHsv(hue = hash % 360, saturation = 0.5f, value = 0.8f)`. Rejected — bypasses the M3 theme palette (could clash with user's dynamic color on Android 12+) and violates Constitution §III "Colors MUST use `MaterialTheme.colorScheme.*`".
- **Random color per session**: Rejected — non-deterministic; user perception of "my Twitter tab is the blue one" breaks across sessions.
- **Favicon fetch + cache**: Out of scope (would add network surface + cache invalidation + Coil dependency). Deferred to post-v1.0 enhancement.

---

## R4 — Repository → Repository concern (BrowserViewModel write-through to TabRepository)

**Decision**: Avoid a direct `Repository → Repository` chain in `data/`. The bridge from `BrowserViewModel` WebView callbacks (`onUrlChanged`, `onLoadFinished`) into `TabRepository` lives at the **use-case layer** (`UpdateActiveTabUrlAndTitleUseCase`). `BrowserViewModel` injects this use case (one of several it now injects); `TabRepositoryImpl` itself depends only on `TabDao` + `DispatcherProvider` + the `@Named("default_home_url")` constant for empty-state recovery (R5).

**Rationale**:
- Constitution §IV explicitly states: **"UseCase pattern MUST be used for all business logic; Repositories MUST NOT depend on other Repositories"**. Inverting this is straightforward — the cross-feature glue lives at the use-case layer where Constitution §IV permits it.
- Spec 010 set the same precedent: `SearchEngineRepositoryImpl` depends on `SettingsRepository` only because the engine choice is settings-state — but Spec 010's plan documented this as an accepted exception. Spec 011 sidesteps that exception entirely by routing the cross-feature dependency through the use-case layer (cleaner posture).
- `BrowserViewModel` injects three new use cases: `ObserveActiveTabUseCase` (read, for cold-start URL seeding), `UpdateActiveTabUrlAndTitleUseCase` (write, on URL/title changes), `CreateTabUseCase` (write, on long-press new tab from the bottom bar — R6). Hilt `@HiltViewModel` constructor injection handles the wiring; BrowserViewModel never sees `TabRepository`.

**Alternatives considered**:
- **`BrowserViewModel` injects `TabRepository` directly**: Cons — couples the `browser/` presentation layer to the `tabs/` data layer, violates feature isolation. Rejected.
- **Shared "session" service in `core/` that both ViewModels read**: Cons — adds a third stateful component for no clear benefit; the use-case layer is already this seam. Rejected.
- **Event bus / shared `Flow` between the two ViewModels**: Cons — implicit coupling, harder to test. Rejected.

---

## R5 — Empty-state auto-create-home-tab (FR-019)

**Decision**: Inside `TabRepositoryImpl`. On every cold start, the first observation of `tabDao.observeAll()` checks `if (tabDao.count() == 0)`; if so, performs a one-time atomic insert of a fresh home tab with the `@Named("default_home_url")` URL. Guarded by a `Mutex` to prevent races between concurrent `observeTabs()` collectors. Same logic on the last-tab-close path: `CloseTabUseCase` (or `TabRepositoryImpl.closeTab`) checks the post-close count; if it would drop to 0, performs the close + insert in the same coroutine (Room's `@Transaction` annotation keeps it atomic at the DAO level).

```kotlin
// Pseudo-code, simplified
override fun observeTabs(): Flow<List<Tab>> = tabDao.observeAll()
    .onStart { ensureAtLeastOneTab() }
    .map { entities -> entities.map { it.toDomain() } }

private suspend fun ensureAtLeastOneTab() = mutex.withLock {
    if (tabDao.count() == 0) {
        tabDao.insert(TabEntity(url = homeUrl, title = "", position = 0, createdAt = now(), lastActiveAt = now()))
    }
}
```

**Rationale**:
- The auto-create rule (FR-019) is an invariant of the tab system: "always ≥ 1 tab". Putting it inside the repository centralizes the invariant — every consumer (TabsViewModel, BrowserViewModel) sees a non-empty list without each having to handle the empty case.
- `Mutex.withLock` is sufficient because all `TabDao` writes are `suspend` functions; coroutine concurrency is tractable. The only race is two simultaneous cold-start collectors, and the lock serializes them.
- Empty `title` on the just-seeded tab is fine — the `WebViewClient.onReceivedTitle` callback fires soon after page load and updates it via `UpdateActiveTabUrlAndTitleUseCase`. Until then, the display layer falls back to "New tab" (R11).

**Alternatives considered**:
- **`TabsViewModel` checks empty state on first emission**: Pros — keeps repository thin. Cons — every consumer (including `BrowserViewModel` via `ObserveActiveTabUseCase`) would need the same check; cleanup duplicated. Rejected.
- **`Application.onCreate()` seeds the first tab**: Cons — runs on every cold start, always. We only want to seed when truly empty, which requires a Room read anyway. Rejected.
- **Migration step in `AppDatabase`**: Cons — Spec 005 explicitly forbids destructive migrations. The seed isn't a migration, it's a runtime invariant. Rejected.

---

## R6 — `NavigationBottomBarCallbacks` expansion (4 → 6 fields)

**Decision**: Spec 011 adds 2 fields to the existing 4-field `data class`: `onTabsSwitcherClick` and `onTabsSwitcherLongClick`. Bundle becomes 6 — exactly at detekt's `LongParameterList.functionThreshold = 6` (PASSES; threshold is "longer than 6 fails"). The 5th `IconButton` in `NavigationBottomBar.kt` uses `Modifier.combinedClickable(onClick = ..., onLongClick = ...)` requiring a file-level `@OptIn(ExperimentalFoundationApi::class)`.

```kotlin
// presentation/browser/components/NavigationBottomBarCallbacks.kt — after Spec 011
data class NavigationBottomBarCallbacks(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onHome: () -> Unit,
    val onTabsSwitcherClick: () -> Unit,      // NEW Spec 011 — single-tap → navController.navigate(Tabs.route)
    val onTabsSwitcherLongClick: () -> Unit,  // NEW Spec 011 — long-press → CreateTabUseCase + remain on BrowserScreen
)
```

The single-tap callback is hoisted from `BrowserScreen` (which gets the `NavController` passed in from `AppNavGraph` — minor `BrowserScreen` signature change). The long-press callback dispatches a small bridge method on `BrowserViewModel` (`fun onLongPressNewTab() { viewModelScope.launch { createTab() } }`) so `BrowserScreen` does not need to inject `CreateTabUseCase` directly.

**Rationale**:
- Constitution §III + Spec 008's documented `LongParameterList.functionThreshold = 6` → bundle MAY be 6 fields, MUST NOT exceed. Two new fields fit exactly.
- `combinedClickable` is the standard Compose primitive for "single-tap and long-press on the same target". `@OptIn(ExperimentalFoundationApi::class)` is well-documented and used widely in Material catalogs.
- Separating the two callbacks (instead of a single `onTabsAction(action: TabsAction)`) keeps the per-callback intent explicit and avoids a sealed-type ceremony for a 2-case dispatch.

**Alternatives considered**:
- **Carve `TabsButton` into its own Composable component**: Pros — lets `NavigationBottomBar` stay at 4 callbacks. Cons — splits the bottom-bar layout across two files for a single-button addition. Rejected — the existing pattern of "one bar, one callback bundle" is simpler.
- **Single `onTabsAction(action: TabsAction)` with a sealed type**: Pros — keeps the bundle at 5 fields. Cons — adds a sealed type for a 2-case dispatch (over-engineered for this scope). Rejected.
- **Don't add long-press; require user to enter switcher to create a tab** (Q2 Option C from clarification): Already rejected by user — "A" was chosen.

---

## R7 — `BrowserViewModel` ↔ `TabsViewModel` coupling

**Decision**: `BrowserViewModel` does NOT inject `TabsViewModel` or `TabRepository` directly. Instead it injects three new use cases:

1. **`ObserveActiveTabUseCase`** — exposes `Flow<Tab?>` derived as `tabs.maxByOrNull { it.lastActiveAt }`. Used in `BrowserUiState.currentUrl` seeding: `BrowserViewModel.init { observeActiveTab().onEach { tab -> _uiState.update { it.copy(currentUrl = tab?.url ?: defaultHomeUrl) } } }`. The first non-null emission seeds the WebView's initial `loadUrl`.
2. **`UpdateActiveTabUrlAndTitleUseCase`** — write-through. `onUrlChanged(url)` → `viewModelScope.launch { updateActiveTab(url, currentTitle) }`. `onLoadFinished(url)` → similar with the now-final title from `WebView.title`.
3. **`CreateTabUseCase`** — long-press new tab. `onLongPressNewTab()` → `viewModelScope.launch { createTab(homeUrl) }` then surfaces `Result.Error(throwable = MaxTabsReachedException)` as a one-shot `BrowserUiState.tabsEvent` for the snackbar.

`TabsViewModel` (the switcher's ViewModel) is independent — `BrowserViewModel` and `TabsViewModel` never directly call each other; they coordinate via the shared Room source-of-truth.

**Rationale**:
- Hilt's `@HiltViewModel` constructor injection handles the wiring without scope leakage.
- Each use case is small, single-purpose, and unit-testable in isolation with a `FakeTabRepository`.
- The coupling is one-way: `BrowserViewModel` is a write-through client of the tab state; `TabsViewModel` is the read+command client. They don't need to know about each other.

**Alternatives considered**:
- **Shared `BrowserAndTabsViewModel`**: Rejected — couples two unrelated UI surfaces, breaks feature isolation.
- **Activity-scoped shared state via Hilt `@ActivityRetainedScoped`**: Pros — single source of in-memory state. Cons — over-engineered for this; Room is already the source of truth and `Flow` already gives reactive coordination. Rejected.

---

## R8 — Inactive-tab WebView lifecycle (FR-027 / A6)

**Decision**: One `WebView` instance lives at any time — the active tab's. On tab switch (`SwitchActiveTabUseCase` writes new `lastActiveAt`), the active-tab `Flow` re-emits with the new `Tab`, `BrowserScreen` recomposes; a `LaunchedEffect(activeTab.id)` fires `webViewActions.loadUrl(activeTab.url)`. The previous `WebView` instance is destroyed via Spec 007's existing `DisposableEffect(Unit) { onDispose { ... } }` cleanup when the `BrowserWebView` Composable's `key` changes.

To trigger the dispose, `BrowserWebView`'s `AndroidView { factory = ... }` is wrapped with `key(activeTab.id) { BrowserWebView(...) }` so Compose disposes the old instance and instantiates a fresh one when the key changes. The factory's `WebViewClient` lockdown (Spec 007 settings) re-applies on every instantiation — same WebView posture for every tab.

**Rationale**:
- Memory-bounded — at most one `WebView` regardless of tab count. 50 tabs × 0 inactive WebViews = baseline memory cost.
- Predictable behavior — every tab activation reloads from URL. No stale DOM, no leaked listeners across switches.
- Lockdown re-application is automatic — Spec 007's factory body runs on every fresh instantiation, so settings always match.

**Alternatives considered**:
- **LRU cache of recent WebViews** (e.g., last 3): Pros — preserves scroll/form state for the most-recently-used tabs. Cons — meaningfully more code (cache eviction, lifecycle coordination), memory overhead, harder to reason about state transitions. Rejected for v1.0; if user feedback demands it, a follow-up spec ships the cache.
- **Single WebView, swap URL via `loadUrl()` only** (no key-based dispose): Cons — the old tab's session state (cookies are shared, but in-page JS state, scroll, form input) leaks across switches. Worse, security: a malicious page could attempt to phish-swap before disposal. Rejected.
- **Per-tab `WebView` always alive (instantiate on tab create, keep until close)**: Pros — instant switch, full state preservation. Cons — 50 tabs × N MB per WebView = OOM on min-spec devices (Android 7 / 2GB RAM). Rejected.

---

## R9 — Process-death restoration test strategy

**Decision**: Use Robolectric-backed Hilt instrumented test with `androidx.test.core.app.ActivityScenario.recreate()` after seeding 3 tabs via a Hilt-injected `TabRepository` test override. Asserts: post-recreate, `ObserveTabsUseCase` emits the same 3 tabs in the same order, the active tab matches the pre-kill active tab.

```kotlin
// Pseudo
@HiltAndroidTest
@UninstallModules(TabsModule::class)
class TabPersistenceProcessDeathTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Before fun seed() = runTest { repository.createTab("https://a.example.com"); ... }

    @Test fun recreate_preserves_tabs_and_active() = runTest {
        val before = repository.observeTabs().first()
        ActivityScenario.launch(MainActivity::class.java).recreate()
        val after = repository.observeTabs().first()
        assertThat(after).isEqualTo(before)
        assertThat(after.maxBy { it.lastActiveAt }.id).isEqualTo(before.maxBy { it.lastActiveAt }.id)
    }
}
```

`ActivityScenario.recreate()` is the closest portable proxy for "Android killed and re-launched the activity". True process-death (Linux SIGKILL) is not testable in JVM — that goes to the manual user-device gate (US2 Independent Test).

**Rationale**:
- Robolectric is already wired (Spec 005). The test runs on JVM in CI, no emulator required for daily PR feedback.
- `ActivityScenario.recreate()` validates the same code path that handles config-change-triggered recreation, which exercises `BrowserViewModel.init { observeActiveTab() }` re-collection from a fresh ViewModel instance.

**Alternatives considered**:
- **Simulate process-death via `instrumentation.runOnMainSync { activity.finish() }` + relaunch**: Pros — closer to true death. Cons — flaky on Robolectric, requires emulator for reliable behavior. Rejected for the unit-CI gate; reserved for the manual gate.
- **Pure unit test on `TabRepository`**: Already covered in `TabRepositoryImplTest`. R9 specifically asserts the **end-to-end** restoration through Hilt graph + ViewModel, so unit-level alone is insufficient.

---

## R10 — Hilt module placement (`TabsModule`)

**Decision**: New file `app/.../di/TabsModule.kt`. Sibling to `SettingsModule.kt` (Spec 006) and `SearchEngineModule.kt` (Spec 010). Single `@Binds` line for `TabRepository` impl; `@InstallIn(SingletonComponent::class)`; `@Singleton` scope (matches `SettingsRepository` / `SearchEngineRepository`).

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class TabsModule {
    @Binds @Singleton
    abstract fun bindTabRepository(impl: TabRepositoryImpl): TabRepository
}
```

**Rationale**:
- Project pattern — one module per feature surface, all under `app/.../di/`. Sibling files keep the module set discoverable.
- `@Singleton` scope — `TabRepositoryImpl` holds an internal `Mutex` for the empty-state seeding (R5); a non-singleton scope would spawn multiple mutexes and races. Same scope as `SettingsRepository`.
- No new `Provides`-style providers needed — only the `@Binds` for the interface-to-impl mapping. `TabDao` is provided by Spec 005's `DatabaseModule`. `DispatcherProvider` is provided by Spec 002's `core/di/DispatcherModule`. The home-URL constant is provided by Spec 007's `UrlConfigModule`.

**Alternatives considered**:
- **Add to `DatabaseModule`** (Spec 005): Cons — `DatabaseModule` is for Room providers; adding the repository binding would conflate concerns. Rejected.
- **Generic `RepositoryModule` for all bindings**: Cons — would centralize all repository bindings, harder to find on-demand; one-module-per-feature is the project precedent. Rejected.

---

## R11 — Tab title fallback ("New tab") string key

**Decision**: New string key `tabs_default_title` localized in all 8 locales:

| locale | value |
|--------|-------|
| en | New tab |
| vi | Tab mới |
| de | Neuer Tab |
| ru | Новая вкладка |
| ko | 새 탭 |
| ja | 新しいタブ |
| zh | 新标签页 |
| fr | Nouvel onglet |

Used at the **display layer** (`TabSwitcherCard`), NOT at write-time. Rationale: the persisted `TabEntity.title` may legitimately be empty (just-created tab before `WebViewClient.onReceivedTitle` fires). Treating empty as "fall back to the localized 'New tab' string" keeps the persistence model simple — no special sentinel string in the database.

**Rationale**:
- Constitution §VIII — all user-facing strings externalized before any feature code uses them.
- Translations are short and unambiguous across locales; verified against Chrome / DuckDuckGo / Firefox conventions for each language.

**Alternatives considered**:
- **Hostname as title fallback** (e.g., empty title → display the URL's hostname): Pros — more informative when the page hasn't reported a title yet. Cons — for the fresh home tab (`https://www.google.com/`), the hostname `www.google.com` is less helpful than "New tab" before the page loads. Rejected as the **default**, but the card layout already shows the hostname as a secondary line — both are visible in practice.
- **Persist `title = "New tab"` literal at create time**: Cons — that literal would then appear in the wrong locale if the user changes language, breaks Constitution §III ("hardcoded UI strings"). Rejected.

---

## R12 — Test fakes (`FakeTabRepository` pattern)

**Decision**: Hand-rolled `FakeTabRepository` class implementing `TabRepository`, backed by a `MutableStateFlow<List<Tab>>` for `observeTabs()` and an inline `MutableList<Tab>` for state. Lives under `app/src/test/.../testdoubles/` (NEW package). Reused by `TabsViewModelTest` and `TabUseCasesTest`.

```kotlin
// Pseudo-code
class FakeTabRepository : TabRepository {
    private val state = MutableStateFlow<List<Tab>>(emptyList())
    private var nextId = 1L
    override fun observeTabs() = state.asStateFlow()
    override suspend fun createTab(url: String): Result<Tab> {
        if (state.value.size >= MAX_TABS) return Result.Error(MaxTabsReachedException())
        val tab = Tab(id = nextId++, url = url, title = "", position = state.value.size, createdAt = now(), lastActiveAt = now())
        state.update { it + tab }
        return Result.Success(tab)
    }
    // ... other methods
    fun emit(tabs: List<Tab>) { state.value = tabs }  // test-only seeding helper
}
```

**Rationale**:
- Pattern matches Spec 010's `FakeSearchEngineRepository`. Same precedent.
- Hand-rolled fakes are easier to reason about than Mockito/MockK setups for a 7-method interface; explicit state + explicit reset.
- `MutableStateFlow` gives free `Flow` semantics matching production behavior (warm/replay-1).
- Reusable via the new `testdoubles` package — `TabUseCasesTest` and `TabsViewModelTest` share the same fake.

**Alternatives considered**:
- **MockK-based fake**: Pros — less boilerplate. Cons — opaque mocking syntax; harder for new readers; needs setup blocks per test. Rejected — project convention is hand-rolled.
- **Use `TabRepositoryImpl` with an in-memory Room DB**: Pros — exercises the real impl. Cons — slower, requires Robolectric for `Context`. Better as a `TabRepositoryImplTest` integration test (already planned), separate from the ViewModel/UseCase unit tests.

---

## Summary

All 12 R-items resolve cleanly. No remaining `[NEEDS CLARIFICATION]` markers. Spec is implementation-ready pending `/speckit-tasks` to break the work into tasks.
