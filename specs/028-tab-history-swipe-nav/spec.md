# Feature Specification: Tab History and Swipe Navigation

**Feature Branch**: `028-tab-history-swipe-nav`
**Created**: 2026-10-16
**Status**: Draft
**Input**: User description: "Keep each tab's back/forward history across tab switches and app restarts, as DuckDuckGo does; swipe inside the page to go back and forward."

## Context

Today a tab remembers only its last address.

- **Switching tabs** shows the page again, but Back has nothing to go to.
- **Reopening the app** is the same. The owner's report: visit YouTube, then Facebook, close the app, reopen it. Only Facebook is there, and Back does nothing.
- **Gestures.** The system edge gesture only goes back, and on Android both edges mean Back. Going forward is only possible from the hub menu.

## Clarifications

### Session 2026-10-16

- Q: When browsing history is cleared, should open tabs also lose Back/Forward to pages visited before the clear? → A: Yes. Every open tab's back and forward is emptied; each tab keeps the page it is showing.
- Q: Should there be a setting to turn off the in-page swipe gesture? → A: Yes. A switch in Settings, on by default.
- Q: Should a tab's saved back/forward be trimmed by the history retention period? → A: No. It lives as long as the tab and goes only on tab close, Fire or a history clear.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Back still works after reopening the app (Priority: P1)

The person browses several pages in a tab and leaves the app. Later they come back. The app may have been closed by them or ended by the system. The tab shows the last page, and Back walks through the earlier pages of that tab as before. Forward works too, if they had gone back before leaving.

**Why this priority**: This is the reported defect. It breaks the most basic browser expectation and loses the person's place.

**Independent Test**: Open three pages in a tab and close the app from Recents. Reopen it and press Back twice. The second and then the first page appear.

**Acceptance Scenarios**:

1. **Given** a tab that visited pages A, B and C, **When** the app is closed from Recents and reopened, **Then** the tab shows C, Back shows B, and Back again shows A.
2. **Given** a tab at B after going back from C, **When** the app is reopened, **Then** the tab shows B and Forward shows C.
3. **Given** a tab whose pages were visited, **When** the system ends the app in the background and the person returns, **Then** Back and Forward work as in scenario 1.
4. **Given** several tabs, each with its own pages, **When** the app is reopened, **Then** each tab has its own history, not another tab's.

---

### User Story 2 - Switching tabs keeps each tab's history (Priority: P1)

The person moves between tabs. Each tab comes back with its own Back and Forward exactly as it was left.

**Why this priority**: The same loss happens on every tab switch, which is far more frequent than reopening the app.

**Independent Test**: Visit A then B in tab 1, and X in tab 2. Switch back to tab 1 and press Back. A appears.

**Acceptance Scenarios**:

1. **Given** tab 1 at B after A, **When** the person switches to tab 2 and back to tab 1, **Then** tab 1 shows B and Back shows A.
2. **Given** tab 1 at A after going back from B, **When** the person switches away and back, **Then** Forward shows B.
3. **Given** an incognito tab with several pages, **When** the person switches to a normal tab and back, **Then** the incognito tab's Back and Forward still work.

---

### User Story 3 - Saved history disappears with the browsing data it belongs to (Priority: P1)

What a tab remembers is browsing data, so it follows the same privacy rules as history.

- Incognito tabs never leave it on the device.
- Closing a tab removes that tab's saved history.
- Fire and clearing history remove all saved history.

**Why this priority**: The app promises privacy. Storing navigation on the device without these rules would break that promise.

**Independent Test**: Browse two pages in an incognito tab and close the app. Inspect the device's app data: no trace of those pages. Close a normal tab: its saved history is gone.

**Acceptance Scenarios**:

