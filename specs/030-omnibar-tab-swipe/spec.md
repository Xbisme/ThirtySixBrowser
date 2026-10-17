# Feature Specification: Swipe the Address Bar to Switch Tabs

**Feature Branch**: `030-omnibar-tab-swipe`
**Created**: 2026-10-17
**Status**: Draft
**Input**: User description: "Swipe left/right on the browser's address bar to switch to the previous/next tab, like DuckDuckGo and Safari on mobile. The neighbouring tab's address and preview follow the finger; releasing past a threshold switches, otherwise it snaps back. Works in normal and incognito, but never crosses between normal and incognito tabs. Must not clash with the in-page swipe back/forward from spec 028 or with tapping the address bar to edit. Respects reduced motion and TalkBack (an accessible alternative). Settings switch, on by default."

## Context

Today switching tabs takes three steps: open the Tabs screen, find the card, tap it. DuckDuckGo and Safari let the person slide the address bar sideways to reach the neighbouring tab in one move.

What the app has now:

- **The address pill** sits in the top bar on web pages. Home and the incognito start page show their own search field instead of the pill.
- **The bottom bar** holds Home, Search, New tab, Tabs and Menu.
- **Both bars** slide away while the page scrolls and come back on scroll up, a new load or an error.
- **Tab order.** The Tabs screen lists tabs with the most recently used first. Switching to a tab moves it to the front, so "the next tab" changes on every switch.
- **Previews.** Normal tabs have a saved picture of their page. Incognito tabs never have one.
- **Swipe in the page** (Spec 028) goes back and forward when the page is at its edge. It has its own switch in Settings.

## Clarifications

### Session 2026-10-17

- Q: Where does the swipe start: the address pill at the top, the bottom bar, or both? → A: The address pill at the top, and on Home and the incognito start page the search field that stands in its place. The bottom bar is unchanged.
- Q: Which order do "previous" and "next" follow? → A: The order tabs were opened in, for the swipe and for the Tabs screen alike. The Tabs screen stops putting the most recently used tab first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Swipe to the neighbouring tab (Priority: P1)

The person has several tabs open. They drag the address bar sideways. The current tab's address and page slide with the finger, and the next tab's address and preview slide in behind them. Releasing past the threshold makes the next tab current. Releasing earlier slides everything back.

In a left-to-right language, dragging toward the left shows the next tab and dragging toward the right shows the previous one. Right-to-left languages mirror this.

**Why this priority**: This is the feature. It turns a three-step switch into one move.

**Independent Test**: Open three tabs A, B, C and show B. Drag the address bar left past the threshold and release. C becomes current. Drag right twice. B and then A become current.

**Acceptance Scenarios**:

1. **Given** tab B with tab C after it, **When** the person drags the address bar toward "next" past the threshold and releases, **Then** C becomes the current tab, showing its page with its own Back and Forward.
2. **Given** tab B with tab A before it, **When** the person drags toward "previous" past the threshold and releases, **Then** A becomes current.
3. **Given** any tab, **When** the person releases before the threshold, **Then** the address bar and page slide back and the same tab stays current.
4. **Given** the first or the last tab, **When** the person drags toward the side with no tab, **Then** the bar moves only a little, resists, and slides back. No tab changes.
5. **Given** only one tab is open, **When** the person drags the address bar sideways, **Then** the bar resists and slides back.
6. **Given** a fast flick toward a side with a tab, **When** the flick is short but quick, **Then** it switches as if the threshold had been passed.
7. **Given** the person swipes three times toward "next" from the first of four tabs, **When** each swipe completes, **Then** the second, third and fourth tabs become current in turn. The order does not change because of the swipes.
8. **Given** tabs A, B, C opened in that order, **When** the person opens the Tabs screen after any number of switches, **Then** the cards are in the order A, B, C, and the current tab is marked, not moved.
9. **Given** tabs A, B, C, **When** the person opens a new tab, **Then** it goes after C, and "next" from C reaches it.

---

### User Story 2 - Normal and incognito stay apart (Priority: P1)

Swiping moves only among tabs of the same kind as the current one. From a normal tab it reaches normal tabs; from an incognito tab it reaches incognito tabs.

**Why this priority**: Crossing into or out of incognito by accident would show private pages in a normal context, or the reverse. The app's privacy promise rules that out.

