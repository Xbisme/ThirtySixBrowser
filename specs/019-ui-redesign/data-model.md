# Data Model: UI Redesign (Spec 019)

No stored data changes. **Room stays at schema v2**; DataStore keys are unchanged. Everything below is either a reserved value, a derived UI model, or a theme token.

## 1. Reserved value

| Name | Value | Meaning |
|---|---|---|
| `UrlConstants.HOME_PAGE_URL` | `thirtysix:home` | A tab whose content is the Home page (or, for an incognito tab, the incognito page). Persisted in the existing `tabs.url` column. Never loaded into a WebView, never recorded in history, never shown in the address well. |

`UrlConfigModule`'s `@Named("default_home_url")` provides `HOME_PAGE_URL`, replacing `https://www.google.com/` (Spec 008 FR-016). `DEFAULT_HOME_URL` is removed once nothing reads it.

## 2. Browser state (in memory)

`BrowserUiState` (existing, immutable) changes:

| Field | Change | Notes |
|---|---|---|
| `loadingState` | `Loading` loses its `progress` field | Progress moves to its own flow (R8) |
| `homeOverlay: Boolean` | **new**, default `false` | Home pressed on a loaded page; Back clears it |
| `cameFromHome: Boolean` | **new**, default `false` | Set on a Home tab's first navigation; consulted when the WebView cannot go back |
| `isHomeContent: Boolean` | **new, derived** | `activeTab.url == HOME_PAGE_URL || homeOverlay` |

New flows on `BrowserViewModel`, deliberately **outside** `uiState`:

| Flow | Type | Emits |
|---|---|---|
| `loadProgress` | `StateFlow<Float>` (0f..1f) | every `onProgressChanged`; resets to 0 on load start and finish |

`ChromeScrollState` (UI-owned, not in the ViewModel):

| Field | Type | Rule |
|---|---|---|
| `offsetPx` | `Float`, snapshot state | clamped to `0..maxOffsetPx`; 0 = bars fully shown |
| `maxOffsetPx` | `Float` | top-bar height + bottom-bar height, measured once |
| `onScrollDelta(dy)` | function | `offset += dy` (down hides, up shows), clamped |
| `show()` | function | snaps to 0 — on load start, error, keyboard open, non-scrollable page, top of page |

### State transitions — Home inside a tab

```
new tab ──► [Home] ──submit / tap Saved·Recent card or row──► [Page, cameFromHome=true]
[Home] ──tap Tab card──► that tab becomes active (this tab stays on Home)
[Page] ──Home button──► [Page + homeOverlay] ──Back──► [Page]
[Page, cameFromHome] ──Back with no WebView history──► [Home]
[Page, !cameFromHome] ──Back with no WebView history──► leave app (unchanged, Spec 008)
incognito tab at HOME_PAGE_URL ──► [Incognito page] (never the Home page)
```

## 3. Home page models (derived, `@Immutable`)

### `HomeUiState`

| Field | Type | Source |
|---|---|---|
| `greeting` | `GreetingPeriod` | `GreetingPeriod.of(LocalTime.now(clock))`, on resume |
| `shortcuts` | `List<HomeShortcut>` | ≤ `BrowserLimits.HOME_SHORTCUT_LIMIT` (8) |
| `recent` | `List<HistoryEntry>` | ≤ `BrowserLimits.HOME_RECENT_LIMIT` (5) |
| `searchEngine` | `SearchEngine` | settings, for the engine initial in the search bar |

### `HomeShortcut`

| Field | Type | Rule |
|---|---|---|
| `stableKey` | `String` | `"tab:<id>"`, `"bookmark:<id>"` or `"history:<id>"` — lazy `key` |
| `kind` | `ShortcutKind` (`Tab`, `Saved`, `Recent`) | lazy `contentType`; the card's tag |
| `title` | `String` | tab/bookmark/history title, falls back to host |
| `url` | `String` | target |
| `host` | `String` | shown under the title |
| `tabId` | `Long?` | set only for `Tab` — tapping activates that tab |
| `screenshotKey` | `Long?` | tab id when a cached screenshot exists |
| `colorSeed` | `Int?` | ARGB average of the favicon, when no screenshot |

**Build rule** (`ObserveHomeContentUseCase`): take open normal tabs except the active one (most recently active first), then recent bookmarks, then recent history; drop any whose URL already appeared; drop `HOME_PAGE_URL`; cap at 8. Incognito tabs are excluded before anything else.

### Tab card for a tab at `HOME_PAGE_URL`

| Part | Value |
|---|---|
| Title | `tabs_default_title` (existing key, "New tab") |
| Art | the hero mesh (`heroDeep`/`heroViolet`/`heroRose`), never a screenshot |
| Favicon | the brand mark in place of a site favicon |
| Host line | none |

An incognito tab at `HOME_PAGE_URL` uses the existing incognito card (mask on `surfaceIncognito`). (analysis U4)