1. **Given** an incognito tab that visited pages, **When** the app's stored data is inspected, at any time and also after the app closes, **Then** no record of those pages exists on the device.
2. **Given** incognito tabs, **When** the app is closed and reopened, **Then** no incognito history is offered, because the tabs are gone as they are today.
3. **Given** a normal tab with saved history, **When** the person closes the tab, **Then** its saved history is deleted.
4. **Given** normal tabs with saved history, **When** the person uses Fire, **Then** all saved tab history is deleted.
5. **Given** normal tabs with history, **When** the person clears browsing history, **Then** the saved history is deleted, and every open tab still shows its page but has nothing behind or ahead of it.

---

### User Story 4 - Swipe inside the page to go back and forward (Priority: P2)

The person drags sideways across the page. An arrow appears and grows with the drag. Releasing past the point where the arrow is full goes back or forward. Releasing earlier does nothing.

In a left-to-right language:
- Dragging toward the right goes back.
- Dragging toward the left goes forward.

Right-to-left languages mirror this.

**Why this priority**: The owner asked for swipe forward. It adds convenience but no new capability, since Back and Forward already exist.

**Independent Test**: In a tab with a page behind and a page ahead, drag right across the page. The previous page appears. Then drag left. The next page appears.

**Acceptance Scenarios**:

1. **Given** a page with a previous page, **When** the person drags toward the back direction past the threshold and releases, **Then** the previous page appears.
2. **Given** a page with a next page, **When** the person drags toward the forward direction past the threshold and releases, **Then** the next page appears.
3. **Given** any page, **When** the person releases before the threshold, **Then** nothing changes and the arrow goes away.
4. **Given** a page with no next page, **When** the person drags toward forward, **Then** the arrow shows that forward is not available and releasing does nothing.
5. **Given** a page that is wider than the screen and not scrolled to its edge, **When** the person drags sideways, **Then** the page scrolls and no arrow appears. The arrow appears only once the page is at its edge in that direction.
6. **Given** a page element that moves with sideways drags itself, such as a carousel or a map, **When** the person drags it, **Then** the element moves and no arrow appears.
7. **Given** the system Back gesture or the Back button, **When** it is used, **Then** it behaves exactly as today.
8. **Given** swipe navigation is turned off in Settings, **When** the person drags sideways at the page's edge, **Then** no arrow appears and nothing navigates.

---

### Edge Cases

- **Saved history that cannot be read**, for example after a system or web engine update. The tab loads its last address as it does today, and shows no error.
- **A very long history.** Only the most recent part is kept, up to a fixed size per tab. The oldest pages go first, and the current page always stays.
- **The app ends during a page load.** Reopening offers the history as of the last finished load. At most the page that was loading is missing.
- **A page reached by submitting a form.** Going back to it follows the web engine's rules: it is not submitted again silently.
- **Offline after reopening.** The tab's history is still there. Pages that need the network show the usual error page.
- **A tab opened by a page (pop-up).** Its history starts at the page it opened with, as today.
- **A tab showing Home or the incognito start page.** No swipe arrow appears there. Back to Home keeps its current rules.
- **While the address bar or the find bar is in use.** Swiping does not start; vertical scrolling and pinch-zoom are unchanged.
- **Mostly vertical drags.** A drag that is mostly vertical is a scroll and never shows the arrow.

## Requirements *(mandatory)*

### Functional Requirements

**Tab history (Stories 1–3)**

