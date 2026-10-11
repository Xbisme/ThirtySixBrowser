# Feature Specification: Web Capabilities

**Feature Branch**: `021-web-capabilities`
**Created**: 2026-10-11
**Status**: Draft
**Input**: User description: "Spec 021 `web-capabilities` — second spec of Constitution v1.5.0 Phase 6 'Real browser'. Make ordinary websites work the way they do in a mainstream browser: file upload, fullscreen video, popups and new windows, styled JavaScript dialogs, HTTP authentication, per-site camera / microphone / location permissions remembered per site with a Site permissions screen, and a long-press menu on links and images."

## Context

ThirtySix renders pages, but much of what a page asks the browser to do goes unanswered. A "Upload photo" button does nothing, YouTube's fullscreen button does nothing, "Sign in with Google" popups fail, Google Meet cannot reach the camera, maps cannot find the person's location, sites behind a password prompt cannot be reached, and long-pressing a link or picture does nothing. Every one of these works in a mainstream browser; their absence makes ThirtySix feel broken on ordinary sites. This spec answers those requests, while keeping the app's privacy promise: nothing about the sites visited or the choices made leaves the phone, and no password is ever stored.

## Clarifications

### Session 2026-10-11

- Q: When a site asks for camera, microphone or location, which choices should the dialog offer? → A: Three: "Always allow" (remembered), "Allow this time" (not remembered; ends when the person leaves the page) and "Don't allow" (remembered).
- Q: When "Open in new tab" is chosen from a link's long-press menu, how should the new tab open? → A: In the background; the current page stays, and a short notice offers "Switch" (FR-020 as written).
- Q: Should Clear browsing data also remove remembered site permissions? → A: Add a "Site permissions" category to the Clear browsing data dialog, unticked by default.
- Q: When a pop-up a page opened on its own is blocked, should the notice let the person open it? → A: Yes — the notice has an "Open" action that opens that blocked pop-up's address once in a new tab; nothing is remembered.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use a site's camera, microphone or location (Priority: P1)

A person joins a video call, uses a map or a voice search. The site asks for the camera, microphone or location; ThirtySix asks the person, in its own words, whether this site may use it. If they allow it, the site works; the next visit does not ask again. If they refuse, the site is told no and keeps working without it. They can review and change these choices later in Settings.

**Why this priority**: Calls, maps and voice input are among the most common reasons a page needs the browser, and today they fail silently. This is also the only part of the spec that adds permissions, so it carries the most privacy weight.

**Independent Test**: Open a camera test page, allow camera for that site, see the camera image; reload and see no new question; in Settings → Site permissions change the site to "Don't allow", reload, and see the site denied.

**Acceptance Scenarios**:

1. **Given** a site asks for the camera for the first time, **When** the request arrives, **Then** ThirtySix shows a dialog naming the site (its address) and "camera", with **Always allow**, **Allow this time** and **Don't allow**.
2. **Given** the person taps Always allow or Allow this time and the app does not yet have Android's camera permission, **When** the dialog closes, **Then** Android's own permission prompt appears; if granted there too, the site gets the camera.
3. **Given** the person tapped Always allow for a site earlier, **When** the same site asks again (same or a later visit), **Then** it gets the camera without any question.
3a. **Given** the person tapped Allow this time, **When** the same page asks again before they leave it, **Then** it gets the camera without a question; **When** they come back to the site later, **Then** they are asked again.
4. **Given** the person tapped Don't allow for a site earlier, **When** the site asks again, **Then** it is refused without any question.
5. **Given** the request comes from an incognito tab, **When** the dialog appears, **Then** it offers only **Allow this time** and **Don't allow**, and the answer applies to that tab's current page only and is never remembered.
6. **Given** Android's permission was permanently denied for the app, **When** the person taps Always allow or Allow this time, **Then** ThirtySix explains that the permission is off for the app and offers to open the app's system settings; the site is refused for now.
7. **Given** remembered choices exist, **When** the person opens Settings → Site permissions, **Then** each site is listed with its choices for camera, microphone and location, and each can be changed to Always allow or Don't allow, or removed so the site is asked again. ("Allow this time" choices are never listed — they are not remembered.)
8. **Given** a site asks for both camera and microphone at once, **When** the request arrives, **Then** one dialog names both, and the answer applies to both.

