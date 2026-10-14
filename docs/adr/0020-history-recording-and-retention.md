# 0020. History is recorded per navigation and bounded by deletion

- Status: accepted
- Date: 2026-10-14

## Context

WebView callbacks do not arrive in the order the UI state suggests, and the History screen loads the
whole table: about 300 B per row, so 100,000 rows is about 30 MB against a ~96 MB heap on minSdk 24.

## Decision

- A visit is recorded with an explicit per-navigation token in `BrowserViewModel` (armed in
  `onLoadStarted`, consumed once in `onLoadFinished`, cleared in `onLoadFailed`), not inferred from
  UI state: by `onPageFinished` the state already shows the new URL as loaded.
- `onReceivedTitle` usually arrives after `onPageFinished`, so the row is written with the title
  known then and back-filled (`UpdateHistoryEntryTitleUseCase`) when the real title lands.
- There is no "keep forever". Rows older than the retention window are deleted, never hidden with a
  `LIMIT`, so anything absent from the list is absent from the database and search never misses a row
  that still exists.
- The sweep (`PruneOldHistoryUseCase`) runs once per process from `ThirtySixApplication`, not from a
  ViewModel, which would never run for someone who never opens History. Its window comes from the
  saved setting; a settings read failure is logged and retried next launch, never replaced by a
  hard-coded window.
- Changing the window writes the setting first and prunes second
  (`ChangeHistoryRetentionUseCase`). A failed prune returns `SavedPruneDeferred` and the next start-up
  sweep enforces the saved window.
- Filtering and day-bucketing run on the default dispatcher (`flowOn`); on main they gave 450 ms
  frames at 10,000 rows. Search is an in-memory `String.contains`, not SQL `LIKE` or Paging, so `%`,
  `_` and `'` are always literal.

## Consequences

- Incognito never records history (0007).
