---
description: "Task list for Spec 004 — Multi-Language Localization Foundation"
---

# Tasks: Multi-Language Localization Foundation

**Input**: Design documents from `/specs/004-localization-multi-language/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)

**Tests**: No automated unit/integration test tasks — Spec 004 ships zero Kotlin code. The Android Lint `MissingTranslation` / `ExtraTranslation` checks (configured in US3) ARE the automated test for translation completeness. Manual emulator verification per [quickstart.md](quickstart.md) covers acceptance scenarios.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. Spec 004's three stories are fully independent — any one can be implemented and verified without the others.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- All file paths are relative to repo root `/Users/xbism3/Documents/Code/jetpack/ThirdtySixBrowser/`

## Path Conventions

- **Mobile (Android single-module)**: app code under `app/src/main/`; resources under `app/src/main/res/`; manifest at `app/src/main/AndroidManifest.xml`; build config at `app/build.gradle.kts`.
- **No new directories** introduced by Spec 004; all new files land in existing `app/src/main/res/` subtree.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization tasks shared across all stories.

> No project-level setup needed. Module structure, Gradle config, lint config block, and existing `app/src/main/res/{values,xml}/` directories were established in Specs 001 + 002 + 003. Spec 004 adds files into the existing tree without scaffolding changes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented.

> No blocking foundational tasks. Each of the three user stories below is fully independent — US1 (translations), US2 (locales_config + manifest), and US3 (lint config) modify disjoint file sets and can be implemented in any order, in parallel, or one at a time.

**Checkpoint**: User story implementation can begin immediately.

---

## Phase 3: User Story 1 — App Speaks the User's Language by Default (Priority: P1) 🎯 MVP

**Goal**: Ship the seven non-English translation files plus the canonical English baseline so that, on a device whose system locale is one of the 8 supported, the app's launcher label and all 7 placeholder Screen titles render in that locale automatically. For English-locale (or unsupported-locale fallback) users, the launcher displays the corrected canonical name `ThirtySix Browser` (Q1 clarification).

**Independent Test**: Per [quickstart.md Gate 4](quickstart.md#gate-4--per-locale-visual-verification-p1--p2-acceptance) and [Gate 5](quickstart.md#gate-5--device-wide-locale-fallback-covers-android-712) — switch device system language to each of the 7 non-English locales in turn, launch the app, verify launcher icon label and all visible Screen titles match the per-locale value table in [data-model.md § per-locale value table](data-model.md#entity-2--baseline-string-catalog). Switch to an unsupported locale (e.g., Portuguese) and verify graceful English fallback.

### Implementation for User Story 1

- [ ] T001 [US1] Fix `app_name` typo in `app/src/main/res/values/strings.xml`: change `<string name="app_name">ThirdtySixBrowser</string>` to `<string name="app_name">ThirtySix Browser</string>` (with space). Q1 clarification per [spec.md § Clarifications](spec.md#clarifications). Leave the other 7 `*_screen_placeholder` keys untouched (they were created in Spec 002 with already-correct English values).
- [ ] T002 [P] [US1] Create `app/src/main/res/values-vi/strings.xml` with all 8 keys, Vietnamese values per [data-model.md § per-locale value table](data-model.md#entity-2--baseline-string-catalog) row "vi" (e.g., `app_name = "Trình duyệt ThirtySix"`, `browser_screen_placeholder = "Trình duyệt"`, etc.).
- [ ] T003 [P] [US1] Create `app/src/main/res/values-de/strings.xml` with all 8 keys, German values (e.g., `app_name = "ThirtySix Browser"`, `bookmarks_screen_placeholder = "Lesezeichen"`, `history_screen_placeholder = "Verlauf"`, `settings_screen_placeholder = "Einstellungen"`, etc.).
- [ ] T004 [P] [US1] Create `app/src/main/res/values-ru/strings.xml` with all 8 keys, Russian values (e.g., `app_name = "ThirtySix Браузер"`, `bookmarks_screen_placeholder = "Закладки"`, `history_screen_placeholder = "История"`, etc.). Brand "ThirtySix" stays in Latin script per [research.md § R6](research.md#r6--app_name-translation-strategy-across-8-locales-q1-clarification-implementation).
- [ ] T005 [P] [US1] Create `app/src/main/res/values-ko/strings.xml` with all 8 keys, Korean values (e.g., `app_name = "ThirtySix 브라우저"`, `tabs_screen_placeholder = "탭"`, `bookmarks_screen_placeholder = "북마크"`, etc.). Brand "ThirtySix" in Latin.
- [ ] T006 [P] [US1] Create `app/src/main/res/values-ja/strings.xml` with all 8 keys, Japanese values (e.g., `app_name = "ThirtySix ブラウザ"`, `tabs_screen_placeholder = "タブ"`, `bookmarks_screen_placeholder = "ブックマーク"`, etc.). Brand "ThirtySix" in Latin.
- [ ] T007 [P] [US1] Create `app/src/main/res/values-zh/strings.xml` with all 8 keys, Simplified Chinese values (e.g., `app_name = "ThirtySix 浏览器"`, `tabs_screen_placeholder = "标签页"`, `bookmarks_screen_placeholder = "书签"`, etc.). Single `values-zh/` covers all Chinese variants per [research.md § R2](research.md#r2--chinese-locale-tag-form-zh-vs-zh-hans-vs-zh-cn).
- [ ] T008 [P] [US1] Create `app/src/main/res/values-fr/strings.xml` with all 8 keys, French values (e.g., `app_name = "Navigateur ThirtySix"`, `bookmarks_screen_placeholder = "Favoris"`, `history_screen_placeholder = "Historique"`, etc.).
- [ ] T009 [US1] Run `./gradlew assembleDebug` from repo root. Verify BUILD SUCCESSFUL with zero `MissingTranslation` lint reports (the existing `lint { warningsAsErrors = true; abortOnError = true }` block will catch any gap even before US3's explicit error escalation lands).
- [ ] T010 [US1] Run `./gradlew installDebug` and on an emulator (any API level 24+), spot-check **at least three non-English supported locales — coverage MUST include one CJK script (Korean, Japanese, or Chinese), one Cyrillic script (Russian), and one Latin-diacritic script (Vietnamese, German, or French)**. For each, set device system language and verify the launcher icon label and 7 placeholder Screen titles match [data-model.md § per-locale value table](data-model.md#entity-2--baseline-string-catalog). Also set device language to Portuguese (unsupported) and verify English fallback per [quickstart.md Gate 6](quickstart.md#gate-6--unsupported-locale-fallback-covers-sc-001-negative-path). Full 7-locale visual sweep is deferred to Polish T017.

**Checkpoint**: User Story 1 fully functional. App auto-detects device locale across all 8 supported, falls back to English on unsupported. SC-001, SC-004, SC-006 acceptance criteria met.

---

## Phase 4: User Story 2 — Android 13+ Per-App Language Picker (Priority: P2)

**Goal**: Declare the supported-locales set to the operating system so that, on Android 13+ devices, the system Settings → Apps → ThirtySix Browser → Language picker appears and lists exactly the eight supported locales plus "System default."

**Independent Test**: Per [quickstart.md Gate 3](quickstart.md#gate-3--system-per-app-language-picker-android-13). Open device Settings → Apps → ThirtySix Browser → Language; verify exactly 9 entries appear (8 locales + "System default"). Pick each in turn and verify app displays in chosen language while device-wide UI remains in original locale.

### Implementation for User Story 2

- [ ] T011 [US2] Create `app/src/main/res/xml/locales_config.xml` with `<locale-config>` root and 8 `<locale android:name="..." />` children for tags `en`, `vi`, `de`, `ru`, `ko`, `ja`, `zh`, `fr`. Schema per [research.md § R1](research.md#r1--locales-config-xml-schema-for-android-13). Note: `app/src/main/res/xml/` already exists from Spec 001 (`backup_rules.xml`, `data_extraction_rules.xml`).
- [ ] T012 [US2] Modify `app/src/main/AndroidManifest.xml`: add the attribute `android:localeConfig="@xml/locales_config"` to the existing `<application>` element (alongside `android:name`, `android:label`, etc.). No `tools:targetApi` annotation needed — `localeConfig` is silently ignored on API < 33. Depends on T011.
- [ ] T013 [US2] Run `./gradlew assembleDebug && ./gradlew installDebug`. On an Android 13+ emulator/device, navigate Settings → Apps → ThirtySix Browser → Language. Verify per [quickstart.md Gate 3](quickstart.md#gate-3--system-per-app-language-picker-android-13) that 9 entries appear (8 supported + "System default"). Select each non-English locale in turn; verify per [quickstart.md Gate 4](quickstart.md#gate-4--per-locale-visual-verification-p1--p2-acceptance) that the app displays in the chosen locale while device-wide UI remains in the original system language.

**Checkpoint**: User Story 2 fully functional. Per-app picker on Android 13+ shows exactly the supported set. SC-002 and SC-003 acceptance criteria met.

---

## Phase 5: User Story 3 — Translation Completeness Gate (Priority: P3)

**Goal**: Configure the build's lint stage to fail at error severity (blocking merge) on any missing or extra translation, so future feature specs cannot ship a string in EN without translating it into all 7 non-English locales.

**Independent Test**: Per [quickstart.md Gate 1 negative test](quickstart.md#gate-1--static-analysis--lint-pass). Temporarily delete one entry from a non-English `strings.xml`; run `./gradlew lintDebug`; verify build FAILS with `MissingTranslation` error. Restore the entry; verify build passes.

### Implementation for User Story 3

- [ ] T014 [US3] Modify `app/build.gradle.kts` — inside the existing `android.lint { }` block (currently sets `abortOnError = true`, `warningsAsErrors = true`, `checkReleaseBuilds = true`, plus a `disable += listOf(...)` block for AGP-9.x false positives), add a new line: `error += listOf("MissingTranslation", "ExtraTranslation")`. Place it after the `disable += ...` block. Add a brief code comment referencing Spec 004 FR-008 / SC-005 and Q2 clarification rationale (belt-and-suspenders on top of `warningsAsErrors = true`). Rationale documented in [research.md § R3](research.md#r3--lint-severity-for-missingtranslation-q2-clarification-implementation).
- [ ] T015 [US3] Verify gate fires (negative test): in any non-English locale file (e.g., `app/src/main/res/values-vi/strings.xml`), temporarily comment out one `<string>` entry. Run `./gradlew lintDebug`. Confirm BUILD FAILED with a `MissingTranslation` error naming the missing key and the locale. Restore the deleted line and re-run `./gradlew lintDebug` to confirm green.
- [ ] T016 [US3] Verify gate also catches ExtraTranslation (negative test): add a key to `app/src/main/res/values-vi/strings.xml` that does NOT exist in `app/src/main/res/values/strings.xml`. Run `./gradlew lintDebug`. Confirm BUILD FAILED with an `ExtraTranslation` error. Remove the spurious key.

**Checkpoint**: User Story 3 fully functional. Translation completeness is now build-blocking; no future feature spec can ship a string without all 8 translations. SC-005 acceptance criterion met.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification gates and post-implementation context updates.

- [ ] T017 Run full [quickstart.md](quickstart.md) 10-gate verification on an Android 13+ emulator/device (or split: API 36 emulator covers Gates 1–4, 7–10; API 30 emulator covers Gates 5–6).
- [ ] T018 [P] Run `./gradlew testDebugUnitTest` and confirm all existing tests pass (Spec 004 adds zero tests; Spec 003 baseline 12/12 should remain).
- [ ] T019 [P] Run `./gradlew detekt ktlintCheck` and confirm zero new violations. Detekt baseline file (`app/detekt-baseline.xml`) MUST remain unchanged from Spec 003.
- [ ] T020 [P] Run `./gradlew assembleRelease` and verify APK size delta vs Spec 003 baseline ≤ 50KB ([quickstart.md Gate 9](quickstart.md#gate-9--apk-size-sanity-check)). Verify 16KB CI gate still passes (no new `.so` introduced; existing `libandroidx.graphics.path.so` alignment unchanged).
- [ ] T021 [P] Run `git grep -n -i "thirdty"` from repo root. Confirm zero matches (the Spec 003 theme rename + Spec 004 `app_name` fix close the last template typo). [quickstart.md Gate 10](quickstart.md#gate-10--git-grep-smoke-test-no-remaining-typo).
- [ ] T022 Update `CLAUDE.md`: header "Last updated" date, header "Current Status" block, Spec Roadmap table row 004 → ✅ Done 2026-05-01, Recent Changes prepend new entry summarizing Spec 004, SPECKIT block flip Active to next spec (005 / 006 per user direction). Mirror the structure used for Spec 002 / Spec 003 entries.
- [ ] T023 Update `.claude/claude-app/sdd-roadmap.md`: header status line + spec table row 004 → ✅ Done 2026-05-01.
- [ ] T024 Update `.claude/claude-app/project-context.md`: header date + status block + new Key Decisions Log entry for Spec 004 (locales config strategy, Chinese tag form, lint severity wiring, no `AppCompatDelegate` in this milestone, brand-name preservation across locales, app_name typo fix).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Empty — no dependencies.
- **Foundational (Phase 2)**: Empty — no blocking prerequisites.
- **User Story 1 (Phase 3, P1)**: No dependencies — can start immediately.
- **User Story 2 (Phase 4, P2)**: No dependencies — can start immediately. Independent of US1.
- **User Story 3 (Phase 5, P3)**: No dependencies — can start immediately. Independent of US1 and US2.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

All three user stories are **fully independent** by design — they modify disjoint file sets:

- US1 → `app/src/main/res/values/` and `values-{vi,de,ru,ko,ja,zh,fr}/` only
- US2 → `app/src/main/res/xml/locales_config.xml` and `app/src/main/AndroidManifest.xml` only
- US3 → `app/build.gradle.kts` only

No story shares a file with another. No story imports from another. Each can be verified in isolation per its independent-test criterion above.

### Within Each User Story

- US1: T001 (EN baseline fix) is independent of T002–T008. T002–T008 are all parallelizable [P]. T009 (build verify) requires T001–T008. T010 (manual emulator verify) requires T009.
- US2: T011 (locales_config) before T012 (manifest reference). T013 (manual verify) requires both.
- US3: T014 (lint config) before T015–T016 (negative-path verification).

### Parallel Opportunities

- Within US1: 7 translation files (T002–T008) are fully parallelizable — different file paths, no shared content.
- Across user stories: All three stories can be implemented in parallel by different developers (or in different terminals by a single developer) since file sets are disjoint.
- Polish phase: T018, T019, T020, T021 are independent Gradle invocations / shell checks — parallelizable. T022, T023, T024 are doc edits to different files — parallelizable.

---

## Parallel Example: User Story 1

```bash
# All 7 translation files can be created concurrently — different file paths, no shared content:
Task: "Create app/src/main/res/values-vi/strings.xml with 8 baseline keys (Vietnamese values)"
Task: "Create app/src/main/res/values-de/strings.xml with 8 baseline keys (German values)"
Task: "Create app/src/main/res/values-ru/strings.xml with 8 baseline keys (Russian values)"
Task: "Create app/src/main/res/values-ko/strings.xml with 8 baseline keys (Korean values)"
Task: "Create app/src/main/res/values-ja/strings.xml with 8 baseline keys (Japanese values)"
Task: "Create app/src/main/res/values-zh/strings.xml with 8 baseline keys (Simplified Chinese)"
Task: "Create app/src/main/res/values-fr/strings.xml with 8 baseline keys (French values)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: nothing to do.
2. Phase 2: nothing to do.
3. Phase 3 (US1): T001–T008 (EN typo fix + 7 translation files).
4. **STOP and VALIDATE**: T009 (build) + T010 (emulator visual sweep across VI, FR, PT-fallback).
5. **MVP demo-ready**: app respects device locale automatically — the core internationalization promise.

