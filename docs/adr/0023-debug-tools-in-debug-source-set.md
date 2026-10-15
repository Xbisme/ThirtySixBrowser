# 0023. Debug tools live only in the debug source set

- Status: accepted
- Date: 2026-10-14

## Context

Performance and recovery tests need seeded data and hooks driven from `adb`, and Compose tests need a
host activity. None of this may reach a release build.

## Decision

- Seeders and hooks (`HistorySeeder`, `DownloadSeeder`, `TabSeeder`, `FireStepDelayReceiver`) and
  `HiltTestActivity` are in `app/src/debug`. Receivers are `exported="true"` so the shell uid can
  reach them with `adb shell am broadcast`; this is safe because the debug source set never compiles
  into a release build.
- `HiltTestActivity` is in the debug APK, not androidTest: declared in the test APK it resolves to a
  different process and `ActivityScenarioRule` fails.
- Seeders write through repositories so seeded data takes the production path. `HistorySeeder`
  writes at most 1,000 rows per broadcast and re-sends itself: a receiver has about 60 s before an
  ANR, and 10,000 rows in one broadcast took 60–67 s on API 24.
- `DownloadSeeder` seeds records, not transfers, with handles above 900,000,000 so they resolve
  through the file-presence fallback. Clearing deletes one by one; the repository has no clear-all.
- `TabSeeder` tops normal tabs up to the tab cap instead of creating `count` new tabs.
- `FireStepDelayReceiver` sets an in-memory delay before every Fire step, so the app can be
  force-stopped part-way to test interrupted-Fire recovery (0008).

## Consequences

- Release builds cannot be seeded (0017).
