# 0015. Fast-changing state stays out of composition

- Status: accepted
- Date: 2026-10-14

## Context

The browser screen draws load progress, sliding bars and swipe gestures every frame. Reading such state
in a composable body recomposes the whole screen per frame.

## Decision

- Load progress has its own flow (`PageViewModel.loadProgress`), held as `State` and read only in
  `ProgressLine`'s `drawBehind`; `uiState` changes only on load-state transitions.
- Swipe and drag offsets are plain float state read in `graphicsLayer`, settled with the suspend
  `animate()`; an `Animatable` updated by per-delta `snapTo` read stale values in `onDragStopped`.
- Desktop mode is read synchronously by tab id when a tab's WebView is built, never from `uiState`,
  which lags a tab switch by one emission.
- Compose cannot load an animated vector with `painterResource`: in-app UI uses
  `ic_brand_mark_static`, and `ic_brand_mark` (animated on API 31+) is only for the splash.
- Domain models are declared stable in `config/compose/stability.conf`; the file names
  `core.domain.model.*` and `feature.*.domain.model.*`.
- Find-in-page results have their own flow; the screen reads only whether find is open.
- Callers pass `ProgressLine` a lambda that reads the `State` itself (`{ state.value }`), never a value
  read in composition.
- O(n) list derivations (history filtering, download status resolution) run on the default
  dispatcher with `flowOn`; the state write stays on main.
- `AddressBar` ignores echoes of text it reported itself; a late echo while the main thread was busy
  overwrote newer typing.

## Consequences

- Every spec with UI attaches the Compose compiler report (0 unexplained non-skippable composables).
