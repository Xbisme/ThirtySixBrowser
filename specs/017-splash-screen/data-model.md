# Data Model: Splash Screen (Spec 017)

**Date**: 2026-09-15
**Branch**: `017-splash-screen`
**Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

---

## Scope note: this feature has no data layer

**Room schema stays at v2. No migration. No new entity, DAO, repository, use case or ViewModel.**

This is deliberate and worth stating plainly, because every spec from 005 onward has added something to the data layer and a reader may expect the same here. Spec 017 adds:

- no persisted state — the launch screen has nothing to remember;
- no DataStore key — FR-016 forbids even *reading* the existing first-run flag;
- no domain model — nothing here is business logic;
- no `UiState`, no `StateFlow` — the launch screen is drawn by the system, not composed by the app.

The whole feature is **resources plus one call in `MainActivity.onCreate`**. What follows therefore models *resource artefacts* and their relationships, which is where this feature's real structure and its real risk of drift live.

---

## Entity 1 — Brand mark

The app's visual identity. One logical entity with four physical representations that MUST all derive from a single approved source.

**Approved source**: [`assets/brand-mark-approved.svg`](./assets/brand-mark-approved.svg), signed off by the product owner on 2026-09-15 — a filled disc, linear gradient Deep Teal → Cyan, numerals "36" solid in off-white, disc Ø268dp on a 432dp canvas. It is committed to the repository rather than left in a working directory, because every representation below derives from it (INV-1) and a source that can disappear is not a source of truth.

| # | Representation | Location | Consumed by | Form |
|---|---|---|---|---|
| 1 | Static vector | `res/drawable/` | launch screen (API 24–30), adaptive icon foreground | `VectorDrawable`, numerals as outline paths |
| 2 | Animated vector | `res/drawable-v31/` | launch screen (API 31+) | `AnimatedVectorDrawable`, same resource name as #1 |
| 3 | Monochrome | `res/drawable/` | themed icons (API 33+) | single-colour; numerals solid, disc as outline ring |
| 4 | Density bitmaps ×5 | `res/mipmap-{m,h,xh,xxh,xxxh}dpi/` | app identity on API 24–25 | `.webp`, rendered from #1 |

### Invariants

- **INV-1**: #1, #2, #3 and #4 MUST all be generated from the approved source. #4 in particular MUST be rendered, never hand-drawn (A10) — hand-drawn bitmaps are how a mark silently drifts from its vector.
- **INV-2**: #1 and #2 MUST share a resource name. Resource qualifiers do the API-level selection; there is no runtime branch (R6).
- **INV-3**: All art MUST lie within the centred Ø288dp circle of the 432dp canvas. Outside it the library paints over with the background colour (R5). Current margin: 10dp per side.
- **INV-4**: The numerals MUST be filled solid, never cut out (FR-003). A cut-out takes its colour from whatever is behind it, which breaks on dark backgrounds — this was built, inspected and rejected.
- **INV-5**: #3 MUST NOT be a silhouette of #1. Flattening a filled disc yields a featureless circle; the disc becomes an outline ring so the numerals stay legible (FR-006).
- **INV-6**: Exactly one mark ships. No round variant (FR-004b) — the round bitmaps and the `android:roundIcon` manifest attribute are both removed.

### Colour references

Vector XML cannot read Kotlin values, so the mark's colours are declared as named `<color>` resources and referenced by name — named constants, not inline literals (Constitution §III).

| Resource | Value | Role | Status |
|---|---|---|---|
| brand primary | `#0F766E` | gradient start | new `<color>`; mirrors `Color.kt` |
| brand accent | `#0891B2` | gradient end | new `<color>`; mirrors `Color.kt` |
| brand on-surface | `#FAFAF9` | numerals | new `<color>`; mirrors `window_background_light` |
| `window_background_light` | `#FAFAF9` | launch background, light | **existing**, reused unchanged (A2) |
| `window_background_dark` | `#0A0F0E` | launch background, dark | **existing**, reused unchanged (A2) |

The duplication between `colors.xml` and `Color.kt` is the same accepted redundancy Spec 003 documented in `colors.xml`: XML resolves before Compose exists, Compose values resolve after. The existing comment block there explains the split and MUST be extended rather than contradicted.

---

## Entity 2 — Launch screen theme

A style resource, not runtime state. It exists in light and dark variants and hands control to the app's real theme.

