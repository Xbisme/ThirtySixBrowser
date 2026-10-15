# Feature Specification: Architecture Cleanup

**Feature Branch**: `025-architecture-cleanup`
**Created**: 2026-10-14
**Status**: Draft
**Input**: User description: "Restructure ThirtySixBrowser to follow the owner's Android template at `/Users/xbism3/Documents/Template/android` (feature-first Clean Architecture + MVVM, a governance set where every rule names the check that enforces it, short agent file, ADRs, architecture tests, a comment policy). Zero user-visible behaviour change."

## Context

Specs 001–024 are merged and the app is feature-complete. The code and the rules that govern it grew one spec at a time and are now hard to work with. Measured on `main` at `8eae890` (2026-10-14):

| Area | Today | Template |
|---|---|---|
| Code layout | Layer-first: `core` 21 · `data` 66 · `domain` 154 · `presentation` 202 · `di` 17 files (462 files, 31,885 lines). 82 use cases in one flat folder; 17 DI modules in one top-level folder; all DAOs and entities in shared folders | Feature-first: `core/` + `feature/<f>/{data, domain, presentation, di}` + root `navigation/` |
| Comments | 21.9 % of non-blank lines (4,860 comment vs 17,296 code). 117 files over 15 % comments, 160 files with a comment block over 5 lines. About 4,000 citations of spec, requirement and task IDs in 775 files | No IDs, no history, no banners; blocks ≤ 5 lines; ≤ 15 % per file |
| Rules | Constitution v1.6.0: 906 lines of prose, few rules backed by a check. `CLAUDE.md`: 804 lines, mostly change history. Four more planning documents in `.claude/claude-app/` repeat it | Constitution where every MUST names its check; agent file under 100 lines; reasons in ADRs; history in git |
| Checks | lint, detekt, ktlint, 16 KB script. No architecture test. Config files at the repository root | Architecture, placement, hardcode and comment tests; comment-content rules; Compose rules; config files under `config/` |

The template's own ADR on comments was written from this app's numbers.

## Clarifications

### Session 2026-10-14

The owner's answer to all three: follow the template fully ("đổi hết theo chuẩn"), with the behaviour-shaped parts (Q2, Q3) split into Spec 026 so this spec stays a structural move.

- Q: Where do contracts and models used by several features live? → A: In `core/` (`core/domain` for contracts and shared models, `core/data` for shared storage such as the database and settings store). Implementations stay in the feature that owns the data. No feature may import another, with no exceptions (FR-006).
- Q: What happens to use cases that only forward one call to a repository? → A: They will be removed, with callers using the repository contract, as the template's ADR 0001 says — **in the follow-up Spec 026**, not here (FR-010).
- Q: Do state holders move to the template's single action entry point with one-off events? → A: Yes, every screen — **in the follow-up Spec 026**, not here (FR-011). Owner, 2026-10-14: "tách sang spec sau cũng được, miễn là chuẩn cấu trúc". This spec moves code and does not change how it works; Spec 026 changes how state holders and use cases work, on top of the new structure.
- Q: How do the steps reach `main`? → A: On one branch, `025-architecture-cleanup`: one commit per step, each green on CI, merged to `main` once at the end (FR-027).
- Q: Does the new constitution keep the mandatory phase-order rule? → A: No. It is retired; an ADR records that Phases 1–6 (Specs 001–024) were completed in order and that spec order is now the owner's call (FR-024).
- Q: What happens to `CLAUDE.md`'s change history and the four `.claude/claude-app/` documents? → A: Moved verbatim to `docs/archive/` (the history as `changelog-001-024.md`), each opening with one line saying it is an archive, not a rule; nothing the agent loads points to it (FR-023).

## User Scenarios & Testing *(mandatory)*

The people served by this spec are the **owner and anyone (person or agent) who changes the code**. The **person using the browser** is served by one promise: nothing they see or keep changes.

### User Story 1 - Nothing changes for the person using the browser (Priority: P1)

Someone who has used ThirtySix for months installs the version built from this spec over their current one. Their open tabs, bookmarks and folders, history, downloads list, settings, language, fireproof sites, site permissions and protection exceptions are all still there, and every screen and action behaves exactly as before.

