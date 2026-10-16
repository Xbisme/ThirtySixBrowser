# Data Model: Tab History and Swipe Navigation

## TabSession (domain, `core/domain/model`)

The spec calls this "saved tab history"; the code calls it `TabSession`.

| Field | Type | Rule |
|---|---|---|
| `tabId` | `Long` | The tab it belongs to. `> 0` is a normal tab, `< 0` is incognito (existing id scheme). |
| `state` | `ByteArray` | Opaque engine state encoded by `TabStateCodec`, at most 256 KiB (FR-003, SC-005). |
| `savedAt` | `Long` | Epoch millis of the last save. |

Equality is by `tabId` and `savedAt`; the bytes are never compared.

## TabSessionEntity (Room, `core/data/datasource/local/entity`)

Table `tab_sessions`, added in schema v6 by `Migration5To6`.

| Column | Type | Constraint |
|---|---|---|
| `tab_id` | `INTEGER NOT NULL` | `PRIMARY KEY`, `FOREIGN KEY → tabs(id) ON DELETE CASCADE` |
| `state` | `BLOB NOT NULL` | — |
| `saved_at` | `INTEGER NOT NULL` | — |

The migration is additive: `CREATE TABLE IF NOT EXISTS tab_sessions (...)`. It names no existing table. Only normal tabs are written here. Incognito sessions never reach the DAO (FR-006).

## Lifecycle

| Event | Normal tab | Incognito tab |
|---|---|---|
| Page finished, history changed (debounced 1 s), WebView released, screen stopped | upsert row | replace in-memory entry |
| Tab's WebView created | read row → restore, or load the address | read entry → restore, or load the address |
| Tab closed, all tabs closed | row removed by cascade, plus the sweep | entry removed by the sweep |
| Fire | rows removed (tabs closed → cascade) and `clearAll()` | entries removed by `clearAll()` |
| Clear history (Fire, Settings, History) | `clearAll()`; live WebView `clearHistory()` | same |
| History retention period | not applied (FR-010a) | not applied |
| App process ends | rows stay | entries are gone with the process |

## Session in the browser's UI

`TabSessionUiState(loadedTabId: Long?)` is the only session value in UI state. The bytes stay inside `TabSessionViewModel`. The WebView factory reads them through the lookup property `savedState: SavedTabStateLookup` (`(tabId) -> ByteArray?`), so no `ByteArray` becomes a composable parameter (constitution IV).

## SwipeNavigationState (presentation, `@Stable`)

| Field | Type | Read in |
|---|---|---|
| `direction` | `Back`, `Forward` or none | composition (changes once per gesture) |
| `progress` | `Float` 0..1, 1 = threshold | `graphicsLayer {}` only |
| `available` | `Boolean`, whether there is a page in that direction | composition |

## Settings

| Key | Type | Default | Owner |
|---|---|---|---|
| `swipe_navigation_enabled` | `Boolean` | `true` | `SettingsDataStore` → `UserSettings.isSwipeNavigationEnabled` |
