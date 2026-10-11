# Quickstart: validating Spec 021

Contracts: [C1 decision tables](contracts/decision-tables.md) ·
[C2 UI and strings](contracts/ui-and-strings.md) ·
[C3 platform and manifest](contracts/platform-and-manifest.md).

## Prerequisites

- `JAVA_HOME` = Android Studio's JBR before any Gradle call.
- Devices: **`TA016_API36_16K`** (release, Constitution Testing Gate 8), **`TA016_API24`**
  (minSdk), and the owner's **Galaxy A55** for real camera/mic/location and timing. On the A55:
  never change the owner's default browser; instrumented runs uninstall the debug build —
  reinstall afterwards; restore `wm density 420` after 360dp checks.
- Test pages: a local fixture served with `python3 -m http.server` behind `adb reverse` is enough
  for file inputs, dialogs, pop-ups and long-press; camera/mic/location need **https** — use
  public demo pages (e.g. webrtc.github.io samples, a maps site) or an https tunnel.

## Automated gates

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest      # C1 tables, migration 2→3, repository, menu builder
./gradlew lintDebug              # ~45 keys × 8 locales
./gradlew detekt ktlintCheck
./gradlew assembleRelease        # separate invocation; then the 16 KB script
./gradlew connectedDebugAndroidTest
git grep -n fallbackToDestructiveMigration -- app/src/main   # comments only
```

## Device gates

| Gate | What | Pass when |
|---|---|---|
| G1 | Permission dialog (US1) | https camera demo → dialog names the site + camera with 3 choices; Always allow → Android prompt → camera image |
| G2 | Remembered + one-time (FR-003) | Reload after Always allow → no question; Don't allow → refused silently; Allow this time → reload page → asked again |
| G3 | Incognito (FR-004) | Same demo in incognito → only 2 choices; Always-allowed site in normal is still asked in incognito; nothing appears in Site permissions |
| G4 | Combined + location (FR-005, edge) | Camera+mic demo → one dialog; maps → location dialog; grant approximate only → site gets approximate |
| G5 | Permanently denied (FR-006) | Deny Android camera twice ("don't ask again") → Allow → explanation + Open settings opens the app's settings |
| G6 | Not secure + other permissions (FR-007, FR-008) | http page asking camera → refused, no dialog; notifications/MIDI requests refused silently |
| G7 | Site permissions screen (FR-009, SC-008) | Lists the site; change to Don't allow → next request refused; remove → asked again; remove all → empty |
| G8 | Clear data category (FR-010a, SC-008a) | Clear with the category unticked → decisions stay; ticked → gone |
| G9 | File upload (US2) | Single image, multiple files, accept=image only, cancel then re-open; capture: Android camera prompt → photo reaches the page; refuse camera → picker still works |
| G10 | Fullscreen (US3, SC-004) | YouTube/HTML5 video → fullscreen, bars hidden; turn phone with auto-rotate off → landscape; Back → same tab, same scroll, playing; 10 trials; incognito fullscreen screenshot is black |
| G11 | Rotation no longer reloads (research R4) | Rotate a loaded page → no reload (same scroll, no progress bar) |
| G12 | Long-press (US4, SC-005) | Link → menu with address and 5 actions; Open in new tab → background + Switch notice; incognito tab; Copy → clipboard; Share → share sheet; Download → appears in Downloads; image and image-link menus; `javascript:` link → Copy only; text → selection still works |
| G13 | Pop-ups (US5, SC-006) | target=_blank and window.open after tap → new active tab; page-opened tab can close itself → back to opener; non-gesture window.open → blocked notice + Open → opens once; sign-in demo (gesture pop-up) completes |
| G14 | JS dialogs + leave (US6) | alert/confirm/prompt show the site and pass answers; 2nd dialog shows the suppress checkbox → further dialogs silent until navigation; beforeunload → Leave/Stay honoured |
| G15 | HTTP auth (FR-032, SC-007) | `httpbin.org/basic-auth/u/p` (or local) → dialog → page loads; restart app → asked again; no credential in `run-as` files, DataStore, Room or logcat |
| G16 | Stale requests (FR-034, FR-035) | Trigger a dialog then switch tab → dialog gone, page answered "cancel"; two requests → shown one after the other |
| G17 | Locales + a11y | DE and RU at 360dp for all new dialogs/sheet/screen; all keys in 8 locales; labels present; TalkBack DEFERRED to a person |
| G18 | 16 KB + size (Gate 8, SC-009) | Release on `TA016_API36_16K`: G1-style flows with zero FATAL; APK ≤ +150 KB; 16 KB script green, no new `.so` |
| G19 | Upgrade (R8) | Install `main`'s build, add data, install this build over it → data intact, Site permissions empty |
| G20 | Privacy (FR-036) | logcat during G1–G15 has no origin, URL, file name or credential from the app process |

## Recording

Results go in `tasks.md` → "Device Gate Results" (device, build, date; DEFERRED stated).
