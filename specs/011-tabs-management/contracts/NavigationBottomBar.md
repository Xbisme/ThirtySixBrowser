# Contract: `NavigationBottomBar` (Spec 011 delta)

**Layer**: `presentation/browser/components/`
**File**: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBar.kt`

This document describes ONLY the deltas Spec 011 introduces. The Spec 008 surface (Back / Forward / Reload-Stop / Home buttons + always-visible bar + disabled-state handling) is unchanged.

## Callbacks bundle expansion (4 → 6 fields)

`NavigationBottomBarCallbacks` data class adds **2 new fields**:

```kotlin
data class NavigationBottomBarCallbacks(
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onHome: () -> Unit,
    val onTabsSwitcherClick: () -> Unit,      // NEW Spec 011 — single-tap → navigate(Tabs.route)
    val onTabsSwitcherLongClick: () -> Unit,  // NEW Spec 011 — long-press → CreateTabUseCase
)
```

Bundle becomes 6 fields — exactly at detekt's `LongParameterList.functionThreshold = 6` (PASSES; the rule fails on >6).

## New affordance: 5th `IconButton` with combined click

```kotlin
@OptIn(ExperimentalFoundationApi::class)  // file-level @OptIn
@Composable
fun NavigationBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    tabCount: Int,        // NEW Spec 011 parameter — drives BadgedBox count
    callbacks: NavigationBottomBarCallbacks,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        // ... existing 4 buttons (Back / Forward / Reload-Stop / Home) unchanged ...

        // 5th button — Tabs switcher
        BadgedBox(
            badge = { Badge { Text(tabCount.toString()) } },
            modifier = Modifier.testTag(TEST_TAG_NAV_TABS_SWITCHER),
        ) {
            // IconButton wrapped to support combinedClickable for long-press
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .combinedClickable(
                        onClick = callbacks.onTabsSwitcherClick,
                        onLongClick = callbacks.onTabsSwitcherLongClick,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.browser_action_tabs_switcher),
                        onLongClickLabel = stringResource(R.string.browser_action_new_tab),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,  // or Icons.Filled.Tabs equivalent
                    contentDescription = null,  // described via combinedClickable's onClickLabel
                )
            }
        }
    }
}

const val TEST_TAG_NAV_TABS_SWITCHER: String = "nav_tabs_switcher"
```

## New parameter: `tabCount: Int`

The bar now takes a `tabCount: Int` parameter to render the count badge over the switcher button. Source of truth: `BrowserViewModel.uiState.tabsCount` (NEW field — derived from the active-tab Flow's accompanying tab list, OR from a separate `getTabCount()` snapshot polled on Flow emission). Final shape decided in tasks.md; the contract is: BottomAppBar reads count from a passed-in `Int`, not from any direct repository read.

## Touch target + accessibility

- The wrapped `Box` uses `minimumInteractiveComponentSize()` to keep the 48×48dp touch target (Constitution §VIII).
- `combinedClickable` accepts `onClickLabel` + `onLongClickLabel` strings — both externalized via `stringResource(R.string.*)` (new keys `browser_action_tabs_switcher` + `browser_action_new_tab`, 8 locales each).
- `contentDescription = null` on the Icon because the parent's `onClickLabel` already describes the action (TalkBack reads the label first).
- Active-tab indicator (a small accent stroke on the badge or a tinted background) renders only when `tabCount > 0` — but since FR-017 guarantees ≥ 1 tab, the badge is always visible in practice.

## Test surface

| Test name | Scenario | File |
|-----------|----------|------|
| renders_5_buttons_with_tabs_switcher | Compose under `setContent { NavigationBottomBar(...) }` → assert all 5 testTags present | `NavigationBottomBarTest` (extend) |
| switcher_singleTap_invokesOnClick | tap the switcher button → onTabsSwitcherClick called once | `NavigationBottomBarTest` |
| switcher_longPress_invokesOnLongClick | longClick → onTabsSwitcherLongClick called once | `NavigationBottomBarTest` |
| switcher_badge_reflectsTabCount | tabCount=7 → badge text is "7" | `NavigationBottomBarTest` |

Total additions: **4 tests** (counted into the 25 new unit-test target — these are component-level Compose UI tests under `app/src/androidTest/`, similar to existing `NavigationBottomBarTest` from Spec 008).