---

### User Story 2 - Upload files (Priority: P1)

A person taps "Upload photo" or "Attach file" on a site. The phone's file picker opens; the files they choose are sent to the page. A site that asks to take a photo directly offers the camera.

**Why this priority**: Forms, social sites, mail and document services all depend on file upload; without it a large share of everyday tasks is impossible.

**Independent Test**: On a page with a file input, pick one image — the page shows it; on an input that allows several files, pick three — the page receives three; cancel the picker — the page receives none and stays usable.

**Acceptance Scenarios**:

1. **Given** a page's file input, **When** the person taps it, **Then** the system file picker opens, limited to the file types the page accepts when it states them.
2. **Given** the input allows several files, **When** the person picks several, **Then** all of them reach the page.
3. **Given** the picker is open, **When** the person cancels, **Then** the page receives no file and can be used again immediately, including opening the picker again.
4. **Given** the page asks to capture a photo or video directly, **When** the person taps the input, **Then** they are offered the camera (through the phone's camera app) as well as choosing an existing file.
5. **Given** capture needs Android's camera permission and the person refuses it, **When** they come back, **Then** they can still choose an existing file.

---

### User Story 3 - Watch video fullscreen (Priority: P1)

A person taps a video's fullscreen button. The video fills the screen, edge to edge, with the browser bars and the phone's status and navigation bars hidden. Leaving fullscreen returns exactly to the page they were on.

**Why this priority**: Video is one of the most common things people do in a browser, and the fullscreen button doing nothing is immediately visible.

**Independent Test**: On a video site, enter fullscreen — video fills the screen, bars hidden; press Back — the page returns, same scroll position, video still where it was.

**Acceptance Scenarios**:

1. **Given** a playing video, **When** the person taps its fullscreen button, **Then** the video fills the whole screen and the browser bars and system bars are hidden.
2. **Given** the video is wider than it is tall, **When** it enters fullscreen, **Then** the screen turns to landscape; on leaving fullscreen it returns to the orientation the person had.
3. **Given** fullscreen, **When** the person presses Back or the page's exit button, **Then** fullscreen ends and the browser returns as it was: same tab, same scroll position, bars back, video continuing.
4. **Given** fullscreen, **When** the person swipes from the screen edge, **Then** the system bars appear temporarily and hide again, without leaving fullscreen.
5. **Given** fullscreen in an incognito tab, **When** it is shown, **Then** screenshots stay blocked as for any incognito content.

---

### User Story 4 - Long-press links and images (Priority: P2)

A person long-presses a link or a picture. A menu shows the address and offers what can be done with it: open it in a new tab or an incognito tab, copy or share its address, download it.

**Why this priority**: A habit everyone brings from other browsers and the main way to open several results at once; the page still works without it, so it ranks after the P1 stories.

**Independent Test**: Long-press a link → menu shows its address; Open in new tab → a new tab with that address appears and the current page stays. Long-press an image → Download image → it appears in Downloads.

**Acceptance Scenarios**:

1. **Given** a link, **When** the person long-presses it, **Then** a menu shows the link's address and offers Open in new tab, Open in incognito tab, Copy link address, Share link, Download link.
2. **Given** an image, **When** the person long-presses it, **Then** the menu shows the image's address and offers Open image in new tab, Download image, Copy image address, Share image link.
3. **Given** an image that is also a link, **When** long-pressed, **Then** the menu offers both groups, link actions first.
4. **Given** Open in new tab, **When** chosen, **Then** a new tab opens with that address **in the background**: the current page stays on screen, and a short notice offers to switch to it.
5. **Given** Open in incognito tab, **When** chosen, **Then** an incognito tab opens with that address, counted against the incognito tab limit (Spec 012).
6. **Given** Download link or Download image, **When** chosen, **Then** the download starts through the existing downloads feature (Spec 015) exactly as a download started by the page would.
7. **Given** Copy, **When** chosen, **Then** the address is on the clipboard and a short confirmation is shown.
8. **Given** the menu, **When** the person presses Back or taps outside it, **Then** it closes and nothing happens.

---

### User Story 5 - Popups and new windows (Priority: P2)

A person clicks "Sign in with Google" or a link that opens in a new window. The new window opens as a new tab, works, and — when the sign-in finishes and the site closes it — goes away, leaving them on the original page. Popups a site opens on its own, without the person doing anything, are blocked.

**Why this priority**: Sign-in flows and "open in new window" links are common, and today they silently fail; but most of the web works without them.

**Independent Test**: On a page, tap a link with a new-window target → a new active tab opens with it; on a sign-in demo, complete the popup flow → the popup tab closes and the original page is signed in; a page that opens a window on load → no tab is created.

**Acceptance Scenarios**:

1. **Given** a link or button that opens a new window after the person's tap, **When** tapped, **Then** a new tab opens with it and becomes active; it is incognito if, and only if, the page that opened it is incognito.
2. **Given** a page tries to open a window without any tap from the person, **When** it tries, **Then** no tab is created and a short notice says a pop-up was blocked, with an **Open** action.
2a. **Given** that notice, **When** the person taps Open, **Then** the blocked pop-up's address opens once in a new active tab (incognito exactly when the page is); the next blocked pop-up is blocked again.
3. **Given** a tab that a page opened, **When** that page closes itself, **Then** its tab closes and the person returns to the tab that opened it.
4. **Given** a tab the person opened themselves, **When** its page tries to close itself, **Then** nothing happens.
5. **Given** the tab limit is reached (Spec 011 normal, Spec 012 incognito), **When** a page opens a new window after a tap, **Then** the existing tab-limit message is shown and no tab is created.

---

### User Story 6 - Site dialogs and sign-in prompts (Priority: P3)

A site shows a message, a yes/no question, asks for a short text, or asks "Leave site?". ThirtySix shows these in its own style, naming the site, so the person knows who is asking. A site that keeps showing dialogs can be silenced. A site protected by a username and password asks for them in a dialog; they are used once and never saved.

**Why this priority**: These already appear today in a plain, unbranded form, so the gap is about clarity and safety rather than something broken; password-protected sites are uncommon.

**Independent Test**: On a test page, trigger alert, confirm, prompt and a leave-page warning — each appears styled and names the site; the answers reach the page. On a password-protected test address, enter credentials → page loads; reopen the app → asked again.

**Acceptance Scenarios**:

1. **Given** a site shows a message, asks yes/no or asks for text, **When** it does, **Then** an app-styled dialog shows the site's address and the site's text, with the matching buttons (OK / Cancel and a text field for a prompt), and the person's answer reaches the page.
2. **Given** the person tries to leave a page that asks "Leave site?", **When** they navigate away or close the tab, **Then** a dialog asks Leave / Stay and honours the answer.
3. **Given** a site shows a second dialog during the same visit, **When** it appears, **Then** it offers "Don't let this site show more dialogs"; once chosen, further dialogs on that page are answered as dismissed without being shown, until the person navigates to another page.
4. **Given** a site requires a username and password, **When** it does, **Then** a dialog names the site and asks for them; Sign in sends them for that request; Cancel stops the attempt.
5. **Given** credentials were entered, **When** the app restarts or the session ends, **Then** nothing is remembered: the person is asked again next time.
6. **Given** a dialog is open, **When** the person presses Back, **Then** it closes as Cancel.

---

### Edge Cases

- **Request from a page that is no longer showing**: a permission, file or dialog request whose tab has been closed or replaced is answered "no" / cancelled; nothing opens over another page.
- **Several permission requests at once**: one dialog at a time, in arrival order; a second request from the same site for the same thing while its dialog is open joins the open dialog.
- **Location precision**: if the person grants only approximate location to the app at the Android prompt, the site gets approximate location; that does not count as a refusal.
- **Site permission for an address that is not https**: sites that are not secure (plain http) are refused camera, microphone and location without asking, as the web platform itself requires.
- **File picker returns nothing usable** (no app, empty result): the page receives no file; no error is shown beyond the page's own handling.
- **Fullscreen and the app going to the background**: leaving the app while fullscreen and returning keeps fullscreen; a phone call or other interruption does not crash or leave the bars hidden after fullscreen ends.
- **Fullscreen and rotation lock**: when the person has auto-rotate off, landscape videos still turn to landscape while fullscreen (as video apps do) and return afterwards.
- **Long-press on something that is neither a link nor an image** (plain text, a video, an input): no menu; the page's normal behaviour applies (text selection still works).
- **Long-press with an unsafe address** (`javascript:`, `data:` …): the menu offers only Copy; it never opens or downloads such an address.
- **Popup with an unsafe address** (`javascript:` …): blocked like a non-tap popup.
- **Dialog flood across pages**: the "don't show more" choice ends when the page changes, so a later page from the same site can show dialogs again.
- **HTTP auth repeated failures**: wrong credentials ask again; after the person cancels, the page shows whatever the site returns for a refused sign-in.
- **Removing a site from Site permissions while it is open**: the next request from that site asks again; access already granted to the open page continues until it is reloaded.
- **Clearing browsing data** (Spec 016) removes remembered site permissions only when its new "Site permissions" category is ticked (unticked by default); otherwise they stay and are managed in Site permissions.

## Requirements *(mandatory)*

### Functional Requirements

**Site permissions (Story 1)**

- **FR-001**: When a page asks for camera, microphone or location, the app MUST ask the person with an app dialog that names the site (its origin as an address) and the capability, offering **Always allow**, **Allow this time** and **Don't allow** (in incognito tabs only Allow this time and Don't allow), unless a remembered or current-page decision exists for that site and capability.
- **FR-002**: Only after the person taps Always allow or Allow this time, and only if the app does not already hold the matching Android permission, the app MUST request that Android permission; it MUST NEVER request these permissions at launch or for any other reason.
- **FR-003**: In normal tabs, **Always allow** and **Don't allow** MUST be remembered per site and per capability (camera, microphone, location) and applied silently to later requests from that site, until the person changes or removes them. **Allow this time** MUST apply only to the current page in that tab and end when the tab navigates to another page, is closed, or the app's process ends; it is never remembered.
- **FR-004**: In incognito tabs, a decision MUST apply only to that tab's current visit and MUST NOT be remembered or read from remembered decisions made in normal tabs.
- **FR-005**: A combined request (for example camera and microphone together) MUST be presented as one dialog and its answer applied to every capability in it.
- **FR-006**: If the Android permission is permanently denied, tapping Always allow or Allow this time MUST explain this and offer to open the app's system settings; the current request MUST be refused.
- **FR-007**: Requests from pages that are not secure (not https) MUST be refused without asking.
- **FR-008**: Every other permission a page may ask for (notifications, clipboard reading, MIDI, sensors, protected media, …) MUST continue to be refused without asking.
- **FR-009**: Settings MUST offer a **Site permissions** screen listing every site with a remembered decision, showing per capability whether it is allowed or not; the person MUST be able to change each to Always allow or Don't allow, remove a site (so it is asked again), and remove all.
- **FR-010**: Remembered decisions MUST be kept on the device only and survive app restarts and updates.
- **FR-010a**: The Clear browsing data dialog (Spec 016) MUST gain a **Site permissions** category, **unticked by default**; when ticked and confirmed, every remembered decision is removed (and any current one-time grants end). When unticked, clearing leaves them untouched.

