# 0027. Browser state split over a page store

- Status: accepted
- Date: 2026-10-15

## Context

After ADR 0026, `BrowserViewModel` was still one holder for the whole browser screen: 514 lines,
29 actions and 14 dependencies. It held the page load, the address bar, Home, find in page,
downloads, the bookmark star, desktop mode and new tabs. `BrowserScreen.kt` was 615 lines.
`feature/browser` carried 26 size-rule suppressions. ADR 0026 left the split to a later decision.

These concerns all need the current page's URL, title, active tab and incognito flag. They also
need to know when a new page starts, because the find bar closes on a new load or a tab switch.
That shared state is what kept them in one class.

## Decision

- **One in-memory page store.** `CurrentPageRepository` (contract in `feature/browser/domain`,
  implementation in `data/repository`) holds `CurrentPage(url, title, activeTab, isIncognito,
  generation)` in a `StateFlow`.
  - It is bound `@ActivityRetainedScoped`, so every browser holder of one activity shares it and it
    survives rotation.
  - It persists nothing: tabs are still written by `UpdateActiveTabUrlAndTitleUseCase`.
  - `startNewPage()`, and any change of active tab id, clears the title and increments
    `generation`. A holder that must reset on a new page watches `generation`.
- **Five holders, one per concern.** Each follows ADR 0026: `onAction(XAction)` is the only input,
  and each has its own `UiState` and `XEvent`.

  | Holder | Owns |
  |---|---|
  | `PageViewModel` | load state, progress, back/forward, history recording, favicon and preview caches |
  | `AddressBarViewModel` | typed text, focus, submit and search, the Home overlay, Back to Home |
  | `FindInPageViewModel` | the find bar; closes when `generation` changes |
  | `DownloadRequestViewModel` | download requests and the storage-permission outcome |
  | `PageActionsViewModel` | bookmark star, desktop mode, new tab |

  `BrowserViewModel` was renamed `PageViewModel`, and `BrowserAction` became the flat `PageAction`.
  Accepted ADRs that mention the old names are left as written.
- **Writers.** Only these holders write to the store:

  | Writer | May write |
  |---|---|
  | `PageViewModel` | `url` from the page, `title`, `activeTab`, `isIncognito`, `startNewPage()` |
  | `AddressBarViewModel` | `url` = Home and `title` = "" on Back without history, nothing else |
  | the others | nothing; they read `page` |

  No holder keeps its own copy of a store value.
- **The screen's state stays one value.** `BrowserUiState` is assembled at `BrowserRoute` by
  `browserUiState(page, pageState, addressBarState, actionsState)`. The bars, the WebView and the
  back handlers keep reading one snapshot, so none of them needed to change.
- **Route and screen.** `BrowserRoute` takes three groups of holders:
  - `BrowserPageHolders`: the five above.
  - `BrowserToolHolders`: hub, suggestions, text size, protection.
  - `PageRequestHolders`: pop-up tabs, long-press menus, site permissions.

  Each group has a `remember…Holders()` that gets its holders from Hilt, and a test can replace any
  one of them. `BrowserScreen(state, sources, actions, hosts)` is split into one file per part.
  What the screen keeps for itself (focus, bar heights, the bars' scroll, open sheets) is
  `BrowserScreenUi`.

## Consequences

- No browser holder exceeds 9 dependencies or 11 actions. The largest browser screen file is
  201 lines, apart from the WebView and chrome client.
- Size suppressions in `feature/browser` went from 26 to 21. `PageViewModel` keeps its own: each of
  its functions handles one WebView callback.
- Tests of a concern build only that holder over a fixture store. A test that crosses concerns (a
  submit followed by the page's URL change) drives two holders over one store. The browser screen's
  instrumented tests build the store-sharing holders through one androidTest fixture.
- A new browser concern gets its own holder and reads the store. It writes to the store only after
  this ADR's writers table is changed by a new ADR.
- The unused `BaseViewModel` was deleted.
