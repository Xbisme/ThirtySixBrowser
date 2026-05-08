# Data Model: History View

**Feature**: 014-history-view
**Date**: 2026-05-08

> No database migration. Reuses `HistoryEntryEntity` from Spec 005 unchanged. New shapes live in the domain + presentation layers only.

---

## Database (Spec 005 — UNCHANGED)

`HistoryEntryEntity` already shipped:

| Column | SQL | Kotlin | Notes |
|--------|-----|--------|-------|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | `Long` | Auto-generated |
| `url` | `TEXT NOT NULL` | `String` | Final settled URL of a successful load |
| `title` | `TEXT NOT NULL` | `String` | May be empty if page omitted `<title>` |
| `visited_at` | `INTEGER NOT NULL` | `Long` | Epoch milliseconds (device local at write time) |

Index: `index_history_entries_visited_at` on `visited_at` (powers the chronological observer).

No constraints added in v1.0:
- No UNIQUE on `(url)` — repeats are intentional (FR-004).
- No FK to anything — history is self-contained.

`HistoryDao` (Spec 005) already exposes: `insert`, `delete(entity)`, `getById`, `getRecent`, `observeAll`, `deleteInRange`, `deleteAll`, `count`.

**Spec 014 additive**: `deleteById(id: Long): Int` — covers FR-020 cleanly without forcing a `getById` round-trip. No schema change.

---

## Domain Models

### `HistoryEntry`

```kotlin
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
```

- Pure Kotlin, no Android imports.
- 1:1 with `HistoryEntryEntity` — `HistoryEntryMapper` does the round-trip.
- `id == 0L` means "not yet persisted" (used at the point `RecordHistoryEntryUseCase` constructs the value before insert).

### `HistoryDayBucket`

```kotlin
sealed class HistoryDayBucket {
    data object Today : HistoryDayBucket()
    data object Yesterday : HistoryDayBucket()
    data class OnDate(val date: LocalDate) : HistoryDayBucket()
}
```

- Locale-agnostic. UI renders the bucket → localized header at composition time.
- `OnDate` carries `LocalDate` (not a `Long` epoch) so equality and ordering are unambiguous.
- The bucketer is a pure function `(visitedAt: Long, today: LocalDate, zone: ZoneId) -> HistoryDayBucket`; lives in a `core/extensions/` helper to keep the use case slim.

> **Naming clarification**: `HistoryDayBucket` is the **classification** (one of three discrete cases — Today, Yesterday, OnDate). `DayGroup` (defined under Presentation Models below) is the **rendered group** that pairs a bucket with its list of entries. Both names are used throughout this feature; do not conflate them.

### Repository contract

See [contracts/HistoryRepository.kt](contracts/HistoryRepository.kt) — 5-method interface. No Repo→Repo dependency.

---

## Presentation Models

### `HistoryUiState` (immutable `data class`)

