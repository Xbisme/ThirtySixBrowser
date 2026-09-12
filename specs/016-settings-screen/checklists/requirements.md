# Specification Quality Checklist: Settings Screen

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-11
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Validation Record

**Iteration 1 — 2026-09-11 — PASS (16/16)** *(post-`/speckit-specify`)*

Counts: 4 pre-spec clarifications · 7 user stories · 35 acceptance scenarios · 45 functional requirements (sequential, gap-free) · 16 success criteria · 14 edge cases · 15 assumptions · 10 dependencies · 12 out-of-scope items · **0 [NEEDS CLARIFICATION] markers**. Automated checks: every `FR-` and `A` cross-reference resolves to a defined item, and no trailing whitespace.

One defect was found and fixed during validation. The edge case for shortening retention over a 10,000-entry history originally leaned on FR-032 "by analogy" — but FR-032 governs the clear-data dialog, so the responsiveness requirement for retention deletion had no home of its own. FR-022 now states it directly, and the edge case cites FR-022 and SC-010.

Notes on the two items that needed the closest reading:

- **"No implementation details"** — a case-insensitive grep of the spec body (everything after the `Input:` line and before `## Dependencies`) for platform and library identifiers returns **zero** hits. Such identifiers appear only in (a) the verbatim `Input:` line, which quotes the product owner's own words, and (b) the Dependencies section, which names prior specs by their published titles — the precedent set by Specs 014 and 015. FR-041 and FR-042 state architectural obligations in the project's own vocabulary (repository interface, use case, view-model, an app-owned abstraction), mirroring Spec 015's FR-053 and FR-054.
- **"Success criteria are technology-agnostic"** — SC-014 (release size delta), SC-015 (16 KB native alignment) and SC-016 (Constitution Check) are build-artifact and governance measures rather than user-facing ones. They are retained because Constitution §IX makes 16 KB alignment a non-negotiable gate for every spec, matching Spec 014 SC-008/SC-009 and Spec 015 SC-012–SC-014. Every other criterion is a user-observable outcome.

**Iteration 2 — 2026-09-11 — PASS (16/16)** *(post-`/speckit-clarify`, 4 questions asked and answered)*

No checkbox changed state. The answers confirmed A1 (retention of 7 / 30 / 90 / 180 days) and A2 (warn and confirm before shortening), split SC-009 into a gating emulator criterion and a recorded, non-gating hardware measurement, and turned SC-014's budget into a measured figure rather than a guess.

**Iteration 3 — 2026-09-11 — PASS (16/16)** *(post-`/speckit-plan`)*

Planning amended the spec in two ways, both re-validated here:

- **A product decision taken mid-planning** (clarification 9): the platform's long-standing storage-clearing call leaves service workers and Cache Storage behind on every device. FR-028 now names them, A13 was revised to two supporting libraries, and A16, A17 and one new edge case record the two consequences.
- **SC-014 filled in** from release-build measurements: at most 3,258,091 bytes (+699,913 B).

Counts: 9 clarifications · 7 user stories · 35 acceptance scenarios · 45 functional requirements · 16 success criteria · **15 edge cases** · **17 assumptions** · 10 dependencies · 12 out-of-scope items · 0 [NEEDS CLARIFICATION] markers. The implementation-term grep of the body still returns zero hits: the new text speaks of a "web engine", "service workers" and "Cache Storage" — web-platform concepts a stakeholder can verify — and never names a library or class.

**Iteration 4 — 2026-09-11 — PASS (16/16)** *(post-`/speckit-analyze` remediation)*

