# Feature Specification: Private / Incognito Mode

**Feature Branch**: `012-private-incognito-mode`
**Created**: 2026-05-03
**Status**: Draft
**Input**: User description: "Spec 012 — `private-incognito-mode`. Add an incognito (private) tab mode on top of Spec 011's tab management infrastructure. Incognito tabs MUST NOT persist cookies, history, cache, or form data, and MUST NOT survive process death. Closing the last incognito tab (or invoking 'Close all incognito') wipes every trace of the incognito session. Crash-resilience is the user's stated top priority."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open an incognito tab and browse without leaving traces (Priority: P1)

A privacy-conscious user wants to browse a website (sensitive research, a one-off login, a shared device) without that visit being recorded in their browsing history, autocomplete suggestions, or stored credentials. They open a new incognito tab from the tab switcher, type a URL, and read pages — when they later check History, none of the visited URLs appear, and no autofill suggestions reference them.

**Why this priority**: This is the entire reason the feature exists. Without P1, the rest is meaningless. It is also the slice that delivers immediate user value: a single incognito tab that doesn't leave history is already a working private mode.

**Independent Test**: Open a fresh install (or wipe normal history first), create one incognito tab, visit 5 distinct URLs across 3 minutes, then open the History screen and verify zero new entries reference those URLs.

**Acceptance Scenarios**:

1. **Given** the tab switcher is open, **When** the user invokes "New incognito tab", **Then** a new tab is created, visually marked as incognito, and no entry referencing the new-tab state is written to persistent History.
2. **Given** an incognito tab is the active tab, **When** the user enters and submits a URL, **Then** the page loads and the visit does NOT appear in History after page load completes.
3. **Given** a normal tab and an incognito tab both exist, **When** the user visits URL X from the normal tab and URL Y from the incognito tab, **Then** History contains an entry for X but no entry for Y.
4. **Given** an incognito tab loaded a page that set a cookie, **When** the user closes that tab (the last incognito tab), **Then** the cookie set by that page is no longer present when the same origin is reloaded in a normal tab afterwards.

---

### User Story 2 - Closing the last incognito tab wipes all incognito session state (Priority: P1)

A user finishes a private session and wants to be confident no residue remains. They close their last incognito tab (or use "Close all incognito"), and all cookies, cached files, form data, and search-form values created during incognito browsing are immediately gone — even if normal tabs remain open.

**Why this priority**: A "private mode" that leaves cookies behind is not a private mode. P1 (no history) without P2 (no cookie persistence) would still leak the session. The two together are the minimum credible private-mode contract.

**Independent Test**: Open a clean install. Create an incognito tab, visit a site that sets a cookie. Close the tab. Reload the same origin in a normal tab and verify the cookie is no longer present.

**Acceptance Scenarios**:

1. **Given** an incognito tab is open and has loaded pages that set cookies and stored cached resources, **When** the user closes the (last) incognito tab, **Then** within the same app session those cookies and cached resources are no longer used by any subsequent request from any tab.
2. **Given** several incognito tabs are open, **When** the user invokes "Close all incognito", **Then** all incognito tabs are closed AND all cookies/cache/form data accumulated during the incognito session are wiped.
3. **Given** an incognito tab and a normal tab both have the same site open, **When** the incognito tab is closed and was the last incognito tab, **Then** the cookie jar is restored to the snapshot taken before incognito began, so normal-tab cookies (including for sites unrelated to incognito browsing) survive intact while any cookies set during the incognito session are gone.
4. **Given** a user types text into a form field in an incognito tab, **When** the tab is closed and a new tab opens to the same form, **Then** no autocomplete suggestion offers the previously-typed value.

---

### User Story 3 - Process death erases all incognito tabs while normal tabs restore (Priority: P1)

A user has 3 normal tabs and 2 incognito tabs open. They switch to another app for a long time and Android kills the browser process. When they return, the app cold-starts and their 3 normal tabs are restored exactly as Spec 011 promises — but the 2 incognito tabs are gone, and no incognito browsing data remains anywhere.

**Why this priority**: Process death is one of the two ways an app session ends on Android (the other is explicit close). If incognito state survives process death, the privacy guarantee is broken every time the OS reclaims memory. This is also the most common crash-vector for incognito features in other browsers — the restore path must be defensive.

**Independent Test**: Open 3 normal tabs + 2 incognito tabs. Force-stop the app from system Settings. Re-launch. Verify exactly 3 tabs are present, all of them normal, and the active-tab pointer is sane (not pointing past the end of the list).

