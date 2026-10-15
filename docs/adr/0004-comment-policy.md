# 0004. Comments only where the code cannot speak

- Status: accepted
- Date: 2026-10-14

## Context

Built spec by spec with an AI agent, the app ended with 21.9 % of its non-blank lines as comments
(4,860 comment lines against 17,296 code lines in 462 files). About 4,000 comments cited spec,
requirement or task IDs; 117 files were over 15 % comments and 160 had a comment block over five
lines. The useful sentence in each was hard to find, and the IDs meant nothing without `specs/`.

## Decision

- No comment by default. A comment is allowed for a non-obvious reason, a platform workaround, an
  outside constraint or a non-evident algorithm, in 1–3 lines.
- Spec, task and requirement IDs, document and constitution citations, change history, banners,
  commented-out code and TODOs without a ticket are forbidden. Traceability goes in commits, PRs and
  `specs/`.
- A reason that needs more than three lines goes in an ADR.
- Enforcement: detekt `ForbiddenComment` for content; `CommentPolicyTest` for size (block ≤ 5 lines;
  ≤ 15 % comments in production files of 30+ lines).

## Consequences

- Reasons that used to sit in long KDoc blocks now live in ADRs 0005–0025.
- `scripts/comment-stats.py` reports the share; `scripts/comment-check.py` checks an edit.
