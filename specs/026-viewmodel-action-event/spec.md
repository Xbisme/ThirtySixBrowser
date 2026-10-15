# Feature Specification: ViewModel Action/Event

**Feature Branch**: `026-viewmodel-action-event`
**Created**: 2026-10-15
**Status**: Draft
**Input**: User description: "Spec 026 viewmodel-action-event: remove the use cases whose body only forwards one repository call (list in specs/025-architecture-cleanup/tasks.md "Spec 026 — forwarding use cases"), move every screen to a single onAction(XAction) entry point with one-off XEvent effects and a Route/Screen split (list in "Spec 026 — screens without onAction"), unify test dispatcher setup with a MainDispatcherRule, and empty config/ktlint/baseline.xml of the 23 vm-forwarding-check / vm-injection-check findings. No user-visible behaviour change; existing unit and instrumented suites are the safety net."

## Context

Spec 025 (merged as PR #43, `0372d0f`, 2026-10-15) moved the code into the feature-first layout but deliberately left how state holders and use cases work unchanged. It handed this spec three lists, measured on `0372d0f`:

| Area | Today | Constitution 2.0.0 |
|---|---|---|
| Use cases | 82 use cases; 33 of them only forward one call to a repository or controller (`specs/025-architecture-cleanup/tasks.md`, "Spec 026 — forwarding use cases") | III: a state holder may call the repository contract directly when the use case would only forward the call |
| Screen input | 18 state holders; none has a single action entry point. They expose 1–33 public functions each (Browser 33, Bookmarks 27, Settings 21, Tabs 14, History 12) | III: user input reaches the state holder as `onAction(XAction)` |
| One-off effects | Mixed: 3 `Channel`s, 10 `MutableSharedFlow`s, some typed (`SettingsEvent`, `DownloadsEvent`), some `Unit`/`Int`/`String`, some error-only (`BookmarksErrorEvent`, `HistoryErrorEvent`, `TabsErrorEvent`) | III: one-off effects are emitted as `XEvent` |
| Route/Screen | 16 composables obtain a state holder; 23 compose-rules findings (18 `vm-forwarding-check`, 5 `vm-injection-check`) kept in `config/ktlint/baseline.xml` by owner approval | IV: a screen collects its `UiState` once, at the route |
| Test main dispatcher | 22 test files call `Dispatchers.setMain` / `resetMain` by hand; no shared rule | VIII: state holders are tested with Turbine and a test main dispatcher |

The constitution marks the action/event shape as **Review** with the note "screens written before 2.0.0 move to it in Spec 026". This spec is that move.

## Clarifications

### Session 2026-10-15

Scope comes from the owner's Spec 025 decisions (Q2/Q3 there: "tách sang spec sau cũng được, miễn là chuẩn cấu trúc"). The owner confirmed the last three answers in `/speckit-clarify` on 2026-10-15; the first is a default from the constitution.

- Q: Which use cases are removed? → A: Each of the 33 listed is re-checked on the current code; one whose body still only forwards one call is removed and its callers use the contract directly. One that has gained logic, or whose removal would make a state holder see a type it must not (FR-005), stays and is recorded with its reason.
- Q: Should the "only `onAction`, only the route obtains a state holder" rule be checked by the build, with the constitution bumped to 2.1.0? → A: Yes, both: a Konsist test for `onAction` and compose-rules without a baseline for routes; principle III moves from Review to Enforced at 2.1.0 (MINOR) (FR-016–FR-018).
- Q: Is `BrowserViewModel` (33 public functions) kept as one state holder with one `BrowserAction`, or split in this spec? → A: Kept as one holder with one `BrowserAction` (grouped by nested sealed interfaces if that reads better); splitting it is left to a later spec.
- Q: When one-off effects are folded into one `XEvent` type, what happens to an effect emitted while the screen is not visible? → A: Keep each effect's current behaviour: buffered effects stay buffered, dropped effects stay dropped; the plan lists every effect. Fixing a dropped effect is a separate change with its own test (FR-009).

## User Scenarios & Testing *(mandatory)*

The people served are the **owner and anyone (person or agent) who changes the code**. The **person using the browser** is served by one promise: nothing they see or keep changes.

### User Story 1 - Nothing changes for the person using the browser (Priority: P1)

Someone who uses ThirtySix every day installs the build from this spec over the current one. Every screen responds to every tap, swipe, long-press and back gesture exactly as before; every snackbar, dialog, navigation and confirmation appears once, at the same moment, with the same text. Their data is untouched.

**Why this priority**: The change rewires how every screen talks to its state holder. A message that appears twice, an action that is lost on rotation, or a navigation that fires again after returning to a screen is a regression the person will see.

**Independent Test**: Run the unit and instrumented suites on the build from this spec and compare with `0372d0f`; walk each screen on an emulator through its main actions, including a rotation and a process restore mid-flow.

**Acceptance Scenarios**:

1. **Given** the build from this spec, **When** the existing unit and instrumented suites run, **Then** every test that passed on `0372d0f` passes, allowing only for flakes already recorded as such.
2. **Given** any screen, **When** the person performs an action that shows a one-off message or navigates, **Then** it happens exactly once, and it does not happen again after rotation, returning from another screen, or the app coming back from the background.
3. **Given** a one-off effect emitted while the screen is not visible (for example a download finishing while the person is on another screen), **When** the screen becomes visible, **Then** the effect is shown exactly as it is today: either delivered once or dropped, never duplicated.
4. **Given** a device with data from the `0372d0f` build, **When** the new build is installed over it, **Then** it starts without error and every record and setting is unchanged; no migration is added.

---

### User Story 2 - Every screen talks to its state holder the same way (Priority: P1)

A contributor opens any screen. Its route obtains the state holder, collects its state once and listens to its one-off effects; the screen composable receives only state and one `onAction` callback. The state holder has one public input, `onAction`, taking a closed set of actions that lists everything the screen can ask for, and one effect stream of a closed set of events. Reading the action type tells the contributor what the screen can do; the screen composable can be previewed and tested with plain state and a lambda.

**Why this priority**: This is the main body of the work and what the constitution already requires; 18 screens do it 18 different ways today.

**Independent Test**: For each state holder, list its public members and confirm the only input is `onAction`; for each screen, confirm only its route obtains a state holder and the screen composable takes state plus `onAction`.

**Acceptance Scenarios**:

1. **Given** any state holder, **When** its public members are listed, **Then** it exposes state as read-only flows, at most one one-off effect stream, and `onAction(XAction)` as the only function a screen calls.
2. **Given** any screen, **When** its composables are read, **Then** exactly one route composable obtains the state holder(s) and collects state and events; the screen composable and its children receive plain values and callbacks, never a state holder.
3. **Given** a screen with several state holders (the browser screen uses browser, hub, page-text-size and protection holders), **When** its route is read, **Then** each holder is obtained once at the route and each is driven through its own `onAction`.
4. **Given** the Compose rules run with the ktlint baseline removed, **When** the static checks run, **Then** no `vm-forwarding-check` or `vm-injection-check` finding is reported.

---

### User Story 3 - A use case exists only when it adds something (Priority: P2)

A contributor needs to read the list of open tabs. They inject the tab repository contract into their state holder instead of looking for an `ObserveTabsUseCase` that only calls `repository.observeTabs()`. Use cases that remain combine calls, hold a rule, or protect a boundary, so finding one means there is logic in it.

**Why this priority**: The 33 forwarding classes double the files a contributor reads per call and each carries its own test of nothing. Removing them is mechanical but touches most state holders, so it lands alongside, not before, the action move.

**Independent Test**: For each use case left in the code, confirm its body does more than forward one call or is recorded as kept with a reason; for each removed one, confirm its former callers use the contract and that the behaviour its test checked is still checked.

**Acceptance Scenarios**:

1. **Given** the code after this spec, **When** every use case is scanned for a body that is only one call on a constructor property, **Then** none is found except those recorded as kept with a reason.
2. **Given** a removed use case that had a unit test, **When** the tests are read, **Then** each behaviour it checked that is not a pure pass-through (an argument transformation, a default, an ordering) is still checked by a repository or state-holder test.
3. **Given** a state holder after this spec, **When** its constructor is read, **Then** it receives use cases and repository contracts only — never a data source, DAO, entity or repository implementation.

---

### User Story 4 - The rule is checked, not reviewed (Priority: P2)

A contributor adds a new screen with a public `onSearchClicked()` on its state holder, or obtains a state holder inside a child composable. The build fails, naming the file and the rule, as it already does for layout and comments.

**Why this priority**: Spec 025 showed that unchecked rules drift. The check depends on the screens having been moved first, so it lands last.

**Independent Test**: On a scratch branch, add a public non-`onAction` function to a state holder, and add a state-holder lookup in a child composable; confirm each fails the build with a clear message; remove them and confirm the build passes.

**Acceptance Scenarios**:

1. **Given** the check is wired, **When** a state holder exposes a public function other than `onAction` (state and event properties excepted), **Then** the unit-test run fails naming the file.
2. **Given** the check is wired, **When** a composable other than a route obtains or receives a state holder, **Then** the static checks fail.
3. **Given** this spec is complete, **When** the constitution is read, **Then** principle III's action/event rule names its check instead of Review, the version is 2.1.0, and the history file has one line for it.

---

### User Story 5 - State-holder tests set up the main dispatcher one way (Priority: P3)

A contributor writes a test for a state holder. They add one shared test rule instead of copying `setMain` / `resetMain` into a setup and teardown, and every state-holder test does it the same way.

**Why this priority**: Small, but it is touched anyway when every state-holder test is rewritten to send actions.

**Independent Test**: Search test sources for direct `Dispatchers.setMain` / `resetMain` calls; only the shared rule has them.

**Acceptance Scenarios**:

1. **Given** the test sources after this spec, **When** they are searched for `Dispatchers.setMain`, **Then** it appears only in the shared rule in `core/testing/`.
2. **Given** any state-holder test, **When** it is read, **Then** it drives the holder through `onAction` and reads effects from its event stream.

### Edge Cases

- **Events emitted with no collector**: today some effects use a buffered `Channel` (kept until collected) and others a `SharedFlow` without replay (dropped when nobody listens). Unifying the type MUST keep each effect's current delivery behaviour (delivered once later vs. dropped); the plan lists each effect and which it is.
- **Effects that survive rotation**: an effect pending during a configuration change must not be lost if it is delivered today, and must not be replayed if it was already shown.
- **Actions that return a value or take a callback today** (e.g. a function that returns whether the action was accepted, or a long-press menu built from a page callback): they become an action plus, if the screen needs a result, a state change or event. Behaviour stays the same.
- **Actions sent from outside the screen**: the entry-point holder receives intents from the activity, not taps. It has no user actions today (0 public functions) and keeps its current hand-over shape, which ADR 0011 records; it adopts the event type only if it already emits effects.
- **State holders with no user input** (`PageTextSizeViewModel`, `EntryPointViewModel`: 0 public functions): they need no action type; the check MUST accept a holder with no `onAction`.
- **WebView bridges** (`PopupTabs`, `SitePermissionGate`, `LongPressActions`): hosted in a ViewModel for its scope, but called from WebView callbacks that need a synchronous answer (a pop-up decision, the pop-up `WebView`, a permission outcome). They are not named `*ViewModel`, keep their public functions, and are exempt from FR-016; ADR 0026 records why.
- **Cross-feature callbacks** (`BrowserSlots`, `SettingsDestinations`, `FireButton`): navigation to another feature stays a callback filled in by `AppNavHost`; the route turns a navigation event into that callback.
- **A forwarding use case used across features from `core/`**: its callers in several features switch to the `core` contract; the contract does not move.
- **A forwarding use case whose contract is a platform controller** (`GetAppLanguageUseCase` → `controller.current`, `IsDefaultBrowserUseCase` → `controller.isDefaultBrowser`): it is removed only if the controller is a domain contract a state holder may see; otherwise it stays with that reason.
- **Instrumented tests that call screen composables directly** with today's parameter lists: they call the route with the holder they build (`XRoute(viewModel = …)`) so they keep exercising the real holder, with the same assertions.
- **Work on `main` during this spec**: merged into the branch and moved to the new shape in the same merge.

## Requirements *(mandatory)*

### Functional Requirements

**Behaviour and data preservation**

- **FR-001**: Every screen, action, message, dialog, navigation and background behaviour MUST behave as on `0372d0f`, including the number of times and the moment each one-off effect is shown. The one exception is US1 scenario 2: an effect already shown is not shown again after rotation (research R4).
- **FR-002**: No stored data, schema, settings key, asset path, manifest entry or permission changes; no migration is added. The build MUST install over `0372d0f` with every record intact.
- **FR-003**: No new runtime dependency is added. A test-only dependency, if needed, is looked up at its latest stable version and added to `gradle/libs.versions.toml`.

**Use cases**

- **FR-004**: Each of the 33 use cases listed as forwarding in Spec 025 MUST be re-checked on the current code. One whose body still only forwards one call MUST be removed and its callers MUST use the contract it forwarded to; its DI binding and test are removed or replaced.
- **FR-005**: A use case MUST be kept when removing it would give a state holder a data source, DAO, entity, repository implementation or Android framework type, or when it now holds logic. Each kept one is recorded in the plan with its reason.
- **FR-006**: Every non-trivial behaviour a removed use case's test checked (argument transformation, defaults, ordering, error mapping) MUST remain checked by a repository or state-holder test.

**Action and event shape**

- **FR-007**: Every state holder with user input MUST expose exactly one public input function, `onAction(action: XAction)`, where `XAction` is a closed type in the screen's folder listing every input the screen can send.
- **FR-008**: Every state holder that produces one-off effects MUST expose them as one read-only stream of a closed `XEvent` type in the screen's folder. The holder's existing `Unit` and typed effect streams and its one-off `UiState` fields (consumed by a `consume…` function) are folded into it. The singleton relays that carry `Int`/`String` to the browser screen are not holders and stay as they are.
- **FR-009**: Each effect MUST keep its current delivery semantics: an effect that is buffered until collected today stays buffered; one that is dropped without a collector today stays dropped. The plan lists every effect and its semantics. A dropped effect that looks like a bug is recorded for a later change, not fixed here.
- **FR-010**: State stays `StateFlow<XUiState>`; extra fast-changing flows already split out for render performance (load progress, ADR 0015) stay separate flows.
- **FR-011**: State holders with no user input keep no `onAction`. The entry-point holder keeps the activity hand-over recorded in ADR 0011.

**Route and screen**

- **FR-012**: Every screen MUST have a route composable that alone obtains its state holder(s), collects state once and collects events, and a screen composable that receives only state values, `onAction`, read-only lookup or hook objects exposed by the holder, and plain callbacks (for example cross-feature navigation).
- **FR-013**: No composable other than a route or an embedded host MAY obtain a state holder, and no composable MAY pass one to another. WebView bridges (see Edge Cases) are obtained at the browser route and passed down as bridges.
- **FR-014**: `config/ktlint/baseline.xml` MUST be deleted and the `ktlint { baseline … }` setting removed, with every Compose rule passing.
- **FR-015**: Existing render-performance patterns MUST be kept: per-frame state is still read in `offset {}`, `graphicsLayer {}` or `drawBehind {}`, and children receive only the fields they need.

**Checks and governance**

- **FR-016**: An automated architecture test MUST fail when a state holder exposes a public function other than `onAction`, naming the file. State and event properties are not functions and are allowed.
- **FR-017**: The Compose rules (`vm-forwarding-check`, `vm-injection-check`) MUST run without a baseline and fail the static checks on a violation.
- **FR-018**: The constitution MUST move principle III's action/event rule from Review to Enforced, naming the checks of FR-016 and FR-017, at version 2.1.0, with one line in `docs/constitution-history.md`. The note "screens written before 2.0.0 move to it in Spec 026" is removed.
- **FR-019**: `AGENTS.md` "Open work" MUST no longer list Spec 026 work.

**Tests**

- **FR-020**: One shared main-dispatcher test rule MUST exist in the core test-support folder, and every unit test that sets the main dispatcher MUST use it; no test calls `Dispatchers.setMain` or `resetMain` directly.
- **FR-021**: State-holder tests MUST drive the holder through `onAction` and read effects from its event stream; their assertions about state and effects are kept.
- **FR-022**: No test is deleted to make the change pass, except tests of removed forwarding use cases whose non-trivial checks are covered per FR-006.

**Delivery**

- **FR-023**: The work MUST be done on branch `026-viewmodel-action-event` as a series of commits — shared test rule and event helper first, then one feature per commit (its forwarding use cases, its state holders, its routes and tests together), then the check and governance. Each commit MUST build and pass the static checks and unit tests; the branch is merged to `main` once, with CI green.

### Key Entities

- **State holder**: a screen's `ViewModel`.
- **Action (`XAction`)**: a closed set of everything a screen can ask its state holder to do; the holder's only input.
- **Event (`XEvent`)**: a closed set of one-off effects (navigate, show a message, open a dialog) the holder emits; each is shown once.
- **Route**: the composable that obtains the state holder, collects its state and events, and hands plain values and `onAction` to the screen.
- **Embedded host**: a route-like composable shown inside another screen through a slot (`HomePage`, `QuickSettingsHost`, `FireOverlayHost`, `rememberFireButton`); it obtains its own holder the same way a route does.
- **WebView bridge**: a `@HiltViewModel` that answers WebView callbacks synchronously (`PopupTabs`, `SitePermissionGate`, `LongPressActions`); not a screen state holder.
- **Screen**: the composable that draws state and reports input through `onAction`; it never sees a state holder.
- **Forwarding use case**: a use case whose whole body is one call on one of its constructor properties.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every unit and instrumented test that passed on `0372d0f` passes on the final build (or is replaced per FR-022), on the 16 KB AVD.
- **SC-002**: 100 % of state holders with user input (16 of 18) expose `onAction` as their only public function; 0 composables other than routes obtain a state holder.
- **SC-003**: The ktlint baseline is gone: 23 findings → 0, with no rule disabled or suppressed.
- **SC-004**: Forwarding use cases: 33 → 0, except those recorded as kept with a reason under FR-005.
- **SC-005**: Direct `Dispatchers.setMain` calls in tests: 22 files → 1 (the shared rule).
- **SC-006**: A manual walk of every screen's main actions, with a rotation and a return from background, shows every message, dialog and navigation exactly once, as on `0372d0f`.
- **SC-007**: Release package size within ±2 % and cold start within ±5 % of the `0372d0f` release, measured on the same device by interleaved runs.
- **SC-008**: Each deliberate violation in User Story 4 fails the build with a message naming the file; the constitution names a check for the action/event rule.

## Assumptions

- Spec 025's lists were produced by a script on an earlier commit; this spec re-checks them on the current code before acting, and the final numbers in the plan may differ slightly.
- `HistoryViewModel` and `DownloadsViewModel` have functions named `onAction…` (e.g. `onActionSheetDismiss`) but no single action entry point; they are in scope like the others.
- `BaseViewModel` stays as the shared base for safe coroutine launches; it is not a state holder.
- The browser screen's four state holders stay four holders, and `BrowserViewModel` is not split; only their entry points change.
- No design work is needed: nothing visible changes, so no design waiver is recorded.
- Device gates run on the AVDs (`TA016_API36_16K`, `TA016_API29_CI`, `TA016_API24`), never on the owner's Galaxy A55 for instrumented tests.