| Field | Type | Purpose |
|-------|------|---------|
| `entries` | `List<HistoryEntry>` | Full list emitted by `ObserveHistoryEntriesUseCase` |
| `searchQuery` | `String` | Raw user-typed query (may be 0/1 char — those don't filter, FR-011a) |
| `groupedEntries` | `List<DayGroup>` | Derived: filtered (when query meets threshold) → bucketed → sorted; computed in the `combine` pipeline, not by the Composable |
| `isClearAllDialogVisible` | `Boolean` | FR-024 confirm dialog gate |
| `pendingActionSheetTarget` | `HistoryEntry?` | Long-press target — non-null while sheet is open (FR-018) |
| `openUrl` | `String?` | Consumed-once signal — when set, `HistoryScreen` pops back to BrowserScreen and calls `consumeOpenUrl()` |

```kotlin
data class DayGroup(
    val bucket: HistoryDayBucket,
    val entries: List<HistoryEntry>, // already reverse-chronological
)
```

State transitions are unidirectional: VM `MutableStateFlow.update { … }` only; Composable read-only.

### `HistoryErrorEvent` (sealed, transient)

```kotlin
sealed class HistoryErrorEvent {
    data object ClipboardCopied : HistoryErrorEvent()      // FR-021 — confirmation snackbar
    data object TabCapReached : HistoryErrorEvent()        // R5 — re-surfaces Spec 011's cap
    data class DeletionFailed(val cause: Throwable) : HistoryErrorEvent()
}
```

Emitted via `historySnackbarEvent: SharedFlow<HistoryErrorEvent>` (replay = 0, capacity = 1, BufferOverflow.DROP_OLDEST). Mirrors Spec 013's `bookmarkSnackbarEvent` channel pattern. Composable collects it via `LaunchedEffect(Unit) { vm.historySnackbarEvent.collect { … } }`.

---

## Constants Added (`core/constants/`)

| Constant | Value | File | Justification |
|----------|-------|------|---------------|
| `BrowserLimits.SEARCH_MIN_CHARS` | `2` | `BrowserLimits.kt` | FR-011a — Q4 clarification answer B |
| `BrowserLimits.MAX_HISTORY_QUERY_LENGTH` | `200` | `BrowserLimits.kt` | Defensive cap on search input length to bound filter cost |
| `DateFormats.HISTORY_DAY_HEADER_STYLE` | `FormatStyle.MEDIUM` | `DateFormats.kt` | FR-029 — locale-aware short date for `OnDate` bucket headers |
| `DateFormats.HISTORY_TIME_OF_VISIT_STYLE` | `FormatStyle.SHORT` | `DateFormats.kt` | FR-029 — locale-aware short time for row time |

No magic numbers leak into the feature; Detekt baseline stays clean.

---

## Mapping Contract

`HistoryEntryMapper`:

```kotlin
fun HistoryEntryEntity.toDomain(): HistoryEntry
fun HistoryEntry.toEntity(): HistoryEntryEntity
fun List<HistoryEntryEntity>.toDomain(): List<HistoryEntry>   // extension on List
```

- Direct field copy. No defensive validation needed (DB columns are NOT NULL).
- For `RecordHistoryEntryUseCase`, the use case constructs `HistoryEntry(id = 0L, url, title, visitedAt = System.currentTimeMillis())` and passes it to the repository, which maps to entity for `dao.insert(...)`.

---

## State-Transition Diagram (high-level)

```
┌────────────────────────────────────────────────────────────────────┐
│ BrowserViewModel.onPageLoaded(url, title)                          │
│   if (!currentTab.isIncognito):                                    │
│     RecordHistoryEntryUseCase(url, title)                          │
│       └── HistoryRepository.recordVisit(url, title, now)           │
│             └── HistoryDao.insert(entity) → Long id                │
│                                                                    │
│ HistoryDao.observeAll() → Flow<List<HistoryEntryEntity>>           │
│   ↓ map (Spec 014 mapper)                                          │
│ HistoryRepository.observeAll() → Flow<List<HistoryEntry>>          │
│   ↓                                                                │
│ ObserveHistoryEntriesUseCase → Flow<List<HistoryEntry>>            │
│   ↓ combine(searchQueryFlow)                                       │
│ HistoryViewModel — derives groupedEntries → HistoryUiState         │
│   ↓ collectAsStateWithLifecycle                                    │
│ HistoryScreen renders                                              │
└────────────────────────────────────────────────────────────────────┘

User actions:
  - Tap row             → updateActiveTabUrlAndTitle + openUrl signal
  - Long-press row      → pendingActionSheetTarget = entry
  - Action: Open new tab → CreateTabUseCase(entry.url) — always normal
  - Action: Delete       → DeleteHistoryEntryUseCase(entry.id)
  - Action: Copy URL     → ClipboardManager.setText(entry.url) + emit ClipboardCopied
  - Clear-all confirm    → ClearAllHistoryUseCase()
```
