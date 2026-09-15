# Quickstart: Splash Screen (Spec 017)

**Date**: 2026-09-15 · **Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md) · **Contracts**: [contracts/](./contracts/README.md)

How to verify Spec 017 is done. Gates **G1–G10** map to the spec's success criteria; each states what passes and what fails.

---

## Prerequisites

```bash
# Gradle needs the Android Studio JBR — without it the build fails in ways that
# are easy to misread (the failure can be swallowed when piped through grep).
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/xbism3/Documents/SelfProject/ThirdtySixBrowser
```

**AVDs**: one at API 24 (minSdk, static-mark path) and one at API 36 (animated path). Both already exist from Specs 015–016.

**Baseline to capture BEFORE any code change** — G7 is a before/after comparison and cannot be reconstructed afterwards:

```bash
# Release APK size baseline (expected 3,122,115 B — the Spec 016 figure)
./gradlew assembleRelease
stat -f%z app/build/outputs/apk/release/app-release.apk

# Cold-start baseline, median of 10 (G7). Run on the API 24 AVD.
for i in $(seq 1 10); do
  adb shell am force-stop com.raumanian.thirtysix.browser.debug
  adb shell am start -W -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity \
    | grep TotalTime
  sleep 2
done
```

> ⚠️ The debug build installs under applicationId `…browser.debug`. Commands against the un-suffixed id fail with "No activities found to run" — a trap this project has hit before.

---

## Automated gates

```bash
./gradlew testDebugUnitTest        # expect 557/557 — unchanged; this feature adds no unit test
./gradlew lintDebug                # expect clean, incl. 8-locale parity (no new strings — SC-012)
./gradlew detekt                   # expect baseline UNCHANGED
./gradlew ktlintCheck              # expect clean
./gradlew assembleDebug
./gradlew assembleRelease          # run SEPARATELY from lintDebug — see note below
./gradlew connectedDebugAndroidTest  # expect 109/109 — unchanged
```

> ⚠️ Running `assembleRelease` and `lintDebug` in one Gradle invocation crashes `lintAnalyzeDebugUnitTest` (found in Spec 016). Run them as separate invocations.

**Expected test counts are unchanged.** This feature adds no unit-testable Kotlin: its logic is resource selection and one call ordering, neither of which a JVM test can observe. A test asserting `installSplashScreen()` was called would assert the implementation, not the behaviour. The behaviour is verified on device by G1–G6.

---

## G1 — 16 KB gate (Constitution §IX) 🔴 blocking

**Re-verify after adding the dependency**, even though R1 confirmed zero `.so` by AAR inspection — §IX requires verification against the built artifact.

```bash
./gradlew assembleRelease
unzip -l app/build/outputs/apk/release/app-release.apk | grep '\.so$'
# Expect exactly 8 entries — the same libandroidx.graphics.path.so and
# libdatastore_shared_counter.so × 4 ABIs the project already ships.

unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so \
  | objdump -p - | grep LOAD | awk '{print $NF}'
# Expect every value 0x4000 or larger.
```

**PASS**: 8 `.so`, zero new, all `align=0x4000`. **FAIL**: any new `.so`, or any value below `0x4000`.

---

## G2 — Launch screen appears, both themes, both API levels (SC-001, SC-003)

For each AVD (API 24, API 36) and each device theme:

```bash
adb shell cmd uimode night no      # light;  'yes' for dark
adb shell am force-stop com.raumanian.thirtysix.browser.debug
adb shell screenrecord --time-limit 6 /sdcard/launch.mp4 &
adb shell am start -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity
wait; adb pull /sdcard/launch.mp4
```

Step through the recording frame by frame.

**PASS**: the mark is centred on the matching launch background (`#FAFAF9` light / `#0A0F0E` dark), and the handover to the browser shows **zero** blank, white or mismatched-colour frames.
**FAIL**: any white flash, any frame of an unexpected colour, or a mark that is clipped or off-centre.

**4 combinations**: {API 24, API 36} × {light, dark}.

---

## G3 — Theme mismatch is one clean transition (SC-003, FR-012a)

The case where the in-app theme differs from the device theme — possible since Spec 016.

1. In the app: Settings → Theme → **Dark**. Device: light.
2. Force-stop, record a cold start as in G2.
3. Repeat inverted: in-app **Light**, device dark.

**PASS**: exactly **one** colour change — device theme on the launch screen, app theme from the first browser frame. No third colour, no repeated flip.
**FAIL**: a flicker back and forth, or an intermediate colour belonging to neither theme.

> This is expected behaviour, not a defect: the system draws the launch screen before the app can read its stored theme (A3). The gate verifies the change is *clean*, not that it is absent.

---

## G4 — Animation on API 31+, still below (SC-005, FR-009/FR-010)

**API 36 AVD**: record a cold start (G2 procedure). **PASS**: the mark animates — disc scales in, numerals fade in.

**API 24 AVD**: record a cold start. **PASS**: the same mark appears **still**, with no error in logcat and no added delay.

```bash
adb logcat -d | grep -iE "splash|AnimatedVector|VectorDrawable" | grep -iE "error|exception|warn"
# Expect no output.
```

**FAIL**: no animation on API 36; any animation artefact, error or visible delay on API 24.

> Selection is by resource qualifier (`drawable-v31/`), so there is no runtime version check to test — only these two device paths.

---

## G5 — Launch screen does not appear when it should not (FR-011)

1. Cold start → launch screen appears. 2. Home button. 3. Reopen from recents.

**PASS**: step 3 shows **no** launch screen; the user returns to the same page.
**FAIL**: a launch screen on resume, or a flicker.

Also check: with an in-app language change (Settings → Language), the activity recreates and **no** launch screen appears.

