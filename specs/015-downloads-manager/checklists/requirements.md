# Specification Quality Checklist: Downloads Manager

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-10
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

**Iteration 1 — 2026-09-10 — PASS (16/16)** *(post-`/speckit-specify`)*

Counts: 7 user stories · 57 functional requirements · 15 success criteria · 12 edge cases · 14 assumptions · 8 dependencies · 13 out-of-scope items · 0 [NEEDS CLARIFICATION] markers.

**Iteration 2 — 2026-09-10 — PASS (16/16)** *(post-`/speckit-clarify`, 5 questions asked and answered)*

No checkbox changed state; the clarifications strengthened requirements that already passed. Counts after clarification: 7 user stories · **67 functional requirements** (57 base, sequential and gap-free, plus 10 sub-numbered: FR-008a/b, FR-014a, FR-024a/b/c, FR-034a/b, FR-036a/b) · 15 success criteria · **15 edge cases** · **15 assumptions** · 8 dependencies · 13 out-of-scope items · **0 [NEEDS CLARIFICATION] markers**.

Two of the five questions closed genuine defects in the first draft rather than merely filling gaps:

- **Cancellation had no home.** FR-036 required the user to be able to cancel an in-flight transfer, but FR-030's four-item action sheet did not offer it and no requirement placed the control anywhere. Now specified as an inline control on the row (FR-036, FR-036a, FR-036b).
- **The hybrid architecture's own premise was unhandled.** The source-of-truth decision rests on the platform service pruning its records on its own schedule, yet no requirement said what a row displays once its handle is forgotten. Now resolved by file-presence inference (FR-024a/b/c), with the accepted limitation recorded as A15.

Two further clarifications hardened requirements that were previously left to the implementer's judgement: the action sheet's contents became an explicit per-state matrix (FR-034a) that also forecloses orphaning an in-flight transfer by removing its record (FR-034b), and an unavailable platform download service now has defined user-visible behaviour plus the invariant that no record exists for a transfer the service never accepted (FR-008a/b) — which is precisely what makes FR-024a's inference sound.

Notes on the two items that needed the closest reading (unchanged from Iteration 1, re-verified against the updated spec):

- **"No implementation details"** — the spec body says "the platform's download service", "the device's public Downloads folder", "the legacy external-storage write permission", "transfer handle" and "an abstraction owned by the app" rather than naming platform classes, permission constants, or libraries. A grep of the body for platform identifiers returns hits only in (a) the verbatim `Input:` line, which quotes the product owner's own words, and (b) the Dependencies section, where prior specs are referred to by their published names — both matching the precedent set by `specs/014-history-view/spec.md`.
- **"Success criteria are technology-agnostic"** — SC-012 (release size delta) and SC-013 (16 KB native alignment) are build-artifact measures rather than user-facing ones. They are retained because Constitution §IX makes 16 KB alignment a non-negotiable gate for every spec, and Spec 014 SC-008/SC-009 set the identical precedent. All other success criteria are expressed as user-observable outcomes.

The behaviours added in Iteration 2 are verified through acceptance scenarios and the FR-034a matrix rather than through new success criteria, matching how Spec 014 handled its own per-entry action rules.

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`. None are currently incomplete.
- **Carried forward to `/speckit-plan` as mandatory research items** (open *questions about the platform*, not gaps in the specification):
  - **R-item (FR-048)**: does the app need to hold the Android 13+ notification permission for the *system* download service's own notifications to be shown? Must be verified on a real device or emulator before the manifest declaration is settled either way.
  - **R-item (FR-026)**: confirm the mechanism by which a completed file is handed to an external app without exposing a raw filesystem path, and whether the platform download service already supplies it.
  - **R-item (FR-024a)**: confirm how and when the platform download service prunes its own records, and that querying a pruned handle fails cleanly rather than throwing — the file-presence fallback depends on detecting that condition reliably.
- **Assumption A6 (incognito downloads are recorded) is now CONFIRMED** by the product owner and promoted to **FR-014a**. It is no longer an open question; overturning it later would require changing FR-014a, A6, and an edge case, and rewriting most of the US2 test coverage.
- **A15 records an accepted limitation**: a download that failed long ago and was then pruned renders identically to one that succeeded and whose file the user later deleted. Accepted deliberately in preference to persisting a terminal outcome, which would have required a completion listener able to run while the app is closed.
- **Known deviation to carry into `plan.md` Complexity Tracking**: the fourth Android permission (legacy external-storage write, version-ceilinged to Android 9 and below), which is not in the Constitution's three-permission table. Note also that the Constitution's permission table lists the notification permission against Spec 015 — if the FR-048 research concludes it is not needed, that table row should be amended rather than left describing a permission the app does not ship.