**Why this priority**: A cleanup that breaks or loses anything is a regression, not a cleanup. Every other story is only acceptable if this one holds.

**Independent Test**: Install the last release from `main` (8eae890), create data in every store, upgrade to the build from this spec, and compare data and behaviour; run the full unit and instrumented suites.

**Acceptance Scenarios**:

1. **Given** a device with data in every store from the pre-cleanup build, **When** the cleaned-up build is installed over it, **Then** the app starts without error and every record, setting and remembered decision is unchanged.
2. **Given** the cleaned-up build, **When** the existing unit and instrumented test suites run, **Then** every test that passed before passes (932 unit, 189 instrumented on the 16 KB AVD), allowing only for flakes already recorded as such.
3. **Given** the debug build, **When** the seeding broadcasts used for measurement are sent, **Then** they still seed and clear data as before.
4. **Given** the release build, **When** size and cold start are measured against the pre-cleanup release on the same device, **Then** both are within measurement noise.

---

### User Story 2 - Architecture rules break the build, not the review (Priority: P1)

A contributor puts a new screen's state holder in the wrong folder, or lets one feature reach into another, or writes a comment citing a task number. The build fails with a message saying what rule was broken and where the code belongs. Files that broke a rule before the rule existed are listed by name in an allowlist; the list is empty when this spec is done.

**Why this priority**: The current rules are prose that nobody checks, which is how the code drifted. Checks are added first so every later step of this spec is verified by them, and so the structure cannot drift again.

**Independent Test**: With the checks wired, introduce one deliberate violation of each rule on a scratch branch and confirm each fails the build with a clear message; remove it and confirm the build passes.

**Acceptance Scenarios**:

1. **Given** the checks are wired, **When** a domain file imports an Android or other platform type, **Then** the unit-test run fails naming the file.
2. **Given** the checks are wired, **When** a file in one feature imports another feature, or shared code imports a feature, **Then** the run fails.
3. **Given** the checks are wired, **When** a type is placed outside its home (state holder outside presentation, use case outside domain, DI module outside a DI folder, storage record outside the data layer), **Then** the run fails.
4. **Given** the checks are wired, **When** a composable shows hard-coded text or a color is written outside the theme, **Then** the run fails.
5. **Given** the checks are wired, **When** a comment cites a spec/requirement/task ID, tells change history, uses a banner, has a TODO without a ticket, runs over 5 lines, or a file is over 15 % comments, **Then** the static checks fail.
6. **Given** this spec is complete, **When** the allowlist of known violations is read, **Then** it is empty.

---

### User Story 3 - Everything about one feature is in one place (Priority: P1)

A contributor needs to change how bookmarks work. They open `feature/bookmarks/` and find its stored records, mapping, repository, use cases, screens, navigation and DI bindings there and nowhere else. They can tell from the folder name alone where a new file goes. Deleting the folder would remove the feature and nothing else.

**Why this priority**: This is the core of the owner's request and the main source of today's confusion.

**Independent Test**: For each feature, list every production file that mentions the feature's types and confirm all of them live under its folder, in `core/`, or in the app-level wiring.

**Acceptance Scenarios**:

1. **Given** the cleaned-up code, **When** the top-level source folder is listed, **Then** it contains only `core/`, `feature/`, `navigation/` and the application and activity entry points.
2. **Given** any feature folder, **When** it is listed, **Then** it follows the template's layout: `data/` (`datasource/local/` with `entity/`, `mapper/`, `repository/`), `domain/` (`model/`, `repository/`, `usecase/`), `presentation/` (one folder per screen plus `navigation/`), and `di/`, with empty layers omitted.
3. **Given** any screen folder, **When** it is listed, **Then** it holds that screen's composables, state holder, state type, event types and screen-only components.
4. **Given** the test sources, **When** they are listed, **Then** they mirror the production layout, and each feature has `fake/` and `fixture/` folders where it has fakes or sample data.

---

### User Story 4 - The rules fit on a page and say how they are checked (Priority: P2)