**Acceptance Scenarios**:

1. **Given** the user has N normal tabs and M (M > 0) incognito tabs, **When** the OS terminates the process and the user re-launches the app, **Then** exactly N tabs are restored, all of them normal, and 0 incognito tabs are restored.
2. **Given** the active tab before process death was an incognito tab, **When** the app re-launches, **Then** the active-tab pointer falls back to the most recently active normal tab — or a fresh normal tab is auto-created if no normal tab existed.
3. **Given** process death occurs mid-incognito-session, **When** the app re-launches, **Then** no on-disk artefact (screenshot file, favicon file, cache file) attributable to the incognito session remains.
4. **Given** any unexpected legacy state on cold start, **When** the app re-launches, **Then** that state is silently ignored and the launch does not crash.

---

### User Story 4 - Visual differentiation in the tab switcher without leaking content (Priority: P2)

When the user opens the tab switcher with both normal and incognito tabs, they can tell at a glance which is which. Normal tabs continue to display their Chrome-style screenshot preview (per Spec 011); incognito tabs are visually distinct (different background colour token + an unmistakable incognito glyph) and DO NOT display a screenshot of the page content — the page content is precisely what the user is trying to keep private from prying eyes glancing at the screen.

**Why this priority**: Without distinct visual treatment users will lose track of which tab is private and may accidentally type private queries into a normal tab. Without screenshot suppression, a stranger glancing at the device sees the private page rendered as a card. P2 because P1 still works without it (the privacy guarantee is intact), but the user-experience guarantee is incomplete.

**Independent Test**: Open 2 normal tabs (different sites, both screenshotted) + 1 incognito tab on a site. Open the switcher. Verify the 2 normal tabs show site screenshots; the incognito tab shows a placeholder/icon and NO snapshot of the rendered page.

**Acceptance Scenarios**:

1. **Given** the tab switcher is showing both normal and incognito tabs, **When** the user looks at the grid, **Then** incognito cards are visually distinct via background colour and an incognito icon overlaid on the title row.
2. **Given** an incognito tab has loaded a page, **When** the user navigates to the tab switcher, **Then** the incognito card displays a placeholder image instead of a screenshot of the rendered page.
3. **Given** the active tab is incognito and the user backgrounds the app, **When** the user views the system "recents" overview, **Then** the recents thumbnail does NOT reveal the page content (it is blanked by the platform "secure window" flag set on the activity).

---

### User Story 5 - Bulk close all incognito tabs in one action (Priority: P2)

A user finishes a multi-tab private research session and wants to wipe everything in one action without going tab-by-tab. They invoke "Close all incognito" from the switcher (visible only when at least one incognito tab exists) and after a confirmation, every incognito tab disappears and all incognito session state is gone — while their normal tabs remain untouched.

**Why this priority**: P1 already covers single-tab close. Bulk close is a convenience that incognito users (often closing several tabs at once) reach for repeatedly. Saves time but isn't required for the privacy contract.

**Independent Test**: Open 4 normal + 3 incognito tabs. Invoke "Close all incognito". Confirm. Verify exactly 4 tabs remain (all normal) and no incognito-session cookies survive.

**Acceptance Scenarios**:

1. **Given** at least one incognito tab exists, **When** the user opens the tab switcher, **Then** a "Close all incognito" affordance is visible.
2. **Given** zero incognito tabs exist, **When** the user opens the tab switcher, **Then** the "Close all incognito" affordance is NOT visible.
3. **Given** the user invokes "Close all incognito" and confirms, **When** the action completes, **Then** all incognito tabs are closed and all incognito-session state (cookies, cache, form data, search history) is wiped, while normal tabs remain unchanged.

---

### Edge Cases

