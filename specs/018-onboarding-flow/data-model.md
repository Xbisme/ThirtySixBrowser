# Data Model: Onboarding Flow (Spec 018)

**Date**: 2026-09-15 · **Spec**: [spec.md](./spec.md) · **Research**: [research.md](./research.md)

---

## Scope note: no persistence is added

**Room stays at v2. No migration. No new DataStore key. No new domain model.**

This feature stores nothing of its own. It is a second presentation of settings that already exist, plus one flag that already exists (A1). What follows models the *state the flow holds while it is running* and the *invariants that keep it correct* — which is where this feature's real difficulty lives, because two of those invariants are about surviving destruction.

What it **does** add: one repository read (R2), one ViewModel, four slides, and their strings.

---

## Entity 1 — Onboarding session state

The state of one run of the flow. Exists only while the flow is showing.

| Field | Type | Persisted? | Survives rotation | Survives locale restart |
|---|---|---|---|---|
| current slide index | 0–3 | no | **MUST** (FR-016) | **MUST** (FR-015) |
| selected theme | existing `ThemeMode` | already, by Spec 006 | n/a — re-read | n/a — re-read |
| selected language | existing `AppLanguage` | by the platform | n/a — re-read | n/a — re-read |
| selected search engine | existing `SearchEngine` | already, by Spec 006 | n/a — re-read | n/a — re-read |

### Invariants

- **INV-1**: The three selections are **never held as local state**. They are read from the settings flow and the language controller, and written straight back through the existing use cases (R4). A local copy would be a second source of truth and would make SC-004 an accident rather than a guarantee.
- **INV-2**: The slide index MUST live in **saved instance state**. A plain `remember` dies on rotation; a plain ViewModel field dies on the locale restart, because the ViewModel store is cleared when the activity is recreated for a configuration change of that kind. Either failure lands the user back on slide 1 — the exact outcome FR-015 exists to prevent (R3).
- **INV-3**: The slide index is bounded to 0–3. A restored index outside that range is clamped rather than trusted, so a corrupted or stale bundle cannot render a non-existent slide.
- **INV-4**: The index is **not** persisted beyond the flow. It is session state, not a setting; a user who leaves and relaunches starts at the beginning (FR-012 edge case).

### State transitions

```
        ┌──────────── Back ────────────┐
        ▼                              │
   [slide 0] ──Next──▶ [slide 1] ──Next──▶ [slide 2] ──Next──▶ [slide 3]
        │                   │                  │                  │
        │ Back              │                  │                  │ Done
        ▼                   │                  │                  ▼
   leave app           ◀────┴──────────────────┴──────────  [flow complete]
   (no flag write)                                                │
        ▲                                                         │
        └──── Skip, from any slide ───────────────────────────────┤
                                                                  ▼
                                                            [browser]
```

- Forward from slide 3 **is** completion — there is no separate finish step (FR-002b, R9).
- Back from slide 0 leaves the app and writes **nothing** (FR-002a).
- Skip from any slide writes the flag and leaves (FR-011).

---

## Entity 2 — First-run flag

Already exists. This feature is its first and only reader, and its second writer.

| Property | Value |
|---|---|
| Stored as | existing DataStore boolean, `is_onboarding_completed` |
| Default | `false` (`AppDefaults.IS_ONBOARDING_COMPLETED`) |
| Read by | the start-up decision, **once per launch** (R2) |
| Written by | finishing (FR-010) and skipping (FR-011) — **those two paths only** |

### Invariants

- **INV-5**: The flag MUST be read to a **real value before the navigation graph is built**. `UserSettings.DEFAULT` carries `false`, so composing the graph against the flow's initial emission sends every returning user to onboarding for a frame, and `NavHost` captures `startDestination` at first composition — so it is not a flash but the graph's actual start (R2). This is the single most likely way to get this feature wrong.
- **INV-6**: The flag MUST NOT be written from screen disposal, `onStop`, or any lifecycle callback. Those fire when the user backs out or leaves, which FR-012 and FR-002a require to leave the flag untouched (R5). The tempting smaller implementation is exactly the wrong one.
- **INV-7**: Writing the flag MUST NOT write any setting. Skip records that the user was asked; it does not record a preference they never expressed (A4).

---

## Entity 3 — Slide

Presentation only; no stored state. Four instances, fixed order (FR-001).

| # | Slide | Content | Choice |
|---|---|---|---|
| 0 | Welcome | app introduction + **the existing privacy string** (R8, A9) | none |
| 1 | Language | Follow system + 8 languages, each by endonym | `AppLanguage` |
| 2 | Theme | Light / Dark / System default | `ThemeMode` |
| 3 | Search engine | Google / DuckDuckGo / Bing | `SearchEngine` |

### Invariants

- **INV-8**: Option lists and labels come from the **same source Settings uses** — `ThemeMode.entries` etc. and the public `themeModeLabel` / `searchEngineLabel` / `appLanguageLabel` functions. This is what makes it impossible for the two screens to offer different options or name them differently (A2, R1).
- **INV-9**: The option **rows are new**; Spec 016's are `private` inside a dialog file and cannot be imported, and a dialog cannot satisfy FR-009's "visible on the slide" or SC-008's tap budget (R1). The KDoc claiming "reuse unchanged" is accurate about statelessness only.
- **INV-10**: The welcome slide's privacy statement MUST be the **existing** `settings_about_privacy_statement`, not a new sentence saying the same thing (R8).
- **INV-11**: Every slide MUST remain usable at the smallest supported width and largest font, scrolling if needed (FR-019). Nine language options do not fit a small screen unscrolled.

---

## Entity 4 — Forward control

One control, one position, two labels (FR-002b, R9).

| Slide | Label | Action |
|---|---|---|
| 0–2 | "next" string | advance one slide |
| 3 | "done" string | complete the flow (FR-010) |

### Invariants

- **INV-12**: One control in one position across all four slides. Not two controls with one hidden — that moves focus order and breaks the predictability a screen-reader user depends on.
- **INV-13**: Two separate string resources, selected by whether the slide is last. Not one string with a placeholder: "Next"/"Done" are different words, not a substitution, and a placeholder produces untranslatable grammar across 8 locales.

---

## Files touched

| Path | Change |
|---|---|
| `domain/repository/SettingsRepository.kt` | + one-shot read of the current settings (R2) |
| `data/repository/SettingsRepositoryImpl.kt` | + its implementation |
| `data/local/datastore/SettingsDataStore.kt` | + first-value read, if not already reachable |
| `domain/usecase/` | + a use case wrapping that read |
| `MainActivity.kt` | resolve the flag before `setContent`; pass `startDestination` |
| `presentation/onboarding/OnboardingScreen.kt` | **replace** the placeholder |
| `presentation/onboarding/` | + ViewModel, UI state, slide composables, option row |
| `presentation/navigation/AppNavGraph.kt` | pass the nav controller into `OnboardingScreen` |
| `res/values/strings.xml` + 7 locale files | + new keys × 8 locales |

**No file is deleted.** `AppDestination.Onboarding` and the `OnboardingScreen` slot already exist and are reused, not recreated.

---

## What is explicitly NOT modelled

- **No new persisted setting.** The three choices already have storage; the flag already exists (A1).
- **No re-run state.** The flow is not re-runnable from Settings (A5), so there is nothing to model for a second run.
- **No per-slide "seen" record.** Position is session state (INV-4); the app does not remember which slides a user read.
- **No account, sign-in or permission state** (FR-021). The flow asks for none.
