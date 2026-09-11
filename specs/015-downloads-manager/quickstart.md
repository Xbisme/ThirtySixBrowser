# Phase 1 — Quickstart: Downloads Manager

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md) · **Data model**: [data-model.md](data-model.md) · **Date**: 2026-09-10

Twelve manual gates. **G9 is blocking** — the manifest's notification line is not finalised until it runs.

## Automated gates (run first — all must be green before any manual gate)

```bash
./gradlew testDebugUnitTest      # includes the v1→v2 migration test (FR-017)
./gradlew lintDebug              # MissingTranslation + ExtraTranslation at error severity
./gradlew detekt                 # baseline must remain UNCHANGED
./gradlew ktlintCheck
./gradlew assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
.specify/scripts/bash/verify-16kb-alignment.sh   # SC-013
```

Two repository invariants to re-check by hand at merge:

```bash
git grep -n "fallbackToDestructiveMigration" -- app/src/main/   # MUST be empty (FR-016)
git status --short app/schemas/                                  # 2.json MUST be committed (FR-017)
```

**Baselines to beat**: APK release ≤ 2.36 MB + 200 KB (SC-012) · unit tests 379/379 + new · instrumented 61/61 + new · detekt baseline unchanged.

## Run order (do them in this sequence)

The gates are not independent — three of them change what the others should show, so order
matters more here than in previous specs.

```bash
# 0. Prerequisites, once
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
emulator -list-avds                      # TA016_API24 (minSdk) and Medium_Phone_API_36.1
adb devices                              # must list a device before anything below
```

| # | Gate | Why here | Device |
|---|------|----------|--------|
| 1 | **G9** ⛔ | **Decides a manifest line.** Run it before anything else — outcome B changes the build every later gate exercises. | API 36 |
| 2 | G11 step 5 | The upgrade check needs a *clean* install of `main`'s build; every other gate leaves data behind that muddies it. **Release blocker.** | either |
| 3 | G1 | Produces the downloads the next four gates act on. | **both** |
| 4 | G3 | Re-downloads the same file from G1's page. | either |
| 5 | G2 | Needs one finished and one in-flight transfer from G1. | either |
| 6 | G4 | Needs G1's completed downloads; API 24 is the real risk. | **both** |
| 7 | G5 | Needs entries in several states, which G1–G4 have now produced. | either |
| 8 | G6 | Destructive — cancels transfers, so run it after the read-only gates. | either |
| 9 | G8 | `pm clear` on the download provider wipes platform state, so run it after everything that depends on that state. | either |
| 10 | G7 | Resizes the display; independent, but restore the display before continuing. | either |
| 11 | G11 steps 1–4 | Needs a clean slate, so run after the destructive gates. | either |
| 12 | G10 | Locale sweep — last, because it re-renders everything the earlier gates produced. | either |
| 13 | G12 | Needs the seeder and, for SC-006, a **release** build on real hardware. | real device |

### Copy-paste command reference

```bash
# --- automated gates (all must be green before touching a device) ---
./gradlew testDebugUnitTest lintDebug detekt ktlintCheck assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk

# --- G9: fresh install with NO notification permission granted ---
adb uninstall com.raumanian.thirtysix.browser.debug     # a leftover grant invalidates the result
./gradlew installDebug                                   # READ THE OUTPUT — silent failures cost Spec 014 a session
adb shell dumpsys package com.raumanian.thirtysix.browser.debug | grep -i POST_NOTIFICATIONS

# --- G11 step 5: the upgrade path (release blocker) ---
git stash && git checkout main && ./gradlew installDebug   # create bookmarks/history/tabs by hand
git checkout 015-downloads-manager && git stash pop
./gradlew installDebug                                     # installs OVER it; read the output
# relaunch and confirm every bookmark, folder, history entry and tab survived

# --- G7: 360dp-wide configuration ---
adb shell wm size 1080x1920 && adb shell wm density 480
# ... run the gate ...
adb shell wm size reset && adb shell wm density reset

# --- G8: make the platform forget a completed download ---
adb shell pm clear com.android.providers.downloads
adb logcat -c && adb logcat -s AndroidRuntime:E          # must stay silent

# --- G10: locale sweep ---
for L in en vi de ru ko ja zh fr; do
  adb shell cmd locale set-app-locales com.raumanian.thirtysix.browser.debug --locales $L
  echo "now showing: $L"; read -r        # inspect, then press enter
done
adb shell cmd locale set-app-locales com.raumanian.thirtysix.browser.debug --locales en

# --- G12: seed 500 records, then measure ---
adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.SEED_DOWNLOADS \
  -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.DownloadSeeder
adb logcat -s DownloadSeeder
adb shell dumpsys gfxinfo com.raumanian.thirtysix.browser framestats
# wipe afterwards
adb shell am broadcast -a com.raumanian.thirtysix.browser.debug.CLEAR_DOWNLOADS \
  -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.dev.DownloadSeeder
```

