# Research: Onboarding Flow (Spec 018)

**Date**: 2026-09-15
**Branch**: `018-onboarding-flow`
**Spec**: [spec.md](./spec.md)

Every finding below was verified by reading the code as it stands on `main` at `4041f60`, not by trusting comments or prior specs' descriptions of themselves. Two findings contradict what the source's own KDoc claims.

---

## R1 — ⚠️ The "reuse unchanged" KDoc is half true, and the half that is false is the half that matters

**This was flagged in the spec's quality checklist as "a claim to test, not a fact". It has now been tested.**

Spec 016's three choosers each carry KDoc saying they are *"Public and stateless for Spec 018's onboarding (FR-037)"* and that onboarding *"can reuse it unchanged"*. Reading the code:

| Symbol | Visibility | Reusable here? |
|---|---|---|
| `ThemeModeChooserDialog` / `SearchEngineChooserDialog` / `AppLanguageChooserDialog` | `public` | ❌ — each renders an `AlertDialog` |
| `themeModeLabel(ThemeMode)` / `searchEngineLabel(SearchEngine)` / `appLanguageLabel(AppLanguage)` | **`public`** | ✅ **yes, directly** |
| `SingleChoiceDialog<T>` | `internal` | ❌ — also an `AlertDialog` |
| `SingleChoiceOptionRow` | **`private`** | ❌ — **not importable at all** |
| `TEST_TAG_SETTINGS_CHOICE_OPTION` | `public const` | ✅ available, but see below |

**Decision**: reuse the three **label functions** and the enum option lists; author the slide's option rows anew.

**Why the dialogs cannot be reused.** FR-009 requires each choice to be visible *on the slide*, applied immediately. A dialog is a modal layer over a screen — the user would tap a row to open it, choose, and dismiss, which is two extra interactions per slide and breaks SC-008's 4-tap budget outright. The KDoc's claim is accurate about *statelessness* (both take `selected` + `onSelect` and own no state) but not about *reusability of presentation*.

**Why `SingleChoiceOptionRow` cannot simply be lifted.** It is `private` to `SingleChoiceDialog.kt`. The options are (a) widen it to `internal` and move it to a shared location, or (b) write onboarding's own row. **Decision: (b), write a new row.** Widening it would couple two screens' visual details through a shared component whose only current caller is a dialog, and the onboarding row has different requirements anyway: it sits in a slide's vertical flow rather than a dialog's scroll area, and it is one of only three or four controls on the screen rather than one of nine in a list.

**What is genuinely shared and must stay shared (A2)**: the option *lists* (`ThemeMode.entries`, `SearchEngine.entries`, `AppLanguage.entries`) and the *labels*. Reusing the label functions is what makes it impossible for the two screens to disagree about what "Follow system" or "System default" is called — which is the actual risk A2 names. No new string is needed for any option.

**Alternatives considered**: (i) call the existing dialogs from the slides — rejected, breaks FR-009 and SC-008; (ii) refactor `SingleChoiceDialog` into a headless list plus two presentations — rejected as a refactor of shipped, tested Settings code in the last spec before release, for one caller.

---

## R2 — ⚠️ The start-up decision cannot be made where settings are read today

**This is the feature's central technical problem and the main risk to FR-013/FR-014.**

`MainActivity` reads settings inside `setContent`:

```kotlin
val settings by observeUserSettings()
    .collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
```

And `UserSettings.DEFAULT` carries `isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED` = **`false`** (verified in `AppDefaults.kt:21`).

So a naive `startDestination = if (settings.isOnboardingCompleted) Browser else Onboarding` inside `setContent` composes **Onboarding on the first frame of every launch**, including for a user who finished it months ago, until disk I/O re-emits. `NavHost` captures `startDestination` at first composition, so the wrong start is not merely a flash — it is the graph's actual start route.

That fails FR-013 for returning users and inverts FR-014.

**Decision**: the flag MUST be resolved **before** `setContent` runs, and the navigation graph MUST be built only once the real value is known.

**The repository offers no one-shot read** — `SettingsRepository` exposes only `observeSettings(): Flow<UserSettings>` (verified: the interface has one observer and five setters, no getter). So the plan needs one of:

