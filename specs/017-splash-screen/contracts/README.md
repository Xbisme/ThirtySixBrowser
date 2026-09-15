# Contracts: Splash Screen (Spec 017)

**Date**: 2026-09-15 · **Spec**: [spec.md](../spec.md) · **Research**: [research.md](../research.md)

## Why there is no `.kt` file here

Every prior spec in this project put Kotlin interfaces in `contracts/` — repositories, use cases, platform seams. **This spec defines none, and inventing one would be dishonest.**

Spec 017 adds no repository, no use case, no ViewModel, no `UiState` and no platform seam. Its entire surface is:

1. resource declarations the Android build system consumes, and
2. one call, in one place, in one order.

Those *are* the contracts — they are just not expressed in Kotlin. Wrapping `installSplashScreen()` in an interface to satisfy a folder convention would add indirection with no seam to test behind: the call has no return value to fake, no failure mode to simulate, and no alternative implementation.

The contracts below are therefore written as the constraints a reviewer checks, each tied to the invariant it enforces.

---

## Contract 1 — Theme handoff (the load-bearing one)

**What breaks if violated**: the app crashes at launch on every device.

```xml
<style name="Theme.ThirtySix.Splash" parent="Theme.SplashScreen">
    <item name="postSplashScreenTheme">@style/Theme.ThirtySix</item>
    <item name="windowSplashScreenBackground">@color/window_background_light</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/&lt;mark&gt;</item>
    <item name="windowSplashScreenAnimationDuration">&lt;small, &gt; 0&gt;</item>
</style>
```

| Constraint | Invariant | Rationale |
|---|---|---|
| `parent` MUST be `Theme.SplashScreen` | INV-8 | The library's theme descends from `android:Theme.DeviceDefault`. **Do not force an AppCompat parent** — see R3. |
| `postSplashScreenTheme` MUST be `Theme.ThirtySix` | INV-7 | `installSplashScreen()` reads this and calls `Activity.setTheme()`. Omit it and the activity stays on a non-AppCompat theme; `AppCompatActivity` then throws. |
| Light and dark variants in the existing theme file pair | INV-9 | Keeps the two from diverging. |
| `Theme.SplashScreen`, **not** `.IconBackground` | INV-10 | The mark is already a disc; the variant would draw a disc behind a disc and shrink the safe zone 288dp → 240dp. |

**The counter-intuitive part, restated**: the pre-spec brief asserted the splash theme must have an AppCompat parent or the app crashes. That is wrong — R3 establishes it from the AAR and from decompiled bytecode. The theme is *correctly* DeviceDefault-based; safety comes from the handoff, not the parent.

---

## Contract 2 — Call ordering in `MainActivity.onCreate`

**What breaks if violated**: the app crashes at launch, or the launch screen never appears.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()               // MUST be first — INV-11
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()                  // unchanged
    setContent { /* unchanged */ }      // unchanged — INV-14
}
```

| Constraint | Invariant |
|---|---|
| `installSplashScreen()` MUST precede `super.onCreate()` | INV-11 — it performs the `setTheme` swap, and `AppCompatActivity.onCreate` reads the theme while building its delegate |
| `setKeepOnScreenCondition` MUST NOT be called | INV-12 — its absence is what makes FR-008 true |
| `setOnExitAnimationListener` MUST NOT be called | INV-13 |
| Everything after `super.onCreate()` unchanged | INV-14 — no new injection, no new state (FR-016) |

**Return value deliberately discarded.** `installSplashScreen()` returns a `SplashScreen` handle whose only purposes are the two calls forbidden above. Keeping it would invite exactly the delay FR-008 forbids.

---

## Contract 3 — Mark resources

**What breaks if violated**: the mark animates nowhere, or ships wrong on some devices.

| Resource | Location | Form | Invariant |
|---|---|---|---|
| mark (static) | `res/drawable/` | `VectorDrawable`, numerals as outline paths | INV-1, INV-4 |
| mark (animated) | `res/drawable-v31/` | `AnimatedVectorDrawable`, **same resource name** | INV-2 |
| mark (monochrome) | `res/drawable/` | numerals solid, disc as outline ring | INV-5 |
| density bitmaps ×5 | `res/mipmap-*dpi/` | `.webp` rendered from the vector | INV-1 |

| Constraint | Invariant | Rationale |
|---|---|---|
| Static and animated MUST share a resource name | INV-2 | Selection is by resource qualifier; there is **no runtime API check** to write or test |
| All art within the centred Ø288dp circle of a 432dp canvas | INV-3 | The library paints over everything outside it (R5). Mark is Ø268dp → 10dp margin |
| Numerals filled solid, never cut out | INV-4 | A cut-out takes its colour from the background and inverts on dark |
| Monochrome MUST NOT be a silhouette | INV-5 | Flattening a filled disc gives a featureless circle — the exact failure FR-006 names |
| Bitmaps rendered, never hand-drawn | INV-1, A10 | Hand-drawn copies are how a mark silently drifts |
| Colours via named `<color>` resources | Constitution §III | Vector XML cannot read `Color.kt`; named resources keep literals out of feature code |

---

## Contract 4 — Single identity

**What breaks if violated**: FR-001 appears to pass while template artwork keeps shipping.

| Constraint | Invariant |
|---|---|
| `android:roundIcon` MUST be removed from the manifest | INV-6, FR-004b |
| The 5 `ic_launcher_round.webp` and `ic_launcher_round.xml` MUST be deleted | INV-6 |
| All 5 `ic_launcher.webp` MUST be **replaced**, not supplemented | FR-004a |
| No file may retain template artwork | FR-001, SC-007 |

**Why this contract exists at all.** Adaptive icons live in `mipmap-anydpi-v26/`, which applies from API 26 — but the project's minSdk is 24. On API 24 and 25 the `.webp` bitmaps *are* the icon. Replace only the adaptive icon and the template robot keeps shipping on the minimum supported version while every visible check passes. This was found during `/speckit-clarify` and is the reason FR-004a exists.

---

## Verification

These contracts are verified by **device inspection and build inspection**, not by unit tests — there is no Kotlin seam to assert against. Contract 1 and 2 failures are launch crashes, caught by SC-006; Contract 3 by SC-005, SC-008, SC-009; Contract 4 by SC-007 and SC-013. See [quickstart.md](../quickstart.md) for the gate procedures.
