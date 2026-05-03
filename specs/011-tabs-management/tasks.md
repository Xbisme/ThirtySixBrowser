---

description: "Task list for Spec 011 — Tabs Management"
---

# Tasks: Tabs Management (Spec 011)

**Input**: Design documents from `specs/011-tabs-management/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/TabRepository.md](contracts/TabRepository.md), [contracts/TabsViewModel.md](contracts/TabsViewModel.md), [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md), [contracts/NavigationBottomBar.md](contracts/NavigationBottomBar.md), [quickstart.md](quickstart.md)

**Tests**: Tests are INCLUDED — Constitution §VI mandates unit tests for all business logic + spec.md SC-006 calls for "at least 25 new unit tests" + 3 new instrumented tests. Spec 010 baseline 162/162 → ~187 expected after this spec.

**Organization**: Tasks are grouped by user story (US1–US3) so each story can be implemented and tested independently. The auto-create-on-empty rule (R5 / FR-019) and the entire data + domain layer are foundational because all 3 stories depend on them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks — safe to run in parallel
- **[Story]**: `[US1]`–`[US3]` for tasks tied to a specific user story; absent for Setup / Foundational / Polish

## Path Conventions

Single-module Android app. Paths below are relative from repo root:

- Production source: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Unit tests: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Instrumented tests: `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/values/` (EN baseline) + `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the spec-011 branch is current and add the cross-cutting constants + string resources every later phase reads from.

- [x] T001 Verify on branch `011-tabs-management` with clean working tree: run `git status` and `git rev-parse --abbrev-ref HEAD`; abort if dirty (apart from this spec's own `specs/011-tabs-management/` files) or on a different branch.
- [x] T002 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt) per [data-model.md Entity 5](data-model.md#entity-5--browserlimits-new-core-constant-file). `object BrowserLimits { const val MAX_TABS: Int = 50 }` with the full KDoc block from data-model.md including the Constitution §III table reference + the rationale for 50 (mid-range Android browser cap, predictable memory on min-spec devices given Spec 011's single-active-WebView strategy).
- [x] T003 [P] Add new string + plurals resources to all 8 locale files. EN baseline at [app/src/main/res/values/strings.xml](../../app/src/main/res/values/strings.xml); translations at `values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`. **Singular `<string>` keys** + EN values:
  - `tabs_switcher_new_tab` = `New tab`
  - `tabs_switcher_close_all` = `Close all tabs`
  - `tabs_switcher_close_all_confirm` = `Close all tabs? This cannot be undone.`
  - `tabs_card_close` = `Close tab`
  - `tabs_default_title` = `New tab` (per R11 — fallback when WebView title not yet known)
  - `browser_action_tabs_switcher` = `Open tabs switcher`
  - `browser_action_new_tab` = `Open new tab`
  - `browser_max_tabs_reached` = `Maximum number of tabs reached. Close a tab to open a new one.`

  **Plural resource** (M4 — FR-015 real-time count surface for the switcher's TopAppBar title):
  - `<plurals name="tabs_switcher_title_count">` with EN items `<item quantity="one">%1$d tab</item>` + `<item quantity="other">%1$d tabs</item>`. Each locale provides its own quantity keys per Android's plural rules (e.g., RU has `one/few/many/other`, VI has `other` only, AR/PL have richer rule sets). Translators follow CLDR plural categories per locale.

  Translations per [research R11](research.md#r11--tab-title-fallback-new-tab-string-key) for the `tabs_default_title` row; translate the other singular keys + the plural following the same brand-rule precedent from Spec 004. Lint `MissingTranslation` from Spec 004 enforces completeness — `./gradlew lintDebug` after this task MUST pass.

**Checkpoint**: Constants + strings ready. Foundational phase can begin.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Stand up the entire domain + data + Hilt slice for `TabRepository`, the 7 use cases, the mapper, the test fakes, and the unit tests for those layers. ALL 3 user stories read from these — without them, no story phase can begin.

**⚠️ CRITICAL**: No user story phase may begin until this phase is complete and tests pass.

### Domain layer

- [x] T004 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Tab.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Tab.kt) per [data-model.md Entity 2](data-model.md#entity-2--tab-domain-model-new). Pure-Kotlin `data class Tab(id: Long, url: String, title: String, position: Int, createdAt: Long, lastActiveAt: Long)`. Zero Android imports.
- [x] T005 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/TabRepository.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/TabRepository.kt) per [contracts/TabRepository.md](contracts/TabRepository.md). 7-method `interface TabRepository` — `createTab(url): Result<Tab>` returns project's existing `Result<T>` from Spec 002 (`core/result/Result.kt` — single type param, `Result.Error(throwable, message)` carries the exception). In the same file, declare the sentinel: `class MaxTabsReachedException : Exception("Maximum number of tabs reached")` — consumers detect cap-reached via `(result as? Result.Error)?.throwable is MaxTabsReachedException`. NO new `Result<T, E>` two-param shape, NO new `sealed class TabError`. Imports: `kotlinx.coroutines.flow.Flow`, `com.raumanian.thirtysix.browser.core.result.Result`, project domain types.

### Use cases (depend on T005)

- [x] T006 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveTabsUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveTabsUseCase.kt). `class @Inject constructor(private val repository: TabRepository) { operator fun invoke(): Flow<List<Tab>> = repository.observeTabs() }`. KDoc cites Spec 011 + the auto-seed-on-empty invariant (FR-019 / R5).
- [x] T007 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveActiveTabUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveActiveTabUseCase.kt). `class @Inject constructor(private val observeTabs: ObserveTabsUseCase) { operator fun invoke(): Flow<Tab?> = observeTabs().map { it.maxByOrNull(Tab::lastActiveAt) } }`. KDoc cites R1 (active-tab pointer derivation).
- [x] T008 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CreateTabUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CreateTabUseCase.kt). `class @Inject constructor(private val repository: TabRepository, @param:Named("default_home_url") private val homeUrl: String) { suspend operator fun invoke(url: String = homeUrl): Result<Tab> = repository.createTab(url) }`. Returns project's existing `Result<T>` (Spec 002); cap-reached failures arrive as `Result.Error(throwable = MaxTabsReachedException, ...)`. KDoc cites FR-016 cap-guard + the BrowserScreen long-press path from Q2 / R6 + the `MaxTabsReachedException` sentinel detection pattern.
- [x] T009 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SwitchActiveTabUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SwitchActiveTabUseCase.kt). `class @Inject constructor(private val repository: TabRepository) { suspend operator fun invoke(tabId: Long) = repository.switchActiveTab(tabId) }`. KDoc cites R1 last-active-write semantics.
- [x] T010 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseTabUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseTabUseCase.kt). `class @Inject constructor(private val repository: TabRepository) { suspend operator fun invoke(tabId: Long) = repository.closeTab(tabId) }`. KDoc cites US3 #3 last-tab auto-recreate path.
- [x] T011 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseAllTabsUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CloseAllTabsUseCase.kt). `class @Inject constructor(private val repository: TabRepository) { suspend operator fun invoke() = repository.closeAllTabs() }`. KDoc cites US3 #4 atomic-wipe-and-seed semantics.
- [x] T012 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/UpdateActiveTabUrlAndTitleUseCase.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/UpdateActiveTabUrlAndTitleUseCase.kt). `class @Inject constructor(private val repository: TabRepository) { suspend operator fun invoke(tabId: Long, url: String, title: String) = repository.updateTabUrlAndTitle(tabId, url, title) }`. KDoc cites the BrowserViewModel write-through bridge from R4.

