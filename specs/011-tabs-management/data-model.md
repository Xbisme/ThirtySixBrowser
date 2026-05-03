# Data Model: Tabs Management (Spec 011)

**Date**: 2026-05-03 | **Branch**: `011-tabs-management`

This document records the data model for Spec 011. **No Room schema migration is performed** — Spec 005's existing v1 schema is sufficient. The new artifacts are: a domain model (`Tab`), a mapper, a UI state, a constant file, and a transient error event sealed type.

---

## Entity 1 — `TabEntity` (Spec 005, UNCHANGED)

The existing `TabEntity` from [Spec 005](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/TabEntity.kt) is reused as-is.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `Long` (PK, auto-increment) | Stable identifier across sessions. |
| `url` | `String` (NOT NULL) | Current URL of the tab. Updated by `BrowserViewModel.onUrlChanged` via `UpdateActiveTabUrlAndTitleUseCase` (FR-022). |
| `title` | `String` (NOT NULL, may be empty) | Page title. May be empty for freshly-created tabs until `WebViewClient.onReceivedTitle` fires. The display layer falls back to the localized "New tab" string (R11). |
| `position` | `Int` (NOT NULL) | Insertion order. Reserved for future drag-to-reorder (FR-034); v1.0 uses it only as a tiebreaker (FR-023). |
| `created_at` | `Long` (NOT NULL, epoch millis) | Set once at insert. |
| `last_active_at` | `Long` (NOT NULL, epoch millis) | Source of truth for the active-tab pointer (R1) AND the switcher's most-recently-active-first ordering (FR-014). Updated on every tab-switch (R1). |

Indexes (Spec 005, unchanged):
- `INDEX_POSITION` on `position` ASC.

NO new columns, NO migration. The intentional absence of `is_incognito` is honored (Spec 012 keeps incognito tabs in-memory only).

---

## Entity 2 — `Tab` (Domain model, NEW)

Pure-Kotlin model — zero Android imports. Lives at `app/src/main/kotlin/.../domain/model/Tab.kt`.

```kotlin
data class Tab(
    val id: Long,
    val url: String,
    val title: String,
    val position: Int,
    val createdAt: Long,
    val lastActiveAt: Long,
)
```

Field-by-field semantics mirror `TabEntity`. The `Tab` model is what the rest of the app sees — `data/repository/TabRepositoryImpl` is the only file that touches `TabEntity` directly.

---

## Mapper — `TabMapper` (NEW)

Top-level pure functions in `app/src/main/kotlin/.../data/mapper/TabMapper.kt`:

```kotlin
fun TabEntity.toDomain(): Tab = Tab(
    id = id,
    url = url,
    title = title,
    position = position,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
)

fun Tab.toEntity(): TabEntity = TabEntity(
    id = id,
    url = url,
    title = title,
    position = position,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
)
```

Sibling to `SettingsMapper.kt` introduced by Spec 006. Same top-level extension-function pattern; the `data/mapper/` package is the established location for entity ↔ domain mapping.

---

## Entity 3 — `TabsUiState` (NEW)

Lives at `app/src/main/kotlin/.../presentation/tabs/TabsUiState.kt`.

```kotlin
data class TabsUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTabId: Long? = null,
    val isCloseAllDialogVisible: Boolean = false,
    val errorEvent: TabsErrorEvent? = null,
) {
    companion object {
        val EMPTY = TabsUiState()
    }
}

sealed class TabsErrorEvent {
    data object MaxTabsReached : TabsErrorEvent()
}
```

| Field | Purpose |
|-------|---------|
| `tabs` | Snapshot from `ObserveTabsUseCase.invoke()`, ordered by `lastActiveAt` DESC then `id` ASC (FR-014). |
| `activeTabId` | Derived from `tabs.maxByOrNull { it.lastActiveAt }?.id`. The switcher uses this to render the active-tab visual indicator. |
| `isCloseAllDialogVisible` | UI-only flag. Toggled when the user taps "Close all tabs" — opens the `CloseAllTabsConfirmDialog` (FR-012). |
| `errorEvent` | One-shot transient event surfaced as a snackbar / toast. Cleared by the consumer immediately after handling (`TabsViewModel.consumeErrorEvent()`). Currently only `MaxTabsReached` (FR-016). |

