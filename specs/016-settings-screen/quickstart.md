# Quickstart: Settings Screen — Validation Guide

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md) · **Data model**: [data-model.md](data-model.md)

This guide proves the feature end to end. It references the contracts and data model instead of repeating them, and contains no implementation code. Automated gates run first; the manual gates **G1–G12** need the two AVDs below.

---

## Prerequisites

| Item | Detail |
|---|---|
| JDK | `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before any Gradle command |
| minSdk AVD | `TA016_API24` — Android 7.0 |
| target AVD | the API 36 AVD used in Spec 015 — Android 16 |
| 16 KB AVD | an API 35+ system image with a **16 KB page size** — Constitution Testing Gate 8, used in G12 |
| App id | the debug build installs as `com.raumanian.thirtysix.browser.debug`; `am start`, `pm clear` and `cmd locale` must use that id |
| Web engine version | record it on each AVD before G5: `adb shell dumpsys webviewupdate` → "Current WebView package" |
| Seeders (debug only) | `HistorySeeder` (Spec 014) and the new `TabSeeder` (research R15) |
| Site-data fixture | see **Fixture** below |

**Emulator pitfalls already paid for in Specs 014 and 015** — check these before blaming the app: always read `adb install` output (a full `/data` fails silently); take a screenshot if typed input goes nowhere (a keyboard tutorial overlay can swallow keystrokes); on API 24, revoking one storage permission does nothing because it shares a group with its sibling.

### Fixture — a local page that stores every kind of site data

Serve a small static page from a scratch directory with `python3 -m http.server 8000`, then run `adb reverse tcp:8000 tcp:8000` and open `http://localhost:8000/` in the app. `localhost` counts as a secure context, which service workers require — `10.0.2.2` does not.

On load the page MUST:
1. Set a cookie that survives restarts (an explicit expiry), and write a `localStorage` key and an IndexedDB database.
2. Register a service worker that puts one response into Cache Storage.
3. Display, as plain text: cookie present / `localStorage` key present / IndexedDB database present / service-worker registration count / Cache Storage entry count.
4. Load one image served with a long `Cache-Control: max-age`, so that the server's request log shows whether the web cache was used.

The server's request log is the evidence for the web-cache checks: a cached image produces no request on reload; an emptied cache produces one.

---

## A. Automated gates

Run on the branch before any manual gate.

```bash
./gradlew testDebugUnitTest lintDebug detekt ktlintCheck assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest          # CI emulator job, or a local AVD
.specify/scripts/bash/verify-16kb-alignment.sh
```

Expected:
- All green. The **detekt baseline is unchanged**, and `lintDebug` passes `MissingTranslation` / `ExtraTranslation` with the non-translatable language names in place (research R11).
- Unit tests cover at least the behaviours below — each maps to a contract clause, so a failure names the broken promise:

| Area | Must prove | Contract |
|---|---|---|
| `SettingsMapper` | missing / unknown retention decodes to 90 days; missing dynamic color decodes to `true` | [data-model §1](data-model.md) |
| `ChangeHistoryRetentionUseCase` | write-then-prune order; `NotSaved` never prunes; prune failure → `SavedPruneDeferred` | [SettingsUseCases.kt](contracts/SettingsUseCases.kt) |
| `PruneOldHistoryUseCase` | cutoff comes from the persisted window, not a constant | [SettingsUseCases.kt](contracts/SettingsUseCases.kt) |
| `ClearBrowsingDataUseCase` | fixed category order; discard **before** wipe; `Partial` counts as cleared; web-cache step skipped after `Complete`; every category attempted after a failure | [SettingsUseCases.kt](contracts/SettingsUseCases.kt), [WebDataCleaner.kt](contracts/WebDataCleaner.kt) |
| `CookieJarSnapshotManagerImpl` | discard replaces a held snapshot with `EMPTY` and a subsequent restore still wipes; discard with nothing held is a no-op | [ExistingInterfaceAmendments.kt](contracts/ExistingInterfaceAmendments.kt) |
| `SetAppLanguageUseCase` | re-selecting the current language never calls the platform | [SettingsUseCases.kt](contracts/SettingsUseCases.kt) |
| `AppLanguage` | the eight tags equal the `<locale>` entries in `locales_config.xml` | [data-model §3](data-model.md) |
| `SettingsViewModel` | shortening opens the confirmation and lengthening does not; the clear dialog opens with all three selected and confirm is disabled when empty; events for every outcome | [data-model §6](data-model.md) |

- Instrumented tests cover: **DataStore read/write of both new keys on a real device** (`SettingsDataStoreInstrumentedTest` — Constitution §VI requires instrumented DataStore coverage); the cookie restore-after-discard wipe; and, in Compose, the overflow menu listing Settings fourth, each row's current value, each chooser, the dynamic color row hidden when unsupported, the retention warning, and the clear dialog's confirm gating and in-progress lock.

---

## B. Manual gates

Record PASS / FAIL / DEFERRED with device, OS build and web-engine version in `tasks.md`.

