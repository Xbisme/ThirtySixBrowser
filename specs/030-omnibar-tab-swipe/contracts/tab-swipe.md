# UI Contract: Address-Bar Tab Swipe

## Gesture

| Input | Field state | Result |
|---|---|---|
| Tap | not focused | Focuses the field for typing, as today. No tab change. |
| Long-press | any | Behaves as today. |
| Drag past slop, `|dx| ≥ 2|dy|` | not focused, setting on, bar on screen | Tab swipe starts. The field never focuses. |
| Drag | focused | Cursor and selection behave as today. No swipe. |
| Mostly vertical drag | any | No swipe. |
| Drag that starts in the page | any | Spec 028 only. |
| Find bar open | — | No swipe (the top bar is not shown). |

Direction, in a left-to-right layout:

- finger moves left: **next** tab comes in from the right;
- finger moves right: **previous** tab comes in from the left.

RTL mirrors this.

Release:

| Condition | Result |
|---|---|
| `|offset| ≥ 0.33 × lane width` and a tab is on that side | Commit |
| Flick ≥ 1,000 dp/s toward a side with a tab | Commit |
| Otherwise | Return |
| No tab on that side | Drag damped ×0.25, always return |

A second pointer, a change of the neighbour on that side, or a configuration change: return, with no switch.

## Surfaces during a drag

| Surface | Current tab | Incoming tab |
|---|---|---|
| Pill lane (clipped to the pill) | Moves with the finger | Ghost pill: host, or "Home", or the incognito label |
| Page area | Moves with the finger (scaled to the screen width) | Screenshot, or the seeded mesh, or the Home look, or the incognito surface |

The bars do not move vertically during a drag. The bottom bar does not move.

## After a commit

- The incoming tab becomes active through the same use case the Tabs screen uses.
- The preview stays until the new tab's first frame, at most 600 ms.

## Accessibility

| Custom action (on the pill and the Home field) | Shown when | Does |
|---|---|---|
| "Next tab" | `next != null` | Switches; announces the title, host or "Home"; for incognito, "Incognito tab" |
| "Previous tab" | `previous != null` | Same, for the previous tab |

The actions do not depend on the setting.

## Settings

| Row | Tag | Default |
|---|---|---|
| "Swipe the address bar to switch tabs" | `settings_tab_swipe_row` | On |

## Tabs screen

- Cards are shown in opening order, and the active card is marked in place.
- On open, the grid scrolls so the active card is visible.

## New strings (× 8 locales)

- `settings_tab_swipe_title`
- `settings_tab_swipe_summary`
- `browser_a11y_next_tab`
- `browser_a11y_previous_tab`
- `browser_a11y_switched_to_tab` (`%1$s`)
- `browser_a11y_switched_to_incognito_tab`
- `browser_tab_swipe_home_label`
