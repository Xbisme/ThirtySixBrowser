# Data Model: Swipe the Address Bar to Switch Tabs

There is no schema change. Room stays at v6.

## Tab (existing, `core/domain/model/Tab.kt`)

| Field | Use in 030 |
|---|---|
| `position` | Opening order. Normal tabs: `maxPosition + 1`, and the home seed is 0 (persisted). Incognito tabs: a running in-memory counter (R1 fix). |
| `createdAt` | First tiebreak for equal positions. |
| `id` | Last tiebreak, by magnitude: normal ids grow, and incognito ids count down from −1. |
| `lastActiveAt` | Still decides the active tab, which has the newest value. On a close, the neighbour can inherit it (R2). |
| `isIncognito` | Decides the kind. The swipe never crosses kinds. |

The KDoc on `position` changes from "only a tiebreaker" to "the order tabs are shown and swiped in".

## TabOrder (new, `core/domain/util/TabOrder.kt`, pure)

```text
inOpeningOrder(tabs: List<Tab>): List<Tab>
    sorted by position ASC, createdAt ASC, |id| ASC

neighbours(tabs: List<Tab>, activeId: Long?): TabNeighbours
    the same-kind tabs of the active one, in opening order → the one before and the one after
    activeId null or not found → TabNeighbours(null, null)
```

## TabNeighbours (new, `core/domain/model/TabNeighbours.kt`)

```text
data class TabNeighbours(val previous: Tab?, val next: Tab?)
```

## Close rule (changed, both tab repositories)

```text
closeTab(id):
    closed = tab(id)
    kindTabs = tabs of this repository, in opening order
    wasActive = closed.lastActiveAt == max(kindTabs.lastActiveAt)
    remove closed
    if wasActive and kindTabs had others:
        heir = the tab after closed, or the one before it if closed was last
        heir.lastActiveAt = max(heir.lastActiveAt, closed.lastActiveAt)
    normal: if the table is now empty → seed a home tab (unchanged)
    incognito: if the list is now empty → end the session (unchanged)
```

The `TabRepository.closeTab` KDoc changes to "the neighbour in opening order becomes the kind's active tab".

## Presentation state

### TabSwipeUiState (`feature/browser/presentation/browser/`)

```text
data class TabSwipeUiState(
    val previous: TabPreview? = null,
    val next: TabPreview? = null,
    val enabled: Boolean = AppDefaults.TAB_SWIPE_ENABLED,
)

@Immutable data class TabPreview(
    val tabId: Long,
    val isIncognito: Boolean,
    val isHome: Boolean,
    val host: String,          // "" for Home and incognito
    val title: String,         // "" for incognito: never shown or spoken
)
```

### TabSwipeAction / TabSwipeEvent

```text
sealed interface TabSwipeAction {
    data class SwitchTo(val tabId: Long, val announce: Boolean) : TabSwipeAction
}
sealed interface TabSwipeEvent {
    data class Announce(val preview: TabPreview) : TabSwipeEvent   // accessibility actions only
}
```

### TabSwipeState (UI-only, `@Stable`, remembered by the screen)

| Field | Kind | Changes |
|---|---|---|
| `offsetPx` | `mutableFloatStateOf` | Every frame. Read only in `graphicsLayer`. |
| `incoming` | `mutableStateOf<TabPreview?>` | Once per drag. |
| `settling` | `mutableStateOf<Boolean>` | Twice per drag. |
| `holdPreview` | `mutableStateOf<Boolean>` | Set on commit, cleared on the new tab's first frame or after 600 ms. |

State transitions:

```text
Idle ──slop passed, sideways, unfocused, enabled──▶ Dragging(side)
Dragging ──release ≥ COMMIT_FRACTION or flick toward a tab──▶ Committing ──slide done──▶ Holding ──first frame / 600 ms──▶ Idle
Dragging ──release short, no tab that side, 2nd pointer, neighbour changed──▶ Returning ──▶ Idle
(animations off: Committing and Returning take zero time)
```

## Settings

| Layer | Addition |
|---|---|
| `StorageKeys` | `TAB_SWIPE_ENABLED = booleanPreferencesKey("tab_swipe_enabled")` |
| `AppDefaults` | `TAB_SWIPE_ENABLED = true` |
| `UserSettings` | `isTabSwipeEnabled` |
| `SettingsRepository` | `setTabSwipeEnabled(Boolean)` |
| `SettingsAction` | `TabSwipeToggled(Boolean)` |

## Limits (`feature/browser/presentation/browser/TabSwipeLimits.kt`)

| Name | Value |
|---|---|
| `COMMIT_FRACTION` | 0.33 of the lane width |
| `FLICK_VELOCITY_DP_PER_S` | 1,000 |
| `EDGE_RESISTANCE` | 0.25 |
| `SIDEWAYS_RATIO` | 2 (`|dx| ≥ 2|dy|`) |
| `PREVIEW_HOLD_MAX_MS` | 600 |
