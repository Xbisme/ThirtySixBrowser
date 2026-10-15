# 0018. Downloads: the platform owns the transfer, the app records it after enqueue

- Status: accepted
- Date: 2026-10-14

## Context

Downloads run in the platform's `DownloadManager`, which keeps live status but can forget a transfer
(provider data cleared, old rows pruned). The app needs its own list that survives that, without a
repository depending on a system service.

## Decision

- Hybrid source of truth: Room keeps only metadata (`DownloadRecordEntity`: no live state, no
  incognito column, no unique constraint on the transfer handle, no foreign keys, no index), and the
  platform keeps live status. `ResolveDownloadStatusUseCase` joins them; no repository touches a
  system service.
- `StartDownloadUseCase` enqueues through the gateway first and writes the record only afterwards, so
  every row is backed by a real transfer and an unknown handle always means "forgotten", never
  "never started". Incognito is not a parameter: incognito downloads are recorded like any other.
- A forgotten handle resolves from file presence only, never stored: Complete if the file exists,
  Missing otherwise. `DownloadedFileProbe` checks the real file in the public Downloads folder first
  and the recorded content URI only as a fallback, because the URI dies with the provider's row.
  A completed record's URI and final name are back-filled once (the platform renames collisions).
- Missing covers both a pruned failed download and a finished one whose file was deleted.
- Cancel removes the row as well as the transfer; the platform cannot distinguish a cancel from a
  forgotten download afterwards, so `Cancelled` is modelled but unreachable.
- "Remove from list" is refused for in-flight entries (UI and use case) and cancels nothing: removing
  the row would orphan a running transfer.
- Deleting a file goes through `DownloadManager.remove`, then verifies the file is gone;
  `contentResolver.delete` removed only the provider row on API 24.
- File names are derived and sanitised in pure Kotlin (`SanitizeDownloadFileNameUseCase`), not with
  `URLUtil`; the server controls the header and largely the URL, so sanitising never trusts derivation.
- `WRITE_EXTERNAL_STORAGE` is declared with `maxSdkVersion=28` and requested only at the first
  download on Android 7–9. `MANAGE_EXTERNAL_STORAGE` is forbidden; `POST_NOTIFICATIONS` is not
  declared because the system download service posts notifications under its own identity.

## Consequences

- Status polling runs only while the Downloads screen collects (`WhileSubscribed`) and stops when
  nothing is in flight; resolution runs on the default dispatcher.
- Tests that check a forgotten download look for the file by name: `pm clear` on the provider kills
  the URI while the file stays.