### Two failure modes that have already cost this project time

- **Silent `adb install` failures.** Spec 014 lost a session to a full `/data` partition where
  installs failed quietly and the device kept a stale APK, briefly masquerading as a
  behavioural bug. Always read the install output; check with `adb shell df /data`.
- **Measuring the wrong build.** Spec 014's T103b is still open because a debug build on an
  emulator was measured against a release-on-Pixel-5 target. For G12, measure a **release**
  build on real hardware or record the gate as explicitly DEFERRED — a misleading number is
  worse than an honest gap.

---

## Test devices

Both AVDs already exist on this machine. Gates that name an API level must run on the matching one.

| AVD | Purpose |
|-----|---------|
| `TA016_API24` | **minSdk**, Android 7.0. The legacy-storage-permission path (FR-010–FR-012) exists *only* here. |
| `Medium_Phone_API_36.1` | Modern target. The zero-storage-permission path (FR-011, SC-005) and the notification-permission question (G9) live here. |

> Spec 014's session note applies: the API 36 AVD hit 96 % `/data` usage and silently refused installs. **Always read `adb install` output** — a suppressed failure masquerades as a behavioural bug.

---

## G1 — Download a file end-to-end *(US1 · FR-001–FR-009 · SC-001, SC-002, SC-005)*

Run on **both** AVDs; the permission half differs by API level.

1. Open a page with direct file links covering **at least three content types** (for example a PDF, a ZIP and an image). Across the whole gate, complete **at least ten downloads** — SC-002 is stated over that sample, so a smaller run cannot prove it.
2. Tap the link.
3. **API 24 only**: expect a storage-permission request with a localized explanation *before* the transfer begins. Grant it. Tap a second download link — expect **no** second prompt.
4. **API 36 only**: expect **no** storage permission request at any point in the whole flow.
5. Watch the transient confirmation naming the file appear over the page without blocking it (≤ 1 s, SC-001).
6. Leave the browser; confirm the transfer continues and the system notification tracks it.
7. When it finishes, open a file manager and confirm the file is in the device's **public Downloads folder**, fully written, and openable there (SC-002).

**PASS**: file present and openable from an unrelated app; permission requested exactly once on API 24 and never on API 36.

---

## G2 — The list, live status, and surviving process death *(US2 · FR-019–FR-024 · SC-003, SC-007)*

1. Start one small download (completes fast) and one large one (stays in flight).
2. Open Downloads. Confirm both are listed **newest first**, each showing filename, size, state and start time.
3. Watch the in-flight row — its progress must visibly advance **at least once per second** (SC-007).
4. With the transfer still running, force-stop the app (`adb shell am force-stop com.raumanian.thirtysix.browser`) and relaunch. Repeat on a fresh transfer until you have done this **at least five times** — SC-003 is stated over five attempts, and a single pass can succeed by luck.
5. Reopen Downloads. The entry must show the state the platform actually reached — not the state captured before the kill (FR-024).
6. Put the device in airplane mode mid-transfer; confirm the row reflects the interruption rather than appearing stalled-but-healthy, and recovers when connectivity returns.

**PASS**: ordering, fields, ≥ 1 Hz progress, correct post-kill state, honest interruption state.

---

## G3 — Duplicate filenames *(FR-007 · research R11)*

1. Download the same file twice.
2. Inspect the public Downloads folder.

**PASS**: **two** files present, neither overwritten, each separately addressable; two rows in the list.
**FAIL**: one file, or a row pointing at a file that another download replaced. A failure here means R11's delegation to the platform is wrong and the plan needs a de-duplication step.

---

## G4 — Opening a completed download *(US4 · FR-025–FR-029 · research R3)*