- **External intent links** (`tel:`, `mailto:`, `intent://`, custom schemes) opened from inside an incognito tab MUST dispatch normally; if no app handles the intent, the system MUST fail gracefully (silent drop or toast) and MUST NOT crash.
- **Web permission requests** (geolocation, camera, microphone, MIDI, protected media) inside an incognito tab MUST be silently denied — same posture as normal tabs per Spec 007 — without any prompt that could leak the page identity to a glancing observer.
- **Mid-load tab close**: closing an incognito tab while a page is still loading MUST cancel the load cleanly without leaving an orphan resource (cache fragment, partial cookie). The close path MUST be the same code path whether the tab is idle, loading, or errored.
- **Tab-limit interaction**: incognito tabs and normal tabs have independent caps (`MAX_INCOGNITO_TABS` and `MAX_TABS` respectively). Hitting either cap MUST surface a localized error message in the user's locale (per the 8-locale catalogue), with a distinct message string for each cap.
- **Last-tab close**: closing the only remaining tab when it is incognito MUST trigger the same auto-create behaviour Spec 011 has for closing the last normal tab, but MUST auto-create a NORMAL tab (not another incognito tab), so the user sees a clean, non-private starting state.
- **Concurrent incognito tabs**: with multiple incognito tabs open simultaneously, closing one MUST NOT wipe state still required by the others. State wipe occurs only on the close of the LAST incognito tab (or via "Close all incognito").
- **Long-press of "+"**: Spec 011 already binds the long-press of "+" in the switcher; this feature MUST NOT collide with that binding. The incognito new-tab affordance is therefore a separate visible affordance, not a long-press.
- **Defensive read of legacy persisted state**: if a future or downgraded build leaves behind a persisted row indicating "this tab was incognito", the launch path MUST silently discard it without crashing. (Spec 005's tab schema has no such column today; this is a forward-compatibility guard.)
- **Stress: rapid open/close**: rapidly opening and closing incognito tabs (≥ 100 cycles in a tight loop) MUST NOT leak memory, leak file descriptors, leak windowing-system resources, or eventually crash the app.

## Requirements *(mandatory)*

### Functional Requirements

#### Tab creation & lifecycle

- **FR-001**: The system MUST allow the user to create a new incognito tab from the tab switcher via a dedicated affordance, distinct from the existing "+" new-tab affordance for normal tabs.
- **FR-002**: An incognito tab MUST exist only in volatile in-process memory; the system MUST NOT write any record of its existence to persistent storage (database, disk file, settings store) at any point during its lifecycle.
- **FR-003**: Closing an incognito tab MUST tear down the underlying browsing surface in a fixed, well-defined sequence (page renderer, navigation history, attached caches) so that no rendering or resource-cleanup race can crash the app or leak resources.
- **FR-004**: The maximum number of incognito tabs the system permits MUST be enforced via an independent ceiling (`MAX_INCOGNITO_TABS`, defaulting to the same value as `MAX_TABS`), separate from the normal-tab cap. Hitting either ceiling MUST surface a localized error message specific to which kind of tab was being opened (two separate strings — "max tabs reached" vs "max incognito tabs reached").
- **FR-005**: When the user closes the last remaining tab (across both normal and incognito), the system MUST auto-create a single new NORMAL tab (per Spec 011's auto-create-on-empty rule), never an incognito tab.
- **FR-006**: When the user closes the last incognito tab while normal tabs remain, the active-tab pointer MUST switch to the most recently active normal tab and MUST NOT be left dangling.

#### Privacy boundary — no persistence

- **FR-007**: Visits made inside an incognito tab MUST NOT be appended to the persistent History store.
- **FR-008**: Form-input values entered inside an incognito tab MUST NOT be available as autofill suggestions in any future tab (normal or incognito).
- **FR-009**: Page screenshots generated inside an incognito tab MUST NOT be written to the on-disk screenshot cache that Spec 011 introduced.
- **FR-010**: Favicons fetched while loading an incognito-tab page MUST NOT be written to the on-disk favicon cache that Spec 011 introduced.
- **FR-011**: After the close of the last incognito tab (or after "Close all incognito"), all cached resources, stored form data, search-form history, SSL-decision memory, and find-on-page state attributable to the incognito browsing session MUST be wiped.
- **FR-011a**: Cookie isolation MUST follow a snapshot-and-restore strategy to compensate for the platform's global cookie jar:
  1. Immediately before the **first** incognito tab in a session is created (transition from "zero incognito tabs" to "one incognito tab"), the system MUST capture a snapshot of the entire cookie jar.
  2. When the **last** incognito tab closes (transition from "≥ 1 incognito tab" back to "zero incognito tabs"), the system MUST wipe the cookie jar and restore the captured snapshot in its place.
  3. The snapshot MUST be held entirely in memory and MUST NOT be written to any persistent storage; if the process is terminated mid-session the snapshot is lost — which is acceptable because incognito tabs themselves are also lost (per FR-012), so no normal-tab cookies can be retroactively over-restored.
  4. Snapshot capture and restore MUST be serialized so that concurrent rapid open/close operations cannot corrupt or duplicate snapshots.
- **FR-012**: When the application process is terminated and re-launched (cold start after process death), zero incognito tabs MUST be restored, and zero on-disk artefacts attributable to any past incognito session MUST be present.

#### Visual differentiation

- **FR-013**: Each incognito tab card in the tab switcher MUST be visually distinct from normal tab cards via (a) a different background colour drawn from the existing theme tokens (no new bespoke colour) and (b) an unmistakable incognito glyph in the card title row.
- **FR-014**: The tab switcher MUST NOT display a screenshot of the rendered page content for any incognito tab — instead it MUST display a placeholder graphic (the incognito glyph centred on the colour-token background) regardless of whether a screenshot was captured for that tab in memory.
- **FR-015**: The address-bar / browser screen MUST display a clear, persistent indication when the active tab is incognito (an icon adjacent to the address bar, or a colour-token-driven status accent).
- **FR-016**: While any incognito tab is the active tab, the activity window MUST have the platform "secure window" flag (`FLAG_SECURE`) set. The flag MUST be set when the user switches to an incognito tab and cleared when the user switches back to a normal tab (or when the last incognito tab is closed). This blanks the system "recents" thumbnail and (as a documented side effect) blocks user screenshots and screen recording while the user is in incognito.
- **FR-016a**: The `FLAG_SECURE` set/clear transition MUST be idempotent (setting twice has the same effect as once; clearing when already clear is a no-op) so that lifecycle-event reordering, configuration changes, and rapid tab-switching do not corrupt the window flag state.

#### Crash-resilience & robustness

- **FR-017**: All teardown operations on an incognito browsing surface MUST execute in a fixed order such that subsequent operations on the same surface are impossible (the surface is "dead" before its memory is released), preventing the use-after-destroy class of crashes that Spec 007 identified.
- **FR-018**: External intent links opened from an incognito tab (`tel:`, `mailto:`, `intent://`, custom schemes) MUST be dispatched, AND the absence of any handler app MUST be handled gracefully without crashing or surfacing an unhandled exception.
- **FR-019**: All web-origin permission requests inside an incognito tab MUST be silently denied — identical posture to normal tabs per Spec 007 — with no exception for any origin.
- **FR-020**: The forbidden bridge between web JavaScript and native code MUST remain forbidden in incognito tabs (no `addJavascriptInterface`, no equivalent JS-to-native bridge), per Constitution §X.
- **FR-021**: If the persistent tab store contains any unexpected record on cold start (e.g. a forward-compat row indicating "this was incognito"), the restore path MUST silently discard it and continue without crashing.
- **FR-022**: The tab switcher MUST render every card without crashing even if a referenced screenshot file or favicon file has been deleted concurrently (e.g. by an in-flight close-all-incognito, or by external file-system-pressure cleanup).
- **FR-023**: A stress sequence of at least 100 rapid open-then-close cycles of incognito tabs MUST complete without app crash, without ANR, and without leaking memory or file descriptors beyond a stable working-set ceiling.

#### Localisation & accessibility

- **FR-024**: All new user-visible strings introduced by this feature (e.g. "Incognito tab", "New incognito tab", "Close all incognito", "You're in incognito mode", limit-error messages, accessibility content descriptions) MUST be present in all 8 supported locales (EN/VI/DE/RU/KO/JA/ZH/FR).
- **FR-025**: The incognito glyph and the incognito-mode indicator MUST carry an accessibility label appropriate to each locale so screen-reader users can perceive incognito state.

### Key Entities *(include if feature involves data)*

- **Tab — incognito flag (in-memory only)**: Each in-memory tab record now carries a boolean attribute indicating whether it is incognito. The persistence-layer schema is unchanged: only normal tabs are written to the persistent store; incognito tabs are written nowhere.
- **Incognito session (implicit)**: The set of currently-open incognito tabs collectively constitute a single "incognito session." The session begins when the first incognito tab is created (or when a new one is opened after the last was closed) and ends when the last incognito tab closes — at which point all session-scoped state (cookies, cache, form data, etc.) is wiped per Q3.
- **Tab cache contracts (existing, scope-narrowed)**: The on-disk screenshot cache and favicon cache from Spec 011 MUST refuse writes originating from incognito tabs. Reads from those caches remain unchanged for normal tabs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a user visits 5 distinct URLs in incognito tabs across a 3-minute session, zero entries referencing those URLs appear in the History screen.
- **SC-002**: After an incognito session in which 5 or more pages were visited, zero new files have been written to the on-disk screenshot cache directory and zero new files have been written to the on-disk favicon cache directory.
- **SC-003**: After "Close all incognito" is invoked, no cookie, cached resource, or stored form value from any incognito-session page is reachable from any subsequently-loaded normal-tab page.
- **SC-004**: After the OS terminates the app and the user cold-starts it again, exactly zero incognito tabs are restored, and zero on-disk artefacts attributable to past incognito sessions remain.
- **SC-005**: 100 consecutive rapid open-close cycles of an incognito tab (≤ 1 second between operations) complete with zero crashes and zero ANRs.
- **SC-006**: When the active tab is incognito and the user views the system "recents" overview, the rendered page content is NOT visible in the recents thumbnail (the recents thumbnail is blanked by `FLAG_SECURE`).
- **SC-007**: Across all 8 supported locales, every new user-visible string introduced by this feature renders correctly without truncation, fallback to English, or untranslated tokens.
- **SC-008**: The release artefact size grows by no more than 100 KB versus the Spec 011 baseline (2.1 MB), measured on the same build configuration.
- **SC-009**: The 16 KB native-page-size compatibility gate continues to pass on the resulting release artefact (no new native libraries introduced is the expected baseline).
- **SC-010**: A user with no prior knowledge of the feature can correctly identify which open tabs are incognito and which are normal in under 2 seconds of looking at the tab switcher (visual differentiation is unambiguous).

## Assumptions

- The existing tab infrastructure from Spec 011 (in-memory tab list, tab repository, switcher UI, screenshot cache, favicon cache) is the substrate for incognito tabs; this feature extends it with an in-memory flag and gated cache writes, and does not replace any of it.
- The platform's web-rendering surface used in Spec 007 exposes a global cookie jar shared across all instances of the surface within the same app process; per-instance cookie isolation is NOT a platform-supported primitive, so the privacy guarantee is implemented via a snapshot-and-restore strategy (see FR-011a): capture the cookie jar before the first incognito tab opens, restore it when the last incognito tab closes.
- "Process death" means full process termination by the OS (not configuration change, not app backgrounding) and is the boundary at which all in-memory state is reset. The persistent tab store from Spec 011 is the only source of truth at cold start.
- The 8 supported locales (EN/VI/DE/RU/KO/JA/ZH/FR) and the existing visual design tokens (`MaterialTheme.colorScheme.*`, spacing tokens, typography tokens) are sufficient for incognito visual differentiation; this feature does NOT introduce a new colour palette or a new theme variant.
- Permission-deny posture, JavaScript-bridge prohibition, and mixed-content prohibition from Spec 007 carry over identically; no incognito-specific exception exists.
- Network-level tracker blocking, per-domain cookie isolation, and supercookie defences are explicitly out of scope (reserved for Spec 019 / future privacy specs).
- A custom incognito theme override (a separate full ColorScheme for incognito mode) is explicitly out of scope; visual differentiation is achieved via existing tokens only.
- This spec follows the project's incremental-scope preference: only files and packages strictly required by this feature are added; no speculative skeleton scaffolding.

## Dependencies

- **Spec 005** (Room database schema) — `TabEntity` schema is unchanged; this feature relies on the fact that incognito tabs are NEVER written to the persistent tab store, so no migration is required.
- **Spec 007** (WebView Compose wrapper) — incognito tabs reuse the same lockdown contract: file-access denied, JavaScript-bridge forbidden, mixed-content disallowed, permissions silently denied, lifecycle teardown sequence preserved.
- **Spec 011** (Tabs management) — the entire tab infrastructure (in-memory tab list, switcher grid, long-press to new-tab, max-tabs limit, screenshot cache, favicon cache, close-tab and close-all-tabs use cases) is the substrate this feature extends.

## Clarifications

### Session 2026-05-03

- Q: MAX-tabs limit — shared pool with normal tabs or independent pool? → A: B (independent — `MAX_INCOGNITO_TABS = MAX_TABS`; two separate counters; two localized limit-error messages). Reflected in FR-004 + Edge Cases.
- Q: Recents-thumbnail privacy — enable `FLAG_SECURE` on the activity window while the active tab is incognito? → A: A (Yes — match Chrome incognito behaviour; `FLAG_SECURE` set on entering incognito, cleared on leaving; side-effect blocks user screenshots and screen recording while incognito is active). Reflected in FR-016 + FR-016a + SC-006 + US4 acceptance scenario 3.
- Q: Cookie isolation strategy given the platform's global cookie jar? → A: B (Snapshot the cookie jar before the first incognito tab opens; restore snapshot when the last incognito tab closes. Preserves normal-tab cookies across the incognito session; wipes incognito-only cookies cleanly). Reflected in FR-011 + FR-011a + Assumptions + US2 acceptance scenario 3.
