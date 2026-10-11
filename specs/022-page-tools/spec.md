# Feature Specification: Page Tools

**Feature Branch**: `022-page-tools`
**Created**: 2026-10-11
**Status**: Draft
**Input**: User description: "022-page-tools — Phase 6 spec 3/4 (Constitution v1.5.0), depends on Spec 021. Six page tools, each reachable from the hub sheet (Spec 019) while a web page is shown: find in page (replaces the top bar), share, per-tab desktop mode, one app-wide text zoom level, print / save as PDF, and offline address-bar autocomplete from bookmarks and history."

## Owner decisions taken before this spec (2026-10-11)

| Topic | Decision |
|---|---|
| Desktop mode scope | **Per tab, not remembered.** A new tab always starts in the mobile layout. |
| Text size scope | **One level for the whole app**, saved with the other settings, changeable from Settings and from the hub. |
| Autocomplete source | **Bookmarks and history on the device only.** Nothing typed is ever sent anywhere to get suggestions. |
| Find bar position | **Replaces the top bar**; the bottom bar is hidden while finding. |
| Design | **Waived as for Specs 020/021** (see Assumptions). |

## Clarifications

### Session 2026-10-11

- Q: When typing an address in an incognito tab, are suggestions from bookmarks and normal history shown? → A: Yes, both, exactly as in a normal tab; nothing typed in incognito is recorded.
- Q: From which typed character do suggestions start? → A: From the first character.
- Q: Which text size levels are offered? → A: Nine fixed levels — 50, 75, 90, 100, 110, 125, 150, 175 and 200%, default 100% — chosen from a list, not a slider.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a word on the page (Priority: P1)

A person reading a long page wants to jump to every place a word appears. They open the hub, tap **Find in page**, and the top bar turns into a find bar with the keyboard up. As they type, every match on the page is highlighted, the current match is scrolled into view, and the bar shows "current of total" (for example "3/12"). Up/down buttons move between matches. Closing the bar removes the highlights and brings the normal top and bottom bars back.

**Why this priority**: It is the page tool people use most, and the one a browser is most visibly missing without it.

**Independent Test**: Open a long article, find a word known to appear several times, step through all matches both ways, close the bar, and confirm no highlight remains.

**Acceptance Scenarios**:

1. **Given** a loaded page, **When** the person chooses Find in page from the hub, **Then** the hub closes, the find bar takes the top bar's place with the text field focused and the keyboard shown, and the bottom bar is hidden.
2. **Given** the find bar is open, **When** the person types a word that appears 12 times, **Then** all 12 are highlighted, the first is scrolled into view and marked as current, and the bar shows "1/12".
3. **Given** "1/12" is shown, **When** the person taps Next 12 times, **Then** the current match advances each time and wraps from 12 back to 1; Previous moves the other way and wraps from 1 to 12.
4. **Given** a word with no match, **When** it is typed, **Then** the bar shows "0/0", nothing is highlighted, and Next/Previous are disabled.
5. **Given** the find bar is open, **When** the person taps Close or presses system Back, **Then** the highlights are cleared, the find bar closes, and the normal top and bottom bars return. Back closes the find bar *before* it navigates the page back.
6. **Given** the find bar is open, **When** the page navigates (a link, Back/Forward, Reload) or the person switches tab, **Then** the find bar closes and its highlights are cleared.

---

### User Story 2 - Get suggestions while typing an address (Priority: P1)

A person starts typing in the address bar and sees, below it, up to six pages they have bookmarked or visited whose title or address contains what they typed. Tapping one opens it. None of what they type leaves the device.

**Why this priority**: It saves typing on every revisit and is the most frequent interaction in the app. It also has the strictest privacy rule in this spec.

**Independent Test**: With a few bookmarks and a history, type part of a known title and part of a known address; confirm the right entries appear, bookmarks first, and that tapping one loads it. Monitor network traffic while typing and confirm no request carries the typed text.

**Acceptance Scenarios**:

