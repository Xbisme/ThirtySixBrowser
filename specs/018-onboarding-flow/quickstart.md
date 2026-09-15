# Quickstart: Onboarding Flow (Spec 018)

**Date**: 2026-09-15 · **Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Contracts**: [contracts/](./contracts/)

How to verify Spec 018 is done. Gates **G1–G12** map to the spec's success criteria; each states what passes and what fails.

---

## Prerequisites

```bash
# Gradle needs the Android Studio JBR.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/xbism3/Documents/SelfProject/ThirdtySixBrowser
```

**AVDs**: `TA016_API24` (minSdk) and `TA016_API36_16K` (16 KB, max).

**The one command every gate here depends on** — this feature is about *first* launch, so most gates need a genuinely fresh state:

```bash
adb shell pm clear com.raumanian.thirtysix.browser.debug
```

> ⚠️ The debug build installs under `…browser.debug`. Commands against the un-suffixed id fail with "No activities found to run" — a trap this project has hit repeatedly.

**Baseline to capture before any change** (G11):

```bash
./gradlew assembleRelease
stat -f%z app/build/outputs/apk/release/app-release.apk   # expect 3,126,212 (Spec 017)
```

---

## Automated gates

```bash
./gradlew testDebugUnitTest        # expect 557 + new ViewModel tests, 0 failures
./gradlew lintDebug                # clean, incl. 8-locale parity for the new strings
./gradlew detekt                   # baseline UNCHANGED
./gradlew ktlintCheck              # clean
./gradlew assembleDebug
./gradlew assembleRelease          # run SEPARATELY from lintDebug
./gradlew connectedDebugAndroidTest  # expect 109 + new, 0 failures
```

> ⚠️ Running `assembleRelease` and `lintDebug` in one Gradle invocation crashes `lintAnalyzeDebugUnitTest` (Spec 016). Separate invocations.

**Unlike Spec 017, this feature does have unit-testable logic** — slide advancement, the last-slide boundary, which exits write the flag, and the no-op language guard are all observable from a JVM test against the ViewModel. They should be tested there; the device gates below cover what only a device can show.

---

## G1 — First launch shows the flow; a returning user does not 🔴 blocking

**This gate catches the feature's main risk** (research.md R2, INV-5).

```bash
adb shell pm clear com.raumanian.thirtysix.browser.debug
adb shell am start -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity
# → expect the welcome slide
```

Then complete the flow, and:

```bash
adb shell am force-stop com.raumanian.thirtysix.browser.debug
adb shell am start -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity
# → expect the browser, with NO onboarding frame
```

**PASS**: fresh state → flow; completed state → browser, on ≥3 relaunches each (SC-001).
**FAIL**: onboarding appearing for a completed user — even for one frame — means the flag is being read from `UserSettings.DEFAULT` before disk I/O. Check R2/INV-5 first.

---

## G2 — No browser frame before the flow (SC-002)

On a cleared install, launch and watch the very first frame.

**PASS**: the launch screen gives way directly to the welcome slide; the browser is never visible.
**FAIL**: any browser frame, however brief.

> ⚠️ **`screenrecord` fails on these AVDs** (`Encoder failed err=-38`) — hit in Spec 016 and Spec 017. If it cannot record, verify structurally: the navigation graph is built once, with the start route already resolved (R2), so a wrong first frame is impossible by construction rather than merely unobserved. **Record SC-002 as "verified structurally" and say so** — do not claim a frame inspection that did not happen.

---

## G3 — Choices apply immediately and land in Settings (SC-003, SC-004)

On each slide, make a choice and watch the flow itself:

- **Theme** → the flow recolours at once.
- **Language** → the flow's own text changes to that language.
- **Search engine** → the chosen engine shows as selected.

Then finish, open Settings, and compare.

**PASS**: each change is visible in the flow within 1 second, and all three settings in Settings match exactly what was chosen.
**FAIL**: a choice that only takes effect after finishing — that is deferred saving, which FR-008 forbids.

---

## G4 — Slide position survives a language change 🔴 blocking (SC-005)

**The gate this feature's hardest requirement exists for** (FR-015, R3).

1. Advance to the **language** slide (slide 2).
2. Choose a different language.
3. The screen flickers as the activity is recreated.

**PASS**: the flow is still on the **language** slide, in the new language, with that language selected.
**FAIL**: landing back on the welcome slide means the position is in a plain `remember` or a plain ViewModel field, neither of which survives activity recreation. See INV-2 — note this failure is **invisible to a rotation test**.

Repeat from the **theme** slide (slide 3) — go back to language, change it, and confirm the return is to the language slide. Verify ≥3 languages from ≥2 slides.