Run on **both** AVDs — the content-URI hand-off is the API 24 risk.

1. Tap a completed PDF row on a device with a PDF viewer → opens externally.
2. Download a file of a type nothing on the device handles; tap it → **clear localized message, no crash** (FR-027).
3. Tap a row whose transfer is still running → nothing opens; the row's state is communicated instead (FR-028).
4. Delete a downloaded file from a file manager, then tap its row → clear localized message plus an offer to remove the stale entry (FR-029).

**PASS**: all four; in particular **no `FileUriExposedException`** on API 24.

---

## G5 — The action-sheet matrix *(US5 · FR-030–FR-035, FR-034a, FR-034b)*

Long-press one entry in each state and record exactly which actions appear:

| Entry state | Open | Copy link | Remove from list | Delete file |
|-------------|:----:|:---------:|:----------------:|:-----------:|
| In flight | ✗ | ✓ | **✗** | ✗ |
| Complete, file present | ✓ | ✓ | ✓ | ✓ |
| Failed | ✗ | ✓ | ✓ | ✗ |
| Cancelled | ✗ | ✓ | ✓ | ✗ |
| Missing (file gone) | ✗ | ✓ | ✓ | ✗ |

Then exercise the actions:
6. "Remove from list" on a completed entry → row gone, **file still on disk** (FR-031).
7. "Delete file" → confirmation required; on confirm both row and file gone (FR-032).
8. "Copy link" → system clipboard holds the exact source address; transient confirmation shown (FR-033). Verify with `adb shell service call clipboard` or by pasting into the address bar.
9. Tap outside the sheet, and separately use the system back gesture → closes, nothing happens (FR-035).

**PASS**: the matrix matches exactly. The in-flight row's empty "remove" cell is the point of FR-034b — it is what makes orphaning a running transfer impossible.

---

## G6 — Cancellation *(US6 · FR-036–FR-039 · research R12)*

1. Start a large download. Confirm a **cancel control is visible on its row** and that cancelling takes **one tap, with no long-press or menu** (FR-036).
2. Cancel at roughly 25 %, 50 %, 75 % and 90 % on fresh downloads, then repeat any one of those points once more — **five cancellations in total**, which is what SC-008 states.
3. After each: the transfer stops, its system notification clears, and **no partial file remains** in the public Downloads folder.
4. Look at a completed row and a failed row → **no cancel control present** (FR-036a).
5. Long-press an in-flight row → the sheet still opens, offering only what G5's matrix allows (FR-036b).

**PASS**: all five, and zero partial files across all cancellations.

---

## G7 — Overflow menu and the bottom bar *(US3 · FR-042–FR-046 · SC-011)*

Run on a **360dp-wide** configuration (`adb shell wm size 1080x1920 && adb shell wm density 480`, or a small AVD skin).

1. The bottom bar shows exactly **five** navigation controls — Back, Forward, Reload/Stop, Home, Tabs — plus the overflow affordance, with **no clipping or overlap** (SC-011).
2. Tap the overflow → menu lists Bookmarks, History, Downloads with localized labels.
3. Each entry opens the correct screen and closes the menu.
4. Tap outside, then separately use the system back gesture → closes with no side effects (FR-045).
5. Confirm Bookmarks and History reach **exactly** the screens and behaviour they had before this spec (FR-044) — this is a regression check on two already-shipped features.
6. Count the taps from a page being browsed to a named download being on screen. SC-004 budgets **3**; the intended route is 2 (overflow → Downloads). Record the actual number.

**PASS**: all five. Restore the display with `adb shell wm size reset && adb shell wm density reset`.

---

## G8 — Forgotten transfer handle *(FR-024a–FR-024c · research R4)*

The point of this gate is that the app behaves correctly when the platform forgets a download — the premise the whole hybrid design rests on.

1. Complete a download.
2. Make the platform forget it while leaving our record intact — clear the download provider's data:
   `adb shell pm clear com.android.providers.downloads`
3. Reopen Downloads.

**PASS**:
- The row is still listed (we did not delete it).
- With the file still present → shown as **complete**, and opening it still works.
- Delete the file from a file manager, reopen → shown as **missing**, with the offer to remove.
- The row is **never** shown as running, pending, or paused (FR-024a).
- No crash and no exception in `adb logcat` from querying the forgotten handle — this is the half of R4 that needed device confirmation.