**File upload (Story 2)**

- **FR-011**: A page's file input MUST open the system file picker, restricted to the page's accepted types when it states them, allowing several files when the page allows it.
- **FR-012**: The files chosen MUST be handed to the page; cancelling or a picker that returns nothing MUST hand back no file and leave the input usable again.
- **FR-013**: When the page asks to capture directly, the person MUST be offered the phone's camera (for a photo or video, matching what the page accepts) alongside choosing an existing file; if capture needs the Android camera permission, it is requested at that moment, and refusing it MUST still leave choosing a file available.

**Fullscreen (Story 3)**

- **FR-014**: A page's fullscreen request MUST show its content over the whole screen with the browser bars and the system bars hidden; edge swipes may reveal the system bars temporarily.
- **FR-015**: Content wider than it is tall MUST be shown in landscape while fullscreen, even with auto-rotate off; the previous orientation MUST be restored when fullscreen ends.
- **FR-016**: Back MUST end fullscreen before doing anything else; ending fullscreen by any means MUST restore the same tab, scroll position and bars, without reloading the page.
- **FR-017**: Fullscreen content of an incognito tab MUST stay protected from screenshots like any incognito content (Spec 012).

**Long-press menu (Story 4)**

- **FR-018**: Long-pressing a link MUST show a menu headed by the link's address offering: Open in new tab, Open in incognito tab, Copy link address, Share link, Download link.
- **FR-019**: Long-pressing an image MUST show a menu headed by the image's address offering: Open image in new tab, Download image, Copy image address, Share image link. An image inside a link MUST show both groups, link actions first.
- **FR-020**: Open in new tab MUST open the address in a new normal tab **in the background** and show a short notice with an action to switch to it; Open in incognito tab MUST open it in a new incognito tab the same way.
- **FR-021**: Download link and Download image MUST start a download through the existing downloads feature (Spec 015), with the same behaviour as a page-started download.
- **FR-022**: Copy MUST place the address on the clipboard and confirm it briefly; Share MUST open the system share sheet with the address.
- **FR-023**: For an address whose scheme is not http or https, the menu MUST offer only Copy.
- **FR-024**: Tab limits (Spec 011: 50 normal, Spec 012: 50 incognito) MUST apply to Open in new tab and Open in incognito tab, using the existing tab-limit message.

