# 0030. Tabs are shown and swiped in opening order

- Status: accepted
- Date: 2026-10-17

## Context

The Tabs screen listed tabs most recently used first, and every switch moved a tab to the front.

Spec 030 adds a swipe on the address bar to reach the previous or next tab. Over a most-recently-used order:

- the order changes on every switch;
- two swipes the same way bounce between the same two tabs.

The owner chose opening order for both the swipe and the Tabs screen, as DuckDuckGo, Chrome and Safari do.

The app has no "active" column. The active tab is the one with the newest `lastActiveAt`. `PageViewModel`, `ObserveAllTabsUseCase`, `TabsViewModel`, the entry point and the incoming-link use case all rely on that rule, or on the list head being the active tab.

## Decision

- **Order.** Tabs are shown and swiped in opening order: `position`, then `createdAt`, then the id's magnitude, through `core/domain/util/TabOrder`.
  - Normal tabs already store `position` as `maxPosition + 1`.
  - Incognito tabs take a running in-memory counter.
  - No schema change.
- **Active tab.** "Active" stays the newest `lastActiveAt`. The repositories keep emitting most recent first. Only the places that show or walk the order apply `TabOrder`.
- **Closing the active tab.** The tab after it becomes active, or the tab before it when it was the last. The heir inherits the closed tab's `lastActiveAt` rather than taking the current time. A normal heir then never jumps ahead of an active incognito tab.
- **Recent tabs on Home** stay most recent first. They are a different list.

## Consequences

- Opening a tab never reorders the Tabs screen. The screen scrolls to the active card when it opens.
- Every close path follows the same rule, because it lives in the two repositories' `closeTab`. That covers the Tabs screen, popups closing themselves, the external-app tab and `CloseTabUseCase`.
- Manual reordering would write `position`. It is not part of this decision.
