# Quickstart: validating Spec 020

How to prove ThirtySix is reachable as a browser. Contracts:
[C1 manifest](contracts/manifest-entry-points.md) ·
[C2 incoming requests](contracts/incoming-requests.md) ·
[C3 default browser + UI](contracts/default-browser-and-ui.md).

## Prerequisites

- `JAVA_HOME` set to Android Studio's JBR before any Gradle call.
- Devices: the owner's **Galaxy A55** (Android 16, reference device, release build) and the
  **`TA016_API24`** AVD (Android 7.0, for the default-apps-screen path). Optional: the 16 KB
  `TA016_API36_16K` AVD for Constitution Testing Gate 8.
- On the A55: clear any per-app language on the debug build before instrumented runs
  (Spec 019 finding), force-stop the app first (Spec 018 finding), and restore
  `wm density 420` after any 360dp check.
- Package ids: debug `com.raumanian.thirtysix.browser.debug`, release
  `com.raumanian.thirtysix.browser`.

## Automated gates

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest        # C2 tables, launch counter, invite visibility, manifest scheme test
./gradlew lintDebug                # 12 new keys × 8 locales
./gradlew detekt ktlintCheck
./gradlew assembleRelease          # then the 16 KB script: no new .so
./gradlew connectedDebugAndroidTest
```

## Device gates

`PKG` = package under test. Link injection uses `am start`, which delivers exactly what a
tapping app would:

```bash
adb shell am start -a android.intent.action.VIEW -d "https://example.org/a" -p $PKG
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "Read https://example.org/b now" -p $PKG
adb shell am start -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT "phở" -p $PKG
adb shell am start -a android.intent.action.WEB_SEARCH --es query "weather hanoi" -p $PKG
```

| Gate | What | Pass when |
|---|---|---|
| G1 | "Open with" (US1, FR-001) | Tapping a link in Gmail / Messages lists ThirtySix; choosing it opens the page in a new active tab |
| G2 | Single instance (FR-004, SC-002) | 20 `VIEW` intents in a row → recents shows 1 ThirtySix entry; tab count +20; earlier tabs unchanged |
| G3 | Back to sender (FR-006) | From Gmail: open link → Back returns to Gmail and that tab is gone. Open link → follow a link in the page → Back → first page → Back → Gmail |
| G4 | Other screens (FR-005) | With History / Settings / Tabs open, send a `VIEW` → the browser shows the new tab |
| G5 | Rejections (FR-015, SC-003) | `am start -a VIEW -d "javascript:alert(1)" -p $PKG` and the C2 rejection list (those the system lets through) → no tab, "This link can't be opened" message |
| G6 | Share + selection + web search (US3, US4, SC-007) | The 3 non-`VIEW` commands above give: address opened / search for "phở" / search for "weather hanoi" with the chosen engine; 10 + 10 share trials all correct |
| G7 | Duplicates and recreation (FR-007, FR-008, SC-008) | Two identical `VIEW`s sent within 1 s → one tab. Rotate and change language right after a link opens, 10 trials → 0 extra tabs. `am kill` in background, reopen from recents → no extra tab |
| G8 | Before onboarding (FR-019, SC-009) | `pm clear $PKG`, send a `VIEW` → onboarding shows; rotate mid-onboarding; Skip → the link opens in a new tab. 10/10 trials |
| G9 | Tab cap (FR-018) | With 50 normal tabs, send a `VIEW` → no new tab, link loads in the active tab, notice shown, Back returns to the previous page. Repeat with an incognito tab active → link loads in the most recently used normal tab |
| G10 | Incognito active (FR-017) | Incognito tab active, send a `VIEW` → a normal tab opens; incognito tab untouched |
| G11 | Default browser, Android 10+ (US2, FR-021, SC-004, SC-006) | A55 with another default browser: Settings row says "isn't your default" → tap → system confirmation → accept → back in Settings the row says "is your default" within 1 s, no restart. Decline path changes nothing. Change default in system settings → row updates on return |
| G12 | Default browser, Android 7–9 (FR-021) | `TA016_API24`: row action opens the default-apps screen; choose ThirtySix; row updates on return |
| G13 | Home invitation (FR-024 – FR-026, SC-005) | Fresh install: launches 1–2 → no card; launch 3 → card on Home. Dismiss → absent over 10 relaunches. Reinstall, reach launch 3, accept + become default → card gone and stays gone. Not shown on an incognito tab's page |
| G14 | Locales + accessibility (FR-029, FR-030) | Settings row, invitation and messages translated in all 8 locales; DE and RU at 360dp without clipping; every new control has a TalkBack label (TalkBack pass itself: DEFERRED to a person, as Specs 016–019) |
| G15 | Speed and size (SC-001, SC-010) | Release on the A55: link → page visible ≤ 1 s warm, ≤ 2 s cold (median of 10, `am start -W` + first frame). Release APK ≤ +60 KB over `main`; 16 KB script green, zero new `.so` |
| G16 | Privacy (FR-016) | `adb logcat` while running G1–G6 contains none of the sent URLs or texts |

## Recording

Results go in `tasks.md` → "Device Gate Results", with device, build type and date per gate,
and DEFERRED stated explicitly where a gate cannot be run.