**Popups and new windows (Story 5)**

- **FR-025**: A new window opened after the person's tap MUST open as a new tab that becomes active, incognito exactly when its opener is incognito.
- **FR-026**: A window a page tries to open without the person's tap MUST be blocked, with a short notice that a pop-up was blocked; no tab is created.
- **FR-026a**: When the blocked pop-up has a web address, the notice MUST offer **Open**, which opens that address once in a new active tab (incognito exactly when the page is), subject to tab limits. The tab is a plain new tab: it is not connected to the page that tried to open it and cannot be closed by that page. Nothing is remembered; later pop-ups are blocked again. A pop-up without a web address (blank or unsafe) gets the notice without Open.
- **FR-027**: When a page in a tab that a page opened closes itself, that tab MUST close and the person MUST return to the opener tab if it still exists; a page in a tab the person opened MUST NOT be able to close its tab.
- **FR-028**: Tab limits MUST apply to new windows, using the existing tab-limit message; no tab is created beyond the limit.

**Dialogs and sign-in prompts (Story 6)**

- **FR-029**: Site messages, yes/no questions and text prompts MUST be shown as app-styled dialogs that name the site and show the site's text unchanged; the person's answer MUST reach the page.
- **FR-030**: A "leave site?" request MUST be shown as a Leave / Stay dialog when the person navigates away or closes the tab, and honoured.
- **FR-031**: From a site's second dialog in one page visit, the dialog MUST offer "Don't let this site show more dialogs"; once chosen, further dialogs on that page MUST be answered as dismissed without being shown, until the page changes.
- **FR-032**: A site requiring HTTP authentication MUST show a dialog naming the site with username and password fields; the credentials MUST be used for that attempt only and MUST NEVER be stored, logged or remembered across attempts or restarts.
- **FR-033**: Back MUST dismiss any of these dialogs as Cancel.

