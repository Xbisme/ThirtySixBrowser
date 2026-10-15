# Constitution history

One line per amendment, newest first. Detail lives in the commit and, for decisions, in `docs/adr/`.

| Version | Date | Change |
|---|---|---|
| 2.1.0 | 2026-10-15 | Action/event shape moved from Review to Enforced: Konsist onAction and main-dispatcher checks, compose-rules without a baseline (ADR 0026) |
| 2.0.0 | 2026-10-14 | Adopted the cleanarch Android template: eight principles, each MUST with its check; feature-first layout; comment policy; phase order retired (ADR 0005). The full 1.6.0 text is in git history |
| 1.6.0 | 2026-10-11 | Phase 6 gains 024 privacy-tools, split from 023 privacy-protection |
| 1.5.1 | 2026-10-11 | Camera, microphone, location and audio-settings permission rows for Spec 021 |
| 1.5.0 | 2026-10-11 | Phase 6 "real browser" (020–023); page scripts allowed only APK-bundled and one-way |
| 1.4.0 | 2026-10-10 | Compose phase discipline (non-negotiable); Phase 5 UI redesign |
| 1.3.0 | 2026-09-11 | Permission rule reconciled with the shipped manifest (no `POST_NOTIFICATIONS`, legacy storage permission capped at API 28) |
| 1.2.0 | 2026-05-01 | Signing rule split: release keystore for distribution, debug-key fallback locally and in CI |
| 1.1.0 | 2026-04-30 | No-hardcode rule with `core/constants/` layout and the detekt `MagicNumber` gate |
| 1.0.0 | 2026-04-30 | Initial constitution |
