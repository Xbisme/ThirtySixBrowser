# Research: Multi-Language Localization Foundation

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-01

## Scope

Spec 004 has **zero `[NEEDS CLARIFICATION]` markers** post-clarify (both Q1/Q2 resolved interactively in [spec.md § Clarifications](spec.md#clarifications)). This research document captures **decision rationale** for the few non-obvious technical choices the plan makes — primarily around lint severity wiring, Chinese locale tag form, and the absence of `AppCompatDelegate` wiring in this milestone.

## R1 — Locales config XML schema for Android 13+

**Decision**: Single `res/xml/locales_config.xml` file using the standard `<locale-config>` schema with eight `<locale>` children.

**Rationale**: This is the only API Android exposes for declaring per-app supported locales (introduced API 33). It is the schema referenced in the official [Android per-app language preferences guide](https://developer.android.com/guide/topics/resources/app-languages). The system Settings → Apps → ThirtySix Browser → Language picker reads this file at install time.

**Schema** (from Android 13+ documentation):

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="vi" />
    <locale android:name="de" />
    <locale android:name="ru" />
    <locale android:name="ko" />
    <locale android:name="ja" />
    <locale android:name="zh" />
    <locale android:name="fr" />
</locale-config>
```

**Wiring**: `AndroidManifest.xml` `<application android:localeConfig="@xml/locales_config" ...>`. No additional `tools:targetApi` annotation needed — `localeConfig` is silently ignored on API < 33, no lint warning.

**Alternatives considered**:
- *Multiple regional variants per language* (e.g., `zh-CN`, `zh-TW`, `zh-HK`) — rejected per spec Assumption (single Chinese variant tagged `zh`).
- *Use `app_locales` Gradle DSL* (auto-generates locales_config from `resourceConfigurations`) — rejected because the auto-generation is opaque, harder to review, and the explicit XML form costs only seven lines of config while making the supported-locales contract reviewable in version control.

## R2 — Chinese locale tag form (`zh` vs `zh-Hans` vs `zh-CN`)

**Decision**: Use bare `zh` for the resource folder name (`values-zh/`) and the `<locale android:name="zh" />` entry.

**Rationale**: Android's resource resolver follows BCP-47 best-match rules. A folder tagged `zh` matches *any* device locale starting with `zh-`, including `zh-CN`, `zh-TW`, `zh-HK`, `zh-Hans`, `zh-Hant`. This is the broadest possible fallback and aligns with the spec's "Simplified Chinese covers all Chinese variants for v1.0" assumption.

**Tradeoff**: Users on `zh-TW` or `zh-Hant` devices will see Simplified Chinese rather than Traditional. This is documented in spec Edge Cases and Assumptions and accepted for v1.0. A future spec may add `values-zh-rTW/` (Traditional) without breaking the existing `values-zh/` (Simplified) — the resolver picks the more specific match.

**Alternatives considered**:
- `b+zh+Hans` (BCP-47 plus syntax, explicitly Simplified script) — rejected because the broad `zh` form already maps to Simplified by convention and avoids unnecessary specificity. AGP supports both forms but the tooling and docs use bare `zh`.
- `zh-rCN` (region-specific Simplified) — rejected because it would NOT match `zh-TW` device locales, leaving Taiwan users falling back to English instead of seeing Simplified Chinese (worse UX than the chosen path).
- `values-zh/` plus `values-zh-rTW/` — rejected as out-of-scope (no Traditional translation available; would require two translation sets, doubling work for negligible v1.0 audience).

## R3 — Lint severity for `MissingTranslation` (Q2 clarification implementation)

**Decision**: Add `error += listOf("MissingTranslation", "ExtraTranslation")` to the existing `android.lint { }` block in `app/build.gradle.kts`.

**Rationale**: Two layers of defense for the FR-008 / SC-005 enforcement gate.

The existing lint config already has:
```kotlin
lint {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    // ...
}
```

`warningsAsErrors = true` implicitly handles many cases, but explicit `error += listOf("MissingTranslation", ...)` is preferred because:
1. **Documentation in code**: Reviewers see the policy by reading `build.gradle.kts` rather than inferring from a global flag.
2. **Survives policy drift**: If a future spec relaxes `warningsAsErrors = true` for some reason, the explicit `error +=` declaration still pins this specific check.
3. **Pairs `MissingTranslation` with `ExtraTranslation`**: catches both directions of asymmetry (key in EN missing from a locale file, AND key in a locale file missing from EN). The latter is rarer but useful when refactoring keys.
4. **AGP 9.x DSL compatibility verified**: The same `+= listOf(...)` form is already used by the spec-001-era `disable += listOf(...)` block — same `Set<String>` API, same syntax.

**Behavior on Spec 004 merge**: With seven new locale files containing all eight keys, the lint check passes. If a future feature adds a string to `values/strings.xml` without translating it, lint fails the build at error severity, blocking merge.

**Alternatives considered**:
- *Rely on `warningsAsErrors = true` alone* — rejected for "documentation in code" reason above. Cost is one line of config.
- *Use a custom lint rule or Detekt rule* — rejected as massively over-engineered. Android Lint's built-in `MissingTranslation` check is exactly what's needed.
- *Run a separate Gradle task for translation completeness* — rejected; Lint already runs in CI and catches this for free.

## R4 — Why no `AppCompatDelegate.setApplicationLocales` in this spec?

**Decision**: Do not wire `AppCompatDelegate.setApplicationLocales` or any related API in Spec 004.

**Rationale**: This API is for **in-app language switching** (the user picks a language inside the app's UI). Spec 004 explicitly excludes the in-app switcher (FR-009, deferred to Spec 016 Settings Screen). The two locale-discovery surfaces this spec DOES enable — automatic device-locale detection (P1) and Android-13+ system per-app picker (P2) — both work entirely through Android's resource system + manifest-declared `localeConfig`. The OS handles the rest:
- Device-wide locale change → Android sends a config-change broadcast → Activity recreates → Compose recomposes with the new locale's resources.
- Per-app picker selection (Android 13+) → OS calls `Activity.recreate()` → same flow.

Constitution VIII says: *"Locale switching MUST take effect without app restart (use `AppCompatDelegate.setApplicationLocales` ...)"*. Spec 004 satisfies the **intent** of this rule (the user does not manually kill/relaunch) without using that specific API in this milestone — the API is reserved for the in-app switcher Spec 016 will add. Future Spec 016 will then call `AppCompatDelegate.setApplicationLocales(...)` from its Settings UI, and the resource files this spec ships are exactly what that API will switch between.

**Alternatives considered**:
- *Wire `AppCompatDelegate` now and hide it behind a flag* — rejected per scope discipline (no speculative code, Constitution X).
- *Add a `LocaleManager` helper class for future Spec 016 use* — rejected; YAGNI, Spec 016 will know what it needs when it's written.

## R5 — Translation source and quality bar

**Decision**: Initial baseline translations seeded from machine translation (e.g., Google Translate, DeepL) for the 7 non-English locales. Native-speaker review deferred to per-feature spec when those features ship user-facing copy.

**Rationale**: The 8 baseline strings are simple ("Browser", "Tabs", "Bookmarks", etc.) — common nouns with established translations across all 7 target languages. Machine translation is highly reliable for this register. Spending native-speaker review effort on placeholder labels that may be replaced when future feature specs implement real Screens would be wasted effort. The contract Spec 004 establishes is **coverage** (no missing translations) plus **enforcement** (lint blocks gaps), not literary polish.

**Quality floor**: Each translation must
1. Use a recognizable noun in the target language (not English left untranslated).
2. Avoid introducing diacritics-stripped or transliterated Latin forms when the target script is non-Latin (Korean, Japanese, Chinese, Russian).
3. Match the platform convention for that locale (e.g., `Закладки` not `Букмарки` for "bookmarks" in Russian).

**Alternatives considered**:
- *Require professional translator before merge* — rejected; cost-prohibitive for placeholder strings; raises Spec 004 from a 1-day task to a multi-week pipeline blocker.
- *Use only English in all 7 non-English files* — rejected; defeats the entire purpose of the spec; would still pass lint (no missing keys) but fails SC-001 on manual locale verification.

## R6 — `app_name` translation strategy across 8 locales (Q1 clarification implementation)

**Decision**: The English baseline `app_name` is `"ThirtySix Browser"`. The seven non-English locale files translate the *common noun* "Browser" but transliterate or preserve the *proper noun brand* "ThirtySix" per the conventional treatment of brand names in each target language.

**Rationale**: "ThirtySix" is the brand identifier; translating the numeral "thirty-six" into each language ("trente-six", "三十六", "삼십육", etc.) would create eight different brand names — terrible for searchability, store discoverability, and brand recognition. Industry convention (Chrome, Firefox, Edge) keeps the brand untranslated and translates only the descriptor.

**Per-locale plan**:

| Locale | `app_name` value | Notes |
|--------|------------------|-------|
| `en` (default) | `ThirtySix Browser` | Canonical |
| `vi` | `Trình duyệt ThirtySix` | "Browser" → "Trình duyệt"; brand preserved |
| `de` | `ThirtySix Browser` | German uses "Browser" as loanword; no change. |
| `ru` | `ThirtySix Браузер` | "Browser" → "Браузер"; brand preserved in Latin script (industry convention) |
| `ko` | `ThirtySix 브라우저` | "Browser" → "브라우저"; brand preserved in Latin |
| `ja` | `ThirtySix ブラウザ` | "Browser" → "ブラウザ" (katakana); brand preserved in Latin |
| `zh` | `ThirtySix 浏览器` | "Browser" → "浏览器"; brand preserved in Latin |
| `fr` | `Navigateur ThirtySix` | "Browser" → "Navigateur"; brand preserved |

**Alternatives considered**:
- *Keep `"ThirtySix Browser"` literal in all 8 files* — rejected; users in non-English locales would see English in their launcher, undermining SC-001 (100% strings render in target locale).
- *Translate "ThirtySix" too* — rejected per brand-name convention above.
- *Use a unified `app_name` only in `values/` and rely on fallback* — would technically work (non-English files inherit EN) but explicitly fails FR-006 ("translations in all eight locales") and SC-001 (must be in target locale, not English).

## R7 — Resource folder naming (`values-zh` vs `values-zh-rCN` etc.)

**Decision**: Use `values-{locale}/` form for all 7 non-English locales (no region qualifier). Specifically: `values-vi`, `values-de`, `values-ru`, `values-ko`, `values-ja`, `values-zh`, `values-fr`.

**Rationale**: Android's resource resolver matches device locale to the most specific resource folder available. With no region qualifier, `values-de/` covers German users in Germany (`de-DE`), Austria (`de-AT`), Switzerland (`de-CH`), etc. — all fall back to the single `de` translation, which is correct for v1.0 coverage. Same for French (`fr-FR`, `fr-CA`, `fr-BE` → `fr`) and Russian.

**Alternatives considered**:
- `values-vi-rVN/`, `values-de-rDE/`, etc. — rejected; over-specifies and excludes regional variants the spec does want to cover (e.g., `values-de-rDE/` would NOT match an Austrian `de-AT` device, forcing English fallback). Edge case in spec explicitly says regional variants resolve to closest supported language, not English.
- BCP-47 plus form (`values-b+vi/`) — equivalent semantics, more verbose, no benefit.

## R8 — Existing `app_name` typo cleanup blast radius

**Decision**: Modify `res/values/strings.xml` only. No Kotlin code references the literal "ThirdtySixBrowser" anywhere because Spec 003 already renamed the theme class from `ThirdtySixBrowserTheme` → `ThirtySixTheme`. Only the typo lived in `app_name` resource; flipping it to `"ThirtySix Browser"` ripples zero-cost.

**Verification before merge**: `git grep -i "thirdty"` should return zero results in source/build files. (Doc files like `.claude/claude-app/project-context.md` and `CLAUDE.md` may retain *historical* references describing the typo for decision-log reasons — those are intentional records, not live typos.)

**Rationale for not bumping a "rename ceremony"**: This is a typo fix in a resource value. App namespace stays `com.raumanian.thirtysix.browser`. Application ID, package, and class names are already correct. The launcher icon label was the only place users could see the typo, and v1.0 hasn't shipped yet — no upgrade-path concern for existing installs.

**Late discovery during implementation**: `settings.gradle.kts:31` had `rootProject.name = "ThirdtySixBrowser"` (the same template typo replicated at the Gradle root level). Not user-visible — `rootProject.name` only affects IDE display + build directory paths — but cleaning it up here ($1 line edit) closes the typo at every scope below the OS-level directory name. Dir path itself (`/Users/.../ThirdtySixBrowser/`) and GitHub repo name are user-owned naming choices left as-is. Fixed inline as part of T021 polish.

## Open items deferred to plan / tasks

None. All technical decisions resolved at planning time. Phase 0 complete.
