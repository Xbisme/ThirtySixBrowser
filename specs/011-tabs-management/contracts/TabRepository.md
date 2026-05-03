# Contract: `TabRepository`

**Layer**: `domain/repository/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/TabRepository.kt`

## Interface

```kotlin
interface TabRepository {
    /**
     * Hot Flow of every persisted tab, ordered by lastActiveAt DESC then id ASC
     * (FR-014). Always emits at least one tab — if the underlying table is empty,
     * a fresh home tab is auto-seeded by the implementation (R5 / FR-019) before
     * the first emission.
     */
    fun observeTabs(): Flow<List<Tab>>

    /**
     * Create a new tab pointing to [url]. Returns
     * `Result.Error(MaxTabsReachedException)` when the count is already at
     * BrowserLimits.MAX_TABS (FR-016). On success, the new tab's lastActiveAt
     * is set to now() so it becomes the active tab immediately (callers do
     * NOT need a separate switchActiveTab call).
     */
    suspend fun createTab(url: String): Result<Tab>

    /**
     * Make [tabId] the active tab. Writes lastActiveAt = now() on the leaving
     * tab BEFORE the new active tab so ordering remains stable (R1).
     * No-op if [tabId] does not exist.
     */
    suspend fun switchActiveTab(tabId: Long)

    /**
     * Persist the live URL + title for [tabId]. Called by BrowserViewModel via
     * UpdateActiveTabUrlAndTitleUseCase on every onUrlChanged + onLoadFinished
     * (FR-022). No-op if [tabId] does not exist.
     */
    suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String)

    /**
     * Durably remove [tabId]. If the post-delete count would drop to 0, a fresh
     * home tab is auto-created in the same coroutine (FR-019 / R5). The new
     * active tab is the most-recently-active surviving tab (US3 #2 / R1).
     */
    suspend fun closeTab(tabId: Long)

    /**
     * Wipe every tab and create exactly one fresh home tab. Atomic at the
     * repository layer (Mutex-guarded — R5/R10).
     */
    suspend fun closeAllTabs()

    /**
     * Snapshot of the current tab count. Used by CreateTabUseCase to render the
     * cap-reached message before attempting createTab (UI affordance disable
     * per FR-016).
     */
    suspend fun getTabCount(): Int
}

/**
 * Sentinel exception carried by `Result.Error.throwable` when `createTab(...)`
 * fails because `BrowserLimits.MAX_TABS` is already reached. Lives in
 * `domain/repository/` next to `TabRepository`. Consumers (BrowserViewModel,
 * TabsViewModel) detect via `(result as? Result.Error)?.throwable is MaxTabsReachedException`.
 */
class MaxTabsReachedException : Exception("Maximum number of tabs reached")
```

**Result shape**: Reuses the existing `Result<T>` from Spec 002 (single type parameter, `Result.Error(throwable, message)` carries an exception). Cap-reached failures are signalled via the `MaxTabsReachedException` sentinel inside `Result.Error.throwable`. This mirrors Spec 006's pattern of returning `Result.Error(throwable)` raw for callers to map (`AppError.from(...)` style) and avoids extending `Result` to a second type parameter. No `sealed class TabError` is introduced — the Constitution §III "no sentinel string for status" rule is honored by using a typed exception class, not a string code.

## Implementation contract (`TabRepositoryImpl`)

**Layer**: `data/repository/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/TabRepositoryImpl.kt`

Constructor dependencies:

```kotlin
class TabRepositoryImpl @Inject constructor(
    private val tabDao: TabDao,
    @param:Named("default_home_url") private val homeUrl: String,
    private val dispatchers: DispatcherProvider,
) : TabRepository
```

Internal state:
- `private val seedMutex: Mutex = Mutex()` — guards the `ensureAtLeastOneTab()` race-condition window (R5).

Behavior contract:
- `observeTabs()` returns `tabDao.observeAll().onStart { ensureAtLeastOneTab() }.map { it.map(TabEntity::toDomain) }.flowOn(dispatchers.io)`.
- `createTab(url)` checks `tabDao.count() >= BrowserLimits.MAX_TABS` BEFORE insert; cap-reached returns `Result.Error(MaxTabsReachedException())`. New tab's `position = (tabDao.maxPosition() ?: -1) + 1`, `createdAt = lastActiveAt = System.currentTimeMillis()`. Returns `Result.Success(insertedTab.toDomain())`.
- `switchActiveTab(tabId)`: reads the tab, updates `lastActiveAt = now()` via `tabDao.update(...)`. The previously active tab gets a slightly older timestamp implicitly (no explicit write needed — the new one is just newer).
- `updateTabUrlAndTitle(tabId, url, title)`: reads the tab, updates `url + title`, leaves `lastActiveAt` unchanged. Idempotent: if values are unchanged, the DAO `update` still runs but produces no observer change.
- `closeTab(tabId)`: deletes the row; post-delete count check; if 0, calls `ensureAtLeastOneTab()` inside the same `seedMutex.withLock` block.
- `closeAllTabs()`: `tabDao.deleteAll() + tabDao.insert(freshHomeTab())` inside `seedMutex.withLock`.

## Test surface

| Test name | Scenario | File |
|-----------|----------|------|
| observeTabs_emptyTable_seedsHomeTab | Cold start with `tabDao.count() == 0` → first emission contains exactly 1 tab with `url = homeUrl` | `TabRepositoryImplTest` |
| observeTabs_orderedByLastActiveDesc | 3 tabs with distinct `lastActiveAt` → emission order matches DESC | `TabRepositoryImplTest` |
| createTab_underCap_succeeds | Create with current count < MAX_TABS → `Result.Success` | `TabRepositoryImplTest` |
| createTab_atCap_returnsFailure | Pre-fill to MAX_TABS, then createTab → `Result.Error(throwable = MaxTabsReachedException)` | `TabRepositoryImplTest` |
| closeTab_lastTab_seedsFreshHome | 1 tab, closeTab → emission contains 1 fresh home tab | `TabRepositoryImplTest` |
| closeAllTabs_atomicWipeAndSeed | 3 tabs, closeAllTabs → emission contains 1 fresh home tab | `TabRepositoryImplTest` |
| updateTabUrlAndTitle_writesThrough | createTab → updateTabUrlAndTitle → emission reflects new URL + title | `TabRepositoryImplTest` |

Total for this file: **7 tests** (counted into the 25 new unit-test target from plan.md).
