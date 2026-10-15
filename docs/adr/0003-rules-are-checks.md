# 0003. Every rule a machine can check is a failing check

- Status: accepted
- Date: 2026-10-14

## Context

Constitution 1.6.0 was 906 lines of prose and `CLAUDE.md` 804 lines; few rules had a check, and
the code broke several of them (domain importing data, layer-first layout). Rule files lose
adherence as they grow.

## Decision

- Architecture, placement, hard-coded values, dispatcher injection and comment size: Konsist 0.18.1
  tests in `app/src/test/.../architecture/`, run by `testDebugUnitTest`.
- Comment content and Kotlin style: detekt 1.23.8 (`config/detekt/detekt.yml`) over main, test,
  androidTest and debug sources. Compose API rules: compose-rules 0.6.8 through ktlint.
- The constitution names the check behind every MUST; `AGENTS.md` stays under 100 lines.
- Files that broke a rule written later are listed in an allowlist with their path, and the list only
  shrinks.

Three gaps of the template were found while wiring the checks:
- Konsist 0.18 is Java 17 bytecode; unit tests run on a JDK 21 launcher while compilation stays on
  toolchain 11.
- `Konsist.scopeFromProduction()` also scans sample `.kt` files under `specs/`; the scope is limited
  to the `app` module.
- detekt's `InjectDispatcher` needs type resolution, which `./gradlew detekt` does not run; a Konsist
  test (`dispatchers are injected`) enforces the rule instead.

## Consequences

- A broken rule is a red build, not a review comment.
- Changing a rule means changing its check and the constitution in the same commit.
- `scripts/comment-check.py` verifies comment-only edits (the code with comments removed must be
  unchanged) and reports the same violations as the checks.