**Common rules**

- **FR-034**: Any request (permission, file picker, dialog, HTTP auth) whose tab has been closed or whose page has changed MUST be answered as refused / cancelled, and its dialog MUST NOT appear over another page.
- **FR-035**: Only one of this spec's dialogs MUST be visible at a time; others wait in arrival order.
- **FR-036**: Nothing about the sites visited, the decisions made, the files chosen or the credentials typed MUST be written to logs or sent anywhere other than to the page itself.
- **FR-037**: The new Android permissions (camera, microphone, precise and approximate location) MUST be declared and added to the Constitution's permission table by this spec, each with its purpose; no other new permission is added.
- **FR-038**: All new text MUST exist in all 8 supported languages, and every new control MUST carry a screen-reader label.

### Key Entities

- **Site permission decision**: for one site (origin) and one capability (camera, microphone, location), whether it is always allowed or never allowed, and when it was decided. Kept on the device for normal tabs only; listed and edited in Site permissions.
- **One-time grant**: an "Allow this time" answer for one site and capability, tied to one tab's current page; held in memory only and dropped when that page changes, the tab closes or the process ends.
- **Pending site request**: a request from a page (permission, file chooser, dialog, sign-in prompt, new window) waiting for the person or the system; tied to the tab and page that made it, and dropped if either changes.
- **Visit dialog suppression**: per tab, whether the person chose "Don't let this site show more dialogs" for the current page; reset when the page changes. Not stored.
- **Page-opened tab marker**: whether a tab was opened by a page (new window), and by which tab, so the page may close it and the person returns to the opener. Not stored beyond the tab's life.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a set of 10 common sites that need these capabilities (video call, map, file upload form, video site, Google sign-in popup, password-protected test address, and similar), each task completes in ThirtySix as it does in a mainstream browser: 10/10.
- **SC-002**: A site given Always allow or Don't allow is never asked again in a normal tab across 10 later visits; a site given Allow this time, and any incognito tab, is asked again on every new visit.
- **SC-003**: The app's camera, microphone and location permissions are never requested at launch or without the person first tapping Allow for a site: 0 occurrences across all test runs.
- **SC-004**: Entering and leaving fullscreen 10 times returns to the same tab and scroll position without a reload in 10/10 trials.
- **SC-005**: From long-press to a new background tab takes at most 2 taps (long-press, Open in new tab), and the page on screen does not change.
- **SC-006**: Popups opened without a tap are blocked in 100% of tested cases; popups after a tap open as tabs in 100% of tested cases.
- **SC-007**: HTTP-auth credentials are found nowhere on the device after use (no stored value, no log line) in 100% of checks.
- **SC-008**: Changing a site's decision in Site permissions takes effect on the site's next request in 10/10 trials.
- **SC-008a**: Clear browsing data with the Site permissions category unticked leaves 100% of remembered decisions; with it ticked, removes 100% of them.
- **SC-009**: The release app grows by no more than 150 KB and adds no native library (16 KB gate stays green).