| Option | Assessment |
|---|---|
| Add a one-shot read to the settings slice and await it before `setContent` | **Chosen.** Smallest honest change; a first-value read is a legitimate repository capability, not a workaround |
| Hold the splash screen until the flag arrives, via `setKeepOnScreenCondition` | **Rejected** — Spec 017 INV-12 forbids it explicitly, and its absence is what makes Spec 017's FR-008 true |
| Compose a neutral placeholder until the flag arrives, then build the graph | Viable fallback, but a third visual state between splash and app, for a read that takes microseconds |
| Block the main thread on a synchronous read | **Rejected** — disk I/O on the main thread at launch, and Spec 017's SC-004 measures cold start |

⚠️ **Spec 017 interaction, stated so it is not rediscovered the hard way**: `installSplashScreen()` must remain the first statement before `super.onCreate()`, and `setKeepOnScreenCondition` must remain uncalled. The flag read therefore happens *between* `super.onCreate()` and `setContent`, not inside the splash's keep-on-screen hook.

---

## R3 — A language change restarts the activity, so slide position must survive process-level recreation

`AppCompatAppLanguageController.apply()` calls `AppCompatDelegate.setApplicationLocales(...)` (verified). On API < 33 AppCompat recreates the activity; on 33+ the framework does. Either way `MainActivity.onCreate` runs again and the whole Compose tree is rebuilt.

**Decision**: the current slide index is held in saved instance state, not in a plain `remember` and not in the ViewModel's ordinary fields.

`rememberSaveable` survives both configuration change (FR-016) and activity recreation (FR-015) because it writes through the activity's saved-state bundle. A ViewModel does **not** survive here: the ViewModel store is cleared when the activity is genuinely finishing-and-recreating for a locale change, so a plain ViewModel field would reset to slide 1 — the exact failure FR-015 exists to prevent. If the position is held in a ViewModel, it must be in a `SavedStateHandle`.

