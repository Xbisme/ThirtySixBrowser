# Data Model: Page Tools (Spec 022)

**Room schema: unchanged (v3).**
**DataStore: one new key.**
Everything else lives in memory only.

## 1. `TextSize` (domain, persisted)

`domain/model/TextSize.kt` follows the `HistoryRetention` pattern.

| Entry | `percent` |
|---|---|
| `Percent50` | 50 |
| `Percent75` | 75 |
| `Percent90` | 90 |
| `Percent100` | 100 (**default**, `AppDefaults.TEXT_SIZE`) |
| `Percent110` | 110 |
| `Percent125` | 125 |
| `Percent150` | 150 |
| `Percent175` | 175 |
| `Percent200` | 200 |

- The percentages are constants in `BrowserLimits`, never literals (§III).
- `fromPercentOrDefault(value: Int?)`: any value that is not one of the nine, including null or a value written by a future build, decodes to the default.
- **Naming:** "Text size" is the user-facing and domain name (`TextSize`). Storage and the WebView side use "text zoom" (`text_zoom_percent`, `textZoomPercent`) because that is the WebView setting it drives (`WebSettings.textZoom`). Both names refer to the same value.
- **Storage:** `StorageKeys.TEXT_ZOOM_PERCENT = intPreferencesKey("text_zoom_percent")`. The value written is `percent`.
- `UserSettings` gains `textSize: TextSize`, and `UserSettings.DEFAULT` uses `AppDefaults.TEXT_SIZE`.
- `SettingsRepository` gains `suspend fun setTextSize(size: TextSize): Result<Unit>`, used by the new `SetTextSizeUseCase`.
- **Not cleared by** `ClearBrowsingDataUseCase` (FR-032). That use case touches no settings key today and none is added.

**Invariants**

- **I-1**: Whatever is stored, the WebView receives one of the nine percentages.
- **I-2**: A fresh install reads 100.

## 2. Desktop mode (presentation, memory only)

The `BrowserViewModel` field is `desktopModeTabs: MutableStateFlow<Set<Long>>` (private). `BrowserUiState` gains:

```text
isDesktopMode: Boolean   // activeTabId in desktopModeTabs
```

| Event | Effect |
|---|---|
| Hub → Desktop site, active tab T is mobile | add T; `actions.setDesktopMode(true)` reconfigures and reloads |
| Hub → Desktop site, T is desktop | remove T; `actions.setDesktopMode(false)` reconfigures and reloads |
| New tab created (any origin) | nothing — its id is absent, so it is mobile (FR-027) |
| Tab activated | the factory applies `isDesktopMode` before its initial load |
| Tab closed | its id is pruned against the open-tab list |
| Process death | the set is gone, so every tab is mobile (FR-028) |

**Invariants**

- **I-3**: Desktop mode affects only the tab whose id is in the set.
- **I-4**: Nothing about desktop mode is ever written to disk.

## 3. `FindInPageState` (presentation, memory only)

This is exposed as its own `StateFlow` by `BrowserViewModel` and collected only by `FindInPageBar` (§V rule 3).

| Field | Type | Meaning |
|---|---|---|
| `isOpen` | Boolean | Whether the find bar replaces the top bar |
| `query` | String | At most `BrowserLimits.MAX_FIND_QUERY_LENGTH` = 200 characters (FR-013) |
| `current` | Int | 1-based current match; 0 when there are no matches |
| `total` | Int | Number of matches; 0 when there are none |

`counterVisible` is derived: `query.isNotEmpty()`. `canStep` is derived: `total > 0`.

**State transitions**

```text
Closed ──open()──▶ Open(query="", 0/0)
Open ──type q──▶ Open(q, 0/0) ──result(o, n)──▶ Open(q, o+1/n)
Open ──next/prev──▶ (WebView moves; result arrives) ──▶ Open(q, k/n)
Open ──close() | Back | onLoadStarted | active tab changed──▶ Closed (+ clearMatches)
```

**Results are dropped when they are stale.** A result is ignored while closed, or when the query has since changed. The listener carries no query, so the ViewModel ignores results arriving after a `close()` until the next `findAll`.

**Invariants**

- **I-5**: Closed ⇒ no highlights on the live WebView.
- **I-6**: `current ≤ total`.
- **I-7**: The bottom bar is not composed while open.

## 4. Address suggestions (domain + presentation, memory only)

### `AddressSuggestion` (domain model)

| Field | Type | Notes |
|---|---|---|
| `url` | String | Identity; unique within a result list |
| `title` | String | May be empty → the UI shows the hostname (FR-018) |
| `source` | `SuggestionSource` | `Bookmark` or `History` |

### `AddressSuggestionSnapshot` (domain)

This is an ordered candidate list: bookmarks first (newest saved first), then history (newest visit first) with bookmarked addresses removed, and one entry per address. It is built once per editing session by `LoadAddressSuggestionsSourceUseCase` and released when editing ends.

### New repository reads (one-shot `suspend`, no Flow)

| Repository | Method | DAO query |
|---|---|---|
| `BookmarkRepository` | `getAllBookmarks(): List<Bookmark>` | `SELECT * FROM bookmarks ORDER BY created_at DESC, id DESC` |
| `HistoryRepository` | `getLatestVisitPerUrl(): List<HistoryEntry>` | the newest row per `url` (`GROUP BY url` keeping `MAX(visited_at)`), ordered by `visited_at DESC` |

Neither query takes user text, so there is no `LIKE` anywhere on this path (FR-017).

### `FilterAddressSuggestionsUseCase` (pure)

`(snapshot, query, limit = BrowserLimits.MAX_ADDRESS_SUGGESTIONS = 6) → List<AddressSuggestion>`

- An empty or blank query returns an empty list.
- A candidate matches when `title.contains(query, ignoreCase = true) || url.contains(query, ignoreCase = true)`.
- The order of the snapshot is preserved, and the filter stops at `limit`.

### `AddressSuggestionsUiState` (presentation, `@Immutable`)

| Field | Type |
|---|---|
| `suggestions` | `List<AddressSuggestion>`, stable through `compose_stability.conf` (`kotlin.collections.*` and `domain.model.*`, §V rule 5). No new dependency. |

The list is shown only when it is non-empty and an address entry is being edited with text that differs from the current page's address (FR-021).

**Invariants**

- **I-8**: No network I/O on this path.
- **I-9**: Nothing on this path is written, in any tab (FR-022).
- **I-10**: No address appears twice.
- **I-11**: Bookmarks precede history.

## 5. Pure helpers (presentation, unit-tested)

| Helper | Input → output |
|---|---|
| `DesktopUserAgent.from(defaultUa)` | the default WebView UA → the desktop UA (research R2) |
| `PageToolsAvailability.of(isHomeContent, loadingState)` | → the set of available `PageTool`s (research R9 table) |
| `ShareContent.of(url, title)` | → the text, plus a subject only when the title is not blank |
| `PrintJobName.of(title, url)` | → the title, else the hostname, else the url |

`PageTool` is an enum: `Find`, `Share`, `DesktopSite`, `TextSize`, `Print`.
