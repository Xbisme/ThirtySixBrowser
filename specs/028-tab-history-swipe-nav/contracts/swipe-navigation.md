# Contract: in-page swipe navigation

## Input

`BrowserPageView` (a `WebView`) reports a gesture only when all of these hold:

1. Swipe navigation is enabled in Settings (FR-018).
2. The tab shows a web page: not Home, not the incognito start page. The address bar and the find bar are not in use (FR-015).
3. The drag is mostly horizontal: `|dx| ≥ 2·|dy|` after touch slop.
4. The page did not consume the drag: an overscroll with `clampedX` arrived in that direction (research R5), or the R5 fallback holds.

## Output

| Phase | Effect |
|---|---|
| Drag | `SwipeNavigationState.progress` follows `|dx| / threshold`, clamped to 0..1 |
| Release with `progress == 1` and `available` | `goBack()` or `goForward()` |
| Release otherwise, or a vertical takeover, or a second finger | cancel; the indicator animates out |

**Direction**:

| Layout | Drag right | Drag left |
|---|---|---|
| LTR | Back | Forward |
| RTL | Forward | Back |

**Threshold**: `SwipeNavigationLimits.THRESHOLD`, a named dp constant (constitution V).

**Unchanged**: the system Back gesture, the Back button and the hub's Back and Forward (FR-016, FR-017).