The owner or an agent starting a task reads a short agent file with the commands and the package map, a constitution of eight principles in which every MUST names the check behind it, and a product-constraints section with this app's real facts (privacy promise, permission table, locales, SDK levels, page-size rule, script-injection limits). Why a decision was made is in a numbered ADR, not in a status file. History lives in git and `specs/`.

**Why this priority**: Shorter, checked rules are what keep the cleanup from decaying, but they depend on the checks and the layout existing first.

**Independent Test**: Read the new constitution and agent file; for every MUST, run the named check; confirm every non-obvious decision recorded in today's `CLAUDE.md` "facts to keep" has an ADR or is covered by a check or test.

**Acceptance Scenarios**:

1. **Given** the new governance set, **When** the constitution is read, **Then** it has the template's eight principles plus this app's product constraints, every MUST names a check or says Review, and its version is 2.0.0 with one line in the history file.
2. **Given** the new governance set, **When** the agent file is read, **Then** it is under 100 lines and holds commands, the package map and the comment and test rules; `CLAUDE.md` only points to it.
3. **Given** a decision that only lived in today's `CLAUDE.md` (for example: the home page is a reserved address never loaded into the web view; each incognito session has its own web profile; the order of the Fire steps; the language change does not recreate the screen), **When** someone looks for why, **Then** they find it in an ADR.
4. **Given** the planning documents that duplicate `CLAUDE.md`, **When** this spec is done, **Then** each rule they held has exactly one live home, the originals sit in `docs/archive/` marked as archive, and nothing the agent loads points there.

---

### User Story 5 - Comments say only what the code cannot (Priority: P2)

A contributor opens any source file and sees code, with at most a short reason where something is not obvious: a platform quirk, an outside constraint, a non-evident algorithm. No spec numbers, no requirement IDs, no "previously…", no banners.

**Why this priority**: Comments are 22 % of the code today and the useful sentence is hard to find, but removing them changes no behaviour and can be done file by file once the content checks exist.

**Independent Test**: Run the comment checks over all production and test sources; sample 20 files by hand and confirm the reasons that remain are real and the important ones removed were moved to an ADR.

**Acceptance Scenarios**:

1. **Given** the cleaned-up sources, **When** the comment checks run, **Then** no file cites a spec, requirement or task ID, tells change history, uses a banner, or has an unticketed TODO.
2. **Given** the cleaned-up sources, **When** comment size is measured, **Then** no block is over 5 lines and no file of 30+ lines is over 15 % comments.
3. **Given** a comment that explained a platform workaround or a non-obvious reason, **When** it is shortened or removed, **Then** the reason survives as a 1–3 line comment or in an ADR.

### Edge Cases

- **Code genuinely shared by several features** (the active tab, settings, protection state read by the browser, the database itself): must have one home that every user can reach without one feature importing another. See FR-006.
- **Names the platform or other code depends on**: the application class, activity, debug receivers and test runner are named in manifests; storage files, database name and keys are on users' devices; saved navigation state may carry type names across process death. Moving code must not break any of them.
- **The database's list of record types** spans many features but is one database at schema version 5. Moving each record type into its feature must not change the schema or require a migration.
- **Shrinker keep rules** that name packages must follow the moved code, or the release build breaks while debug passes.
- **Several state holders on one screen** (the browser screen uses the browser, hub, page-text-size and protection holders): they move into the browser screen's folder or the feature that owns them, unchanged.
- **Instrumented tests that need a per-test settings store or the Hilt test runner** must keep working after their packages move.
- **A file that cannot meet a rule for a real reason** (e.g. a long platform-workaround explanation): the reason goes to an ADR and the code links to it by file name; no permanent allowlist entry.
- **Work on `main` during the cleanup**: any change landing on `main` meanwhile is merged into the branch and moved into the new layout in the same merge; each step commit stays green so the branch is always mergeable.

## Requirements *(mandatory)*

### Functional Requirements

**Behaviour and data preservation**

- **FR-001**: The application identifier, the database file name, schema version (5) and schema, the settings store file name and every key, asset paths, and the per-session web-profile naming MUST stay unchanged. No database migration is added.
- **FR-002**: Every screen, action, message, permission prompt and background behaviour MUST behave as before; the build from this spec MUST install over the pre-cleanup build with every stored record intact.
- **FR-003**: The manifest's declared components, intent filters and permissions MUST be unchanged in behaviour; the debug build's seeding broadcasts MUST keep their action names.
- **FR-004**: No new runtime dependency is added. Tools added for checks are test- or lint-only, and their versions are looked up at the time they are added.

