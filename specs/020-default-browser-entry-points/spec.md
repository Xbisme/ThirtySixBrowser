# Feature Specification: Default Browser & Entry Points

**Feature Branch**: `020-default-browser-entry-points`
**Created**: 2026-10-11
**Status**: Draft
**Input**: User description: "Spec 020 `default-browser-entry-points` — the first spec of Constitution v1.5.0 Phase 6 'Real browser'. Make ThirtySix reachable as a browser from the rest of the phone: open web links from other apps, receive shared text, search selected text, and let the user make ThirtySix the default browser from Settings and from a dismissible Home invitation."

## Context

Today ThirtySix can only be started from its launcher icon. The phone does not know it is a
browser: it never appears in the "Open with" choice for a link, it cannot be chosen as the
default browser, and a link tapped in a mail, chat or settings app always opens somewhere
else. Every other feature shipped in Specs 001–019 is therefore reachable only by people
who remember to open the app first and type an address. This spec makes the app reachable
from everywhere a web address appears on the phone.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open a link from another app (Priority: P1)

A person taps a web link in a mail, chat or note app. ThirtySix is offered as a way to open
it (or opens it directly once it is the default browser). The page opens in a new tab in
ThirtySix; the tabs the person already had stay exactly as they were. When they press Back
on that fresh tab, they return to the app they came from.

**Why this priority**: This is what makes the app a browser rather than a website viewer.
Without it, nothing else in Phase 6 is reachable by people who did not start in the app.

**Independent Test**: With ThirtySix installed, tap an http and an https link in another app
and pick ThirtySix: each opens in a new active tab, existing tabs are unchanged, and Back
from the new tab returns to the sending app.

**Acceptance Scenarios**:

1. **Given** ThirtySix is not running, **When** the person opens an https link from another app and chooses ThirtySix, **Then** ThirtySix starts, a new normal tab loads that address, and it is the active tab.
2. **Given** ThirtySix is running with 3 tabs open, **When** a link arrives from another app, **Then** a 4th tab is created for the link and made active, and the other 3 tabs keep their pages.
3. **Given** ThirtySix is running and showing History or Settings, **When** a link arrives, **Then** the person lands on the browser showing the new tab, not on the screen they left.
4. **Given** a tab was just opened from another app and has no back history, **When** the person presses Back, **Then** that tab is closed and the person returns to the app that sent the link.
5. **Given** a tab was opened from another app and the person then followed links inside it, **When** they press Back, **Then** Back walks back through those pages first, as it does for any tab.
6. **Given** the person opens 20 links from other apps one after another, **When** they then look at the system's recent-apps view, **Then** there is exactly one ThirtySix entry, not one per link.

---

### User Story 2 - Make ThirtySix the default browser (Priority: P1)

A person who likes ThirtySix wants every link on the phone to open in it. In Settings they see
whether ThirtySix is the default browser; if it is not, one action takes them through the
system's own confirmation (or to the system's default-apps screen on older Android). When
they come back, Settings already shows that ThirtySix is the default. A gentle invitation on
the Home page offers the same thing once, for people who never visit Settings.

**Why this priority**: Being offered in "Open with" is only half of being a browser; most
people expect a browser they use to be the default. It also unlocks the next three Phase 6
specs, which assume links arrive without an extra choice each time.

**Independent Test**: On a phone where another browser is the default, open Settings, use the
default-browser row, confirm in the system prompt, return: the row says ThirtySix is the
default, links from other apps now open in ThirtySix without a choice, and the Home
invitation no longer appears.

**Acceptance Scenarios**:

1. **Given** ThirtySix is not the default browser on Android 10 or later, **When** the person uses the Settings row's action, **Then** the system's own default-browser confirmation appears, and accepting it makes ThirtySix the default.
2. **Given** ThirtySix is not the default browser on Android 7–9, **When** the person uses the Settings row's action, **Then** the system's default-apps settings screen opens, where the person can pick ThirtySix.
3. **Given** the person has just made ThirtySix the default and returns to the app, **When** Settings is visible, **Then** the row shows ThirtySix as the default without needing a restart, and offers no action.
4. **Given** ThirtySix is not the default and onboarding is complete, **When** the person opens the Home page, **Then** a dismissible invitation offers to make ThirtySix the default browser.
5. **Given** the invitation is visible, **When** the person dismisses it, **Then** it disappears and does not come back on later launches; the Settings row is still available.
6. **Given** the invitation is visible, **When** the person accepts it and ThirtySix becomes the default, **Then** the invitation disappears and does not return.
7. **Given** the person declines the system confirmation, **When** they return to the app, **Then** nothing changes: the row still offers the action and the invitation (if not dismissed) is still shown.
8. **Given** ThirtySix was the default and the person later makes another app the default in system settings, **When** they return to ThirtySix's Settings, **Then** the row shows that ThirtySix is no longer the default and offers the action again.