1. **Given** a bookmark "Kotlin docs" and history entries containing "kotlin", **When** the person types "kot", **Then** a list of at most 6 suggestions appears, bookmarks first, then history entries from the most recently visited, each showing its title and address.
2. **Given** a page that is both bookmarked and in history, **When** it matches, **Then** it appears once, as the bookmark.
3. **Given** suggestions are shown, **When** the person taps one, **Then** that address loads in the current tab, the suggestions close, and the address bar leaves editing mode exactly as it does after submitting.
4. **Given** suggestions are shown, **When** the person submits the typed text instead (keyboard Go), **Then** the typed text is handled exactly as before this spec (address or search); no suggestion is silently substituted.
5. **Given** the typed text contains `%`, `_`, `'` or `\`, **When** suggestions are computed, **Then** each character matches only itself.
6. **Given** nothing matches, or the field is empty, **When** the person types, **Then** no suggestion list is shown.
7. **Given** any typing in the address bar, **When** network traffic is observed, **Then** no request containing the typed text is made until the person submits or taps a suggestion.

---

### User Story 3 - Share the page (Priority: P2)

A person wants to send the page they are reading. They tap **Share** in the hub and the phone's share sheet opens with the page's address, and its title where the target app uses one.

**Why this priority**: Very common, and simple to deliver.

**Independent Test**: Share a page to a notes or messaging app and confirm the received text is exactly the page address.

**Acceptance Scenarios**:

1. **Given** a loaded page, **When** the person taps Share, **Then** the system share sheet opens with the page's current address as the text and its title as the subject.
2. **Given** a page with no title, **When** shared, **Then** the address is shared with no subject.
3. **Given** an incognito tab, **When** the person taps Share, **Then** sharing works the same way. It is an explicit action by the person, and the app records nothing about it.

---

### User Story 4 - Make text bigger or smaller everywhere (Priority: P2)

A person who finds web text too small (or too large) picks a text size once, and every page uses it from then on.

**Why this priority**: Accessibility value for a minority who badly need it; low effort.

**Independent Test**: Set text size to 150%, open three different sites in two tabs, confirm body text is enlarged on each; relaunch the app and confirm 150% is still in effect.

**Acceptance Scenarios**:

1. **Given** Settings, **When** the person opens the new **Text size** row, **Then** a chooser lists 50%, 75%, 90%, 100%, 110%, 125%, 150%, 175% and 200% with the current level selected; 100% is the default.
2. **Given** the hub on a web page, **When** the person taps the **Text size** tile, **Then** the same chooser opens and the tile shows the current level.
3. **Given** a new level is chosen, **When** the chooser closes, **Then** every open tab shows the new size without being reloaded, and pages opened afterwards use it too.
4. **Given** a chosen level, **When** the app is closed and reopened, **Then** the level is unchanged.
5. **Given** an incognito tab, **Then** it uses the same level as every other tab.

---

### User Story 5 - Ask for the desktop version of a site (Priority: P2)

A site shows a cut-down mobile page. The person taps **Desktop site** in the hub; the tab reloads and the site serves its desktop layout. The tile shows as switched on for that tab only.

**Why this priority**: Fixes a real class of broken sites, but is used occasionally.

**Independent Test**: Open a site that serves a different desktop layout, switch Desktop site on, confirm the desktop layout; open a new tab and confirm it gets the mobile layout; switch back and confirm the first tab is still in desktop mode.

**Acceptance Scenarios**:

1. **Given** a web page in a tab in mobile mode, **When** the person turns Desktop site on, **Then** the tab reloads the current address identifying itself as a desktop browser, the page is laid out at desktop width, and the hub tile shows as on.
2. **Given** a tab in desktop mode, **When** the person turns Desktop site off, **Then** the tab reloads as a mobile browser and the tile shows as off.
3. **Given** a tab in desktop mode, **When** the person follows links within it, **Then** they stay in desktop mode.
4. **Given** one tab in desktop mode, **When** the person opens or switches to another tab, **Then** that tab is in its own mode, and a newly created tab (including a link from another app, a pop-up or "open in new tab") is in mobile mode.
5. **Given** a tab in desktop mode, **When** the app's process is restarted, **Then** that tab comes back in mobile mode (desktop mode is not remembered).

---

### User Story 6 - Print or save the page as PDF (Priority: P3)

A person wants a copy of a receipt or an article. They tap **Print** in the hub and the phone's print screen opens, where they can pick a printer or "Save as PDF".

**Why this priority**: Occasional use; the system provides almost all of the experience.

**Independent Test**: Print a loaded page to "Save as PDF", open the saved PDF and confirm it contains the page's content.

**Acceptance Scenarios**:

1. **Given** a fully loaded page, **When** the person taps Print, **Then** the system print screen opens for that page, with the page title (or the hostname when there is no title) as the job and suggested file name.
2. **Given** the person cancels in the print screen, **Then** they return to the page unchanged.
3. **Given** an incognito tab, **When** the person prints, **Then** printing works; the app keeps no record of the job, and the file goes only where the person chooses.

---

### Edge Cases

- **Home and the incognito start page.** There is no web page, so only Text size is offered (FR-002).
- **Page still loading.** Find, Share and Desktop site work during loading; find results grow as content arrives. Print is offered only once the page has finished loading.
- **Error page.** When a load failed, Share and Desktop site stay available (the address is meaningful and a desktop request may succeed); Find and Print are not offered.
- **Find + rotation or language change.** The find bar, its text and the current match survive a rotation and an in-app language change.
- **Find + new search text.** Changing the text restarts the count from the first match.
- **Very long find text.** Find text is capped at 200 characters, like history search.
- **Find while the hub or a dialog opens.** A page dialog (Spec 021) shown while finding appears above the find bar; the find bar stays open.
- **Autocomplete in an incognito tab.** Suggestions still come from the person's bookmarks and normal history (reading local data sends nothing anywhere); nothing typed in incognito is recorded.
- **Autocomplete with very large history.** Suggestions stay responsive with 10,000 history entries (see SC-003).
- **Autocomplete when the address bar shows the current page.** Focusing the bar without typing shows no suggestions; they appear only once the text is changed.
- **Desktop mode on a pop-up tab (Spec 021).** A pop-up starts in mobile mode, like any new tab.
- **Desktop mode while loading.** Turning it on or off stops the current load and reloads the address.
- **Share target missing.** If no app can receive the share, the system's own message is shown; the app adds nothing.
- **Print with no print service.** The system print screen always offers "Save as PDF"; the app does not handle missing printers itself.
- **Text size and page zoom.** Text size changes text only; pinch-zoom continues to work independently.

## Requirements *(mandatory)*

### Functional Requirements

**Hub entry points**

- **FR-001**: While a web page is shown, the hub MUST offer Find in page, Share, Desktop site, Text size and Print, each reachable in at most two taps from the page (open hub, tap tool).
- **FR-002**: On Home and the incognito start page, the hub MUST NOT offer Find in page, Share, Desktop site or Print; Text size MUST remain available.
- **FR-003**: The Desktop site tile MUST show whether the current tab is in desktop mode; the Text size tile MUST show the current level.
- **FR-004**: Choosing a tool MUST close the hub before the tool appears.

**Find in page**

- **FR-005**: Opening Find in page MUST replace the top bar with a find bar containing a text field, a "current/total" match count, Previous, Next and Close, and MUST hide the bottom bar until the find bar closes.
- **FR-006**: The field MUST be focused with the keyboard shown when the bar opens.
- **FR-007**: Every match MUST be highlighted as the person types, case-insensitively, with the current match visibly distinct and scrolled into view.
- **FR-008**: Next and Previous MUST move the current match and wrap at both ends; both MUST be disabled when there are no matches.
- **FR-009**: With no matches the count MUST read "0/0"; with an empty field no count is shown and nothing is highlighted.
- **FR-010**: Close and system Back MUST close the find bar and clear all highlights. While the find bar is open, Back MUST close it rather than navigate.
- **FR-011**: Navigation in the tab (link, Back, Forward, Reload, a new address) and switching tab MUST close the find bar and clear its highlights.
- **FR-012**: The find bar, its text and current match MUST survive rotation and an in-app language change.
- **FR-013**: Find text MUST be limited to 200 characters.

**Autocomplete**

- **FR-014**: While the person edits the address bar text, or the Home page's search field, from the first typed character on, the app MUST show up to 6 suggestions whose title or address contains the typed text, case-insensitively.
- **FR-015**: Suggestions MUST come only from data already on the device: bookmarks and history. The app MUST NOT send the typed text, or anything derived from it, over the network to produce suggestions.
- **FR-016**: Bookmark matches MUST come before history matches; history matches MUST be ordered by most recent visit; an address that appears in both MUST appear once, as the bookmark; one address MUST NOT appear twice.
- **FR-017**: Every typed character MUST match only itself; no character acts as a wildcard.
- **FR-018**: Each suggestion MUST show the page title (or the hostname when the title is empty) and the address.
- **FR-019**: Tapping a suggestion MUST load its address in the current tab and leave the address bar's editing mode as submitting does.
- **FR-020**: Submitting the typed text MUST behave exactly as before this spec; the app MUST NOT replace the typed text with a suggestion.
- **FR-021**: No suggestions MUST be shown when the field is empty, unchanged from the current page's address, or nothing matches.
- **FR-022**: Suggestions MUST be offered in incognito tabs from the same bookmarks and normal history as in a normal tab, and typing in an incognito tab MUST NOT be recorded anywhere.

**Share**

- **FR-023**: Share MUST open the system share sheet with the current page address as the shared text and the page title, when present, as the subject.
- **FR-024**: Share MUST work in incognito tabs, and the app MUST NOT record what was shared or where.

**Desktop site**

- **FR-025**: Turning Desktop site on MUST reload the current tab's address while identifying the browser to sites as a desktop browser and laying the page out at desktop width; turning it off MUST reload as a mobile browser.
- **FR-026**: Desktop mode MUST belong to one tab: it MUST persist across navigation within that tab and across tab switches, and MUST NOT affect any other tab.
- **FR-027**: Every newly created tab MUST start in mobile mode, whatever its origin (new tab, link from another app, pop-up, open in new tab).
- **FR-028**: Desktop mode MUST NOT be saved; after the app process restarts every tab is in mobile mode.

**Text size**

- **FR-029**: The app MUST offer the text size levels 50%, 75%, 90%, 100%, 110%, 125%, 150%, 175% and 200%, with 100% as the default.
- **FR-030**: Settings MUST gain a **Text size** row showing the current level that opens the level chooser; the hub's Text size tile MUST open the same chooser.
- **FR-031**: A chosen level MUST apply to every open tab, normal and incognito, without reloading, and to every page opened afterwards.
- **FR-032**: The level MUST be saved and restored across app restarts, and MUST NOT be reset by Clear browsing data.

**Print**

- **FR-033**: Print MUST open the system print screen for the current page once it has finished loading, with the page title, or the hostname when there is no title, as the job name.
- **FR-034**: Cancelling the print screen MUST return to the page unchanged; the app MUST NOT keep a record of print jobs.

**Cross-cutting**

- **FR-035**: Every new piece of text MUST be translated into all 8 supported languages.
- **FR-036**: Every new control MUST have a screen-reader label, and the find count MUST be announced when it changes.
- **FR-037**: The feature MUST NOT add any permission and MUST NOT change the database schema.

### Key Entities

- **Find session**: belongs to the current tab while open — the find text, the total number of matches and the current match position. Never saved.
- **Tab page mode**: per tab, mobile or desktop. Held only while the app process lives.
- **Text size level**: one app-wide value from the fixed list of levels, saved with the other settings.
- **Suggestion**: a page address with its title and its source (bookmark or history), produced on demand from existing bookmarks and history. Never saved.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Each of the five hub tools is reachable from a loaded page in at most 2 taps.
- **SC-002**: On a page where a word appears 50 times, the count reads "1/50" and all 50 matches are highlighted within 1 second of the last keystroke.
- **SC-003**: With 10,000 history entries and 500 bookmarks, on the project's reference emulator: every typed character appears in the field within 100 ms (Constitution §V), no keystroke is dropped, and the suggestion list reflects the new text within 200 ms.
- **SC-004**: While typing 20 different texts into the address bar with traffic monitored, 0 network requests contain the typed text.
- **SC-005**: Changing text size takes effect on all open tabs within 1 second, with 0 page reloads.
- **SC-006**: On 5 sites known to serve a different desktop layout, turning Desktop site on yields the desktop layout on 5/5, and a new tab opened afterwards yields the mobile layout on 5/5.
- **SC-007**: Printing 3 different pages to "Save as PDF" produces 3 PDFs that contain the pages' visible text.
- **SC-008**: All new text is present in all 8 languages, with no truncation at 360dp width.
- **SC-009**: The release app grows by no more than 100 KB, and no native library is added.

## Assumptions

- **Design waiver (owner decision, 2026-10-11, as for Specs 020 and 021)**: Constitution §X's "Design before spec" rule is waived because Claude Design is not available on the owner's account. The find bar, the suggestion list, the new hub tiles and the Text size row and chooser are built from existing Spec 019 design-system components (the top bar, `QuickTile`, the shared choice dialog, Settings rows, list rows) and tokens. The owner will supply designs later; a later visual change MUST NOT change the behaviour specified here.
- Find in page matching is case-insensitive and otherwise follows the web engine's own text matching.
- "Desktop width" is whatever the web engine uses for a desktop-identified page; no specific pixel width is promised.
- Text size scales page text only. It does not change the app's own interface, which already follows the system font size.
- History search (Spec 014) waits for two characters; suggestions deliberately do not, because they are capped at 6.
- Depends on Spec 019 (hub, top and bottom bars, Home), Spec 021 (pop-up tabs, page dialogs), Spec 013/014 (bookmarks and history as the suggestion sources) and Spec 016 (Settings).
- **Out of scope**: search-engine suggestions, inline auto-fill of the address, per-site text size or desktop mode, reading mode, translating pages, find across tabs, removing a suggestion from the list. Any of these needs its own roadmap entry (§X).
