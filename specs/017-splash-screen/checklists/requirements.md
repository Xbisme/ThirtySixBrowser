# Specification Quality Checklist: Splash Screen

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

Counts after clarification: **21 functional requirements** (18 + FR-004a, FR-004b, FR-012a), **13 success criteria** (12 + SC-013), **10 assumptions** (9 + A10), 3 user stories, **10 edge cases** (8 + 2). Numbering is contiguous; the `a`/`b` suffixes follow the convention Spec 015 established (FR-024a, FR-034a) for requirements inserted into an existing group.

Four clarifications were asked and integrated. Two of them closed defects in the spec rather than merely adding detail:

- **FR-001 was unverifiable as written.** It claimed no template artwork would remain, while FR-004 said the mark needs no per-density bitmaps — leaving the 10 bitmap files the project actually ships unaddressed. Had this shipped, the template robot would have remained on Android 7.0 while the criterion appeared to pass. Now split into FR-004 (scalable form), FR-004a (bitmap fallback, regenerated from the same mark) and FR-004b (round variant retired).
- **FR-018 contradicted the Constitution.** It required a spoken screen-reader description for the mark; Constitution §VIII requires decorative-only imagery to be explicitly marked so screen readers skip it. The launch screen has no control and cannot be acted on, so §VIII governs. FR-018 is reversed, and the knock-on is that the spec now introduces **zero** new strings — so FR-017, SC-012 and A6 were all restated, and A6 moved from an assumption to a settled fact.

The other two removed ambiguity that would have produced arbitrary acceptance results: FR-012a now states what a theme-mismatched launch must look like (one clean transition, verified in both directions), and FR-008 now defines "ready" as *the browser window can be drawn* rather than *saved tabs have restored* — without which a tester could not distinguish a correct build, and a 50-tab restore could have silently become the artificial delay FR-008 forbids.

**Technology-agnosticism**, checked by grep for library names, platform API names, file paths, file formats and version numbers: zero hits outside the verbatim `**Input**` line, which records the product owner's own words and is required by the template to be reproduced as given. The pre-spec discussion produced a technically detailed brief; it was deliberately restated in user-facing terms throughout. Examples of the translation applied:

| Brief said | Spec says |
|---|---|
| `androidx.core:core-splashscreen` 1.2.0 | "the supporting library for the launch screen" (A4, A5) |
| VectorDrawable, `<path>`, `<text>` not rendered | "scalable artwork" (FR-004); "the platform's scalable-artwork format cannot draw live text" (A9) |
| `res/drawable-v31/`, AnimatedVectorDrawable | "animates on Android 12 and newer" (FR-009) |
| Adaptive icon, 5 mipmap folders | "the app's identity on the device" (FR-007) |
| 432dp frame / 288dp safe zone | "the region that remains visible under every shape the device may apply" (FR-005) |
| AppCompat theme parent or the app crashes | "the launch window's styling MUST remain compatible with the app's existing screen host" (FR-015) |
| `is_onboarding_completed` flag | "the stored first-run flag" (FR-016) |

**Two judgement calls worth flagging for review:**

1. **FR-015 is deliberately vague about *why*.** The brief states a hard fact — a non-AppCompat theme parent crashes the app at launch. Naming that here would be an implementation detail, so the requirement states the constraint and the verification obligation without the mechanism. Planning must carry the specific cause forward; it is recorded in `CLAUDE.md` and in the Spec 016 notes, so it is not at risk of being lost.

2. **SC-004 replaces a fixed millisecond target with a relative one.** No cold-start baseline figure exists in the project's records, so an absolute target would have been invented. A 5% regression bound against a measured before/after on the same device is verifiable today; an absolute number would not have been. This also avoids the trap Spec 014's T103b and Spec 015's SC-006 fell into — stating a hardware-class target that cannot be measured with the hardware on hand. Per A7, no criterion in this spec needs Pixel 5-class hardware.

**One assumption carries a planning obligation**: A4 states the supporting library has no native code, which is the project's hard 16 KB alignment constraint (Constitution §IX). The spec requires re-verification at the moment the library is added rather than trusting the pre-spec lookup. A5 likewise requires the size contribution to be measured, not assumed, before merge.

**Items marked incomplete require spec updates before `/speckit-plan`.** None are incomplete. `/speckit-clarify` has been run and its four answers are integrated; the spec is ready for `/speckit-plan`.