### Data layer (depend on T004 + T005)

- [x] T013 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapper.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapper.kt) per [data-model.md "Mapper"](data-model.md#mapper--tabmapper-new). Top-level functions `fun TabEntity.toDomain(): Tab` + `fun Tab.toEntity(): TabEntity`. Sibling to existing `SettingsMapper.kt` from Spec 006 — same top-level extension-function pattern, no new convention introduced.
- [x] T014 Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImpl.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImpl.kt) per [contracts/TabRepository.md § Implementation contract](contracts/TabRepository.md#implementation-contract-tabrepositoryimpl). Constructor: `(private val tabDao: TabDao, @param:Named("default_home_url") private val homeUrl: String, private val dispatchers: DispatcherProvider)`. Internal `private val seedMutex = Mutex()`. Implement all 7 methods per the contract:
  - `observeTabs()` returns `tabDao.observeAll().onStart { ensureAtLeastOneTab() }.map { entities -> entities.map(TabEntity::toDomain).sortedWith(compareByDescending(Tab::lastActiveAt).thenBy(Tab::id)) }.flowOn(dispatchers.io)`. The `sortedWith(...)` step enforces FR-014 ordering (lastActiveAt DESC, id ASC tiebreak) — Spec 005's `TabDao.observeAll()` returns position-ordered rows, so the resort here is required and intentional. Cheap for ≤ 50 rows.
  - `createTab(url)` cap-checks `tabDao.count() >= BrowserLimits.MAX_TABS` BEFORE insert; on cap-reached returns `Result.Error(MaxTabsReachedException(), message = null)`; else inserts with `position = (maxPosition() ?: -1) + 1`, `createdAt = lastActiveAt = now()`, returns `Result.Success(insertedTab.toDomain())`
  - `switchActiveTab(tabId)` reads + updates `lastActiveAt = now()` via `tabDao.update(...)`
  - `updateTabUrlAndTitle(tabId, url, title)` reads + updates url+title (leaves lastActiveAt unchanged)
  - `closeTab(tabId)` deletes; post-delete `if (tabDao.count() == 0) ensureAtLeastOneTab()` inside `seedMutex.withLock`
  - `closeAllTabs()` `seedMutex.withLock { tabDao.deleteAll(); tabDao.insert(freshHomeTab()) }`
  - `getTabCount()` returns `tabDao.count()`
  - Private `ensureAtLeastOneTab()` mutex-guarded (R5)
  - Private `freshHomeTab()` factory: `TabEntity(url = homeUrl, title = "", position = 0, createdAt = now(), lastActiveAt = now())`
  Imports: `androidx.room.*`-bound types via `TabDao` + `TabEntity` from Spec 005, `kotlinx.coroutines.flow.*`, `kotlinx.coroutines.sync.Mutex`, `javax.inject.Inject`, `javax.inject.Named`, project domain types + Spec 002 `DispatcherProvider`.

### Hilt module (depends on T005 + T014)

- [x] T015 Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/di/TabsModule.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/di/TabsModule.kt) per [research R10](research.md#r10--hilt-module-placement-tabsmodule). `@Module @InstallIn(SingletonComponent::class) abstract class TabsModule { @Binds @Singleton abstract fun bindTabRepository(impl: TabRepositoryImpl): TabRepository }`. Sibling to existing `SettingsModule.kt` and `SearchEngineModule.kt`.

### Test infrastructure

- [x] T016 [P] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/testdoubles/FakeTabRepository.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/testdoubles/FakeTabRepository.kt) per [research R12](research.md#r12--test-fakes-faketabrepository-pattern). Hand-rolled `class FakeTabRepository : TabRepository` backed by `MutableStateFlow<List<Tab>>` + `nextId: Long` counter. Implements all 7 interface methods with simple in-memory semantics matching the production contract (cap-guard, last-tab recreate, close-all wipe-and-seed). Adds test-only helpers: `fun emit(tabs: List<Tab>)` for direct seeding, `fun reset()` for between-test cleanup. **NEW package** `testdoubles/` — first time the project introduces this layer; KDoc notes the precedent.

### Foundational tests (test-first; mostly [P])

- [x] T017 [P] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapperTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/TabMapperTest.kt) — 4 tests: (1) `TabEntity.toDomain()` round-trip preserves all 6 fields; (2) `Tab.toEntity()` round-trip preserves all 6 fields; (3) double round-trip `entity.toDomain().toEntity() == entity`; (4) double round-trip `tab.toEntity().toDomain() == tab`. Pure JVM test.
- [x] T018 [P] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/model/TabTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/model/TabTest.kt) — 1 test: `Tab` data-class equality + `copy` with a single-field change produces an unequal but partially-equal instance. Pure JVM test.
- [x] T019 Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImplTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImplTest.kt) — 7 tests per [contracts/TabRepository.md § Test surface](contracts/TabRepository.md#test-surface): `observeTabs_emptyTable_seedsHomeTab`, `observeTabs_orderedByLastActiveDesc`, `createTab_underCap_succeeds`, `createTab_atCap_returnsFailure` (pre-fill to MAX_TABS via direct DAO inserts), `closeTab_lastTab_seedsFreshHome`, `closeAllTabs_atomicWipeAndSeed`, `updateTabUrlAndTitle_writesThrough`. Uses Robolectric (Spec 005 wiring, SDK 33 pin via `app/src/test/resources/robolectric.properties`) + `Room.inMemoryDatabaseBuilder` for the `AppDatabase` + Turbine for Flow assertions. `runBlocking` for the cap-fill setup.
- [x] T020 [P] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/TabUseCasesTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/TabUseCasesTest.kt) — 6 tests, one per use case (excluding `ObserveTabsUseCase` which is a thin pass-through tested implicitly via `ObserveActiveTabUseCase`):
  - `observeActiveTab_returnsMaxByLastActiveAt` — seed FakeTabRepo with 3 tabs, assert use case emits the tab with max `lastActiveAt`
  - `createTab_delegates_returnsRepoResult` — fake returns `Success(tab)`, use case returns same; fake returns `Failure(MaxTabsReached)`, use case propagates
  - `switchActiveTab_delegates` — fake records the call, use case forwards `tabId`
  - `closeTab_delegates` — same delegation pattern
  - `closeAllTabs_delegates` — same
  - `updateActiveTabUrlAndTitle_delegates` — same