**Independent Test**: Open normal tabs N1, N2 and incognito tab I1. From N2, swipe toward the side where I1 would be. Nothing changes. From I1, swipe either way. Nothing changes.

**Acceptance Scenarios**:

1. **Given** a normal tab, **When** the person swipes, **Then** only normal tabs can become current.
2. **Given** an incognito tab, **When** the person swipes, **Then** only incognito tabs can become current.
3. **Given** the only incognito tab, **When** the person swipes, **Then** it resists as in Story 1 scenario 5.
4. **Given** an incognito neighbour, **When** it slides in, **Then** it shows the incognito surface, never a picture of its page and never another tab's preview.

---

### User Story 3 - The address bar still works as before (Priority: P1)

Tapping the address bar still opens it for typing. Scrolling, pinch-zoom and the in-page swipe from Spec 028 behave as today.

**Why this priority**: The address bar is the most used control in the app. A gesture that steals taps or edits would be worse than having no gesture.

**Independent Test**: Tap the address bar 20 times; it opens for typing every time. Drag across the page at its edge; it goes back as in Spec 028, and the tab does not change.

**Acceptance Scenarios**:

1. **Given** the address bar is not being edited, **When** the person taps it, **Then** it opens for typing as today, and no tab changes.
2. **Given** the address bar is being edited, **When** the person drags sideways in it, **Then** the text cursor or selection moves as today and no tab changes.
3. **Given** a sideways drag that starts in the page, **When** it is released, **Then** only Spec 028's back and forward can happen, never a tab switch.
4. **Given** a mostly vertical drag on the address bar, **When** it is released, **Then** nothing switches.
5. **Given** the find bar is open, **When** the person drags, **Then** no tab switch starts.

---

### User Story 4 - Everyone can switch tabs, and the setting can turn it off (Priority: P2)

People who use TalkBack, Switch Access or a keyboard can reach the next and previous tab without the gesture. People who find the gesture gets in the way can turn it off. When the system asks for reduced motion, the switch happens without the slide.

**Why this priority**: The gesture is an extra path. The Tabs screen stays available to everyone, so this story adds parity, not core value.

**Independent Test**: With TalkBack on, focus the address bar and open its actions. "Next tab" and "Previous tab" are listed and work. Turn the setting off. Dragging the address bar sideways does nothing.

**Acceptance Scenarios**:

1. **Given** TalkBack is on and a neighbouring tab exists, **When** the person opens the address bar's actions, **Then** "Next tab" and "Previous tab" are offered, and each one switches and announces the new tab's title.
2. **Given** there is no tab on one side, **When** the actions are read, **Then** the action for that side is not offered.
3. **Given** the swipe setting is off, **When** the person drags the address bar sideways, **Then** nothing moves and no tab changes. The accessibility actions remain.
4. **Given** the system's animations are off or reduced, **When** a swipe passes the threshold, **Then** the new tab appears at once with no slide, and a release before the threshold returns at once.
5. **Given** a fresh install, **When** Settings is opened, **Then** the swipe-between-tabs switch is on.

---

### Edge Cases

- **The bars are hidden by scrolling.** The swipe needs the address bar on screen. Scrolling up brings it back, as today.
- **A tab is closed or opened during a drag**, for example by a page. The drag ends without switching, and the bar slides back.
- **The neighbouring tab has no preview yet**, such as a tab restored after a restart that has not loaded. It slides in with the same placeholder the Tabs screen uses.
- **The neighbouring tab is Home or the incognito start page.** It slides in showing that page's look. After the switch the tab shows Home, as it does when picked from the Tabs screen.
- **A switch while the current page is loading.** The current tab's load continues as it would after a switch from the Tabs screen.
- **The keyboard is open** because the address bar is being edited. No tab swipe starts.
- **Two fingers.** A second finger cancels the swipe.
- **Rotation or window resize during a drag.** The drag ends without switching.

## Requirements *(mandatory)*

### Functional Requirements

**The gesture**