---

## G9 — Notification permission determination ⛔ BLOCKING *(FR-047, FR-048 · research R2)*

**Run this on `Medium_Phone_API_36.1` before the manifest's notification line is written.** Both outcomes are acceptable; guessing is not.

1. Build with **no** notification permission declared and no runtime request.
2. Fresh-install (`adb uninstall` first — a leftover grant from an earlier build invalidates the result).
3. Confirm in system settings that the app has **not** been granted notification access.
4. Start a download.

**Outcome A — the system's download notification appears anyway**: the permission is not needed. Leave it undeclared. **Then amend the Constitution's permission table**, which currently lists `POST_NOTIFICATIONS` against Spec 015 — leaving it there would describe a permission the app does not ship.

**Outcome B — no notification appears**: the permission is needed. Declare it, add the runtime request with a localized rationale, and record the extra strings across all 8 locales. The Constitution table then stands as written.

Record the outcome, the device, and the OS build in the PR body either way.

---

## G10 — Localization and accessibility *(FR-049–FR-052 · SC-010)*

1. Sweep all 8 locales with `adb shell cmd locale set-app-locales com.raumanian.thirtysix.browser --locales <tag>` for `en, vi, de, ru, ko, ja, zh, fr`.
2. On each: the Downloads screen, the overflow menu labels, the action sheet, the empty state, the delete confirmation, and every error message are translated — no raw keys, no English fallbacks.
3. File sizes, dates and times follow that locale's conventions (FR-050).
4. Enable TalkBack; sweep the screen. Every row, the overflow affordance and its entries, action-sheet items, the cancel control and the confirm buttons announce localized descriptions (FR-051).
5. Confirm `downloads_screen_placeholder` is gone from all 8 locale files (FR-052) — `lintDebug` fails the build if a stale key lingers, but check visually too.

---

## G11 — Empty state, incognito recording, and the real upgrade path *(US7 · FR-014a, FR-040, FR-041 · FR-016, FR-017)*

1. Fresh install → open Downloads → localized empty state (icon + message), not a blank screen.
2. Start a download without leaving the screen → the list replaces the empty state with no reopening (FR-041).
3. Remove the last remaining entry → empty state returns.
4. **Incognito**: open an incognito tab, download a file. It **must** appear in the Downloads list, with **no** incognito marking of any kind (FR-014a), and the file must be in the public Downloads folder like any other.
5. **Upgrade path — the real-world counterpart to the automated migration test.** This is the project's first migration; do not skip it.
   - Install the build from `main` (schema v1). Create data in every table: a bookmark in a folder, several history entries, and 2–3 open tabs.
   - Install this branch's build **over it** (`adb install -r`, and **read the output**).
   - Relaunch. Every bookmark, folder, history entry and tab must still be present and correct, and the new Downloads screen must open on an empty list.

**PASS**: all five. A failure at step 5 is a release blocker regardless of anything else.

---

## G12 — Performance and hostile filenames *(SC-006 · SC-015)*

**Performance** — needs a debug-only seeder in the `debug` source set, mirroring Spec 014's `HistorySeeder` (living in `debug/` rather than behind a `BuildConfig.DEBUG` check, so the class is absent from the release artifact entirely).

1. Seed **500 records**, at least 5 of them in flight.
2. Open Downloads and scroll. Measure with `adb shell dumpsys gfxinfo com.raumanian.thirtysix.browser framestats`.
3. **Target: frame budget ≤ 16 ms p99** on Pixel 5-class hardware, on a **release** build (SC-006).

> Spec 014's T103b is still open precisely because a debug build on an emulator was measured against a release-on-real-hardware target and unsurprisingly missed it. Do not repeat that: measure the **release** build on **real hardware**, or record the gate as explicitly DEFERRED in the PR body rather than reporting a misleading number.

**Hostile filenames (SC-015)** — drive at least six through the sanitiser, via a local test server or a unit test on the derivation path:

```text
../../../../etc/passwd
..\..\windows\system32\evil.dll
/absolute/path/file.pdf
....//....//escape.txt
.hidden
<empty string>
```

**PASS**: every one lands as a plain single-segment filename inside the public Downloads folder; nothing is written outside it. Verify with `adb shell ls -la /sdcard/Download/` and confirm nothing appeared elsewhere.
