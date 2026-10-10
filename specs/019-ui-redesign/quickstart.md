# Quickstart: validating Spec 019

How to prove the redesign works. Design references: [design system](https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys), [screens canvas](https://claude.ai/artifact/FRRCWBmGa85kmLSFT8tmak).

## Prerequisites

- `JAVA_HOME` set to Android Studio's JBR before any Gradle call.
- AVDs: `TA016_API24` (minSdk), `TA016_API29_CI`, `TA016_API36_16K` (16 KB). Not `Medium_Phone_API_36.1` (its `/data` is full).
- Force-stop the app before `connectedDebugAndroidTest` (Spec 018 finding).

## Automated gates

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest            # includes progress-isolation + ChromeScrollState tests
./gradlew lintDebug                    # 8-locale parity at error severity
./gradlew detekt ktlintCheck
./gradlew assembleRelease              # then the 16 KB script; no new .so
./gradlew assembleRelease -PcomposeReports   # compiler reports in app/build/compose_reports
./gradlew connectedDebugAndroidTest
grep -rniE "0f766e|0891b2|5eead4|67e8f9" app/src/main   # SC-003: expect no output
```

## Device gates

| Gate | What | Pass when |
|---|---|---|
| G1 | Brand (US1) | Launcher icon (API 24 bitmap, API 36 adaptive, themed mono), launch screen, no teal anywhere |
| G2 | Insets (FR-008, SC-002) | On API 24, 29, 36 with gesture and 3-button navigation, nothing overlaps a system bar or the keyboard |
| G3 | Canvas match (SC-001) | Each of the 15 artboards vs the built screen at 360dp, light + dark: 0 open differences |
| G4 | Home (US2) | Greeting changes with the clock (set device time 08:00 / 14:00 / 20:00); cards order Tab → Saved → Recent, no duplicates, ≤ 8; empty install shows only greeting + search; incognito tab shows the incognito page |
| G5 | Home navigation (R1) | New tab → Home; submit → page; Back → Home; Home on a page → Back → same page; Back on a page not from Home leaves the app |
| G6 | Chrome (US3) | Progress line grows on the well; bars hide on scroll down, return on scroll up, stay on error and with the keyboard |
| G7 | Hub (FR-031) | Every tile and button reaches its destination; Back/Forward enablement matches page history; downloads button only while downloading |
| G8 | Tabs (FR-040) | Chip modes per clarification Q3; swipe past half closes, short swipe springs back; close button still works |
| G9 | Libraries & settings (US4) | Every Spec 011–016 action still works; dialogs apply on select; destructive confirms name the loss |
| G10 | Incognito & onboarding (US5) | Plum bars in both themes; incognito page copy; onboarding Spec 018 gates G1–G12 re-run green |
| G11 | Locales (SC-008) | All screens in DE and RU at 360dp, no clipping; all 8 locales translated |
| G12 | Dynamic color (FR-005, SC-009) | Android 12+, three contrasting wallpapers: primary, surfaces and hero glow follow; hero stays dark; pastels, danger, plum fixed; contrast holds |
| G13 | Recomposition (FR-060–065, Gates 9–10) | Layout Inspector counts before (`main`) / after for: page load, page scroll, Home scroll, hub drag, tab swipe, download progress. Chrome count does not grow with frames; compiler report shows no unexplained non-skippable composables in touched files |
| G14 | Frame time (SC-005) | Release build, Pixel 5-class hardware, p95 ≤ 16 ms — or recorded **DEFERRED** (release seeding blocker); never reported from an emulator |
| G15 | Size and start (SC-010, SC-011) | APK ≤ 3,134,976 + 409,600 B; cold start ≤ +5% vs Spec 018 on the same AVD; new tab → Home ≤ 300 ms, median of 10, tap to first Home frame measured with a Perfetto trace on TA016_API36_16K |
| G16 | Tab switcher (Constitution §V) | Opening Tabs with 10 tabs renders ≤ 200 ms (median of 10); DEFERRED to Pixel 5-class hardware if the emulator figure is not representative |
| G17 | Reduced motion | With *Remove animations* on: tab swipe release, bar show and hero collapse jump to their end states |

## Recording

Results go into `tasks.md` → "Device Gate Results", with the AVD name, build type and date for each gate, and DEFERRED stated explicitly where a gate cannot be run (TalkBack, G14).
