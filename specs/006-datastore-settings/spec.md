# Feature Specification: DataStore Settings — Persistent User Preferences Foundation

**Feature Branch**: `006-datastore-settings`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "datastore-settings — Persist user settings (theme mode, language code, search engine, onboarding completion flag) qua androidx.datastore:datastore-preferences với full Clean Architecture data layer (DataStore wrapper + Repository interface/impl + UseCases) để ViewModels có thể đọc/ghi settings tuân thủ Constitution §IV."

## Clarifications

### Session 2026-05-01

- Q: How do `SettingsRepository` setters surface disk-write failures (disk full, permission denied, file corruption during write) to the caller? → A: Each setter returns `Result<Unit>` from the existing `core/result/` wrapper introduced by Spec 002 (`Success<Unit>` on persisted write; `Error(AppError)` on failure with the underlying exception mapped to `AppError.Database` via the existing `AppError.from()` mapper). Callers branch on `Result` explicitly. Consistent with Spec 002 architecture; no new error-handling mechanism is introduced.
- Q: How does the domain model represent the "no language override — follow device locale" state for the `language_code` setting? → A: A new sealed interface `LanguageOverride` in `domain/model/` with two variants — `LanguageOverride.FollowSystem` (object, the default) and `LanguageOverride.Explicit(val bcp47: String)` (data class, holds an in-app override code). `UserSettings.languageOverride: LanguageOverride` is non-nullable. The DataStore Preferences storage layer maps a missing or null on-disk value to `FollowSystem` and a non-null string to `Explicit(bcp47)` inside the SettingsMapper. This eliminates `String?` ambiguity at the domain boundary and gives consumers an exhaustive `when` branching surface.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Theme Choice Survives App Restart (Priority: P1)

A user who has expressed a preference for the app's visual theme (light, dark, or follow-system) reasonably expects that preference to remain in effect every subsequent time they open the app — including after the operating system has killed the process for memory pressure or after the device reboots. Today, the app forgets the user's theme choice on every cold start because Spec 003 stored it only in memory.

**Why this priority**: Theme is the most visible, most frequently noticed user preference. Forgetting it on every launch is the single most visible "this app feels broken" failure for a tools/productivity browser. Without P1 working, no other settings story has value.

**Independent Test**: A consumer of the settings layer (currently MainActivity) sets the theme to a non-default value, the app process is force-killed and re-launched cold, and the app renders the previously-chosen theme on first frame — without flashing the default theme first. This story is fully verifiable through the existing Spec 003 theme rendering plus the new persistence layer; no UI for changing the theme is required (Spec 016 will add the UI).

**Acceptance Scenarios**:

1. **Given** the user has previously chosen "Dark" as their theme preference, **When** the app process is killed and the user re-opens the app, **Then** the app applies the Dark theme on the very first composition without any visible flash of Light or System theme.
2. **Given** the app is running with theme "Dark" applied, **When** another part of the app updates the theme preference to "Light", **Then** the running UI re-composes to Light theme without requiring an app restart.
3. **Given** the user has never opened the app before (fresh install), **When** the app starts for the first time, **Then** the app applies the default theme ("Follow System") and operates normally without errors.

---

### User Story 2 — First-Launch Default Settings Are Available Without Errors (Priority: P1)

The very first time the app runs after install, no settings have been written to disk yet. Every settings key must yield a sensible, documented default value so that consumers (theme renderer, future onboarding flow, future settings screen) can read settings synchronously-enough to render the first frame without crashing, blocking, or showing a blank UI.

**Why this priority**: Without first-launch defaults working, the app crashes or hangs on every fresh install — blocking 100% of new users. This is structurally impossible to defer.

**Independent Test**: Wipe all app data, start the app, and immediately read every supported settings key. Verify each returns its documented default and that the read completes within a budget that lets the first frame render without a perceptible delay or fallback UI.

**Acceptance Scenarios**:

1. **Given** the app has just been installed for the first time and no settings have ever been written, **When** the app reads the theme preference, **Then** the value returned is "Follow System".
2. **Given** the app has just been installed for the first time, **When** the app reads the language preference, **Then** the value returned is `LanguageOverride.FollowSystem` (no explicit override; device locale honored as in Spec 004).
3. **Given** the app has just been installed for the first time, **When** the app reads the search engine preference, **Then** the value returned is "Google".
4. **Given** the app has just been installed for the first time, **When** the app reads the onboarding-completed flag, **Then** the value returned is "false" (onboarding has not yet been shown).
5. **Given** the app has just been installed for the first time, **When** the first frame is rendered, **Then** the time from process start to settings being available for the first frame is short enough that no fallback "loading settings" UI is required.

