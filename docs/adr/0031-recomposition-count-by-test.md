# 0031. Recomposition counts come from tests, not only Layout Inspector

- Status: accepted
- Date: 2026-10-17

## Context

Quality gate 6 asked for a Layout Inspector recomposition count for each changed screen's hot
interaction. Layout Inspector needs Android Studio driven by hand.

- Specs 019 and 028 both closed with that count left open, because nobody ran it.
- Spec 030 would have been the third.
- A gate that is skipped every time checks nothing. It also hides the regression it was meant to
  catch.

The interactions that matter, such as a drag, a scroll or a progress tick, can be driven from
Compose UI tests. A test can count recompositions with a `SideEffect` in the content it hosts.

## Decision

Gate 6's count comes from an instrumented test:

- the test drives the hot interaction;
- it counts recompositions of the parts that must stay still;
- it asserts an upper bound on that count.

Layout Inspector is used only when no test can drive the interaction. Constitution 2.1.0 → 2.2.0
(MINOR: the check changes, and the principle does not).

## Consequences

- The count runs in `connectedDebugAndroidTest`, locally and in CI, and a regression fails the
  build.
- The bound is set from the design. For example, a drag that reads its offset only in
  `graphicsLayer` recomposes its content at most once per drag.
- Earlier specs keep their recorded state. Their missing counts are not backfilled by this ADR.