---

### User Story 3 - Share text or a link to ThirtySix (Priority: P2)

A person reads something in another app and uses the Share sheet to send it to ThirtySix. If
what they shared contains a web address, ThirtySix opens that address; otherwise it searches
for the shared text with the search engine the person chose in Settings.

**Why this priority**: Many apps share a link as text instead of opening it, and sharing a
phrase to search it is a common habit. Valuable, but people can still copy and paste without
it.

**Independent Test**: From another app, share (a) a bare https address, (b) a sentence that
contains an address, (c) a sentence without one: (a) and (b) open the address in a new tab,
(c) opens a search for the sentence with the chosen engine.

**Acceptance Scenarios**:

1. **Given** the shared text is exactly a web address, **When** it is shared to ThirtySix, **Then** that address opens in a new active tab.
2. **Given** the shared text is a sentence containing one web address (for example "Read this https://example.org/a — so good"), **When** it is shared, **Then** the address `https://example.org/a` opens, not a search for the sentence.
3. **Given** the shared text contains no web address, **When** it is shared, **Then** a search for that text opens with the user's chosen search engine.
4. **Given** the shared text contains several web addresses, **When** it is shared, **Then** the first web address in the text opens.
5. **Given** the shared text is empty or only whitespace, **When** it is shared, **Then** ThirtySix opens without creating a tab or a search, and nothing is lost.

---

### User Story 4 - Search selected text (Priority: P3)