**Consequence for FR-015a**: choosing the language already in effect must not call `apply()`, or the user pays a 111–288 ms black flash (CLAUDE.md's recorded figure) for a no-op. The controller's `current()` gives the comparison.

**Consequence for the recreation window**: the flag is still `false` during the restart, so the start-up decision (R2) correctly routes back to Onboarding — which is what FR-015 wants. The restored slide index then puts the user back where they were.

---

## R4 — Save-on-select is an existing, tested pattern; follow it exactly

`SettingsViewModel` already implements what FR-008/FR-009 ask for: `onThemeSelected`, `onSearchEngineSelected`, `onLanguageSelected` each write through a use case immediately, and the UI re-reads from the settings flow rather than holding a local copy.

**Decision**: the onboarding ViewModel mirrors this, reusing the same use cases. No new setting, no new stored value, no new persistence path (A1).

Reusing the same write path is what makes SC-004 ("choices show identically in Settings afterwards") true by construction rather than by coincidence.

---

## R5 — Completion is written on exactly two paths, and must not be written on a third

From FR-010, FR-011, FR-012 and FR-002a, the flag is written when the user finishes the last slide, and when the user skips. It is **not** written when the user leaves by the Back gesture from the first slide, nor by leaving the app.

**Decision**: one call site pattern — a single "leave the flow" action that takes the completion write with it — invoked by finish and by skip only. Back and app-exit route elsewhere.

This is worth stating because the tempting implementation is to write the flag in the screen's disposal or in `onStop`, which would be smaller code and would silently violate FR-012 and FR-002a: a user who backed out on slide 1 would never be asked again.

---

## R6 — Removing the flow from history (FR-010a)

The flow and the browser are two routes in one existing graph. Leaving the flow must replace it rather than stack on top of it, so Back from the browser's first page leaves the app rather than returning to onboarding.

**Decision**: navigate to the browser popping the onboarding route off the stack inclusively, so the browser becomes the only entry.

**Interaction with R2 worth noting**: if the graph's `startDestination` were Onboarding, a naive pop-to-start would leave Onboarding *as the start destination* and Back would still find it. Popping the onboarding route explicitly and inclusively is what makes SC-015 pass. A test that only checks "the browser is showing" would not catch this; SC-015 presses Back.

---

## R7 — No new dependency

Everything needed already exists: a slide-paging surface, saved state, the settings use cases, the language controller, the label functions and the option enums. Verified against the version catalog — nothing in the feature calls for an artifact that is not already declared.

**Decision**: add no dependency. **A7 is therefore settled, not merely assumed**, and Constitution §IX's 16 KB gate is unaffected — though it is still re-run at the gate, since the rule is about the built artifact rather than about intent.

**Consequence for SC-013**: the APK budget of +204,800 B should be comfortable. The feature's cost is roughly four slides of layout, one ViewModel, one use case addition and its strings. For comparison, Spec 017 used 2% of the same budget; Spec 016, which added two libraries, used the whole of a larger one.

---

## R8 — Strings, and the one string that must not be re-invented

Every user-visible string here is new except the option labels (R1). The welcome slide's privacy statement is the interesting case: `settings_about_privacy_statement` already exists and says exactly what FR-004 requires —

> "This app collects no personal data and sends nothing to any server run by ThirtySix. Your history, bookmarks, downloads and settings stay on this device."

**Decision**: reuse that exact string rather than write a second privacy sentence.

A9 gives the reason: two wordings of the same promise drift apart, and of all the strings in this app, the privacy claim is the one where drift would be most damaging — it is the app's central marketing claim and a Play Store data-safety commitment. Reusing it also means eight fewer translations and one fewer thing to keep in sync.

New strings are still needed for: slide titles and bodies, the forward control in both its states (R9), Skip, and the position indicator. All go into all 8 locales, enforced at build time (FR-017).

---

## R9 — The forward control's two labels

FR-002b requires one control in one position, relabelled on the last slide.

**Decision**: two string resources, one control. The label is chosen by whether the current slide is the last.

Not a single string with a placeholder, and not two controls with one hidden: the first would produce untranslatable grammar, the second would move focus order and break the "same position" guarantee that makes the flow predictable to a screen-reader user.

---

## R10 — Verification approach

| Criterion | Where | How |
|---|---|---|
| SC-001, SC-007, SC-014 | both AVDs | fresh install, complete/skip, relaunch |
| SC-002 | both AVDs | frame-by-frame of a recorded cold start ⚠️ see note |
| SC-003, SC-004 | either AVD | observe in-flow, then open Settings |
| SC-005 | both AVDs | language change from ≥2 slides × ≥3 languages |
| SC-006 | either AVD | rotate on ≥2 slides |
| SC-008 | either AVD | count taps |
| SC-009 | build | `lintDebug` translation parity |
| SC-010 | manual | screen-reader pass, 2 locales, one non-Latin ⚠️ needs a person |
| SC-011 | either AVD | smallest width + largest font, all 4 slides |
| SC-012 | either AVD | airplane mode |
| SC-013 | build | APK size vs 3,126,212 B baseline |
| SC-015 | either AVD | Back from browser after both exits |

⚠️ **`screenrecord` fails on these AVDs** with `Encoder failed (err=-38)` — hit in Spec 016 and again in Spec 017. SC-002 must be verified another way: a first launch that lands on onboarding, checked by what the window actually shows, plus the structural guarantee from R2 that the graph is never built with the wrong start. If a recording proves impossible, record SC-002 as verified structurally and say so, rather than claiming a frame inspection that did not happen.

⚠️ **The screen reader is not scriptable on these emulators** (Spec 016 T109, Spec 017 SC-012). SC-010 is a manual pass by a person.

**No criterion here needs Pixel 5-class hardware** (A8), so this spec adds nothing to the backlog that Spec 014's T103b and Spec 015's SC-006 already hold.

---

## Summary of decisions

| # | Decision |
|---|---|
| R1 | Reuse the three **label functions** and enum lists; **write new option rows** — the dialogs and their private row cannot serve a slide |
| R2 | **Resolve the flag before `setContent`**; add a one-shot settings read. Not via `setKeepOnScreenCondition` (Spec 017 INV-12) |
| R3 | Slide index in **saved instance state** — a plain ViewModel field resets on a locale restart |
| R4 | Mirror `SettingsViewModel`'s save-on-select, reusing the same use cases |
| R5 | Write completion on **finish and skip only**; never in disposal or `onStop` |
| R6 | Pop the onboarding route **inclusively** when leaving, or SC-015 fails |
| R7 | **No new dependency** — A7 settled by inspection, not assumed |
| R8 | **Reuse the existing privacy string**; new strings for everything else, 8 locales |
| R9 | One control, two string resources, chosen by position in the sequence |
| R10 | Both existing AVDs; SC-002 and SC-010 have recording/scripting limits, stated in advance |

**No NEEDS CLARIFICATION items remain.**