---

### User Story 3 — Language Preference Persists for Future In-App Picker (Priority: P2)

When a future Settings Screen (Spec 016) lets the user choose an in-app language override (overriding the device locale), that choice must survive the same process-death + restart cycle as the theme preference. This story establishes the persistence contract now so that Spec 016 only needs to add UI, not invent a new persistence story under deadline pressure.

**Why this priority**: P2 because no UI exists in this spec to *exercise* the language picker — Spec 016 owns the UI. But the underlying persistence guarantee must exist before Spec 016 starts, or Spec 016 will block on this work.

**Independent Test**: A test caller writes a non-default language code, the test process is killed and re-started, and the same caller reads the value back as written. No UI involvement.

**Acceptance Scenarios**:

1. **Given** a caller writes `LanguageOverride.Explicit("vi")`, **When** the app process is killed and a fresh read is performed, **Then** the read returns `LanguageOverride.Explicit("vi")`.
2. **Given** a caller writes `LanguageOverride.Explicit("vi")` and then writes `LanguageOverride.FollowSystem` (clearing the override), **When** a fresh read is performed, **Then** the read returns `LanguageOverride.FollowSystem`.

---

### User Story 4 — Search Engine Preference Persists (Priority: P2)

The user's chosen search engine — currently only Google in v1.0, but the system must store the choice as an extensible identifier so that adding a second engine in a future release does not require a data migration — must persist across process deaths. This story establishes the storage contract, not the picker UI (Spec 016) and not multi-engine support (post-v1.0).

**Why this priority**: P2 because v1.0 only ships one engine, so no user-visible bug exists if the value is forgotten. But the contract must exist so Spec 010 can read the choice and Spec 016 can later add a picker.

**Independent Test**: A test caller writes "Google" (the only valid v1 value), kill+restart, read back. Verify that future-incompatible writes are rejected at the API boundary (a caller cannot write an arbitrary string).

**Acceptance Scenarios**:

1. **Given** a caller writes the search engine choice "Google", **When** the app process is killed and a fresh read is performed, **Then** the read returns "Google".
2. **Given** the stored search engine value somehow becomes corrupted or unrecognized (e.g., a future version was downgraded), **When** a fresh read is performed, **Then** the read returns the documented default ("Google") rather than crashing.

---

### User Story 5 — Onboarding Completion Flag Persists (Priority: P2)

When the future Onboarding flow (Spec 018) finishes, the app must remember that fact so onboarding never re-appears on subsequent launches. The persistence contract must exist before Spec 017 (splash) and Spec 018 (onboarding) ship.

**Why this priority**: P2 because no UI in this spec consumes the flag yet. But Spec 017/018 cannot start until the contract exists.

**Independent Test**: Default-read returns false on fresh install. Set to true, kill+restart, read returns true. No UI involvement.

**Acceptance Scenarios**:

1. **Given** the app has just been installed, **When** the onboarding-completed flag is read, **Then** the value returned is "false".
2. **Given** a caller writes "true" to the onboarding-completed flag, **When** the app process is killed and a fresh read is performed, **Then** the read returns "true".

---

### User Story 6 — Concurrent Writes Do Not Lose Data (Priority: P3)

In normal app use, settings writes happen one at a time in response to user actions. But in edge cases — for example, an in-flight write from the Settings Screen overlapping with an automatic write from a deep-link handler — two writes to two different settings keys may happen at nearly the same instant. Both writes must persist; neither may be silently dropped.

**Why this priority**: P3 because the realistic frequency of true concurrent writes in this app is very low (no background services, no multi-process). But establishing and testing the guarantee now prevents a category of subtle data-loss bugs from appearing later when more settings are added.

**Independent Test**: Kick off two writes to two different settings keys from two different concurrent tasks. After both complete, both values must be present on the next read.

**Acceptance Scenarios**:

1. **Given** two concurrent tasks each write a different settings key at the same instant, **When** both writes have completed, **Then** a fresh read returns both written values intact.
2. **Given** two concurrent tasks each write the same settings key at the same instant with different values, **When** both writes have completed, **Then** a fresh read returns one of the two values (last-writer-wins is acceptable; data corruption or a missing value is not).

---

### User Story 7 — Schema Evolution Stays Backward-Compatible (Priority: P3)