### G1 — Reach Settings; theme (US1 · FR-001 – FR-008 · SC-001, SC-002)

On **both** AVDs:
1. Install and launch. The app opens without a crash — a wrong window-theme parent fails immediately at launch (research R2).
2. On a loaded page, open the overflow menu: Settings is listed after Bookmarks, History and Downloads, and the Settings screen is reached in **2 taps**.
3. Choose Light, Dark and System in turn while recording with `adb shell screenrecord`:
   - every visible surface changes within **100 ms** — step through the recording from the frame where the option is selected to the first frame fully in the new theme; at 60 Hz that is no more than 6 frames (SC-002);
   - the screens are **not rebuilt**: the Settings list keeps its scroll position and no blank frame appears — compare with a language change in G4, which does rebuild them;
   - back in the browser, the same tab is open at the same address. The page itself reloads on return, exactly as it does after Bookmarks or History, because leaving the browser screen always releases its web view (FR-004).
4. Force-stop and relaunch: the last choice holds.
5. API 36 only: with System selected, run `adb shell cmd uimode night yes` and then `no`; the app follows. On API 24, which has no system-wide dark mode, record what System resolves to.

### G2 — Dynamic color (US6 · FR-009, FR-010)

1. **API 36**: dynamic color shows as on for a fresh install; turn it off → the app switches to its own teal palette immediately, in both light and dark themes; relaunch → still off.
2. **API 24**: no dynamic color control is shown.

### G3 — Search engine (US2 · FR-011, FR-012 · SC-003)

For each of Google, DuckDuckGo and Bing, submit `cà phê sữa`, `東京タワー` and `weather & forecast` from the address bar: 9/9 results pages belong to the chosen engine. A results page left open in another tab is unchanged after switching engines. The choice survives a relaunch.

### G4 — App language (US3 · FR-013 – FR-019 · SC-004, SC-005, SC-006)

On **both** AVDs, with **3 normal tabs and 1 incognito tab** open:
1. Choose each of the eight languages and then Follow system. Each time: the whole app is in the new language within **1 second**, measured from a screen recording as in G1 (SC-004), with no restart; you are **still on the Settings screen** afterwards (research R12); all four tabs are still open; the incognito tab still shows its indicator and screenshots are still blocked while it is active.
2. The language list shows the same eight self-named entries whatever language the app is in (FR-014).
3. Re-select the language already active: nothing is rebuilt and no page reloads (FR-006).
4. With Follow system selected, change the device language to one the app does not support: the app appears in English. Then set a regional variant such as Canadian French: the app appears in French.

On **API 36 only** — the system-settings round trip (SC-005), three times in each direction:
5. `adb shell cmd locale set-app-locales com.raumanian.thirtysix.browser.debug --locales vi`, return to the app: Settings shows Tiếng Việt as selected.
6. Choose English in the app, then `adb shell cmd locale get-app-locales com.raumanian.thirtysix.browser.debug`: it reports `en`.

### G5 — Clear browsing data (US4 · FR-025 – FR-033 · SC-007 · research R5, R6)

On **both** AVDs, and first record the web-engine version. The app logs which site-data path ran; record whether it was the complete path or the fallback.

Setup: open the fixture (all five indicators present), visit three other sites, bookmark one page, keep at least two downloads from Spec 015, and note the counts of tabs, bookmarks and downloads.

1. Open **Clear browsing data**: all three categories are selected. Deselect all: confirm is disabled.
2. **History only** → History is empty; reloading the fixture still shows every indicator present, and the fixture image is served from cache (no new request in the server log).
3. **Cached images and files only** → the fixture still shows its cookie, but the image produces a new request in the server log; tab previews and site icons show placeholders until pages are revisited.
4. **Cookies and site data only** → reload the fixture:
   - **Complete path**: cookie, `localStorage`, IndexedDB, service worker **and** Cache Storage all absent. The image also produces a new request, because the web cache was emptied with them (spec A16).
   - **Fallback path**: cookie, `localStorage` and IndexedDB absent; the service worker and Cache Storage entries **remain**. Record this as the expected A17 limitation, not a failure.
5. **All three**: the conditions of steps 2–4 hold together; the counts of tabs, bookmarks and downloads, and every downloaded file, are unchanged (FR-031); a completion message appears. **Run this step three times** on each AVD, recreating the fixture's data between runs (SC-007).
6. Tap confirm repeatedly while a clear is running: only one clear runs, and the dialog cannot be dismissed until it finishes (FR-032).

### G6 — Incognito cookies stay cleared (FR-030 · SC-008 · research R7)

On **both** AVDs, three runs each:
1. In a normal tab, open the fixture so its cookie is set.
2. Open an incognito tab — this sets the normal-browsing cookies aside.
3. In Settings, clear **cookies and site data**.
4. Close the last incognito tab, then reload the fixture in the normal tab: the cookie is **absent**.

**Control run** (once per AVD, to prove the gate can fail): repeat steps 1, 2 and 4 **without** step 3 — the cookie must come back, showing that Spec 012's restore really runs.