No checkbox changed state and the counts are unchanged (45 FRs, 16 SCs, 17 assumptions). Analysis found that leaving the browser screen always releases its web view, which made every "no page reload" criterion unverifiable — US1's story, independent test and scenario 3, FR-004, FR-008, FR-012 and SC-002. They now require that the app's screens are not rebuilt and that the same tab returns at the same address. SC-002 and SC-004 gained a definition of when timing starts and stops, and FR-022, FR-032, SC-010 and one edge case now say "no ANR" instead of the unmeasurable "responsive".

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`. None are currently incomplete.
- **Status after planning (2026-09-11)**: every candidate below was resolved — A1 and FR-021 by clarification, SC-009 by clarification, SC-014 by measurement — and every research item was answered in [research.md](../research.md), under R1, R2, R5, R6, R7, R8 and R10. The notes below are kept as the record of what planning started from.
- **Candidates for `/speckit-clarify`** — each has a documented default, so none is a gap, but each is a product choice the owner may want to confirm:
  - **A1 — the retention set** (7 / 30 / 90 / 180 days). The 180-day ceiling rests on Spec 014's ~300 B-per-entry memory measurement; a longer option would reopen that risk.
  - **FR-021 — warning before shortening retention**. Chosen because shortening deletes irreversibly; the alternative is to apply silently.
  - **SC-014 — the +400 KB size budget** is provisional: the supporting library's real size impact after shrinking has not been measured.
  - **SC-009 — the 3-second clearing target** on Pixel 5-class hardware.
- **Carried forward to `/speckit-plan` as research items** (open questions about the platform and codebase, not gaps in the specification):
  - **R — language mechanism on Android 7.0–12 (FR-015–FR-017)**: official documentation (verified 2026-09-11) says per-app language switching below Android 13 works only when the activity extends `AppCompatActivity`, which in turn requires an activity theme descending from `Theme.AppCompat`. Today `MainActivity` extends `ComponentActivity` and `Theme.ThirtySix` descends from `android:Theme.Material.*`. Plan must cover the base-class and theme migration, prove Spec 003's cold-start `windowBackground` fix survives in both `values/` and `values-night/`, and decide between the library's automatic locale storage (documented to perform a blocking main-thread disk read) and app-managed storage applied before `onCreate`. `androidx.appcompat` 1.8.0 was the latest stable on 2026-09-11 with zero `.so` across its declared dependency graph; re-verify both at the moment of addition (§IX), including against the versions Gradle actually resolves.
  - **R — instrumented test host**: the Hilt test activity and existing Compose instrumented tests assume a `ComponentActivity` host; confirm what the base-class change requires of them.
  - **R — retiring the Spec 006 language value (FR-017)**: the `language_override` key, the `LanguageOverride` model, its setter use case and their tests become dead. The `StorageKeys` deprecation rules bind only keys that have shipped to users, and none has, so plan should confirm outright removal is permitted.
  - **R — clearing mechanism (FR-027–FR-029)**: choose between the framework APIs — cookie removal (asynchronous, Looper-bound callback), web storage deletion, and HTTP cache clearing (which needs a live web view on its creating thread but clears the whole app's cache) — and `androidx.webkit`'s `WebStorageCompat.deleteBrowsingData` (added in 1.13.0, stable 1.17.0 on 2026-09-11, feature-gated on the installed WebView version, and a new dependency whose transitive native-code status is unverified). The favicon cache currently has **no** clear-all operation, while the screenshot cache does.
  - **R — the incognito discard rule (FR-030)**: the Spec 012 cookie snapshot manager's restore step returns early when no snapshot is held, which skips wiping the cookies set during the incognito session. "Discarding" the set-aside cookies must therefore replace the held snapshot with an **empty** one — not remove it — or clearing cookies mid-session would leak incognito cookies into normal browsing when the session ends.
  - **R — retention (FR-020–FR-024)**: the start-up sweep currently reads the fixed `MAX_HISTORY_DAYS` constant; plan must move the window into persisted settings, keep the sweep independent of the History screen, and add the immediate prune on confirmed shortening.
  - **R — version display (FR-034)**: build-config generation is not enabled in `app/build.gradle.kts` (only Compose is), so plan must choose between enabling it and reading the installed version at runtime.
  - **R — size budget (SC-014)**: measure the release APK delta once the supporting library is added, before the budget is treated as settled.
- **Constitution posture expected at plan time**: §VIII is satisfied as written by the chosen language mechanism, so no amendment is anticipated. The one new dependency must pass §IX at the moment of addition. No permission change is expected.
