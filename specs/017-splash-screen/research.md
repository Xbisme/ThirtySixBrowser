# Research: Splash Screen (Spec 017)

**Date**: 2026-09-15
**Branch**: `017-splash-screen`
**Spec**: [spec.md](./spec.md)

All findings below were verified against primary sources on **2026-09-15** — the artifact itself (downloaded AAR and POM) and Google's Maven repository. Per Constitution §IX, no version number here is remembered or carried over from an earlier session.

---

## R1 — Library version and 16 KB compliance (Constitution §IX)

**Decision**: `androidx.core:core-splashscreen` **1.2.0**.

**Verified 2026-09-15**, from `https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/maven-metadata.xml`:

| Field | Value |
|---|---|
| `<latest>` / `<release>` | **1.2.0** — both point at the stable release |
| `lastUpdated` | `20251105180234` → 2025-11-05 |
| Highest version in list | 1.2.0 — there is no newer alpha/beta to avoid |

**16 KB / native code — verified by inspecting the AAR, not by reading release notes.** `unzip -l core-splashscreen-1.2.0.aar` lists **39 entries and zero `.so` files**: `classes.jar` (29,660 B), resource XML, `R.txt`, `proguard.txt`, manifest, licence. AAR total **42,293 B**.

→ **A4 is confirmed by direct inspection.** The library cannot affect the 16 KB gate because it ships no native code at all. The gate is still re-run after the dependency is added (per §IX's CI requirement), but the expected result is the unchanged 8 `.so` entries the project already has.

**Unlike Spec 016's `androidx.webkit`, the `<release>` tag needs no working around** — there, the tag pointed at `1.18.0-alpha01` and had to be rejected. Here it points at the stable release.

**minSdk**: the AAR's own `AndroidManifest.xml` declares `<uses-sdk android:minSdkVersion="21" />`. Project minSdk is 24 → above the floor. ✅

**Alternatives considered**: implementing a launch screen by hand with a themed `windowBackground` and no library. Rejected — it cannot reach the platform's own launch-screen mechanism on Android 12+, so the mark could never animate (FR-009), and it would reintroduce the hand-rolled "splash Activity" pattern Google explicitly deprecates. Constitution Technical Standards already names `androidx.core:core-splashscreen` as the choice.

---

## R2 — Transitive dependencies and APK cost

**Decision**: accept; the size contribution is effectively zero.

From the POM (verified 2026-09-15), the runtime dependencies are:

| Dependency | POM version | Already in project | Resolved |
|---|---|---|---|
| `androidx.appcompat:appcompat-resources` | 1.7.0 | yes — via `appcompat` **1.8.0** (Spec 016) | **1.8.0** (project wins) |
| `androidx.annotation:annotation` | 1.8.1 | yes — transitively, throughout | existing or newer |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.0.21 | yes — Kotlin **2.3.21** | **2.3.21** |

→ **A5 is confirmed**: every transitive dependency already resides in the graph at an equal or newer version, so Gradle adds no new artifact. The only genuinely new code is the library's own 29,660 B `classes.jar`, before R8 shrinking removes what is unused.

**This makes SC-010's budget generous rather than tight.** The 204,800 B allowance is essentially all available for artwork. Note the artwork *replaces* existing bitmaps rather than adding to them, so the net delta may even be negative — R8 has produced a negative delta on this project before (Specs 010 and 014).

---

## R3 — ⚠️ The library's splash theme is NOT AppCompat, and that is correct

**This finding overturns a claim in the pre-spec brief and is the single most important item in this document.**

The brief stated: *"Splash theme PHẢI có parent AppCompat … parent không phải AppCompat sẽ CRASH lúc khởi động."* Extrapolated from Spec 016, that is the wrong conclusion here.

**What the library actually defines** (`res/values/values.xml` inside the AAR, verified 2026-09-15):

```
Theme.SplashScreen
  └─ parent = Theme.SplashScreen.Common
       └─ parent = Base.Theme.SplashScreen.DayNight
            └─ parent = Base.Theme.SplashScreen.Light
                 └─ parent = Base.v21.Theme.SplashScreen.Light
                      └─ parent = android:Theme.DeviceDefault.Light.NoActionBar
```

The splash theme descends from **`android:Theme.DeviceDefault`**, not from AppCompat. Forcing an AppCompat parent onto it would fight the library's design.

**Why this does not crash**, established by decompiling `SplashScreen$Impl` with `javap` (bytecode evidence, not inference):

```
setPostSplashScreenTheme(Resources$Theme, TypedValue):
  getstatic  androidx/core/splashscreen/R$attr.postSplashScreenTheme
  invokevirtual Resources$Theme.resolveAttribute
  invokevirtual android/app/Activity.setTheme       ← swaps the theme back
```

`installSplashScreen()` resolves the `postSplashScreenTheme` attribute and calls `Activity.setTheme(...)` with it, **before** `AppCompatActivity` inflates any view. The activity therefore carries the DeviceDefault-based splash theme for a moment during `onCreate`, then carries `Theme.ThirtySix` — which is AppCompat — for the entire time AppCompat is actually doing anything.

**The real constraint, restated correctly:**

> The splash theme MUST declare `postSplashScreenTheme` pointing at `Theme.ThirtySix`, and `installSplashScreen()` MUST be called **before** `super.onCreate()`. Get either wrong and the activity stays on a non-AppCompat theme, and *then* AppCompat throws.

So FR-015's requirement stands and its verification is essential — but the mechanism is the handoff, not the parent. A plan that had "forced AppCompat parent on the splash theme" would have produced a subtly broken launch screen while appearing to satisfy the brief.

**Alternatives considered**: (a) AppCompat parent on the splash theme — rejected, fights the library and loses the DayNight/`windowSplashScreen*` wiring the library sets up per API level; (b) no `postSplashScreenTheme`, calling `setTheme()` by hand — rejected, reimplements what the library already does and adds an ordering bug waiting to happen.

---

## R4 — ⚠️ `windowSplashScreenAnimationDuration` does not control how long anything is shown

**Decision**: set it to a small non-zero value as metadata only; never rely on it for timing.

The AAR's `res/values/values.xml` declares `<integer name="default_icon_animation_duration">10000</integer>` — the library's own default is **10 seconds**, which by itself disproves the widely repeated claim of a hard 1000 ms cap. No official documentation states such a cap; the 1,000 ms figure in circulation is a *design recommendation* ("we recommend not exceeding 1,000 ms on phones") that blogs have restated as a platform limit.

Per the platform documentation and the library's own KDoc, the attribute has **no effect on how long the splash screen is displayed**. It is metadata, readable back via `SplashScreenView.getIconAnimationDuration()`. The real constraint is that it must be `> 0` when the icon is animated.

**Consequence for this feature**: nothing about FR-008 depends on this attribute. The launch screen ends when the first frame can be drawn — which the library handles by default, since this plan deliberately does **not** call `setKeepOnScreenCondition`. The animation plays for as long as the launch happens to take and is simply cut off when the app is ready, which is exactly FR-008's "MUST NOT be extended to let an animation finish".

---

## R5 — Icon geometry: the 288dp figure in the brief is for a *different* configuration

**Decision**: design within a **288dp** visible circle inside a **432dp** canvas — the brief's numbers are right, but for a reason worth recording, because the library offers a second configuration where they change.

From the AAR's `values.xml`:

| Dimension | Value | Applies to |
|---|---|---|
| `splashscreen_icon_size_no_background` | **288dp** | `Theme.SplashScreen` (chosen) |
| `splashscreen_icon_size_with_background` | 240dp | `Theme.SplashScreen.IconBackground` |
| `splashscreen_icon_mask_size_no_background` | 410dp | the circular mask applied |

The compat drawable (`res/drawable-v23/compat_splash_screen_no_icon_background.xml`) draws the icon at 288dp **and then paints an oval stroke 109dp wide in the background colour over the outer edge** — a hard circular mask. Anything outside the inscribed circle is painted over, on both the compat path and the Android 12+ path.

**Decision: use `Theme.SplashScreen` (no icon background).** The approved mark is already a filled disc on a contrasting background; adding the library's icon-background circle would draw a disc behind a disc.

**Geometry for the artwork**: a 432dp × 432dp canvas whose art stays within the centred 288dp circle. The approved mark's disc is Ø268dp, leaving 10dp of clearance on each side. ✅ Confirmed safe with margin.

---

## R6 — Animation only exists on API 31+; the still form is the *same file* elsewhere

**Decision**: static `VectorDrawable` at `res/drawable/`, `AnimatedVectorDrawable` override at `res/drawable-v31/`, same resource name.

The `windowSplashScreenAnimatedIcon` attribute is mapped to the platform's real `android:windowSplashScreenAnimatedIcon` **only in `res/values-v31/`** (verified in the AAR). Below API 31 the library's compat layer draws the drawable into a `layer-list` as a plain image — an `AnimatedVectorDrawable` placed there would render as its first frame and never animate.

Resource qualifiers resolve this without any code branch: Android picks `drawable-v31/` on API 31+ and `drawable/` below. **FR-009 and FR-010 are satisfied by resource selection, not by a runtime version check** — which also means there is no branch to unit-test, only two device paths to verify (SC-005).

**Animation content (A8)**: disc scales in, numerals fade in. Kept short and simple; per R4 it is cut off whenever the app is ready, so it must look acceptable when truncated at any point — a motion that only reads correctly once complete would be wrong here.

---

## R7 — Android 7.0 cannot read adaptive icons: the bitmap fallback is load-bearing

**Decision**: regenerate all five density bitmaps from the approved mark; keep the existing density set exactly.

Adaptive icons live in `mipmap-anydpi-v26/`, which by definition applies from **API 26**. The project's minSdk is **24**, so API 24 and 25 fall back to the `.webp` bitmaps. Current state:

| Folder | Size | Contents today |
|---|---|---|
| `mipmap-mdpi` | 48×48 | template robot |
| `mipmap-hdpi` | 72×72 | template robot |
| `mipmap-xhdpi` | 96×96 | template robot |
| `mipmap-xxhdpi` | 144×144 | template robot |
| `mipmap-xxxhdpi` | 192×192 | template robot |

Each also has an `ic_launcher_round.webp` sibling → **10 bitmap files total**.

This is precisely the gap the clarification session found: without FR-004a, **FR-001 would have appeared to pass while the template robot kept shipping on API 24–25.** The bitmaps are not decorative leftovers; they are the real icon on the minimum supported version.

**FR-004b (retire the round variant)**: the manifest currently declares both `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`. Since the mark is a circular disc, a round-cropped variant is visually identical to the standard one. Pointing `roundIcon` at the same resource — or dropping the attribute — removes 5 files and a whole class of drift. **Decision: drop the `android:roundIcon` attribute and delete the 5 round bitmaps**, which is simpler than keeping an attribute that aliases its sibling.

**Bitmap generation (A10)**: rendered from the approved SVG at each density, so they cannot drift from the vector form.

---

## R8 — The mark: vector conversion and the monochrome variant

**Decision**: one `VectorDrawable` authored from the approved artwork, with numerals converted to outline paths.

`VectorDrawable` supports `<path>`, gradients via `<gradient>`, but **not `<text>`** — text must be converted to outlines. The approved mark's "36" is set in a system sans-serif; the outlines are generated once and committed as path data.

The gradient (Deep Teal `#0F766E` → Cyan `#0891B2`) is expressible as a `<gradient android:type="linear">` inside `<aapt:attr name="android:fillColor">` — the same construct the template's own artwork uses, so it is known to work in this project's toolchain.

**Monochrome (FR-006)**: themed icons tint a single-colour layer. The current adaptive icon declares `<monochrome android:drawable="@drawable/ic_launcher_foreground"/>` — pointing at the *colour* foreground, which is wrong even today. A dedicated monochrome drawable is needed: the numerals as solid shapes with the disc as an outline ring, so the mark stays recognisable rather than collapsing into a filled circle (which is what a naive silhouette of a filled disc would produce — the exact failure FR-006 names).

**Colour constants (Constitution §III)**: the brand hexes already exist in `presentation/theme/Color.kt` as Compose `Color` values, and the launch backgrounds already exist in `res/values/colors.xml`. Vector XML cannot reference Kotlin values, so the mark's colours are declared as `<color>` resources in `colors.xml` and referenced by name — named constants, not inline literals, satisfying §III.

---

## R9 — Where `installSplashScreen()` goes, and the one ordering rule

**Decision**: first statement in `MainActivity.onCreate`, before `super.onCreate(savedInstanceState)`.

Required ordering:

```
onCreate:
  installSplashScreen()        ← must precede super.onCreate
  super.onCreate(...)
  enableEdgeToEdge()
  setContent { ... }
```

Rationale, from R3: `installSplashScreen()` performs the `setTheme(postSplashScreenTheme)` swap. `AppCompatActivity.onCreate` reads the theme as it sets up its delegate, so the swap must already have happened. Calling it after `super.onCreate()` leaves the activity on the DeviceDefault-based splash theme — the failure FR-015 exists to catch.

**No `setKeepOnScreenCondition`, no `setOnExitAnimationListener`.** Per the duration and readiness decisions (FR-008), the launch screen ends at the first drawable frame, which is the library's default behaviour with no extra calls. Adding a keep-on-screen condition is what would *create* an artificial delay. This also keeps `MainActivity` free of any new state.

**Interaction with Spec 016's language handling**: `AppCompatDelegate.setApplicationLocales` recreates the activity. A recreation is not a cold start, so no launch screen appears — consistent with FR-011. Nothing extra is required.

---

## R10 — Verification approach, and why nothing here needs Pixel 5 hardware

**Decision**: verify on the two emulators the project already uses.

| Criterion | Where | How |
|---|---|---|
| SC-001, SC-003, SC-005 | API 24 + API 36 AVDs | `screenrecord` of a cold start, frame-by-frame |
| SC-004 (≤5% slower) | API 24 AVD | `am start -W` median of ≥10 cold starts, before vs after |
| SC-002, SC-008, SC-009 | rendered artwork | side-by-side render comparison |
| SC-006 | both AVDs | 10 launches each, zero crashes |
| SC-007, SC-013 | release APK | inspect packaged resources |
| SC-010 | release APK | byte size vs 3,122,115 B baseline |
| SC-011 | either AVD | airplane mode cold start |
| SC-012 | build + TalkBack | lint parity; screen reader announces nothing |

**SC-004's measurement instrument**: `adb shell am start -W` reports `TotalTime`, which ends at the first frame drawn — exactly the moment FR-008 defines as "ready", and deliberately *not* including tab restoration. The measurement is a before/after comparison on one device and build type, so emulator overhead cancels out.

→ **A7 is confirmed: no criterion in this spec is a hardware-class performance target.** Spec 014's T103b and Spec 015's SC-006 are deferred because they state absolute p99 frame targets that only real hardware can settle. SC-004 is a *relative* regression bound, which is honest to measure anywhere as long as both halves use the same device. **This spec adds nothing to the hardware-measurement backlog.**

**Known emulator hazards** (from `.claude` memory and prior specs): `adb reverse` drops during Gradle-driven device runs; 2 GB AVDs ANR under load; TalkBack is not scriptable, so SC-012's "announces nothing" check is a manual pass; `FLAG_SECURE` blanks screenshots — not applicable here unless an incognito tab is open, so device checks must start from a normal tab.

---

## Summary of decisions

| # | Decision |
|---|---|
| R1 | `androidx.core:core-splashscreen` **1.2.0**; zero `.so` confirmed by AAR inspection; minSdk 21 ✅ |
| R2 | All transitive deps already present at ≥ versions; net new code ≈ 29 KB pre-shrink |
| R3 | **Splash theme parent is DeviceDefault, not AppCompat**; safety comes from `postSplashScreenTheme` + call ordering |
| R4 | `windowSplashScreenAnimationDuration` is metadata only; no 1000 ms cap exists; set `> 0` and ignore |
| R5 | `Theme.SplashScreen` (no icon background); art within Ø288dp of a 432dp canvas; mark is Ø268dp ✅ |
| R6 | Static vector in `drawable/`, animated in `drawable-v31/`; resource qualifiers, no runtime branch |
| R7 | Regenerate 5 density bitmaps (API 24–25 fallback); **delete the 5 round bitmaps and the `roundIcon` attribute** |
| R8 | Numerals converted to outline paths; dedicated monochrome drawable; colours as named `<color>` resources |
| R9 | `installSplashScreen()` before `super.onCreate()`; no keep-on-screen, no exit listener |
| R10 | Both existing AVDs; `am start -W` for the relative timing bound; **no Pixel 5 hardware needed** |

**No NEEDS CLARIFICATION items remain.**