`TabsErrorEvent` is sealed for forward-compat — future failure modes (e.g., DB write error during close-all) can extend it without breaking consumers.

---

## Entity 4 — `BrowserUiState` (Spec 007/008/009/010, EXTENDED)

The existing `BrowserUiState` is **NOT** changed structurally — its existing fields remain the source of truth for `currentUrl`, `loadingState`, `addressBarText`, etc. Spec 011 adds ONE optional one-shot error field for the long-press new-tab path (FR-002):

```kotlin
data class BrowserUiState(
    // existing fields from Specs 007/008/009/010 — unchanged
    val currentUrl: String,
    val loadingState: LoadingState,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val addressBarText: String = "",
    val isAddressBarFocused: Boolean = false,
    // Spec 011 addition — one-shot transient surface for the long-press new-tab path
    val tabsEvent: TabsErrorEvent? = null,
)
```

`tabsEvent` is the same sealed type as `TabsUiState.errorEvent` (re-used to keep one source of error semantics for both surfaces). Consumed by a snackbar inside `BrowserScreen`.

---

## Entity 5 — `BrowserLimits` (NEW core constant file)

Lives at `app/src/main/kotlin/.../core/constants/BrowserLimits.kt`. First file in this constants location (the file was reserved by Constitution §III's table but not yet created).

```kotlin
package com.raumanian.thirtysix.browser.core.constants

/**
 * Spec 011 — limits for the multi-tab system.
 *
 * Reserved per Constitution §III table row "Magic numbers / limits → core/constants/BrowserLimits.kt".
 * Future limits (max history days, max bookmarks, etc.) live here as the corresponding specs
 * (014 history, 013 bookmarks) ship.
 */
object BrowserLimits {
    /**
     * Maximum number of open tabs the user may keep at once. When reached, the new-tab
     * affordances (BrowserScreen long-press AND TabsScreen "new tab" card) are visually
     * disabled and a localized "max tabs reached" message is surfaced (FR-016 / SC-004).
     *
     * Value 50 is mid-range for Android browsers (Chrome ~100, DuckDuckGo no hard cap,
     * Firefox no hard cap); 50 keeps memory predictable on min-spec devices (Android 7,
     * 2 GB RAM) given Spec 011's single-active-WebView strategy (FR-027 / R8).
     */
    const val MAX_TABS: Int = 50
}
```

---

## Storage decisions summary

| Concern | Decision | Reason |
|---------|----------|--------|
| Active-tab pointer storage | Derived (`MAX(last_active_at)`) — NOT a column | R1 — avoids Room migration; single source of truth |
| Active-tab pointer in DataStore | NO | Splits source of truth across Room + DataStore — race condition risk (R1) |
| `is_incognito` column | NO (intentional absence from Spec 005) | Spec 012 — incognito = in-memory-only |
| Empty-state seed | One-time atomic insert inside `TabRepositoryImpl.observeTabs()` (mutex-guarded) | R5 — invariant lives in the repository |
| Per-tab WebView persistence | NO — only the active tab has a live WebView | R8 — memory-vs-fidelity trade-off; URL reload on switch |
| Schema version bump | NO | A1 / A2 — `TabEntity` v1 columns sufficient for v1.0 |
| Backup posture | DB excluded from Auto Backup (Spec 005, unchanged) | Privacy-first; Spec 011 inherits |

---

## State transitions

The transitions below describe what happens when the user takes a single action; all writes go through `TabRepository` (which writes through `TabDao` synchronously inside `suspend` functions; Room serializes via WAL).

### Open a new tab (long-press from BrowserScreen)

1. `NavigationBottomBar` `onTabsSwitcherLongClick` → `BrowserViewModel.onLongPressNewTab()`.
2. `viewModelScope.launch { createTab(homeUrl) }` → `TabRepositoryImpl.createTab(url)`.
3. Cap check: `if (tabDao.count() >= MAX_TABS) return Result.Error(MaxTabsReachedException())`.
4. `tabDao.insert(TabEntity(url = homeUrl, title = "", position = currentMaxPos + 1, createdAt = now(), lastActiveAt = now()))`.
5. `TabDao.observeAll()` re-emits with the new row included.
6. `ObserveActiveTabUseCase` re-derives `Tab` with the new max `lastActiveAt`.
7. `BrowserViewModel.uiState` updates `currentUrl` to the new tab's `url`.
8. `BrowserScreen` `LaunchedEffect(activeTab.id)` fires — fresh WebView is composed, loads home URL.

### Switch to an inactive tab (tap a card in switcher)

1. `TabSwitcherCard` `onClick` → `TabsViewModel.onTabClick(tabId)`.
2. `viewModelScope.launch { switchActiveTab(tabId) }` → `TabRepositoryImpl.switchActiveTab(tabId)`.
3. Atomic write: `tabDao.update(tabAt(tabId).copy(lastActiveAt = now()))` (the new active tab gets the freshest timestamp; the previously active tab now sits second-most-recent automatically).
4. `TabDao.observeAll()` re-emits.
5. `navController.popBackStack()` returns from `Tabs` route to `Browser` route (caller-side, after the use case suspends successfully).
6. `BrowserScreen` recomposes; `LaunchedEffect(activeTab.id)` fires with the new id; old WebView is disposed (Spec 007 `DisposableEffect` cleanup); fresh WebView loads the new tab's URL.

### Close a tab (tap × on a card)

1. `TabSwitcherCard` `onCloseClick` → `TabsViewModel.onCloseTab(tabId)`.
2. `viewModelScope.launch { closeTab(tabId) }` → `TabRepositoryImpl.closeTab(tabId)`.
3. `tabDao.delete(tabAt(tabId))`.
4. Post-delete count check: `if (tabDao.count() == 0)` → atomic insert of fresh home tab (R5; same logic as cold-start empty seed).
5. If the closed tab was the active one, the most-recently-active surviving tab (by `MAX(last_active_at)`) becomes active (US3 #2). The auto-create path covers the "closed last tab" case (US3 #3).
6. `TabDao.observeAll()` re-emits with the row removed (and possibly the seeded fresh home tab if last-tab-close).

### Close all tabs (tap "close all" → confirm)

1. `TabsScreen` "Close all" button → `TabsViewModel.onCloseAllRequested()` → `_uiState.update { it.copy(isCloseAllDialogVisible = true) }`.
2. `CloseAllTabsConfirmDialog` "Confirm" → `TabsViewModel.onCloseAllConfirmed()`.
3. `viewModelScope.launch { closeAllTabs() }` → `TabRepositoryImpl.closeAllTabs()`.
4. Atomic: `tabDao.deleteAll()` then `tabDao.insert(freshHomeTab())` in the same coroutine — Room's `@Transaction` annotation can guarantee atomicity if the DAO method is annotated; for v1.0 the `Mutex` lock guarding `ensureAtLeastOneTab()` provides the same semantics at the repository layer.
5. `navController.popBackStack()` returns to `Browser` route (US3 #4).
6. `BrowserScreen` recomposes with the fresh home tab as active.

### Cold start with persisted tabs

1. `BrowserViewModel.init { observeActiveTab().onEach { ... } }` collects the first emission from `ObserveActiveTabUseCase`.
2. `TabRepositoryImpl.observeTabs()` flows `tabDao.observeAll().onStart { ensureAtLeastOneTab() }`.
3. If `tabDao.count() == 0`: insert fresh home tab (FR-019).
4. First emission to `ObserveActiveTabUseCase` arrives — derived `activeTab = tabs.maxByOrNull { it.lastActiveAt }`.
5. `BrowserUiState.currentUrl = activeTab.url`.
6. `BrowserScreen` composes; `LaunchedEffect(activeTab.id)` fires; fresh WebView loads the URL.
7. Switcher metadata (other tabs) hydrates lazily — `TabsScreen` is not yet on the back-stack, so no work is done for inactive tabs until the user opens the switcher.