Once a settings key has shipped to a user, that key's name and stored value type must never be silently re-purposed in a future release. If the project later needs to change semantics (e.g., split one key into two, or change a string into an integer), the new semantics must live behind a new key name with explicit migration logic — never an in-place change. The codebase must make this discipline visible to future maintainers.

**Why this priority**: P3 because it is a maintainability discipline rather than a user-visible feature. But getting it documented and demonstrated now is far cheaper than discovering after a breaking change has already shipped.

**Independent Test**: A documented "schema rules" entry exists in the spec / plan and is referenced from the data layer source. A negative-path test demonstrates what happens if an old key is read after being renamed (the test asserts the documented "old value gone, new key starts at default" behavior, proving the rule must be followed).

**Acceptance Scenarios**:

1. **Given** the project's settings schema documentation, **When** a maintainer wants to change a key's meaning, **Then** the documentation directs them to add a new key + migration code rather than rename in place.
2. **Given** a simulated rename of a settings key, **When** the app reads the new key after the rename, **Then** the read returns the documented default for the new key and the old key's value is not silently inherited (proving the rule must be followed).

---

### Edge Cases

- **Storage corruption on disk**: If the underlying settings file is corrupted (truncated, partially written during a crash), the app must recover by treating each individual key's value as missing and falling back to that key's documented default — never crash, never block startup.
- **Storage write fails (disk full, permission denied)**: Each setter on the settings repository returns a `Result<Unit>` (the project's existing `core/result/` wrapper from Spec 002). On disk-write failure the return value is `Result.Error(AppError.Database(...))`; the underlying exception is mapped through the existing `AppError.from()` mapper. The in-memory observable state remains consistent: a failed write does NOT update the observable snapshot, so observers continue to see the previous (still-on-disk) value. Callers (UI ViewModels, future Settings Screen) MUST handle both `Result` branches explicitly.
- **First frame races settings cold-load**: The very first read of a settings key on cold start may need to wait for the disk file to be opened and parsed. The system must guarantee that the first observable emission contains documented default values for any keys not yet in the file, so the first frame can render without blocking.
- **Backup-restore on a new device**: When the user restores the app on a new device from a cloud backup, the previously-persisted settings must be available on first launch on the new device (subject to the backup being included — see FR-013). The user should not have to re-set their preferences.
- **Locale resolved from "follow system" value**: When the language preference is the "follow device locale" value (the default), the app must continue to behave exactly as it does today (Spec 004) — no in-app override is applied. This must remain true even if a future caller accidentally writes the wrong "no-override" sentinel.
- **Theme value not recognized by current build**: If a future build wrote a theme value that the current build doesn't recognize (e.g., user downgraded the app), the app must fall back to the documented default ("Follow System") rather than crash.

## Requirements *(mandatory)*

### Functional Requirements

#### Persistence and durability

- **FR-001**: The system MUST persist user settings to local device storage in a form that survives app process termination, device reboot, and OS-initiated process death due to memory pressure.
- **FR-002**: The system MUST guarantee that any settings value successfully written by a caller is readable by a subsequent caller after the app process has been killed and re-started.
- **FR-003**: The system MUST NOT transmit settings values to any remote server or to any party outside the user's device.

#### Settings keys (v1.0 scope)

- **FR-004**: The system MUST support a persistent "theme mode" setting whose value is one of: "Light", "Dark", or "Follow System". Default value: "Follow System".
- **FR-005**: The system MUST support a persistent "language override" setting represented in the domain layer as a sealed type `LanguageOverride` with exactly two variants: `FollowSystem` (the default, indicating no in-app override is active and the device locale is honored as in Spec 004) and `Explicit(bcp47: String)` (an in-app override carrying a BCP-47 language tag matching one of Spec 004's eight supported locales). The on-disk storage MAY use a nullable string under the hood, but consumers of the repository MUST only see the sealed `LanguageOverride` type — never a raw nullable string. Default value: `LanguageOverride.FollowSystem`.
- **FR-006**: The system MUST support a persistent "search engine" setting whose value is one of an enumerable list of supported search engines. The v1.0 list contains exactly one entry: "Google". Default value: "Google".
- **FR-007**: The system MUST support a persistent "onboarding completed" boolean flag. Default value: "false".

#### Default-handling and read semantics

- **FR-008**: When any individual settings key has never been written, the system MUST return that key's documented default value rather than null, an error, or undefined behavior.
- **FR-009**: When the system reads a stored value that does not match the set of recognized values for an enum-typed key (theme mode or search engine), the system MUST fall back to that key's documented default and MUST NOT crash, throw, or propagate the unrecognized value.
- **FR-010**: The system MUST expose settings to consumers as a single coherent "current settings" snapshot, observable as a stream that emits the full snapshot every time any individual key changes.

#### Write semantics

- **FR-011**: The system MUST expose individual setters for each of the four settings keys (so a caller can update one key without having to re-supply the other three). Each setter MUST return `Result<Unit>` (the existing `core/result/` wrapper introduced by Spec 002) — `Result.Success(Unit)` if the value was persisted to disk, or `Result.Error(AppError)` if the disk write failed. Disk-related exceptions MUST be mapped to `AppError.Database` via the existing `AppError.from()` mapper. Setters MUST NOT throw to the caller for routine disk failures.
- **FR-012**: When two callers write different keys concurrently, both writes MUST persist; neither may be silently dropped. When two callers write the same key concurrently, exactly one of the written values MUST persist (last-writer-wins is acceptable).

#### Backup, restore, and privacy posture

- **FR-013**: The system MUST INCLUDE the settings storage file in OS-managed Auto Backup and Device-to-Device transfer — explicitly differing from the database-storage policy established in Spec 005, which excludes the database from backup. Rationale: settings (theme, language, search engine, onboarding flag) contain no private browsing data and a restore-to-new-device experience that preserves these preferences is a clear UX win.
- **FR-014**: The settings storage policy difference between settings (included in backup) and the database (excluded in backup) MUST be documented in the same place that the per-store backup posture is configured, so that future maintainers can see the contrast at a glance.

#### Architecture and access discipline

- **FR-015**: View-layer code (Activities, ViewModels, Composables) MUST NOT read or write the settings storage file directly. All access MUST go through a documented Repository contract that lives in the domain layer. This is required by Constitution §IV (the "Repository pattern" rule).
- **FR-016**: The settings storage file's name and location MUST be defined in exactly one place in the codebase and referenced by symbol from anywhere it is used, per Constitution §III (No-Hardcode Rule).
- **FR-017**: The keys used inside the settings storage MUST be defined in exactly one place in the codebase and referenced by symbol from anywhere they are used, per Constitution §III.
- **FR-018**: The default values for each settings key MUST be defined in exactly one place in the codebase, alongside other application defaults, per Constitution §III.

#### Schema evolution

- **FR-019**: Once a settings key name has shipped to users, the same key name MUST NOT be re-purposed for a different meaning or different value type in any future release. To change semantics, a new key name MUST be introduced and a migration step MUST move (or default) the value forward. This rule MUST be documented in plan-level documentation and the documentation MUST be discoverable from the settings storage source code.

#### Integration with prior specs

- **FR-020**: The in-memory `MutableState<ThemeMode>` introduced by Spec 003 in the app's main entry point MUST be replaced by a value sourced from the settings persistence layer. After this spec ships, the theme mode visible to the user on app launch MUST be the value most recently written by any caller, not the hard-coded "Follow System" default that Spec 003 used.
- **FR-021**: The "theme mode" type currently defined in the presentation layer (Spec 003) MUST be relocated to the domain layer, because theme mode is a domain concept (a user preference) and the presentation layer's only role is to render it. All existing consumers in the presentation layer MUST continue to compile and behave identically after the move.

### Key Entities *(include if feature involves data)*

- **User Settings Snapshot**: An immutable bundle of the four user preferences (theme mode, language code, search engine, onboarding completion flag) that represents the app's current state at a point in time. Consumers observe this entity rather than the four keys individually.
- **Theme Mode**: A user preference describing how the app should select its visual color scheme — one of "Light", "Dark", or "Follow System". A domain concept (not a presentation concept), even though the presentation layer is its primary consumer.
- **Search Engine**: A user preference identifying which search service the address bar should use to resolve queries. The v1.0 enumeration contains a single entry ("Google"); the type is shaped as an enumeration to permit future additions without changing the persistence shape.
- **Language Override**: A user preference identifying whether the UI language is being overridden in-app and, if so, which BCP-47 language tag is in effect. Modeled as a sealed type with exactly two variants: `FollowSystem` (no override; device locale is used as in Spec 004) and `Explicit(bcp47)` (in-app override carrying a specific tag). The "no override" state is a first-class value, not the absence of a value — consumers branch on the sealed variants exhaustively rather than null-checking.
- **Onboarding Completed Flag**: A user preference recording whether the user has ever finished the onboarding flow on this install. Consumed by future splash and onboarding specs to decide whether to show onboarding.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a user expresses a non-default theme preference and the app process is killed and re-launched cold, the app renders the previously-chosen theme on the first composition with no visible flash of any other theme.
- **SC-002**: On a fresh install with no prior settings data, the time from app process start to "settings available with documented defaults to the first composer" is short enough that no fallback "loading settings" placeholder UI is needed.
- **SC-003**: When any caller writes a settings key, every consumer subscribed to the settings stream observes the new value within one observation cycle of the write completing — no manual refresh needed.
- **SC-004**: Two concurrent writes from two coroutines, targeting two different keys, both persist to disk and are visible on the next read 100% of the time across at least 100 test repetitions.
- **SC-005**: The settings storage file is verifiably included in OS-managed Auto Backup and Device-to-Device transfer (and the database from Spec 005 remains excluded) — both confirmed by inspecting the project's backup-rules configuration.
- **SC-006**: The continuous-integration 16 KB-page-size verification gate continues to pass with zero new native libraries introduced by this spec.
- **SC-007**: The project's static-analysis gates (Android Lint, Detekt, ktlint) all pass with zero new warnings or violations and with the existing Detekt baseline file unchanged.
- **SC-008**: The release APK size delta introduced by this spec is below 200 KB.
- **SC-009**: The Constitution check (the 11-row gate run at plan time) passes both before and after the design phase, with zero unresolved violations.
- **SC-010**: After this spec ships, no view-layer code in the project reads or writes the settings file directly. A repository-grep for direct settings-file access from the presentation layer returns zero results.
- **SC-011**: After this spec ships, the in-memory theme MutableState in the app's main entry point is gone, replaced by a value sourced from the settings persistence layer. A repository-grep for the prior MutableState pattern in the main entry point returns zero results.

## Assumptions

- **Spec 005 is fully merged into `main`** — the database layer's backup-exclusion policy and Hilt module placement conventions exist and can be referenced as the contrasting example for settings backup (which is included rather than excluded).
- **The eight locales established by Spec 004 (EN, VI, DE, RU, KO, JA, ZH, FR) are the only BCP-47 tags a caller will pass to `LanguageOverride.Explicit`** in v1.0. Validation of arbitrary BCP-47 inputs is out of scope; the in-app picker (Spec 016) will constrain the input set to these eight. To clear an in-app override the caller passes `LanguageOverride.FollowSystem` (not an empty `Explicit("")`).
- **There is exactly one search engine** ("Google") in v1.0. The setting is shaped as an enumeration to make future additions cheap, but multi-engine support is explicitly Spec 010 / Spec 016 work.
- **No view-layer code other than `MainActivity` currently consumes theme mode**. The cleanup of Spec 003's in-memory MutableState is therefore localized to a single file.
- **Settings UI does not exist in this spec**. The Settings Screen, theme toggle, language picker, and search engine picker are all Spec 016. This spec ships only the persistence layer and the cleanup of Spec 003's in-memory placeholder.
- **Onboarding screen does not exist in this spec**. Spec 017 (splash) and Spec 018 (onboarding) own the consumers of the onboarding-completed flag.
- **Process death survival can be tested without a real Android emulator** for the persistence guarantee in isolation — by tearing down and re-creating the storage object inside a single test process. Production-grade kill -9 + restart verification on a real device is a manual gate, deferred to user verification (consistent with Spec 004's manual gates pattern).
- **DataStore Preferences (the key-value, untyped variant of Jetpack DataStore) is the chosen mechanism**, per the project's Tech Stack table in CLAUDE.md and project-context.md. Proto DataStore is out of scope.
- **The storage file name `thirtysix_settings` is fixed** by the project's Architecture Decisions table in CLAUDE.md.
- **Encryption-at-rest is not required** for v1.0 because the four settings keys do not contain credentials, tokens, or PII. Encryption may be revisited in a later spec if a key with sensitive value is ever introduced.

## Out of Scope

- Settings UI (Settings Screen) — Spec 016
- Theme toggle UI — Spec 016
- Language picker UI — Spec 016
- Search engine picker UI — Spec 016
- "Default home URL" preference — deferred to Spec 016 because no UX driver exists yet
- "Tracker blocker enabled" preference — deferred to Spec 019 (optional)
- Onboarding screen rendering and flow — Spec 018
- Splash screen — Spec 017
- Reading settings from any ViewModel other than the main-activity boundary (no consumer ViewModels exist yet)
- Multi-process settings access (the app is single-process)
- Encryption-at-rest of settings
- Cloud sync of settings across devices via any non-OS channel (the OS Auto Backup is the only sync mechanism)
- Proto DataStore (typed schema variant) — Preferences DataStore is the chosen variant