| Attribute | Value | Requirement |
|---|---|---|
| parent | `Theme.SplashScreen` | R3 — **DeviceDefault-based, deliberately NOT AppCompat** |
| `postSplashScreenTheme` | `Theme.ThirtySix` | **load-bearing** — the handoff that keeps AppCompat working |
| `windowSplashScreenBackground` | `window_background_light` / `window_background_dark` | FR-012 |
| `windowSplashScreenAnimatedIcon` | the brand mark | FR-009/FR-010 |
| `windowSplashScreenAnimationDuration` | small, `> 0` | R4 — metadata only, controls nothing |

### Invariants

- **INV-7**: `postSplashScreenTheme` MUST resolve to `Theme.ThirtySix`. Without it the activity stays on a non-AppCompat theme and `AppCompatActivity` throws at launch (R3, FR-015).
- **INV-8**: The theme MUST NOT be given an AppCompat parent. That fights the library and discards the per-API-level wiring it sets up. Safety comes from INV-7, not from the parent.
- **INV-9**: Light and dark variants MUST be defined in the same pair of files that already carry `Theme.ThirtySix` (`values/themes.xml`, `values-night/themes.xml`), so the two themes cannot diverge across a future edit.
- **INV-10**: `Theme.SplashScreen` — not `Theme.SplashScreen.IconBackground`. The mark is already a disc on a contrasting field; the icon-background variant would draw a disc behind a disc, and would also shrink the safe zone from 288dp to 240dp (R5).

---

## Entity 3 — Launch sequence

Not persisted state; the ordering contract inside `MainActivity.onCreate`. Recorded here because getting it wrong is the feature's main failure mode.

```
1. installSplashScreen()            ← MUST precede step 2 (R9, INV-11)
2. super.onCreate(savedInstanceState)
3. enableEdgeToEdge()
4. setContent { … }                 ← unchanged from today
```

### Invariants

- **INV-11**: Step 1 MUST precede step 2. `installSplashScreen()` performs the `setTheme` swap (R3), and `AppCompatActivity.onCreate` reads the theme while building its delegate. Reversing them leaves the activity on the splash theme and AppCompat throws.
- **INV-12**: `setKeepOnScreenCondition` MUST NOT be called. Its absence is what makes FR-008 true — the launch screen ends at the first drawable frame. Adding one is precisely how an artificial delay would be introduced.
- **INV-13**: `setOnExitAnimationListener` MUST NOT be called. Nothing in this feature needs to observe or extend the exit.
- **INV-14**: Steps 2–4 MUST be unchanged. No new injection, no new state, no change to what is composed (FR-016).

### State transitions

There is one transition, owned by the system:

```
[launch screen, device theme]  ──first drawable frame──▶  [browser, app theme]
```

Where the in-app theme differs from the device theme, this transition also changes the background colour. It MUST be a single clean change — no third colour, no repeated flip (FR-012a, SC-003). The app cannot make the first frame match its stored theme, because the system draws the launch screen before the app can read storage (A3).

---

## Files touched

| Path | Change |
|---|---|
| `gradle/libs.versions.toml` | + `core-splashscreen` version and library entry |
| `app/build.gradle.kts` | + one `implementation` line |
| `app/src/main/AndroidManifest.xml` | splash theme on the activity; **remove** `android:roundIcon` |
| `app/src/main/res/values/themes.xml` | + splash theme (light) |
| `app/src/main/res/values-night/themes.xml` | + splash theme (dark) |
| `app/src/main/res/values/colors.xml` | + 3 brand `<color>` entries |
| `app/src/main/res/drawable/` | + mark (static), + monochrome; **replace** `ic_launcher_background`, `ic_launcher_foreground` |
| `app/src/main/res/drawable-v31/` | **new folder** + mark (animated) |
| `app/src/main/res/mipmap-anydpi-v26/` | `ic_launcher.xml` repointed; **delete** `ic_launcher_round.xml` |
| `app/src/main/res/mipmap-*dpi/` | **replace** 5 `ic_launcher.webp`; **delete** 5 `ic_launcher_round.webp` |
| `MainActivity.kt` | + one call, + one import |

**Net file count falls by 5** — the round variant's removal outweighs the additions, which is worth noting against SC-010's budget.

---

## What is explicitly NOT modelled

- **No first-run / onboarding state.** FR-016 forbids reading the flag Spec 006 stores. Spec 018 owns it.
- **No launch-screen duration.** FR-008 rules out a configurable or minimum duration, so there is nothing to model (R4).
- **No per-user or per-device variation.** The mark and the theme are identical for everyone; the only variation is device theme and API level, both resolved by resource qualifiers.