---

## G6 — App launches, both extremes, no crash (SC-006, FR-015) 🔴 blocking

**This gate catches the theme-handoff failure**, the feature's main risk (R3, Contract 1).

```bash
for i in $(seq 1 10); do
  adb shell am force-stop com.raumanian.thirtysix.browser.debug
  adb shell am start -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity
  sleep 2
done
adb logcat -d | grep -iE "FATAL|AndroidRuntime|IllegalStateException"
```

**PASS**: 10/10 launches succeed on **both** AVDs, zero crashes.
**FAIL**: any crash. A failure mentioning **`AppCompat … theme`** means `postSplashScreenTheme` is missing or `installSplashScreen()` runs after `super.onCreate()` — check Contract 1 and Contract 2, in that order.

Repeat on the **release** build, which R8 shrinks differently.

---

## G7 — Launch is not measurably slower (SC-004)

Re-run the baseline command from Prerequisites on the **same AVD and build type**, and compare medians.

**PASS**: post-change median ≤ **105%** of the baseline median.
**FAIL**: above that.

> `am start -W`'s `TotalTime` ends at the first frame drawn — exactly FR-008's definition of "ready", and deliberately excluding tab restoration. Because this is a before/after on one device, emulator overhead cancels out, which is why **no Pixel 5-class hardware is needed** (A7, R10). This spec adds nothing to the hardware-measurement backlog that Specs 014 and 015 already hold.

---

## G8 — No template artwork survives anywhere (SC-007, SC-013, FR-001) 🔴 blocking

```bash
# 1. Nothing references the template's green, and no round variant remains
grep -rn "3DDC84" app/src/main/res/ || echo "clean"
ls app/src/main/res/mipmap-*/ | grep round || echo "round variant gone"
grep -n "roundIcon" app/src/main/AndroidManifest.xml || echo "roundIcon attribute gone"

# 2. What actually ships in the APK
unzip -l app/build/outputs/apk/release/app-release.apk | grep -iE "mipmap|ic_launcher"
```

Then **look at every one of the 5 bitmaps** — pull them and view them. This gate is not satisfied by a grep alone.

**PASS**: all 5 `ic_launcher.webp` show the new mark; zero round-variant files; no `roundIcon` attribute; no `#3DDC84` anywhere.
**FAIL**: any bitmap still showing the robot — the precise failure FR-004a was written to prevent, invisible on API 26+ and shipping on API 24–25.

Confirm visually on the **API 24 AVD** (bitmap path) and the **API 36 AVD** (adaptive path): the app-list icon is the same mark on both.

---

## G9 — Mark legibility and masking (SC-002, SC-008, SC-009)

**SC-002 — identical on both backgrounds**: render the mark on `#FAFAF9` and on `#0A0F0E`, side by side. **PASS**: numerals keep the same colour and contrast in both. **FAIL**: numerals darken or invert on the dark background — the cut-out failure FR-003 forbids.

**SC-008 — legible small**: view the app-list icon on two AVDs of differing density. **PASS**: "36" is readable at the smallest drawn size.

**SC-009 — masking**: on the API 36 AVD, switch the device icon shape through **circle, squircle, rounded square, teardrop** (Settings → Display, or launcher settings). **PASS**: nothing meaningful clipped in all 4. **FAIL**: any shape cutting into the numerals or the disc edge.

**Monochrome (FR-006)**: enable themed icons (API 33+). **PASS**: the mark stays recognisable — numerals visible against a ring. **FAIL**: a featureless filled circle.

---

## G10 — APK budget, offline, accessibility (SC-010, SC-011, SC-012)

**SC-010**:

```bash
stat -f%z app/build/outputs/apk/release/app-release.apk
```

**PASS**: ≤ **3,326,915 B** (baseline 3,122,115 + 204,800).
Expect comfortable headroom: the library adds ~29 KB pre-shrink (R2) and the round-variant deletion removes 5 files, so the net delta may be small or negative — R8 produced negative deltas in Specs 010 and 014.

**SC-011 — offline**: airplane mode on, cold start. **PASS**: the launch screen is identical. (FR-013 — it touches no network by construction.)

**SC-012 — accessibility**: `lintDebug` covers string parity, and this feature adds **zero** strings. Then, with TalkBack on, cold-start the app.
**PASS**: the launch screen announces **nothing**; the first announcement belongs to the browser.
**FAIL**: any announcement for the launch mark — it is decorative by FR-018.

> ⚠️ This is the inverse of the usual accessibility check: confirming silence, not a label. TalkBack is **not scriptable** on these emulators (recorded in Spec 016's T109 and in `.claude` memory), so this is a manual pass by a person.

---

## Gate summary

| Gate | Covers | Blocking | Where |
|---|---|---|---|
| G1 | 16 KB, §IX | 🔴 | build |
| G2 | SC-001, SC-003 | | API 24 + 36 × light/dark |
| G3 | SC-003, FR-012a | | either AVD, both directions |
| G4 | SC-005 | | API 24 (still) + API 36 (animated) |
| G5 | FR-011 | | either AVD |
| G6 | SC-006, FR-015 | 🔴 | both AVDs, debug + release |
| G7 | SC-004 | | API 24, before/after |
| G8 | SC-007, SC-013 | 🔴 | build + both AVDs |
| G9 | SC-002, SC-008, SC-009 | | API 36 + render comparison |
| G10 | SC-010, SC-011, SC-012 | | build + manual TalkBack |

**Done when**: all automated gates green, G1/G6/G8 pass, and G2–G5, G7, G9, G10 are recorded with their observed values in `tasks.md` under a "Device Gate Results" heading, as Specs 015 and 016 did.