**Structure**

- **FR-005**: Production code MUST be organised as `core/` (shared data, domain, design system and DI), `feature/<name>/` (per-feature data, domain, presentation and DI) and a root `navigation/`, following the template's layout and placement table.
- **FR-006**: A feature MUST NOT import another feature, and `core/` MUST NOT import any feature; only the app-level entry points and root navigation may reference several features. Contracts and models needed by several features MUST live in `core/` (`core/domain` for contracts and models, `core/data` for shared storage); their implementations stay in the owning feature and are bound by its DI module. No exception is granted to any feature, including browser.
- **FR-007**: The features, by package name, are: `browser` (including the home page and the incognito page, which an empty tab shows inside the browser screen), `tabs` (including incognito), `bookmarks`, `history`, `downloads`, `settings`, `onboarding`, `privacy` (protection data, Fire, fireproof sites, protection exceptions, page-script data), `sitepermission`, and `entrypoint` (default browser and incoming links). Research R1 records why home is part of `browser`.
- **FR-008**: Each screen MUST have its own folder holding its composable(s), state holder, immutable state type, its existing event types and screen-only components.
- **FR-009**: Repository implementations keep the existing `*RepositoryImpl` naming as the project's one style, recorded in an ADR.
- **FR-010**: Every use case moves to the feature that owns it, or to `core/domain` if several features use it, unchanged. The plan lists each of the 82 use cases with its new home and marks the ones that only forward one call; that list is the input for Spec 026, which removes them.
- **FR-011**: State holders keep their current public shape in this spec. The constitution states the template's action/event rule; Spec 026 brings every screen to it. Until then the existing screens are listed by file in this spec's plan (Complexity Tracking) as the work Spec 026 owns; the rule is Review, not a check, so no allowlist entry is needed.

**Checks**

- **FR-012**: Architecture, placement, hard-coded-value and comment-size rules MUST be automated tests that run with the unit tests, failing with a message that names the file and the rule.
- **FR-013**: Comment content rules (IDs, history, banners, unticketed TODOs), the injected-dispatcher rule and Compose rules MUST run in the static checks.
- **FR-014**: Check configuration files MUST live under `config/`.
- **FR-015**: A list of known violations MAY be used during the migration; every entry names its file, and the list MUST be empty when this spec is complete. Disabling or weakening a check is not allowed.

**Comments**

- **FR-016**: Production and test sources MUST contain no spec, requirement or task IDs, no citation of planning documents or the constitution, no change history, no banners, no commented-out code and no TODO without a ticket.
- **FR-017**: No comment block MAY exceed 5 lines, and no production file of 30 or more lines MAY exceed 15 % comment lines.
- **FR-018**: A reason that is still true and not evident from code MUST survive, either as a 1–3 line comment or in an ADR linked by file name.

**Governance**

- **FR-019**: The constitution MUST be replaced by the template's form at version 2.0.0: eight principles, each MUST naming its check or saying Review, plus a Product Constraints section carrying this app's privacy promise, permission table, 8 locales, SDK 24/36, 16 KB alignment, and the limits on bundled one-way page scripts and on exposing app objects to pages.
- **FR-020**: The agent file MUST be under 100 lines and hold commands, the package map, and the comment and test rules; `CLAUDE.md` MUST only point to it.
- **FR-021**: ADRs MUST exist for the template's four decisions (adapted to this app) and for each non-obvious decision that today lives only in `CLAUDE.md` or a comment and is not already enforced by a check or test.
- **FR-022**: A one-line-per-amendment constitution history MUST exist; earlier versions 1.0.0–1.6.0 are summarised there.
- **FR-023**: Every rule or still-true decision in today's `CLAUDE.md` and `.claude/claude-app/` documents MUST end up in exactly one live home (constitution, agent file or ADR). The originals — `CLAUDE.md`'s change history as `docs/archive/changelog-001-024.md`, and the four `.claude/claude-app/` files — MUST be moved verbatim to `docs/archive/`, each starting with one line marking it as an archive and not a rule. Neither the agent file, `CLAUDE.md` nor the constitution may reference `docs/archive/`.
- **FR-024**: The old constitution's mandatory phase-order rule MUST NOT appear in the new one. An ADR MUST record that Phases 1–6 (Specs 001–024) were completed in order under that rule, and that from Spec 025 on the owner decides spec order.