- [x] T021 Run `./gradlew assembleDebug testDebugUnitTest` to verify (a) all new files compile, (b) Hilt KSP processes the new `@Binds` without error, (c) all foundational unit tests pass (~17 new tests from T017–T020 added to Spec 010 baseline 162 = ~179), (d) no Spec 005–010 regression. Abort and fix if any failure.

**Checkpoint**: Foundation complete — domain + data + Hilt + test fakes + foundational tests all green. ALL 3 user story phases unblocked.

---

## Phase 3: User Story 1 — Multi-tab + grid switcher (Priority: P1) 🎯 MVP

**Goal**: User can open multiple tabs, see them as cards in a grid switcher screen (the existing `AppDestination.Tabs` route, currently a placeholder), tap a card to switch active, and long-press the 5th BottomAppBar button to open a new home tab without going through the switcher (Q2 shortcut).

**Independent Test**: Quickstart Gate M1 — fresh launch shows 1 home tab; long-press the 5th bottom-bar button → 2nd tab opens; type a different URL into address bar; single-tap the 5th button → switcher screen with 2 cards (active visually distinct); tap the home card → switcher closes, BrowserScreen shows home page; reverse round-trip works; tab-count badge reflects "2" throughout.

### Tests for User Story 1 (write before / alongside implementation)