## Assumptions

- **Design waiver (owner decision, 2026-10-11, as for Spec 020)**: Constitution §X's "Design before spec" rule is waived because Claude Design is unavailable on the owner's account. The permission dialog, the site dialogs, the sign-in dialog, the long-press menu and the Site permissions screen are built from existing Spec 019 design-system components (the shared confirm/choice dialogs, bottom sheets, Settings rows) and tokens. The owner will supply designs later; a later visual change MUST NOT change the behaviour specified here.
- **Three choices** (clarified 2026-10-11): Always allow and Don't allow are remembered; Allow this time lasts for the current page only (FR-001, FR-003).
- **Open in new tab opens in the background** (FR-020, confirmed 2026-10-11), as in mainstream mobile browsers; the notice offers to switch.
- **A blocked pop-up can be opened once from its notice** (FR-026a, clarified 2026-10-11); per-site "always allow pop-ups" exceptions remain out of scope. A pop-up opened this way is a plain tab, so a sign-in flow that relies on the pop-up talking back to its opener may still need the person to retry by tapping the site's button.
- **Site permissions in Clear browsing data are an opt-in category** (FR-010a, clarified 2026-10-11), unticked by default so a routine clear does not make every site ask again.
- **"Origin"** means scheme + host + port of the page that asks, shown to the person as the site's address.
- **Not secure (http) sites are refused** capabilities without asking (FR-007), as the web platform restricts these capabilities to secure pages anyway.
- **Capture uses the phone's camera app** (FR-013); ThirtySix does not include its own camera screen.
- **Landscape for wide fullscreen content** (FR-015) follows what video apps do even with auto-rotate off.
- **Reference device**: the owner's Galaxy A55 (Android 16) with release builds; Android 7 behaviour is checked on the API 24 emulator (Android 7–9 grant runtime permissions the same way from API 23, so the flow is identical).
- **Out of scope**: find in page, sharing the current page from the app menu, desktop mode, text zoom, print, autocomplete (Spec 022); tracker blocking and other protections (Spec 023); password saving and autofill (not in v1 by the Constitution); protected (DRM) media; web notifications; per-site pop-up exceptions; clipboard reading and other permissions listed in FR-008.

## Dependencies

- **Spec 007** — the page view this spec extends.
- **Spec 011 / Spec 012** — tabs, incognito tabs, tab limits and screenshot protection (FR-017, FR-020, FR-024, FR-025, FR-028).
- **Spec 015** — downloads, reused by the long-press menu (FR-021).
- **Spec 016** — the Settings screen that hosts Site permissions (FR-009) and the Clear browsing data dialog, which gains a category (FR-010a).
- **Spec 019** — design-system components and Compose Phase Discipline for all new UI.
- **Spec 020** — new-tab and tab-limit handling patterns reused for new windows and background tabs.