### `GreetingPeriod`

`Morning` (05:00–11:59) · `Afternoon` (12:00–17:59) · `Evening` (18:00–04:59). Maps to `home_greeting_morning|afternoon|evening`.

## 4. Repository and DAO additions (queries only)

| Layer | Addition |
|---|---|
| `BookmarkDao` | `observeRecent(limit: Int): Flow<List<BookmarkEntity>>` — `ORDER BY created_at DESC LIMIT :limit` |
| `HistoryDao` | `observeRecent(limit: Int): Flow<List<HistoryEntryEntity>>` — `ORDER BY visited_at DESC LIMIT :limit` (uses the existing `visited_at` index) |
| `BookmarkRepository` | `observeRecent(limit)` |
| `HistoryRepository` | `observeRecent(limit)` |

No new table, column or index. `app/schemas/…/2.json` is unchanged.

## 5. Theme tokens

Source of truth: the design system's `tokens.json` (copied into `specs/019-ui-redesign/assets/tokens.json` at implementation time). Kotlin home: `presentation/theme/Color.kt` (light/dark values), `ThirtySixColors.kt` (extended), `Shape.kt`, `Type.kt` (unchanged families, adjusted letter-spacing on headline styles), `Spacing.kt` (unchanged 4/8/16/24/32).

| Group | Tokens |
|---|---|
| M3 roles | primary, onPrimary, primaryContainer, onPrimaryContainer, surface, background, surfaceContainerLow, surfaceContainer, surfaceVariant, onSurface, onSurfaceVariant, outline, outlineVariant, error, onError, scrim |
| Extended | fabStart, fabEnd, heroDeep, heroViolet, heroRose, glass, glassStroke, onGlass, onGlassMuted, surfaceIncognito, onIncognito, positive, tileSky/Ink, tileSun/Ink, tileMint/Ink, tilePeach/Ink |
| Shapes | extraSmall 6, small 10, medium 14, large 20, extraLarge 28, sheet 32, full |
| Elevation | soft (2-layer), bar (upward), fab (violet glow + halo) |

**Invariants**
- **INV-1**: No colour literal outside `Color.kt` / `ThirtySixColors.kt` (Constitution §III); the web-page mock colours on the canvas are not app UI.
- **INV-2**: The hero's base and rose glow never come from the dynamic scheme (clarification Q2).
- **INV-3**: `surfaceIncognito` is identical in light and dark themes.

## 6. Icons

Material Symbols Rounded, weight 300, grade −25, 24dp; `F` = filled variant also needed.

`home`, `home F`, `search`, `add`, `grid_view`, `tab`, `bookmark`, `bookmark F`, `bookmarks`, `history`, `download`, `downloading`, `settings`, `arrow_back`, `arrow_forward`, `refresh`, `close`, `lock`, `domino_mask`, `domino_mask F`, `contrast F`, `translate F`, `travel_explore F`, `mop F`, `palette F`, `schedule F`, `cookie`, `shield`, `phone_android F`, `chevron_right`, `more_vert`, `delete_sweep`, `delete F`, `folder`, `folder F`, `create_new_folder`, `description`, `folder_zip`, `image`, `movie`, `check`, `check_circle`, `history_toggle_off`, `cookie_off`, `screenshot_monitor`, `smartphone`, `warning`, `content_copy`, `edit`, `drive_file_move`, `open_in_new`, `auto_awesome`.

Mirrored in RTL: none (no RTL locale ships), but `arrow_back`/`arrow_forward`/`chevron_right` carry `android:autoMirrored="true"` for safety.

## 7. Strings (new keys, all 8 locales)

`home_greeting_morning`, `home_greeting_afternoon`, `home_greeting_evening`, `home_privacy_line`, `home_recent_title`, `home_recent_see_all`, `home_shortcut_tag_tab`, `home_shortcut_tag_saved`, `home_shortcut_tag_recent`, `home_search_hint` (may reuse `browser_address_bar_hint`), `hub_privacy_banner`, `hub_incognito_banner_title`, `hub_incognito_banner_body`, `hub_quick_settings_title`, `hub_tile_theme`, `hub_tile_language`, `hub_tile_search`, `hub_tile_clear_data`, `hub_downloads_in_progress_cd`, `nav_search`, `nav_menu`, `tabs_chip_open`, `tabs_chip_incognito`, `incognito_page_title`, `incognito_page_subtitle`, `incognito_page_section_title`, `incognito_page_point_history`, `incognito_page_point_cookies`, `incognito_page_point_screenshots`, `incognito_page_footnote`, `onboarding_action_start_browsing`.

Reused unchanged: `settings_about_privacy_statement` (onboarding), `bookmarks_*`, `history_*`, `downloads_*` state words, `settings_*` titles, `tabs_*` actions.

Removed if orphaned after the hub replaces it: `browser_action_more` and the overflow-menu item labels.