- **FR-001**: Each normal tab's back and forward history MUST survive switching tabs, leaving and reopening the app, and the system ending the app in the background.
- **FR-002**: After the app reopens, a tab MUST show its last page with its previous and next pages reachable by Back and Forward. This applies to every normal tab the person returns to, not only the one shown first.
- **FR-003**: A tab's saved history MUST be bounded by a fixed size. Over the bound, the oldest pages are dropped first. The current page and the pages ahead of it are kept.
- **FR-004**: Saved history MUST be refreshed after every finished page load, when the person switches away from the tab, and when the app goes to the background.
- **FR-005**: When a tab's saved history is missing or cannot be read, the tab MUST load its last address as it does today, without an error message.
- **FR-006**: Incognito tabs MUST keep their back and forward history across tab switches while the app runs, and MUST NOT write it to device storage at any time.
- **FR-007**: Closing a tab MUST delete its saved history. Closing all tabs MUST delete all of it.
- **FR-008**: Fire MUST delete all saved tab history.
- **FR-009**: Clearing browsing history MUST delete all saved tab history and empty every open tab's back and forward, normal and incognito. Each tab keeps the page it is showing.
- **FR-010**: Saved tab history MUST stay inside the app's private storage, and MUST be excluded from device backups like the rest of the browsing data.
- **FR-010a**: The history retention period MUST NOT trim a tab's saved history. It lives as long as the tab and is removed only as FR-007 to FR-009 say.

**Swipe navigation (Story 4)**

- **FR-011**: A sideways drag inside the page area MUST show a direction arrow that follows the drag, once the page cannot scroll further in that direction.
- **FR-012**: Releasing past the threshold MUST go back or forward. Releasing before it MUST do nothing.
- **FR-013**: Direction MUST follow reading direction. In left-to-right languages, dragging right is back and dragging left is forward; right-to-left languages mirror this.
- **FR-014**: When there is no page in the drag's direction, the arrow MUST show that the move is unavailable, and releasing MUST do nothing.
- **FR-015**: The swipe MUST NOT start when:
  - the drag is mostly vertical;
  - the page or one of its elements uses the sideways drag;
  - the address bar or the find bar is in use;
  - the tab shows Home or the incognito start page.
- **FR-016**: The swipe MUST work the same with gesture navigation and with the three-button navigation bar. The system Back gesture and the Back button MUST keep their current behaviour.
- **FR-017**: The arrow MUST have an accessible description of the action it performs. Back and Forward MUST remain available from the hub for people who do not use gestures.
- **FR-018**: Settings MUST offer a switch to turn swipe navigation on or off. It is on by default and remembered across restarts. While it is off, sideways drags behave exactly as before this feature.

### Key Entities

- **Saved tab history**: what one normal tab remembers about where it has been. It holds:
  - the pages behind and ahead of the current one, in order;
  - which of them is current;
  - when it was last saved.

  It is opaque to the app. It belongs to exactly one tab, is bounded in size, and is deleted with that tab and with Fire or a history clear. Incognito tabs have no saved tab history.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a walk on a test device, after closing the app from Recents and after forcing the system to end it, each of 3 tabs with 5 visited pages goes back through all 5 pages. This MUST hold 100% of the time.
- **SC-002**: After 10 switches between 3 tabs, every tab's Back and Forward reach the same pages as before the switches. This MUST hold 100% of the time.
- **SC-003**: After browsing in incognito and after closing the app, the app's stored data holds no trace of incognito pages. The check finds 0 matches.
- **SC-004**: After closing a tab, using Fire, or clearing history, saved tab history for the affected tabs is 0.
- **SC-005**: One tab's saved history never exceeds 256 KB, however long the tab has been used.
- **SC-006**: Opening the app and showing the last tab is not measurably slower than today. The median cold start is within 5% of the previous release on the same device.
- **SC-007**: In a walk of 20 deliberate swipes each way on pages with history, at least 19 go back or forward as intended.
- **SC-008**: In 50 ordinary vertical scrolls and in 10 drags on a carousel and on a map, the swipe arrow never appears.

## Assumptions

- **Scroll position and form text.** Restoring a page may or may not keep them. The tab returns to the right page, and anything beyond that is best effort, decided by the web engine.
- **The existing tab limit** bounds the total size of saved history: the tab limit times the per-tab bound in SC-005.
- **Approach.** The approach follows DuckDuckGo's Android browser: the web engine's own navigation state is kept per tab on the device, and the tab falls back to its last address when that state cannot be used.
- **Back to Home.** A page opened from Home keeps its current Back-to-Home rule while the app runs. That rule is not restored after the app reopens.