- [x] T022 [P] [US1] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModelTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModelTest.kt) — 4 US1-scope tests per [contracts/TabsViewModel.md § Test surface](contracts/TabsViewModel.md#test-surface): `initialState_emptyRepo_emitsSeededHomeTab`, `onTabClick_switchesActive`, `onNewTabClick_underCap_addsTab`, `onNewTabClick_atCap_emitsErrorEvent`. Uses `FakeTabRepository` (T016) + manual use-case construction (`CreateTabUseCase(fakeRepo, "https://www.google.com/")`, etc.). `MainDispatcherRule` from kotlinx-coroutines-test for `viewModelScope`. Remaining 4 tests (close + close-all flows) deferred to US3 (T041).
- [x] T023 [P] [US1] Create [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabPlaceholderColorTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabPlaceholderColorTest.kt) — 4 tests for the deterministic placeholder algorithm per [research R3](research.md#r3--tab-thumbnail-rendering-deterministic-placeholder-algorithm): (1) same hostname → same `TabPlaceholderStyle` across calls; (2) `paletteRoleIndex` always in `0..7`; (3) glyph derivation strips non-alphanumeric prefixes (e.g., `"www.example.com"` → `'W'`, not `'.'`); (4) empty / unicode-only hostname → fallback `'?'` glyph. Pure JVM test.
- [x] T024 [P] [US1] Extend [app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) per [contracts/BrowserViewModel.md § Test surface](contracts/BrowserViewModel.md#test-surface-additions-to-existing-browserviewmodeltest) — 3 US1-scope additions: `init_seedsCurrentUrlFromActiveTab`, `onUrlChanged_writesThroughToActiveTab`, `onLoadFinished_writesThroughTitle`. Construct `BrowserViewModel` with the new 5-arg signature: pass a fake `ObserveActiveTabUseCase` that emits a controlled `Tab?` value, fake `UpdateActiveTabUrlAndTitleUseCase` that records calls, and fake `CreateTabUseCase`. The other 3 BrowserViewModel additions (`onLongPressNewTab_*`, `consumeTabsEvent_*`) deferred to US3 (T042). All existing Spec 007/008/009/010 BrowserViewModel tests must still pass after the constructor signature change — update each test's `BrowserViewModel(...)` call to pass no-op fakes for the 3 new params.
- [ ] T025 [P] [US1] Extend [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarTest.kt](../../app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarTest.kt) per [contracts/NavigationBottomBar.md § Test surface](contracts/NavigationBottomBar.md#test-surface) — 4 new component tests: `renders_5_buttons_with_tabs_switcher` (assert all 5 testTags including new `TEST_TAG_NAV_TABS_SWITCHER`), `switcher_singleTap_invokesOnClick`, `switcher_longPress_invokesOnLongClick`, `switcher_badge_reflectsTabCount` (`tabCount=7` → badge text `"7"`). Existing 13 Spec 008 tests must still pass after the bundle expands 4→6 fields — update each test's `NavigationBottomBarCallbacks(...)` call to pass `{}` no-op lambdas for the 2 new fields.

### Implementation for User Story 1

- [x] T026 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsUiState.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsUiState.kt) per [data-model.md Entity 3](data-model.md#entity-3--tabsuistate-new). `data class TabsUiState(tabs: List<Tab> = emptyList(), activeTabId: Long? = null, isCloseAllDialogVisible: Boolean = false, errorEvent: TabsErrorEvent? = null)` + `companion object { val EMPTY = TabsUiState() }` + `sealed class TabsErrorEvent { data object MaxTabsReached : TabsErrorEvent() }` (in same file or a sibling `TabsErrorEvent.kt` — final placement based on detekt `MatchingDeclarationName` rule; if rule fires, split to sibling file).
- [x] T027 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabPlaceholderColor.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabPlaceholderColor.kt) per [research R3](research.md#r3--tab-thumbnail-rendering-deterministic-placeholder-algorithm). `data class TabPlaceholderStyle(paletteRoleIndex: Int, glyph: Char)` + `object TabPlaceholderColor` with `private const val PALETTE_SIZE = 8` and `fun forHostname(hostname: String): TabPlaceholderStyle`. Pure-Kotlin (no Compose imports) so the unit test (T023) runs on JVM. The Composable layer (T028) maps `paletteRoleIndex` → `MaterialTheme.colorScheme.*` role at draw time.
- [x] T028 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherCard.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherCard.kt) — Composable `@Composable fun TabSwitcherCard(tab: Tab, isActive: Boolean, onClick: () -> Unit, onCloseClick: () -> Unit, modifier: Modifier = Modifier)`. Layout: Material3 `Card` with the placeholder area (background color from `MaterialTheme.colorScheme` palette index resolved via `TabPlaceholderColor.forHostname(...).paletteRoleIndex` + glyph rendered with `MaterialTheme.typography.headlineMedium` in matching `*OnContainer` foreground role) on top, then page title (or fallback `stringResource(R.string.tabs_default_title)` when empty per R11) in `MaterialTheme.typography.titleSmall`, then hostname extracted from `tab.url` via Spec 009's `UrlPatterns.hostname(...)` extension in `MaterialTheme.typography.bodySmall`, then a small × `IconButton` aligned to the top-right corner. Active-tab visual indicator: `Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))` when `isActive` is true. `Modifier.semantics { contentDescription = "${tab.title}, ${hostname}" }` for TalkBack. `onCloseClick` lambda defined at this stage but wired in US3 (T044) — for US1 it's a no-op-stub at the call site.
- [x] T029 [P] [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherNewTabCard.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherNewTabCard.kt) — Composable `@Composable fun TabSwitcherNewTabCard(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`. Layout: Material3 `Card` (same shape as `TabSwitcherCard` for visual consistency in the grid) with a centered `Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tabs_switcher_new_tab))` and a label below using `MaterialTheme.typography.titleSmall`. When `enabled = false` (cap reached), tinted with `MaterialTheme.colorScheme.outline` and `Modifier.alpha(0.5f)` (or use `IconButton(enabled = false)` if simpler) — onClick still fires for the localized message surface (T045 wires the snackbar).
- [x] T030 [US1] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModel.kt) per [contracts/TabsViewModel.md](contracts/TabsViewModel.md). `@HiltViewModel class TabsViewModel @Inject constructor(observeTabs: ObserveTabsUseCase, private val createTab: CreateTabUseCase, private val switchActiveTab: SwitchActiveTabUseCase, private val closeTab: CloseTabUseCase, private val closeAllTabs: CloseAllTabsUseCase) : ViewModel()`. Implements 7 user-action methods (`onTabClick`, `onCloseTab`, `onNewTabClick`, `onCloseAllRequested`, `onCloseAllConfirmed`, `onCloseAllDismissed`, `consumeErrorEvent`). `uiState: StateFlow<TabsUiState>` derived from `observeTabs()` + an internal `MutableStateFlow` for UI-only transient state (dialog visibility + error event) using `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), TabsUiState.EMPTY)`. **For US1, only `onTabClick` and `onNewTabClick` are functionally exercised** — the close + close-all methods are stubbed-and-implemented but their UI surfaces are wired in US3.
- [x] T031 [US1] Rewrite [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt) — replace the placeholder Composable with the real switcher screen. Signature: `@Composable fun TabsScreen(navController: NavHostController, modifier: Modifier = Modifier, viewModel: TabsViewModel = hiltViewModel())`. Layout: `Scaffold` with `topBar` showing `TopAppBar(title = pluralStringResource(R.plurals.tabs_switcher_title_count, count = state.tabs.size, state.tabs.size))` (M4: real-time count surface per FR-015 — uses Android's plural string resources for proper localization). Body: `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm))`. Items: `state.tabs` mapped to `TabSwitcherCard(tab, isActive = (tab.id == state.activeTabId), onClick = { viewModel.onTabClick(tab.id) }, onCloseClick = {})` (close stub for US1) + a final `TabSwitcherNewTabCard(enabled = state.tabs.size < BrowserLimits.MAX_TABS, onClick = { viewModel.onNewTabClick() })`. Stable `key = { tab -> tab.id }` per Constitution §V. **Pop-back wiring (M3 — explicit event flow, NOT activeTabId watcher)**: add `LaunchedEffect(Unit) { viewModel.popBackEvent.collect { navController.popBackStack(AppDestination.Browser.route, inclusive = false) } }`. This collects the one-shot signal emitted by `TabsViewModel.onTabClick`, `onCloseAllConfirmed`, and `onNewTabClick`-success per [contracts/TabsViewModel.md § NavController coupling](contracts/TabsViewModel.md#navcontroller-coupling). For US1, "Close all tabs" button + dialog and the snackbar surface are deferred to US3 (T045 / T047).
- [x] T032 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarCallbacks.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarCallbacks.kt) per [contracts/NavigationBottomBar.md § Callbacks bundle expansion](contracts/NavigationBottomBar.md#callbacks-bundle-expansion-4--6-fields). Add `val onTabsSwitcherClick: () -> Unit` and `val onTabsSwitcherLongClick: () -> Unit` as the 5th + 6th fields. Bundle becomes 6 — exactly at detekt's `LongParameterList.functionThreshold = 6` (PASSES). Update KDoc to note Spec 011 expansion + the cap on the bundle.
- [x] T033 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt) per [contracts/NavigationBottomBar.md § New affordance](contracts/NavigationBottomBar.md#new-affordance-5th-iconbutton-with-combined-click). Add file-level `@OptIn(ExperimentalFoundationApi::class)`. Add `tabCount: Int` as a new parameter to `@Composable fun NavigationBottomBar(...)` (placed after `isLoading`, before `callbacks`). Add the 5th button as a `BadgedBox` wrapping a `Box` with `Modifier.minimumInteractiveComponentSize().combinedClickable(onClick = callbacks.onTabsSwitcherClick, onLongClick = callbacks.onTabsSwitcherLongClick, role = Role.Button, onClickLabel = stringResource(R.string.browser_action_tabs_switcher), onLongClickLabel = stringResource(R.string.browser_action_new_tab))`. Icon `Icons.AutoMirrored.Filled.List` (or `Icons.Filled.Tab` if available in the bundled material-icons-core). Add `const val TEST_TAG_NAV_TABS_SWITCHER: String = "nav_tabs_switcher"` at the file bottom matching the existing pattern from Spec 008. Imports: `androidx.compose.foundation.ExperimentalFoundationApi`, `androidx.compose.foundation.combinedClickable`, `androidx.compose.foundation.layout.Box`, `androidx.compose.material3.BadgedBox`, `androidx.compose.material3.Badge`, `androidx.compose.material3.minimumInteractiveComponentSize`, `androidx.compose.ui.semantics.Role`.
- [x] T034 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) per [contracts/BrowserViewModel.md](contracts/BrowserViewModel.md). Constructor: add 4 new params after `buildSearchUrl` — `observeActiveTab: ObserveActiveTabUseCase`, `observeTabs: ObserveTabsUseCase`, `private val updateActiveTabUrlAndTitle: UpdateActiveTabUrlAndTitleUseCase`, `private val createTab: CreateTabUseCase`. Add private `_activeTab: StateFlow<Tab?> = observeActiveTab().stateIn(viewModelScope, WhileSubscribed(5_000), null)` AND public `val tabCount: StateFlow<Int> = observeTabs().map { it.size }.stateIn(viewModelScope, WhileSubscribed(5_000), 1)` (initial value `1` since R5 guarantees ≥ 1 tab once Flow has emitted — avoids empty-badge flash on cold start). Add private `_currentTitle: MutableStateFlow<String> = MutableStateFlow("")`. Add `init {}` block with TWO collectors: (1) `_activeTab.onEach { tab -> tab?.let { _uiState.update { state -> state.copy(currentUrl = it.url) } } }.launchIn(viewModelScope)`; (2) `_activeTab.map { it?.id }.distinctUntilChanged().onEach { _currentTitle.value = "" }.launchIn(viewModelScope)` — resets cached title on tab switch so a stale title from a previous tab does not leak into the next tab's first write-through. Add `onLongPressNewTab()` + `consumeTabsEvent()` methods per the contract. Add NEW `onTitleReceived(title: String)` method per [contracts/BrowserViewModel.md § New method — `onTitleReceived`](contracts/BrowserViewModel.md#new-method--ontitlereceivedtitle-string). Modify `onUrlChanged(url)` and `onLoadFinished(url)` to write through via `viewModelScope.launch { updateActiveTabUrlAndTitle(activeId, url, _currentTitle.value) }` (`activeId = _activeTab.value?.id`). Update file-top KDoc to note Spec 011 expansion. **CRITICAL**: every test that constructs `BrowserViewModel` directly (Spec 007/008/009/010 baseline + T024 additions) MUST pass no-op fakes for the 4 new params — keep T024's update for the new tests + a companion update across all existing tests' `BrowserViewModel(...)` calls.
- [x] T035 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt) — pass `navController` to both `BrowserScreen(navController = navController)` and `TabsScreen(navController = navController)` so each screen can drive the back-stack natively. Existing route entries unchanged otherwise.
- [x] T036 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt). Signature: add `navController: NavHostController` as the second parameter (after `modifier`). Collect `val tabCount by viewModel.tabCount.collectAsStateWithLifecycle()` (the StateFlow added by T034). Pass `tabCount = tabCount` + new callbacks `onTabsSwitcherClick = { navController.navigate(AppDestination.Tabs.route) }` + `onTabsSwitcherLongClick = viewModel::onLongPressNewTab` into `NavigationBottomBar(...)`. **Title plumbing for write-through (M1 / M5)**: extend `BrowserNavigationCallbacks` (Spec 008 callbacks bundle in [BrowserNavigationCallbacks.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserNavigationCallbacks.kt)) with a new lambda `onTitleChange: (String) -> Unit` (bundle goes from 3 → 4 fields, well under detekt threshold). Wire `onTitleChange = viewModel::onTitleReceived` at the call site. Inside `BrowserWebView` (modify the chrome client section), invoke the new callback from `WebChromeClient.onReceivedTitle(view: WebView?, title: String?) { title?.let { callbacks.onTitleChange(it) } }`. Tab-switch driver: collect `val activeTab by viewModel.observeActiveTab().collectAsStateWithLifecycle(initialValue = null)` (or expose the `_activeTab` StateFlow publicly via a `val activeTab: StateFlow<Tab?>` getter on `BrowserViewModel` — pick the public-getter path for cleaner Compose collection). Add `LaunchedEffect(activeTab?.id) { activeTab?.let { webViewActions.loadUrl(it.url) } }` inside `BrowserScaffoldContent` so a tab switch triggers a fresh URL load. Wrap `BrowserWebView(...)` with `key(activeTab?.id ?: 0L) { ... }` so Compose disposes the prior WebView (Spec 007 `DisposableEffect` cleanup fires) and instantiates a fresh one on switch per [research R8](research.md#r8--inactive-tab-webview-lifecycle-fr-027--a6).
- [x] T037 [US1] Modify the BrowserUiState data class in [BrowserUiState.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt) per [data-model.md Entity 4](data-model.md#entity-4--browseruistate-spec-007008009010-extended) — add `val tabsEvent: TabsErrorEvent? = null` as the new optional field. Existing fields unchanged. Update file-top KDoc to note Spec 011 addition.
- [x] T038 [US1] Run `./gradlew testDebugUnitTest lintDebug` — all tests pass (Spec 010 baseline 162 + T017–T020 ~17 + T022–T024 ~11 = ~190 tests), zero lint warnings. Fix any failures before proceeding.
- [x] T039 [US1] **MANUAL on-device gate M1** — multi-tab + grid switcher round-trip per [quickstart.md Gate M1](quickstart.md#gate-m1--us1-multi-tab-switcher-round-trip-p1-headline). 8 sub-steps. DEFER to user-device pass per Spec 008/010 precedent.

**Checkpoint**: US1 done — multi-tab + grid switcher fully functional; long-press shortcut works; tab-count badge reflects state. **MVP delivered**. The system is shippable here even without US2 / US3 because: persistence already works (Phase 2's `TabRepositoryImpl` writes through `TabDao`), and close affordances can be a fast follow-up.

---

## Phase 4: User Story 2 — Persistence across app kill / restart (Priority: P1)

**Goal**: Tabs survive process death + relaunch in the same order with the same active tab.

**Independent Test**: Quickstart Gate M2 — open 3 tabs with distinct URLs; make 2nd active; force-stop from system Settings; relaunch; all 3 tabs present with 2nd active.

US2's production code is **already shipped** by Phase 2 (`TabRepositoryImpl` writes through Room's WAL; `BrowserViewModel` write-through via `UpdateActiveTabUrlAndTitleUseCase` from T034 ensures the live URL trace is durable). What remains is the integration test + the manual on-device gate.

### Tests for User Story 2

- [x] T040 [US2] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabPersistenceProcessDeathTest.kt](../../app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabPersistenceProcessDeathTest.kt) per [research R9](research.md#r9--process-death-restoration-test-strategy). `@HiltAndroidTest` + `@UninstallModules(TabsModule::class)` + nested `FakeTabsModule` overriding `TabRepository` with a Hilt-injected `TabRepositoryImpl` over an in-memory Room DB seeded with 3 tabs. Test method `recreate_preserves_tabs_and_active`: snapshot tabs via `repository.observeTabs().first()`; `ActivityScenario.launch(MainActivity::class.java).recreate()`; re-snapshot; assert lists are equal AND `activeTabId` (max-`lastActiveAt`) matches. Uses Robolectric-backed Hilt (Spec 007 `HiltTestRunner` + `HiltTestActivity` precedent).

### Implementation for User Story 2

US2 has **no production-code change** beyond what Phase 2 + US1 already shipped.

- [ ] T041 [US2] Run `./gradlew connectedDebugAndroidTest --tests "*TabPersistenceProcessDeathTest*"` — must pass on the CI emulator. If it fails, the persistence path is broken and either Phase 2 or T034 needs revision.
- [x] T042 [US2] **MANUAL on-device gate M2** — process-death restoration on a real device per [quickstart.md Gate M2](quickstart.md#gate-m2--us2-process-death-restoration-p1-value-prop). 7 sub-steps including the active-tab assertion + the most-recently-active-first ordering check. DEFER to user-device pass.

**Checkpoint**: US2 done — process-death restoration verified end-to-end. SC-002 satisfied.

---

## Phase 5: User Story 3 — Close + close-all (Priority: P2)

**Goal**: User can close individual tabs (× on each card) + close all tabs at once (with confirmation dialog). Closing the last tab auto-creates a fresh home tab. Localized "max tabs reached" message surfaces when at the cap.

**Independent Test**: Quickstart Gate M3 — open 3 tabs; close non-active → card disappears, active unchanged; close active → fall back to most-recently-active surviving tab; close last → fresh home tab auto-created; "close all" + confirm → exactly 1 fresh home tab; "close all" + cancel → no change.

### Tests for User Story 3

- [x] T043 [P] [US3] Extend [TabsViewModelTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsViewModelTest.kt) (created in T022) — add 4 US3 tests: `onCloseTab_removesFromList`, `onCloseTab_lastTab_recreatesHome`, `onCloseAllConfirmed_wipesAndSeeds`, `consumeErrorEvent_clearsField`. Also add `onCloseAllRequested_setsDialogVisible` + `onCloseAllDismissed_clearsDialog` for the dialog-visibility flow.
- [x] T044 [P] [US3] Extend [BrowserViewModelTest.kt](../../app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt) (extended in T024) — add 3 US3 tests: `onLongPressNewTab_underCap_succeeds`, `onLongPressNewTab_atCap_setsErrorEvent`, `consumeTabsEvent_clearsField`.

### Implementation for User Story 3

- [x] T045 [P] [US3] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/CloseAllTabsConfirmDialog.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/CloseAllTabsConfirmDialog.kt) — `@Composable fun CloseAllTabsConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit)`. Material3 `AlertDialog` with `title = stringResource(R.string.tabs_switcher_close_all)`, `text = stringResource(R.string.tabs_switcher_close_all_confirm)`, confirm button label localized via the existing Material3 default or a project shared "close" string, dismiss button "Cancel" localized. TalkBack-friendly (default AlertDialog handles it).
- [x] T046 [US3] Wire close X handler in [TabSwitcherCard.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/components/TabSwitcherCard.kt) (created stub in T028) — replace the no-op `onCloseClick` callback at the call site in [TabsScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt) with `onCloseClick = { viewModel.onCloseTab(tab.id) }`. The close × IconButton already exists per the T028 contract.
- [x] T047 [US3] Modify [TabsScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabsScreen.kt) (created in T031) — add a "Close all tabs" `IconButton` (or `TextButton`) in the `TopAppBar` `actions` slot using `Icons.Filled.DeleteForever` (or text label per `stringResource(R.string.tabs_switcher_close_all)`). Wire `onClick = viewModel::onCloseAllRequested`. Below the LazyVerticalGrid, add `if (state.isCloseAllDialogVisible) CloseAllTabsConfirmDialog(onConfirm = viewModel::onCloseAllConfirmed, onDismiss = viewModel::onCloseAllDismissed)`. Add a `Snackbar` (or `LaunchedEffect(state.errorEvent) { /* show snackbar via SnackbarHostState */ }`) for `state.errorEvent == TabsErrorEvent.MaxTabsReached` showing `stringResource(R.string.browser_max_tabs_reached)` then calling `viewModel.consumeErrorEvent()`.
- [x] T048 [US3] Modify [BrowserScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) (modified in T036) — add a `SnackbarHost` to the `Scaffold`'s `snackbarHost` slot. Add `LaunchedEffect(state.tabsEvent) { state.tabsEvent?.let { /* show snackbar with R.string.browser_max_tabs_reached */; viewModel.consumeTabsEvent() } }`. This surfaces the long-press cap-reached message when the user attempts a new tab from the BrowserScreen at the cap.
- [x] T049 [US3] Run `./gradlew testDebugUnitTest --tests "*TabsViewModelTest*" --tests "*BrowserViewModelTest*" lintDebug` — all tests pass (~25 new total = 17 foundational + ~8 viewmodel = SC-006 met), zero lint warnings.
- [x] T050 [US3] **MANUAL on-device gate M3** — close + close-all + auto-create-on-empty per [quickstart.md Gate M3](quickstart.md#gate-m3--us3-close--close-all-p2-hygiene). 7 sub-steps. DEFER to user-device pass.

**Checkpoint**: US3 done — close + close-all + dialog confirmation + max-tabs snackbar all wired. SC-003 (close durability) and SC-004 partial (max-tabs message) satisfied.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final automated quality gates spanning all 3 stories + the remaining 2 manual on-device gates (M4 max-tabs UI, M5 50-tab cold-start) + docs + PR.

### Remaining instrumented tests

- [ ] T051 [P] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabSwitcherFlowTest.kt](../../app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/TabSwitcherFlowTest.kt) — instrumented end-to-end test: launch `MainActivity`, tap the 5th BottomAppBar button (testTag `nav_tabs_switcher`), assert TabsScreen rendered, tap a card, assert BrowserScreen back in foreground with the tapped tab's URL in the address bar. Uses Hilt instrumented runtime (Spec 007 wiring) + a fake `TabRepository` seeded with 2 tabs.
- [ ] T052 [P] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/MaxTabsLimitTest.kt](../../app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/tabs/MaxTabsLimitTest.kt) — pre-fill fake `TabRepository` to MAX_TABS, launch BrowserScreen, long-press the switcher button, assert a snackbar appears showing the localized `browser_max_tabs_reached` text. Repeat the assertion in the switcher's "new tab" card path. (Multi-locale assertions are deferred to manual M4.)
- [ ] T052b [P] Create [app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/TabWebViewLockdownTest.kt](../../app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/TabWebViewLockdownTest.kt) per [SC-012](spec.md#measurable-outcomes). 3 instrumented tests asserting the Spec 007 WebView lockdown settings (FR-028) hold for each tab activation path: (1) `homeTab_lockdownSettingsApplied` — fresh launch, snapshot the active `WebView`'s settings via Espresso's view-matching, assert `allowFileAccess=false`, `allowContentAccess=false`, `allowFileAccessFromFileURLs=false`, `allowUniversalAccessFromFileURLs=false`, `mixedContentMode=MIXED_CONTENT_NEVER_ALLOW`, `domStorageEnabled=true`, no `addJavascriptInterface` call recorded; (2) `createdTab_lockdownSettingsApplied` — long-press the bottom-bar switcher button to create a new tab, assert the same; (3) `restoredTab_lockdownSettingsApplied` — seed the Hilt-injected `TabRepository` with a tab whose URL differs from home, recreate the activity, assert the activated WebView still has all lockdown settings. Reuses Spec 007 `HiltTestRunner` + `@UninstallModules(TabsModule::class) FakeTabsModule` pattern.

### Quality gates

- [x] T053 [P] Run `./gradlew lintDebug` — zero warnings. Watch for `MissingTranslation` (caught by Spec 004 lint config) and any `UnusedResources` (none expected — every new string is referenced).
- [x] T054 [P] Run `./gradlew detekt` — zero violations, **baseline UNCHANGED** from Spec 010 (SC-009). The bundle expansion in `NavigationBottomBarCallbacks` (T032) is exactly at the `LongParameterList` threshold and PASSES; if a future tightening fires, re-bundle into nested data classes per the Spec 008 precedent.
- [x] T055 [P] Run `./gradlew ktlintCheck` — zero violations. The `class-signature` rule disabled in Spec 006's `.editorconfig` for `@Inject constructor` multi-line params remains in effect; new files follow the same convention.
- [ ] T056 Run `./gradlew connectedDebugAndroidTest` — all instrumented tests pass: existing Spec 008 `NavigationBottomBarTest` (extended in T025) + Spec 007 `BrowserScreenInstrumentedTest` + new `TabPersistenceProcessDeathTest` (T040) + `TabSwitcherFlowTest` (T051) + `MaxTabsLimitTest` (T052). If the Spec 005 / 007 Robolectric SDK 33 pin needs revisiting (Hilt graph + WebView setup — known flaky per `BrowserScreenOfflineErrorTest` KDoc), document the workaround inline in the test file.
- [x] T057 Run `./gradlew assembleRelease` and measure APK size: `ls -lh app/build/outputs/apk/release/app-release.apk`. Expected ≤ 2.2 MB (Spec 010 baseline 2.0 MB + 200 KB SC-007 budget). If overrun, document the actual delta + root cause (likely the new Composables + 6 strings × 8 locales) in CLAUDE.md Recent Changes.
- [x] T058 Run the 16 KB CI alignment check from [quickstart.md Gate G6](quickstart.md#automated-gates-ci--local): `unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so | objdump -p - | grep LOAD | awk '{print $NF}'`. Every value must be `0x4000` or larger. Zero new `.so` introduced (no new packages) — count must match Spec 010's 28/28 entries. If any new `.so` appears, abort and investigate.
- [x] T059 Re-run Constitution Check from [plan.md](plan.md#constitution-check-post-design) post-implementation. Expected 11/11 PASS with the documented use-case-coordination exception under Principle IV (Complexity Tracking row). Document the ack in the PR description.

### Manual on-device gates (deferred)

- [x] T060 **MANUAL on-device gate M4** — max-tabs limit + 8-locale snackbar message per [quickstart.md Gate M4](quickstart.md#gate-m4--sc-004-max-tabs-limit--8-locale-message). 5 sub-steps. DEFER to user-device pass.
- [x] T061 **MANUAL on-device gate M5** — 50-tab cold-start performance (SC-010 ≤ 500 ms; SC-005 ≤ 200 ms grid render) + memory profile via `adb shell dumpsys meminfo` per [quickstart.md Gate M5](quickstart.md#gate-m5--sc-010-cold-start-with-50-tabs-perf). 7 sub-steps. DEFER to user-device pass on Pixel 5+-class device.

### Docs + PR

- [x] T062 Update [CLAUDE.md](../../CLAUDE.md) Recent Changes with the Spec 011 implementation summary: implementation date, file count (15 new + 4 modified + 8 locale `strings.xml`), test count delta (+25 unit + 3 instrumented = +28), APK size delta vs Spec 010 baseline, 16 KB gate result, Constitution Check result + Complexity Tracking ack, deferred manual gates list (T039, T042, T050, T060, T061). Bump Active Spec block status to `✅ Implemented (manual gates deferred)` and roll Spec Roadmap row to `✅ Done` once all manual gates pass (Spec 008 / 010 precedent).
- [ ] T063 Open PR for `011-tabs-management` → `main`: title `feat: Implement Tabs Management (Spec 011) — multi-tab + grid switcher + persist via Room TabEntity`. Body MUST include: Constitution Check 11/11 PASS, file delta summary, test count delta, APK delta, 16 KB gate result, deferred manual gates list, AND a link to the [Complexity Tracking row](plan.md#complexity-tracking) explicitly acknowledging the cross-feature use-case coordination exception (mirrors Spec 010 PR convention).

**Checkpoint**: All quality gates green. PR opened. User runs the 5 deferred manual gates (T039, T042, T050, T060, T061) on a real device.

---

## Dependencies

```text
T001 (Setup verify)
  │
  ├─ T002 [P] (BrowserLimits)    ┐
  └─ T003 [P] (8 locales)        │  parallel — different files
                                 ▼
                        Phase 1 done
                                 │
                ┌────────────────┼────────────────┬──────────────┐
                ▼                ▼                ▼              ▼
            T004 [P]          T005 [P]        T013 [P]
             (Tab)         (TabRepository)   (TabMapper)
                              │
              ┌──────────┬────┼────┬──────────┬─────────┬──────────┐
              ▼          ▼    ▼    ▼          ▼         ▼          ▼
            T006 [P]  T007 [P] T008 [P]   T009 [P]  T010 [P]   T011 [P]   T012 [P]
            ObserveTabs  ActiveTab  Create  Switch   Close     CloseAll   UpdateUrl
                                                                              │
              T014 (TabRepoImpl — depends on T004+T005+T013)
                  │
              T015 (TabsModule — depends on T005+T014)
                  │
              T016 [P] (FakeTabRepository — depends on T005)
                  │
              T017 [P] (TabMapperTest)   ┐
              T018 [P] (TabTest)         │  parallel — different files
              T019    (TabRepoImplTest)  │  needs Robolectric — depends on T014
              T020 [P] (TabUseCasesTest) │  uses FakeTabRepo (T016) + use cases
                                         ▼
                                  T021 (foundation build verify)
                                         │
                                  Phase 2 done — all 3 stories unblocked
                                         │
                ┌────────────────────────┼─────────────────────────┐
                ▼                        ▼                         ▼
              US1 (T022–T039)          US2 (T040–T042)           US3 (T043–T050)
                │                        │                         │
                ├─ Tests:                ├─ T040 (instrumented     ├─ Tests:
                │   T022/T023/T024 [P]   │       persistence test) │   T043/T044 [P]
                │   T025 [P]             │                         │
                ├─ UI new files:         │  US2 has no new prod    ├─ Implementation:
                │   T026/T027/           │  code beyond Phase 2 +  │   T045 [P] (dialog)
                │   T028/T029 [P]        │  T034 (write-through)   │   T046 (TabSwitcherCard wire)
                ├─ Implementation:       │                         │   T047 (TabsScreen close-all + snackbar)
                │   T030 (TabsViewModel) │  T041 (CI test run)     │   T048 (BrowserScreen snackbar)
                │   T031 (TabsScreen)    │  T042 (manual M2)       │
                │   T032 (NavBottomBar   │                         │  T049 (test run)
                │         callbacks 4→6) │                         │  T050 (manual M3)
                │   T033 (NavBottomBar   │                         │
                │         5th button)    │                         │
                │   T034 (BrowserVM      │                         │
                │         constructor +  │                         │
                │         init + write-  │                         │
                │         through)       │                         │
                │   T035 (AppNavGraph)   │                         │
                │   T036 (BrowserScreen) │                         │
                │   T037 (BrowserUiState)│                         │
                ├─ T038 (test+lint run)  │                         │
                └─ T039 (manual M1)      │                         │
                                         ▼
                                Phase 6 Polish (T051–T063)
                                  T051/T052 [P] (instrumented)
                                  T053/T054/T055 [P] (lint/detekt/ktlint)
                                  T056 (full instrumented suite)
                                  T057→T058→T059 (assemble+16KB+constitution)
                                  T060/T061 (manual M4/M5)
                                  T062 (CLAUDE.md)
                                  T063 (PR open)
```

### Story-level dependencies

- **Foundational independence**: All Phase 2 use-case tasks (T006–T012) parallelize after T005. Mapper (T013) parallelizes with use cases. RepositoryImpl (T014) depends on T013 + T005 + T004. Module (T015) depends on T014. FakeTabRepository (T016) only depends on T005. Tests (T017–T020) all parallel except T019 which needs T014.
- **US1 critical path**: T022–T025 (tests, parallel) → T026–T029 (UI new files, parallel) → T030 (TabsViewModel) → T031 (TabsScreen) || T032 → T033 (bottom-bar) || T034 (BrowserViewModel — same-file modifications, sequential within); T035 (AppNavGraph) and T036 (BrowserScreen) depend on multiple of above. T037 (BrowserUiState) parallel with most. T038 verify, T039 manual.
- **US2 has no production-code dependency on US1** strictly — but in practice, the persistence is only meaningful with multi-tab. Tests can be written in parallel with US1 if a developer is staffed on it. T041 (CI run) needs the foundation + T034 in place.
- **US3 depends on US1's TabsScreen + BrowserScreen scaffolding** — T046/T047/T048 modify files first created in US1. Tests (T043/T044) can be written in parallel.

### Parallel opportunities

- **Phase 1**: T002 (BrowserLimits) and T003 (8 locale strings) — different files.
- **Phase 2 use cases**: T006–T012 — 7 different files, all parallel after T005 lands.
- **Phase 2 tests**: T017, T018, T020 — parallel; T019 sequential (needs T014).
- **Phase 3 (US1) tests**: T022, T023, T024, T025 — 4 different test files.
- **Phase 3 (US1) new UI files**: T026 (TabsUiState), T027 (TabPlaceholderColor), T028 (TabSwitcherCard), T029 (TabSwitcherNewTabCard) — 4 different files; can parallelize with the test scaffolding if a single developer batches the writes.
- **Phase 5 (US3) tests**: T043 + T044 — different test files.
- **Phase 6**: T051 + T052 (different instrumented test files), T053 + T054 + T055 (independent Gradle tasks), T060 + T061 (independent manual gates that the user can run in any order).

---

## Implementation Strategy

### MVP scope = Phase 1 + Phase 2 + Phase 3 (US1)

After T001–T039, the multi-tab system is functional: open tabs, switch via grid, long-press shortcut, persist via Room (US2 falls out automatically because Phase 2 already writes through). Close affordances + close-all + max-tabs snackbar (US3) are a fast follow-up but not blocking for shippable MVP.

### Incremental delivery

- **Increment 1 (Foundational + US1, P1)**: T001–T039 — full multi-tab + switcher + persistence. ~30 prod files (15 new + 4 modified + 8 locale `strings.xml` + 3 helpers) + ~9 test files = ~1500 prod LOC + ~1000 test LOC. **Ships as MVP.**
- **Increment 2 (US2, P1 verification)**: T040–T042 — process-death restoration test + manual gate. **Pure test addition** + verification; no new production code beyond Increment 1.
- **Increment 3 (US3, P2)**: T043–T050 — close + close-all + dialog + snackbar wiring. ~3 new + 4 modified files. ~7 new tests.
- **Polish**: T051–T063 — 2 more instrumented tests, automated quality gates, manual M4 + M5, docs, PR.

### Parallel-execution plan (single developer)

Day 1 (~3 h): Foundation
- T001 → T002 + T003 (parallel) → T004 + T005 + T013 (parallel) → T006–T012 (parallel) → T014 → T015 → T016 → T017 + T018 + T020 (parallel) → T019 → T021.

Day 2 (~5 h): US1 part 1 — UI scaffolding + ViewModel
- T022 + T023 + T024 + T025 (parallel test scaffolding).
- T026 + T027 + T028 + T029 (parallel UI new files).
- T030 (TabsViewModel) → T031 (TabsScreen).

Day 3 (~3 h): US1 part 2 — Bottom bar + BrowserScreen wiring
- T032 → T033 (NavigationBottomBar same-file deltas, sequential).
- T034 (BrowserViewModel) → T035 (AppNavGraph) → T036 (BrowserScreen) → T037 (BrowserUiState).
- T038 (test + lint run). T039 deferred.

Day 4 (~1 h): US2 verification
- T040 (instrumented test) → T041 (CI run). T042 deferred.

Day 5 (~3 h): US3
- T043 + T044 (parallel test additions).
- T045 (dialog) → T046 (close handler) → T047 (TabsScreen close-all + snackbar) → T048 (BrowserScreen snackbar).
- T049 (test + lint run). T050 deferred.

Day 6 (~2 h): Polish
- T051 + T052 (parallel instrumented tests).
- T053 + T054 + T055 (parallel quality gates).
- T056 → T057 → T058 → T059 (assemble + 16KB + Constitution).
- T060 + T061 deferred.
- T062 (CLAUDE.md). T063 (PR).

Total estimate: **~17 hours** of focused work + the user's 5 manual gates batch (typically 30–60 min).