**Tests**

- **FR-025**: Test sources MUST mirror the production layout; shared fakes and sample data move into each feature's `fake/` and `fixture/` folders.
- **FR-026**: No existing test is deleted to make the cleanup pass. A test MAY be moved, renamed or merged when its subject moves, with the same assertions.

**Delivery**

- **FR-027**: The work MUST be done on one branch (`025-architecture-cleanup`) as a series of commits — checks first, then one feature or shared area per commit, then comments and governance. Each commit MUST build and pass every gate on CI, so the branch could be merged after any of them. The branch is merged to `main` once, at the end; `main` is merged into it whenever `main` moves.

### Key Entities

- **State holder**: a screen's `ViewModel`; the two words mean the same thing in this spec and its tasks.
- **Feature**: a user-facing capability with its own data, domain, presentation and DI; may depend on `core/`, never on another feature.
- **Core**: shared infrastructure and contracts used by several features: database, settings store, dispatchers, design system, shared models.
- **Check**: an automated rule run by the build; named by the constitution for each MUST.
- **Known-violations list**: a temporary, named list of files exempt from a check during the migration; empty at the end.
- **ADR**: a numbered record of one decision: context, decision, consequences.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % of user data present before the upgrade is present after it, across every store, on a device upgraded from the pre-cleanup build.
- **SC-002**: Every test that passed before the cleanup passes after it (932 unit, 189 instrumented), with no test deleted and every behaviour it checked still checked. The only edits allowed in a test are its package, imports, and calls adapted to a moved or re-typed signature (e.g. a cache taking encoded bytes instead of a bitmap).
- **SC-003**: Release package size within ±2 % and cold start within ±5 % of the pre-cleanup release, measured on the same device by interleaved runs.
- **SC-004**: 0 files in the known-violations list, and each rule shown to fail on a deliberate violation.
- **SC-005**: Comments are at most 12 % of non-blank production lines (from 21.9 %), with 0 ID citations, 0 blocks over 5 lines and 0 files over 15 %.
- **SC-006**: The constitution is under 250 lines (from 906) and the agent file under 100 lines (from 804 in `CLAUDE.md`), with every MUST naming a check or Review.
- **SC-007**: For every feature, 100 % of its production files sit under its own folder; a contributor can name the folder for a new file of any type from the placement table alone.
- **SC-008**: Every step commit on the branch passes all quality gates on CI (build, unit, instrumented, lint, detekt, ktlint, 16 KB), and the final merge to `main` is green.

## Assumptions

- **Spec 026 (follows this one)**: remove forwarding use cases and move every screen to `onAction(<Screen>Action)` with `<Screen>Event` and a route/screen split. It starts from this spec's structure and the use-case list from FR-010.

- The template at `/Users/xbism3/Documents/Template/android` (commit `e8f74f5`, 2026-10) is the reference; where it is silent, the official Android architecture guide decides.
- The app stays a single Gradle module; splitting features into modules is out of scope.
- The source root stays `app/src/main/kotlin/` and the package root stays `com.raumanian.thirtysix.browser`; only the packages below it change.
- `specs/001–024` stay as the historical record and are not edited, except where FR-023 needs a pointer.
- Dependency upgrades (AGP, Kotlin, Compose, Hilt) are out of scope unless a check tool requires one; any such bump is its own step.
- Comment **content** rules (IDs, history, banners, TODOs) apply to production, unit-test and instrumented-test sources, and the static checks cover all three. Comment **size** rules (5-line blocks, 15 % density) and placement rules apply to production sources only, as in the template.
- No design, string or translation change; no change to CI except what the new checks need.
- Device gates use the 16 KB AVD and the API 29 emulator; the owner's Galaxy A55 is used only with the owner present.