### G7 — History retention (US5 · FR-020 – FR-024 · SC-010)

On **API 24** — every one of the four windows is exercised (SC-010):
1. Fresh install: retention shows 90 days, and the chooser offers exactly 7, 30, 90 and 180 days.
2. Seed history spanning 200 days: `adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_HISTORY -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.HistorySeeder --ei count 300 --ei days 200`, then relaunch so the start-up sweep applies the **90-day** window: nothing older than 90 days remains.
3. Choose 30 days → a warning appears → **cancel**: retention still reads 90 days and History is unchanged.
4. Choose 30 days → **confirm**: History immediately shows nothing older than **30 days**, including when History was already further back in the navigation stack; relaunch: still nothing older than 30 days.
5. Choose 7 days → confirm: nothing older than **7 days** remains, immediately and after a relaunch.
6. Choose 180 days: **no** warning is shown. Re-seed history spanning 200 days and relaunch: nothing older than **180 days** remains.
7. Re-seed **10,000** entries spanning 200 days, choose 90 days and confirm: nothing older than 90 days remains, and **no ANR** occurs — no ANR dialog, and no `ANR in` line in logcat (FR-022).

### G8 — Performance (SC-009 · Constitution §V)

**Gating part — clearing, API 24 AVD, debug build:**
1. Seed 10,000 history entries (`HistorySeeder --ei count 10000`) and top the tab count up to 50 (`TabSeeder`, which never exceeds `MAX_TABS` — research R15); open the fixture once.
2. Clear all three categories while recording with `adb shell screenrecord`. Pass if it completes with **no ANR** (no ANR dialog, and no `ANR in` line in logcat), the dialog shows work in progress until it finishes, and the progress indicator keeps animating. Record the elapsed time from the recording, from the confirm tap to the completion message; the per-step durations the web-data cleaner writes to logcat are supplementary.

**Non-gating part — clearing, Pixel 5-class hardware, release build:** if such hardware is available, create comparable data through the UI and record whether clearing completes within 3 seconds, measured from a screen recording as above. The seeders exist only in the debug build, which installs under a different application id, so they cannot populate a release install (research R15). **Never substitute the emulator timing for this figure**; if no hardware is available, record "not measured — no hardware".

**Settings list scrolling — Constitution §V, indicative only** (run in the Polish phase, once every row exists): on the API 36 AVD, set a large font so the list scrolls (`adb shell settings put system font_scale 1.3`), run `adb shell dumpsys gfxinfo com.raumanian.thirtysix.browser.debug reset`, scroll the Settings list from top to bottom and back five times, then run `adb shell dumpsys gfxinfo com.raumanian.thirtysix.browser.debug` and record the janky-frame percentage and the 90th and 99th percentile frame times. Reset the font scale afterwards. The Constitution's 60 fps target is defined on Pixel 5-class hardware: record that figure only from such hardware, and never substitute the emulator numbers for it.

### G9 — About (US7 · FR-034 – FR-036 · SC-013)

About shows the app name and a version equal to `adb shell dumpsys package com.raumanian.thirtysix.browser.debug | grep versionName`, plus the privacy statement. With airplane mode on, everything in About still appears.

### G10 — Regressions in earlier specs

On **both** AVDs:
1. **Spec 003** — cold start in the system light theme and in the system dark theme shows no flash of the wrong window background after the theme re-parenting (research R2).
2. **Spec 015** — Bookmarks, History and Downloads are still reachable from the overflow menu and behave as before.
3. **Spec 012** — screenshot protection still turns on with an incognito tab active and off without one.
4. **Spec 014** — the start-up retention sweep still runs when History is never opened (seed old entries, relaunch without opening History, then check).

### G11 — Localization and accessibility (FR-038 – FR-040 · SC-011, SC-012)

1. Sweep the Settings screen, all six dialogs and every message in all 8 locales (on API 36 use `cmd locale set-app-locales`): nothing untranslated, nothing clipped at 360dp width.
2. TalkBack pass in English and in one non-Latin locale (Japanese or Russian): every row, option, switch, checkbox and action is announced with a label and its selected or checked state; every target is at least 48×48dp.

### G12 — Build artefacts and governance (SC-014, SC-015, SC-016)

1. `assembleRelease`, then compare `app/build/outputs/apk/release/app-release.apk` with the Spec 015 baseline of 2.44 MB: the delta is within the budget recorded in **spec SC-014** (research R3).
2. `verify-16kb-alignment.sh` passes; no `.so` entry was added beyond the existing eight.
3. `research.md` records each new dependency's version, lookup date and native-code status (FR-045).
4. **Constitution Testing Gate 8** — on the 16 KB AVD (API 35+), confirm `adb shell getconf PAGE_SIZE` prints `16384` (if `getconf` is missing, confirm the AVD was created from a 16 KB page-size system image). Install the release APK with `adb install -r app/build/outputs/apk/release/app-release.apk` — read the output — then launch it, open Settings, change the theme and the language once each, and open the clear-data dialog: no crash.
5. Constitution Check post-implementation: 11/11 PASS.