After MVP, US2 and US3 add quality and discoverability without touching the same files.

### Incremental Delivery

1. MVP = US1 (Phase 3 complete) → demo: switch emulator device locale, see translations.
2. Add US2 (Phase 4) → demo: Android 13+ system Settings shows per-app picker.
3. Add US3 (Phase 5) → demo: temporarily delete a translation key, watch lint fail the build.
4. Polish (Phase 6) → final verification + doc updates.

### Single-Developer Strategy (recommended for solo dev)

Sequential, P1 → P2 → P3 → Polish. Each phase is small (US1: ~30 minutes for 7 translation files; US2: ~10 minutes; US3: ~5 minutes). Total estimate: 1–2 hours including emulator verification.

### Parallel-Team Strategy (if applicable)

- Developer A: US1 (translations)
- Developer B: US2 (locales_config + manifest)
- Developer C: US3 (lint config)
- All converge at Polish phase for cross-cutting verification.

---

## Notes

- `[P]` tasks = different files, no dependencies — safe to parallelize.
- `[Story]` label maps task to specific user story for traceability.
- Each user story is independently completable and testable per the disjoint-file design.
- No automated tests added (Spec 004 ships zero Kotlin code; lint = the test).
- Commit after each task or logical group (e.g., commit T001 alone, then commit T002–T008 as one batch, then T009–T010 verification).
- Stop at any checkpoint to validate the story independently.
- Avoid: cross-story file edits in a single commit (breaks story-independent rollback if needed).
