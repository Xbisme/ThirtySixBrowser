# Specification Quality Checklist: Onboarding Flow

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-15
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

## Validation Notes

**Iteration 2 (after `/speckit-clarify`, 2026-09-15) — all items still pass. 16/16 → 16/16; no item changed state.**

Counts after clarification: **27 functional requirements** (24 + FR-002a, FR-002b, FR-010a) · **15 success criteria** (14 + SC-015) · **10 assumptions** · **4 user stories** · **11 edge cases** (9 + 2). Numbering is contiguous; the `a`/`b` suffixes follow the convention established in Spec 015 (FR-024a) and Spec 017 (FR-004a) for requirements inserted into an existing group.

Three clarifications were asked and integrated. All three closed gaps that would have produced arbitrary acceptance results rather than merely adding detail:

- **FR-002a — the system Back gesture was undefined.** FR-002 said the user can "move backward" without saying whether that included the device's own Back gesture, on a screen where every other screen in this app handles Back explicitly. The worst available reading was that Back closes the app on the very first launch, which would look like a crash. Back now moves to the previous slide, and on the first slide leaves the app **without** recording completion — leaving by a system gesture is not consent to never being asked again.
- **FR-010a — the flow could be reached again by pressing Back.** Nothing said the flow leaves the navigation history, so a user who had just finished it could land back in it from the browser's first page. That contradicts A5's "appears once per install". Note the first-run flag does **not** cover this: the flag governs the *next launch*, the history governs the *current* one.
- **FR-002b — the finishing control was unspecified**, while SC-008 was quietly budgeting for it.

**One arithmetic error in the original spec was found and corrected.** SC-008 read "at most 5 interactions — one per slide plus a finish". That assumed a separate finishing step, which the Q3 answer removes: with the forward control simply relabelled on the last slide, accepting every default is **4 taps**, not 5. The criterion now states 4, and states that each choice actually changed adds exactly one, so changing all three settings costs 7. Had this gone unfixed, a correct build would have measured 4 against a target of 5 and the discrepancy would have had to be explained away at gate time.

**Technology-agnosticism re-checked after all three integrations**: still zero hits outside the verbatim `**Input**` line and the Spec 006 name in Dependencies. In particular, FR-010a describes the *observable* outcome ("using the Back gesture from the browser's first page leaves the app") rather than naming the navigation mechanism that achieves it.

**Technology-agnosticism**, checked by grep for framework names, platform APIs, file paths and Kotlin constructs: the only hit outside the verbatim `**Input**` line is the word "datastore" inside the *name* of Spec 006 in the Dependencies section, which is how every prior spec refers to its dependencies. The pre-spec brief was heavily technical and was deliberately restated:

| Brief said | Spec says |
|---|---|
| `AppCompatDelegate.setApplicationLocales` → recreate activity | "Changing the app language restarts the screen" (FR-015) |
| `is_onboarding_completed` in DataStore | "the first-run flag" / "first-run has been recorded as complete" (FR-013) |
| `AppDestination.Onboarding`, `startDestination`, nav graph | "the screen slot the app already has" (FR-013, A1) |
| `rememberSaveable` / `SavedStateHandle` | "the current slide position MUST survive a screen restart" (FR-015, entity 3) |
| No separate Activity — single-activity model | Stated as a consequence in the clarification, not as a requirement |
| minSdk 24 / targetSdk 36 | "the minimum and the maximum supported Android versions" (SC-014) |

**Three judgement calls worth flagging for review:**

1. **FR-014 ("the browser MUST NOT appear, even briefly") is stated as a user-visible guarantee, not a mechanism.** The brief said the flag must be read *before* the nav graph is built. That is the implementation; the observable requirement is that no browser frame is seen. SC-002 makes it verifiable by frame-by-frame inspection. Planning must carry the ordering constraint forward — it is recorded in the brief and in this note.

2. **SC-008 counts interactions rather than seconds.** A time target for a four-slide flow would be dominated by reading speed, which the app does not control and which varies by locale. "At most 5 interactions to complete, 1 to leave" is a property of the design and is verifiable by counting taps.

3. **Skip is specified as leaving settings untouched, not as writing the defaults (A4).** Both produce the same observable result today, because the stored defaults and the effective defaults agree. Writing them explicitly would make skip a disguised write and would silently pin a value that currently follows the system — so the weaker, honest behaviour was chosen and recorded as an assumption rather than left implicit.

**Two assumptions carry obligations into planning:**

- **A7** — no new dependency is expected, but if one proves unavoidable its version must be looked up at the moment of addition and its native-code status verified (Constitution §IX).
- **A2** — the option lists and labels must come from the same source the Settings screen uses, so the two screens cannot disagree. ⚠️ Planning must verify *how* much can be reused: the brief notes that Spec 016's three choosers carry KDoc saying "reuse unchanged" but are all dialogs, whereas this flow needs the options shown inline on a slide. **Treat that KDoc as a claim to test, not a fact.**

**Items marked incomplete require spec updates before `/speckit-plan`.** None are incomplete. `/speckit-clarify` has been run; its three answers are integrated alongside the five pre-spec clarifications, and no open questions remain. The spec is ready for `/speckit-plan`.