A person selects a word or phrase in another app and picks ThirtySix from the text-selection
menu (or from the system's web-search action). ThirtySix opens a search for that text with
the chosen engine.

**Why this priority**: A convenience that power users notice, but the share path of Story 3
already covers the same need with one more tap.

**Independent Test**: Select a word in another app, choose ThirtySix from the selection menu:
a new tab opens with a search for exactly that word using the chosen engine.

**Acceptance Scenarios**:

1. **Given** text is selected in another app, **When** the person picks ThirtySix from the selection menu, **Then** a new active tab opens a search for the selected text with the chosen engine.
2. **Given** another app issues the system's web-search request with a query, **When** ThirtySix handles it, **Then** a new active tab opens a search for that query.
3. **Given** the selected text is itself a web address, **When** it is sent from the selection menu, **Then** it is searched as text, not opened as an address (the person asked to search).

---

### Edge Cases

- **Onboarding never completed**: a link (or shared text, or search) arrives on a fresh install before Spec 018's onboarding was finished or skipped. Onboarding is shown first and is not skipped silently; when the person finishes or skips it, the pending link opens in a new tab. The link is not lost.
- **Tab cap reached**: 50 normal tabs (Spec 011) are already open when a link arrives. The existing tab-cap message is shown and no tab is created; the person's tabs are untouched.
- **Active tab is incognito**: incoming links, shares and searches always open in a new **normal** tab, never in the incognito session, and never inherit incognito state.
- **Unsafe or non-web addresses**: a delivered address whose scheme is not http or https (for example `intent:`, `javascript:`, `file:`, `content:`, `data:`, `market:`) is never loaded. ThirtySix opens without creating a tab and shows a short message that the link could not be opened.
- **Malformed address**: an http/https address that cannot be parsed into a valid web address is treated as unsafe (above) rather than searched, because the person tried to open a link, not to search.
- **Very long shared or selected text**: text longer than 2,048 characters is cut to the first 2,048 characters before it is used as an address or a search.
- **Same link twice in quick succession**: the same address delivered twice within 2 seconds (a double tap in the sending app) produces one new tab, not two.
- **Configuration change or process death**: rotating the phone, changing the app language or the system reclaiming the app while a link is being delivered never opens the link a second time when the app comes back, and never loses it if it had not been opened yet.
- **Link while on another screen**: a link arriving while the person is on History, Bookmarks, Downloads, Settings or the Tabs screen brings them to the browser showing the new tab.
- **Default-browser state changed outside the app**: the Settings row and the Home invitation re-check the state every time the app comes back to the foreground.
- **No default-browser screen on the device**: if neither the system confirmation nor the default-apps screen can be opened (some customised Android builds), the action shows a short message instead of doing nothing.

## Requirements *(mandatory)*

### Functional Requirements

**Being a browser**

- **FR-001**: ThirtySix MUST declare itself to the system as able to open http and https web addresses, so that it appears in the system's choice of apps for a web link and is eligible to be chosen as the default browser.
- **FR-002**: ThirtySix MUST NOT declare itself for any other scheme (no `file:`, `content:`, `intent:`, `javascript:`, `data:`, `market:` and so on).

**Opening incoming links (Story 1)**

- **FR-003**: An incoming web address MUST open in a newly created **normal** tab that becomes the active tab; existing tabs MUST keep their pages, order and history.
- **FR-004**: When ThirtySix is already running, an incoming address MUST be delivered to the running app rather than starting another copy of it; across any number of incoming links the system's recent-apps view MUST show a single ThirtySix entry.
- **FR-005**: Wherever the person is in the app when an address arrives (History, Bookmarks, Downloads, Settings, Tabs), the app MUST show the browser with the new tab.
- **FR-006**: Pressing Back on a tab that was opened from another app and has no back history of its own MUST close that tab and return the person to the app that sent the link. Once the person has navigated inside that tab, Back MUST first walk back through that tab's history as for any other tab.
- **FR-007**: The same address delivered twice within 2 seconds MUST produce a single new tab.
- **FR-008**: An address MUST be opened at most once per delivery: a configuration change (rotation, language change) or the app being recreated after the system reclaimed it MUST NOT open it again.

**Shared text and searches (Stories 3 and 4)**

- **FR-009**: ThirtySix MUST accept plain text shared to it from another app's Share sheet.
- **FR-010**: When shared text contains at least one web address, the first web address in the text MUST be opened; otherwise the whole text MUST be searched with the user's chosen search engine (Spec 010), using the same search-address building the address bar uses.
- **FR-011**: ThirtySix MUST offer an entry in other apps' text-selection menu; selected text sent this way MUST always be searched, even when it looks like a web address.
- **FR-012**: ThirtySix MUST handle the system's web-search request; its query MUST be searched with the chosen engine.
- **FR-013**: Shared, selected or searched text MUST be trimmed of surrounding whitespace and cut to the first 2,048 characters before use; text that is empty after trimming MUST NOT create a tab or a search.
- **FR-014**: Opened addresses, shares and searches from Stories 3 and 4 MUST follow the same new-tab rules as Story 1 (FR-003 to FR-008).

**Safety**

- **FR-015**: Any delivered address whose scheme is not http or https, or that cannot be parsed as a valid web address, MUST NOT be loaded; the app MUST open without creating a tab and show a short message that the link could not be opened.
- **FR-016**: Incoming links, shared text and selected text MUST NOT be written to any log, analytics or diagnostic output, and MUST NOT be sent anywhere except the web page itself or, for searches, the user's chosen search engine.
- **FR-017**: Incoming content MUST always open in a normal tab, never in an incognito tab, regardless of whether the active tab is incognito.
- **FR-018**: When 50 normal tabs are already open, an incoming address or search MUST show the existing tab-cap message (Spec 011) and MUST NOT create a tab or replace an existing one.

**Arriving before onboarding**

- **FR-019**: If onboarding (Spec 018) has never been completed or skipped, an incoming address, share or search MUST NOT skip it; the content MUST be held until the person finishes or skips onboarding and then opened under the same rules. It MUST survive a configuration change during onboarding.

**Default browser (Story 2)**

- **FR-020**: Settings MUST contain a "Default browser" row that states whether ThirtySix is currently the default browser.
- **FR-021**: When ThirtySix is not the default, the row MUST offer an action that, on Android 10 and later, shows the system's own default-browser confirmation, and on Android 7–9 opens the system's default-apps settings screen. When ThirtySix is the default, the row MUST state it and offer no action.
- **FR-022**: The default-browser state shown in Settings and used by the Home invitation MUST be re-read every time the app returns to the foreground, so changes made in system settings are reflected without a restart.
- **FR-023**: If the device can open neither the system confirmation nor the default-apps screen, the action MUST show a short message instead of doing nothing.
- **FR-024**: The Home page MUST show a dismissible invitation to make ThirtySix the default browser, only when all hold: ThirtySix is not the default; onboarding is complete; the invitation has never been dismissed; the current tab is not incognito.
- **FR-025**: The invitation MUST offer the same action as the Settings row and a way to dismiss it. Once dismissed, it MUST never reappear on its own, including after app restarts and updates. The dismissal MUST be remembered on the device only.
- **FR-026**: When ThirtySix becomes the default, the invitation MUST disappear; it MUST NOT reappear if ThirtySix later stops being the default (the Settings row remains the way back).
- **FR-027**: Declining the system confirmation MUST change nothing: no state is recorded, the row keeps its action and an undismissed invitation stays.

**Platform and quality constraints**

- **FR-028**: The feature MUST NOT add any runtime permission.
- **FR-029**: All new user-visible text (the Settings row, its states, the invitation, the "could not open link" message and the selection-menu entry name) MUST exist in all 8 supported languages.
- **FR-030**: Every new control MUST carry a screen-reader label, and the invitation's dismiss control MUST be reachable and announced.

### Key Entities

- **Incoming request**: what another app hands to ThirtySix — a web address to open, text to interpret, or a query to search — together with whether it must always be searched (selection menu, web-search request) or interpreted (Share sheet). Held only until it is opened or rejected; never stored beyond that.
- **Default-browser state**: whether the phone currently names ThirtySix as its default browser. Owned by the system, read on demand, never stored by the app.
- **Invitation dismissal**: a single on-device flag recording that the person dismissed (or accepted) the Home invitation, so it is never shown again.
- **Opened-from-another-app marker**: the fact that a tab was created for an incoming link and has not yet been navigated, used only to make Back return to the sending app (FR-006).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A link tapped in another app shows its page loading in ThirtySix within 1 second when ThirtySix is already running, and within 2 seconds from a cold start, on the project's reference device (median of 10).
- **SC-002**: Across 20 links opened from other apps in a row, the recent-apps view shows exactly 1 ThirtySix entry and exactly 20 new tabs exist, with every earlier tab unchanged.
- **SC-003**: 100% of the tested non-web and malformed addresses (at least `intent:`, `javascript:`, `file:`, `content:`, `data:`, `market:`, and three malformed http/https forms) are refused without loading anything.
- **SC-004**: A person can make ThirtySix the default browser from Settings in at most 2 taps inside ThirtySix plus the system's own confirmation.
- **SC-005**: After a dismissal, the Home invitation appears 0 times across 10 subsequent launches; after ThirtySix becomes the default it also appears 0 times.
- **SC-006**: After changing the default browser in system settings and returning, Settings shows the new state within 1 second, without restarting the app.
- **SC-007**: In 10 trials each, a shared sentence containing an address opens that address, and a shared sentence without one opens a search with the chosen engine — 20/20 correct.
- **SC-008**: Rotating the phone or changing the app language right after a link opens produces 0 duplicate tabs across 10 trials.
- **SC-009**: On a fresh install, a link opened before onboarding is completed opens immediately after the person finishes or skips onboarding in 10/10 trials.
- **SC-010**: The release app grows by no more than 60 KB and adds no native library (16 KB gate stays green).

## Assumptions

- **Design waiver (owner decision, 2026-10-11)**: Constitution §X's "Design before spec" rule is waived for this spec because Claude Design is not available on the owner's current account. The two new UI pieces — the Settings "Default browser" row and the Home invitation — are built from existing Spec 019 design-system components (the Settings list row; the Home page's card style) and its tokens. The owner will supply a design later; a later visual change MUST NOT change the behaviour specified here.
- **Reference device**: the owner's Galaxy A55 (Android 16) used for Spec 019's real-device pass, release build; Android 7–9 behaviour is checked on the project's API 24 emulator.
- **Back to the sending app** (FR-006) follows the widely used browser convention of closing the fresh tab. If the system does not return to the sending app on some launcher, the person lands on the phone's previous screen; the tab is still closed.
- **"Within 2 seconds"** (FR-007) is a duplicate-delivery window, not a debounce of different links; two different addresses always open two tabs.
- **2,048 characters** (FR-013) matches the longest address the app already accepts for bookmarks; the address bar has no separate limit.
- **The first web address wins** (FR-010): the app does not ask which of several addresses to open.
- **Selected text is always searched** (FR-011) because the person explicitly picked a search action; the Share sheet is where addresses are opened.
- **Invitation timing**: shown from the first Home view after onboarding is completed, with no launch counter or delay; it is shown on the Home page only, never over a loaded web page.
- **Custom Tabs stay out of scope** (Constitution: WebView only). Apps that would use Custom Tabs fall back to an ordinary link open, which FR-001 to FR-008 handle.
- **No new dependency is expected**: system role and intent handling are part of the platform and of AndroidX libraries already in the project; anything added at plan time is verified per Constitution §IX and the 16 KB gate.
- **Out of scope**: file upload, fullscreen video, popups, site permissions (Spec 021); find in page, sharing from the app, autocomplete (Spec 022); tracker blocking and other protections (Spec 023); launching other apps for non-web schemes, which Spec 012's external-intent guard already covers for links clicked inside pages.

## Dependencies

- **Spec 010** — search-address building with the user's chosen engine (FR-010 to FR-012).
- **Spec 011** — tab creation, active tab and the 50-tab cap message (FR-003, FR-018).
- **Spec 012** — incognito tabs, which incoming content must never join (FR-017).
- **Spec 016** — the Settings screen that hosts the new row (FR-020).
- **Spec 018** — onboarding, which incoming content must not skip (FR-019).
- **Spec 019** — the Home page that hosts the invitation, the design-system components, and Compose Phase Discipline for the new UI.