---

## G5 — Rotation keeps the slide (SC-006)

Rotate on at least two slides, one of them a choice slide.

**PASS**: same slide, same selections.
**FAIL**: reset to slide 1.

> G5 passing does **not** imply G4 passes. `remember` survives nothing; `rememberSaveable` survives both; a plain ViewModel field survives rotation but **not** the locale restart. Only G4 separates the last two.

---

## G6 — Skip works from every slide and is remembered (SC-007)

For each of the 4 slides, on a cleared install: reach that slide, tap Skip, confirm the browser opens, then force-stop and relaunch.

**PASS**: 4/4 open the browser, and 4/4 relaunches go straight to the browser.
**FAIL**: the flow reappearing means the flag was not written on skip — FR-011's specific failure.

Also: skip without choosing anything, then open Settings. **PASS**: settings are at their defaults, untouched (A4, INV-7).

---

## G7 — Back behaves as specified (FR-002a) 🔴 blocking

| From | Back should | Then |
|---|---|---|
| slide 1 (welcome) | close the app | relaunch → **the flow appears again** (flag not written) |
| slides 2–4 | go to the previous slide | choices intact |

**PASS**: all four rows behave as stated.
**FAIL**: Back on slide 1 writing the flag (the user is never asked again after backing out — FR-012 violated), or Back doing nothing at all.

---

## G8 — The flow is gone from history after leaving (SC-015) 🔴 blocking

After finishing — and separately, after skipping — press Back on the browser's first page.

**PASS**: the app closes, both times.
**FAIL**: returning to onboarding means the route was not popped inclusively (FR-010a, R6). A test that only checks "the browser is showing" misses this entirely.

---

## G9 — Interaction budget (SC-008)

Count taps on a cleared install, accepting the default on every slide.

**PASS**: exactly **4** taps — Next, Next, Next, Done. Skip from any slide is **1** tap.
**FAIL**: 5 or more, which would mean a separate finish control crept in (FR-002b).

Then count with all three choices changed: **PASS** at 7.

---

## G10 — Localization and accessibility (SC-009, SC-010, SC-011)

**SC-009**: `lintDebug` enforces 8-locale parity. **PASS**: clean, no missing/extra/unused keys.

**SC-011**: set the smallest supported width and the largest font scale, then visit all 4 slides. **PASS**: everything reachable, scrolling where needed, nothing cut off. The 9-option language slide is the one to watch.

**SC-010**: with the screen reader on, sweep the flow in 2 locales, one non-Latin.
**PASS**: every control has a localized label; each option announces its selected state; touch targets meet the minimum.
⚠️ **Not scriptable on these emulators** (Spec 016 T109, Spec 017 SC-012) — this is a **manual pass by a person**. Record it DEFERRED if no one is available, rather than claiming it.

---

## G11 — APK budget and offline (SC-012, SC-013)

```bash
stat -f%z app/build/outputs/apk/release/app-release.apk
```

**PASS**: ≤ **3,331,012 B** (baseline 3,126,212 + 204,800). Expect comfortable headroom — no new dependency (R7), so the cost is four slides, a ViewModel and strings.

**SC-012**: cleared install, airplane mode, complete the whole flow. **PASS**: identical behaviour (FR-020).

---

## G12 — Launch health on both extremes (SC-014)

10 launches on **each** AVD, in **both** states (cleared and completed), checking logcat for `FATAL EXCEPTION`.

**PASS**: 40/40 launches, zero crashes.
**FAIL**: any crash. Note this is where a start-up ordering mistake (R2) is most likely to surface as something other than a wrong screen.

---

## Gate summary

| Gate | Covers | Blocking | Where |
|---|---|---|---|
| G1 | SC-001 | 🔴 | both AVDs |
| G2 | SC-002 | | both AVDs (see recording note) |
| G3 | SC-003, SC-004 | | either |
| G4 | SC-005 | 🔴 | both AVDs |
| G5 | SC-006 | | either |
| G6 | SC-007 | | either, ×4 slides |
| G7 | FR-002a | 🔴 | either |
| G8 | SC-015 | 🔴 | either, both exits |
| G9 | SC-008 | | either |
| G10 | SC-009, SC-010, SC-011 | | build + manual |
| G11 | SC-012, SC-013 | | build + either |
| G12 | SC-014 | | both AVDs |

**Done when**: automated gates green; G1, G4, G7, G8 pass; and every other gate is recorded with its observed value in `tasks.md` under "Device Gate Results", with anything unrun marked DEFERRED and why — as Specs 015, 016 and 017 did.
