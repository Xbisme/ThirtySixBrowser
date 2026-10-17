# Quickstart: validating Tab History and Swipe Navigation

The commands are in `AGENTS.md`. Devices: `TA016_API36_16K`, plus a smoke on `TA016_API24`, with animations off.

## G1. Every commit

`detekt ktlintCheck testDebugUnitTest` and `compileDebugAndroidTestKotlin`, all green.

## G2. Data

- **Migration**: `AppDatabaseMigrationTest` migrates v1 → v6. Every existing row is kept, and `tab_sessions` is empty and writable.
- **DAO test**: deleting a tab removes its session (cascade).
- **Repository tests**:
  - an incognito save never reaches the DAO;
  - an over-bound state is rejected;
  - `retainOnly` and `clearAll`;
  - `clears` increments.
- **Codec test**: round trip; unknown version, truncated bytes and a non-bytes value all give `null`.

## G3. Device walk (SC-001 … SC-008)

1. **SC-001**: in 3 tabs, open 5 pages each, including youtube.com and facebook.com.
   - Close the app from Recents and reopen it. Each tab goes back through all 5 pages.
   - Repeat after `adb shell am kill` while the app is in the background.
2. **SC-002**: switch between the 3 tabs 10 times. Back and Forward reach the same pages each time.
3. **SC-003**: browse 2 pages in an incognito tab and close the app. `tab_sessions` has no row with `tab_id < 0`, and the database and files hold no incognito URL.
4. **SC-004**: close a tab, then use Fire, then clear history.
   - `tab_sessions` is empty after each, for the affected tabs.
   - After the history clear, the open tab has no Back.
5. **SC-005**: open 60 pages in one tab. The row is ≤ 256 KiB.
6. **SC-006**: 2 × 12 interleaved, speed-compiled cold starts against `main`. The median is within 5%.
7. **SC-007**: 20 swipes each way on pages with history. At least 19 work.
8. **SC-008**:
   - 50 vertical scrolls: no arrow.
   - Drags on a carousel page and on maps.google.com: no arrow.
   - With the swipe off in Settings: no arrow.
9. **RTL**: with the app set to an RTL locale, if one is added later, the direction mirrors. Today only the code path is checked by a unit test, since the 8 locales are LTR.

## G4. Release checks

`lintDebug`, `assembleRelease`, 16 KB alignment, the Compose report with zero unexplained non-skippable composables, the APK size, and CI 6/6.
