# 0005. The mandatory phase order is retired

- Status: accepted
- Date: 2026-10-14

## Context

Constitution 1.0–1.6 required specs to land in a fixed phase order (Phases 1–4 = v1.0, Phase 5 =
UI redesign, Phase 6 = the "real browser" specs 020–024), and every spec to appear in the roadmap
first. Phases 1–6 (Specs 001–024) were completed in that order by 2026-10-14.

## Decision

The rule is not carried into constitution 2.0.0. From Spec 025 on, the owner decides which spec
comes next. The historical roadmap is kept in `docs/archive/sdd-roadmap.md`.

## Consequences

- No spec needs a phase or a roadmap row to start; `specs/NNN-*` keeps the record.
