# Quickstart: validating Page Tools (Spec 022)

How to prove the feature works end to end. The contracts are in [contracts/PageToolsContracts.kt](contracts/PageToolsContracts.kt) and the state rules in [data-model.md](data-model.md).

## Prerequisites

- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`; without it Gradle fails silently.
- AVDs: `TA016_API36_16K` (16 KB, API 36) and `TA016_API24` (minSdk). A Galaxy A55 for the real-device gates.
- Run `adb shell am force-stop com.raumanian.thirtysix.browser.debug` before `connectedDebugAndroidTest`.
- Clear any per-app language before a device run (`cmd locale set-app-locales … --locales ""`), because `SettingsScreenThemeTest` fails if the app language differs from the device's.
- Local fixtures: serve `specs/022-page-tools/fixtures/` (created in tasks) with `python3 -m http.server 8022` and `adb reverse tcp:8022 tcp:8022`. ⚠️ `adb reverse` is dropped by every Gradle device run, so re-run it after `connectedDebugAndroidTest`.
  - `find.html` holds a known count of a test word: 50 × "lorem", plus Vietnamese and Cyrillic words for the case-folding check.
  - `ua.html` echoes `navigator.userAgent`, `navigator.userAgentData?.mobile` and `window.innerWidth`.

## Automated gates

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew detekt ktlintCheck
./gradlew connectedDebugAndroidTest   # on TA016_API36_16K; also on API 29 in CI
./gradlew assembleRelease             # compare the APK size with main (SC-009: ≤ +100 KB); 16 KB check unchanged
./gradlew assembleRelease -PcomposeReports=true   # Compose report: zero unexplained non-skippable composables in touched files
```

Expected results:

- Unit tests are green, including the new tests named in research R11.
- No new `.so`.
- `git grep -n "LIKE" -- app/src/main/` shows no new hit.

## Device gates

| Gate | Steps | Pass when |
|---|---|---|
| **G1 Find** (US1, SC-002) | `find.html` → hub → Find in page → type "lorem" | "1/50" within 1 s; all highlighted; Next ×50 wraps to 1; Previous from 1 → 50; "zzz" → "0/0" with Next/Previous disabled |
| **G2 Find closing** (FR-010, FR-011) | Open find → Back; open again → tap a link; open again → switch tab; open again on a page that calls `alert()` from a timer | Each of the first three closes the bar and leaves no highlight; Back did **not** navigate the page; the bottom bar returns. The `alert()` appears above the find bar, and the bar is still open after the dialog is dismissed (spec Edge Cases) |
| **G3 Find survives** (FR-012) | Find "lorem", go to match 7 → rotate → change the app language | The bar is still open with "lorem" and 7/50 |
| **G4 Find, Unicode** | Find "TIẾNG" on a page containing "tiếng"; find "ПРИВЕТ" on a page containing "привет" | Both match |
| **G5 Suggestions** (US2) | Seed 3 bookmarks + history (debug `HistorySeeder`, 10,000 rows) → focus the address bar → type "kot" | ≤ 6 rows, bookmarks first, no duplicate address; a tap loads it and the keyboard closes |
| **G6 Literal characters** (FR-017) | Bookmark a title with `50%_off`; type `%`, then `_`, then `5_` | Only entries that literally contain those characters appear |
| **G7 No network** (SC-004) | Proxy or `tcpdump` on the AVD; type 20 different texts without submitting | 0 requests contain the typed text |
| **G8 Suggestions timing** (SC-003) | 10,000 history + 500 bookmarks; type 10 characters quickly | Every character appears within 100 ms and none is dropped; the list updates within 200 ms (screen recording via `adb emu screenrecord`) |
| **G9 Home + incognito** (R6, FR-022) | Type on Home's search field; then in an incognito tab | Suggestions appear in both; after the incognito session, History shows nothing new |
| **G10 Share** (US3) | Share to Keep / Messages, normal and incognito; a page with an empty title | The received text is exactly the address; the subject is the title, or absent |
| **G11 Text size** (US4, SC-005) | Settings → Text size → 150%; hub tile shows "150%"; 2 tabs open; relaunch; Clear browsing data | Both tabs enlarged with no reload (the progress line never moves); 150% survives relaunch **and** clear data; incognito uses 150% too |
| **G12 Desktop** (US5, SC-006) | `ua.html` → Desktop site on; then 5 real sites (e.g. wikipedia.org, reddit.com, bbc.com, github.com, amazon.com), tapping 2 links within each | `ua.html` shows no "Mobile"/"wv" and `mobile=false` where supported (`innerWidth` is recorded for diagnosis only; the spec promises no pixel width); 5/5 sites serve the desktop layout and **stay desktop after the 2 links** (FR-026); a new tab is mobile; the tab returns in mobile mode after `am kill` |
| **G13 Print** (US6, SC-007) | Print 3 pages to "Save as PDF"; Print on a loading page; cancel | 3 PDFs with the page text; the job name is the title or the hostname; no Print tile while loading; cancel returns unchanged |
| **G14 Availability** (FR-001, FR-002, R9, SC-001) | Hub on Home, on the incognito start page, on a loading page, on an error page; then from a loaded page, count the taps to reach each of the five tools | The tiles match the research R9 table; each tool is reached in ≤ 2 taps |
| **G15 Locales** (SC-008) | All 8 languages at 360dp: hub page-tools rows, find bar, suggestions, Text size row and chooser | Everything translated, nothing clipped (DE first) |
| **G16 API 24** | G1, G11, G12 and G13 on `TA016_API24` | Same results; UA metadata is skipped silently if unsupported |
| **G17 Recomposition** (§V rule 9) | Layout Inspector: type 10 characters into find, then into the address bar | The WebView host and the bottom/top chrome do not recompose per keystroke or per find result |

Screen-reader checks (FR-036) go to a person, as in Specs 016–021. The ones to check: the find count is announced; the Desktop site tile is read as "on"/"off"; each suggestion is read with its title and address.
