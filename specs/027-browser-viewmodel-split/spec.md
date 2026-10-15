# Feature Specification: Browser View Model Split

**Feature Branch**: `027-browser-viewmodel-split`
**Created**: 2026-10-15
**Status**: Draft
**Input**: User description: "Spec 027 browser-viewmodel-split: split BrowserViewModel (514 lines, 29 actions, one holder for the whole browser screen) into smaller state holders by responsibility (page load and navigation state, address bar input, find in page, downloads, bookmark star/tab creation), each with its own onAction/XAction and XEvent per ADR 0026; shrink BrowserScreen.kt (615 lines) accordingly; delete the unused core BaseViewModel and its test; reduce the size-rule @Suppress annotations in feature/browser that the split makes unnecessary. No user-visible behaviour change; existing unit and instrumented suites are the safety net."

## Context

Spec 026 (PR #44, `90cd024`) brought every screen to `onAction`/`XEvent` and left `BrowserViewModel` whole by the owner's choice. Measured on `90cd024`:

| Area | Today |
|---|---|
| `BrowserViewModel.kt` | 514 lines, 14 constructor dependencies, 29 actions in five groups, `@Suppress("TooManyFunctions", "LongParameterList")` |
| What it owns | page load and navigation state (12 WebView callbacks, history recording, title, favicon, screenshot), address bar text/focus/submit, Home overlay and "came from Home", find in page, downloads and the storage-permission outcome, bookmark star, long-press new tab, desktop mode per tab |
| Shared by all of the above | the current URL, the active tab, whether it is incognito |
| `BrowserScreen.kt` | 615 lines, 4 size suppressions; `BrowserScreenInputs.kt` has one more |
| Largest files in `feature/browser/presentation/browser` | `BrowserScreen.kt` 615, `BrowserWebView.kt` 560, `BrowserViewModel.kt` 514 |
| `@Suppress` for size rules in `feature/browser` | 26 (of 68 in the app) |
| `core/presentation/util/BaseViewModel` | no production user; one test (`BaseViewModelTest`) |
| Browser view-model tests | 7 files, 1,630 lines |

## Clarifications

### Session 2026-10-15

- Q: How is `BrowserViewModel` split? → A: Option A, done the standard way: separate state holders, one per concern (FR-003). The state they share (current URL, title, active tab, incognito) lives in one in-memory source of truth in the data layer (a repository behind a domain contract), written at once when the page reports a change, so every holder reads the same value at the same moment as today. Owner, 2026-10-15: "thế thì A", after asking which way is most standard regardless of time.

## User Scenarios & Testing *(mandatory)*

The people served are the **owner and anyone who changes the browser screen**. The **person using the browser** is served by one promise: nothing they see or keep changes.

### User Story 1 - Nothing changes for the person using the browser (Priority: P1)

Someone browsing loads pages, types in the address bar, searches from Home, finds in page, stars pages, downloads files, switches desktop mode and goes back to Home exactly as before; every message, load and navigation happens once, at the same moment.

**Why this priority**: The browser screen is the app. A split that changes the order of a load, a history record or a message is a regression the person sees.

**Independent Test**: Run the unit and instrumented suites against `90cd024`; repeat the Spec 026 behaviour walk on the 16 KB AVD.

**Acceptance Scenarios**:

1. **Given** the build from this spec, **When** the unit and instrumented suites run, **Then** every test that passed on `90cd024` passes, with the same assertions.
2. **Given** a page load, **When** it starts, progresses, finishes and gets its title, **Then** exactly one history entry is written with that title, as today, and nothing is recorded in incognito.
3. **Given** the address bar or Home search, **When** the person submits, **Then** the keyboard closes, focus clears and the page loads in that order, as today.
4. **Given** a device with data from `90cd024`, **When** this build is installed over it, **Then** every record and setting is kept; no migration is added.

---

### User Story 2 - Each part of the browser screen has one small owner (Priority: P1)

A contributor changing find in page, downloads, the bookmark star or the address bar opens one small file for that concern: its state, its actions and its tests, without reading the rest of the browser.

**Why this priority**: This is the request; today every browser change touches a 514-line holder and a 615-line screen file.

**Independent Test**: For each concern listed in FR-003, find its state, its actions and its tests in files named for it; no file in the browser screen's folder is over the size limit of SC-002.

**Acceptance Scenarios**:

1. **Given** the split, **When** a contributor looks for find-in-page logic, **Then** its state, actions and tests are in files named for find in page, not in a shared browser file.
2. **Given** the split, **When** the size rules run, **Then** the browser screen's holders and screen files need no `TooManyFunctions`, `LongMethod` or `LongParameterList` suppression.
3. **Given** a concern's tests, **When** they run, **Then** they build only that concern's collaborators.

---

### User Story 3 - No dead shared code (Priority: P3)

A contributor reading `core/presentation/util` finds only code that something uses.

**Why this priority**: Small, but `BaseViewModel` misleads: it looks like the base every holder extends, and none does.

**Independent Test**: Search for `BaseViewModel`; nothing is found.

**Acceptance Scenarios**:

1. **Given** this spec is done, **When** the sources are searched for `BaseViewModel`, **Then** neither it nor its test exists.

### Edge Cases

- **Shared current URL**: the address bar, the star, Home and history all read the URL the page reports. Whatever the split, each reads the same value at the same moment as today; a star tapped right after a redirect stars the new URL.
- **Order of page callbacks**: started → URL changed → progress → finished → title arrives late. History must stay "one row per finished navigation, title patched later", including redirects and a load stopped by the person.
- **Tab switch**: the find bar closes and the title cache resets on a tab switch; desktop mode is per tab and forgotten on process restart.
- **Incognito**: no favicon, screenshot or history writes while the active tab is incognito, whichever holder now does them.
- **Events across holders**: a load requested by one concern (address bar, Home) and a message from another (star, download) must keep today's order and kinds (Keep/Drop, ADR 0026).
- **Tests that drive several concerns at once** (for example submit then page callbacks then history): they keep their assertions; only how they reach the concerns changes.

## Requirements *(mandatory)*

### Functional Requirements

**Behaviour**

- **FR-001**: Every page load, navigation, history record, title/favicon/screenshot write, address-bar and Home submit, find-in-page result, download request, storage-permission message, bookmark toggle and message, desktop-mode switch, long-press new tab and Back-to-Home MUST behave as on `90cd024`, including order and timing of one-off effects.
- **FR-002**: No stored data, schema, settings key, manifest entry or permission changes; no migration; no new runtime dependency.

**Split**

- **FR-003**: The browser screen's logic MUST be divided by concern: (a) page load and navigation state with history recording and page artefacts, (b) address bar and Home input and submit, (c) find in page, (d) downloads and the storage-permission outcome, (e) bookmark star, long-press new tab and desktop mode. Each concern has its own action type, its own one-off events (if any) and its own tests.
- **FR-004**: Each concern follows ADR 0026: input through `onAction`, one-off effects as `XEvent` with their current Drop/Keep kind, lookups as properties.
- **FR-005**: State shared by several concerns (current URL, page title, active tab, incognito) MUST have one in-memory source of truth in the data layer, behind a domain contract, updated synchronously when the page reports a change; every concern MUST see the same value it sees today at the same moment. It is never read back from the database for this purpose.
- **FR-006**: `BrowserRoute` stays the only composable that obtains holders; `BrowserScreen.kt` MUST be divided into files by part (content, top area, hub, handlers/effects) so no file in the browser screen's folder exceeds the limit in SC-002.

**Cleanup**

- **FR-007**: `core/presentation/util/BaseViewModel` and `BaseViewModelTest` MUST be deleted.
- **FR-008**: Every size-rule `@Suppress` (`TooManyFunctions`, `LongMethod`, `LongParameterList`, `CyclomaticComplexMethod`) on the browser screen's holders and screen files MUST be removed. Suppressions on WebView client classes, page-request types and other files this spec does not split stay, each with its reason.

**Tests and delivery**

- **FR-009**: Tests of the browser holder MUST move with their concern and keep their assertions; no test is deleted except `BaseViewModelTest`. A test that spans concerns stays and drives them through their actions.
- **FR-010**: The architecture checks of Spec 026 stay green without new exemptions; `KNOWN_VIOLATIONS` stays empty.
- **FR-011**: Work on branch `027-browser-viewmodel-split`, one concern per commit, each commit green (static checks, unit tests, androidTest compile); merged once with CI green after the instrumented suite and behaviour walk.

### Key Entities

- **Concern**: one part of the browser screen's behaviour with its own state, actions, events and tests (FR-003).
- **Shared page state**: the current URL, page title, active tab and incognito flag that several concerns read, held in one in-memory store (FR-005).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every unit and instrumented test that passed on `90cd024` passes (minus `BaseViewModelTest`), on the 16 KB AVD; the Spec 026 behaviour walk shows every message, load and navigation once.
- **SC-002**: No file in the browser screen's folder (`feature/browser/presentation/browser/`, not counting `BrowserWebView.kt` and `BrowserChromeClient.kt`) is over 300 lines.
- **SC-003**: Size-rule suppressions in `feature/browser` drop from 26 by at least the 7 on the browser holder and screen files (FR-008).
- **SC-004**: No browser concern's holder exceeds 10 constructor dependencies or 12 actions.
- **SC-005**: Release APK within ±2 % and cold start within noise of `90cd024`; the Compose report shows 0 non-skippable.
- **SC-006**: `BaseViewModel` appears nowhere in the sources.

## Assumptions

- `BrowserWebView.kt` (560 lines) and `BrowserChromeClient.kt` are WebView glue with one method per platform hook; they are out of scope.
- `HomeViewModel`, `HubViewModel`, `ProtectionViewModel`, `AddressSuggestionsViewModel` and the WebView bridges keep their shape.
- Suppressions outside the browser holder and screen files (Home hero, WebView clients, page requests, sheets) are out of scope.
- No design work: nothing visible changes.
- Device gates run on the AVDs, never instrumented tests on the owner's Galaxy A55.