- **FR-001**: A sideways drag on the address bar MUST move the current tab's address and page with the finger and bring the neighbouring tab in from the side the finger moves away from.
- **FR-002**: Releasing past the threshold, or flicking quickly toward a side that has a tab, MUST make that tab current. Releasing otherwise MUST return to the current tab.
- **FR-003**: Direction MUST follow reading direction. In left-to-right languages, dragging left shows the next tab and dragging right the previous one; right-to-left languages mirror this.
- **FR-004**: When there is no tab on the dragged side, the bar MUST resist and return, and no tab changes.
- **FR-005**: "Previous" and "next" MUST follow the order the tabs were opened in. Switching tabs, by swipe or otherwise, MUST NOT change that order, so repeated swipes in one direction walk through every tab of that kind.
- **FR-005a**: The Tabs screen MUST list tabs in the same order, oldest opened first, with the current tab marked in place. A new tab, including one opened by a page, MUST go at the end.
- **FR-005b**: The order MUST survive restarts for normal tabs. Closing a tab MUST leave the others in their order.
- **FR-006**: A swipe MUST move only between tabs of the same kind as the current tab: normal to normal, incognito to incognito.
- **FR-007**: A tab reached by swiping MUST behave exactly like one picked from the Tabs screen: its page, its Back and Forward, and its place as the most recently used tab.

**What it must not disturb**

- **FR-008**: A tap on the address bar MUST open it for typing as today. A drag on the bar MUST start a tab swipe only after it has clearly moved sideways, and MUST NOT open the bar for typing.
- **FR-009**: The tab swipe MUST NOT start while the address bar is being edited, while the find bar is open, for a mostly vertical drag, or for a drag that starts outside the address bar.
- **FR-010**: Spec 028's in-page swipe, vertical scrolling, pinch-zoom and the system Back gesture MUST keep their current behaviour.
- **FR-011**: A second finger, a tab list change, or a rotation during a drag MUST end the drag without switching.

**Previews and privacy**

- **FR-012**: The incoming normal tab MUST show its saved preview or, without one, the Tabs screen's placeholder. The incoming incognito tab MUST show the incognito surface and never a page picture.
- **FR-013**: The feature MUST NOT store anything new about incognito tabs and MUST NOT add network requests.

**Access and settings**

- **FR-014**: The address bar MUST offer "Next tab" and "Previous tab" accessibility actions when a tab of the same kind exists on that side. Using one MUST switch tabs and announce the new tab.
- **FR-015**: When the system's animations are off or reduced, the switch and the return MUST happen without sliding.
- **FR-016**: Settings MUST offer a switch to turn swiping between tabs on and off, separate from Spec 028's switch. It is on by default and remembered across restarts. While it is off, sideways drags on the address bar do nothing; the accessibility actions stay.
- **FR-017**: All new text MUST be available in the app's 8 languages.

### Key Entities

- **Tab order**: the sequence of tabs of one kind, in the order they were opened. "Previous" and "next" walk it, and the Tabs screen shows it. It changes only when tabs are opened or closed.
- **Swipe setting**: one on/off value for swiping between tabs, kept with the other browsing settings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a walk of 20 deliberate swipes each way across 4 tabs, at least 19 land on the intended tab.
- **SC-002**: In 20 taps on the address bar, it opens for typing 20 times and no tab changes.
- **SC-003**: In 20 in-page swipes at a page edge and 50 vertical scrolls, the tab never changes.
- **SC-004**: From 4 normal and 2 incognito tabs, no sequence of swipes ever crosses between the two kinds (0 crossings in 40 swipes).
- **SC-005**: The drag keeps up with the finger: on the reference device, at most 1% of frames are slow during a swipe.
- **SC-006**: Switching by swipe takes one gesture, against three steps through the Tabs screen.
- **SC-007**: A TalkBack user can move to the next and previous tab from the address bar without the Tabs screen.
- **SC-008**: Cold start stays within 5% of the previous release on the same device.

## Assumptions

- **Reference behaviour** is DuckDuckGo's and Safari's address-bar swipe on mobile: the bar slides, the neighbour comes in from the side, and the ends resist.
- **The threshold** is about a third of the bar's width, or a quick flick. The exact values are tuned during design and kept as named constants.
- **The address bar** in this spec means the pill at the top on web pages and the search field that Home and the incognito start page show in its place. The bottom bar gets no swipe.
- **No new tab is opened** by swiping past the last tab. Safari does that; this spec does not.
- **Tab order** changes in the Tabs screen as clarified; the current tab is still the one last used, and it is still the one shown when the app opens. Reordering tabs by hand is out of scope.
- **Design** builds from the Spec 019 components under the same design waiver as recent specs.
