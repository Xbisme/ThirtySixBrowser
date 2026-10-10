# Feature Specification: UI Redesign

**Feature Branch**: `019-ui-redesign`
**Created**: 2026-10-10
**Status**: Draft
**Input**: User description: "Spec 019 `ui-redesign` — rebuild every screen of ThirtySix Browser from the approved ThirtySix design system (https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys) and the approved screens canvas (https://claude.ai/artifact/FRRCWBmGa85kmLSFT8tmak), while meeting Constitution v1.4.0 §V Compose Phase Discipline. Presentation only; every existing feature (Specs 007–018) keeps its behaviour unless listed. Brand moves from Deep Teal to violet; new Home page for new tabs; compact browsing chrome that respects the status bar; bottom bar Home · Search · New tab · Tabs · Menu with Back removed; Hub sheet replaces the overflow menu; restyled Tabs, History, Bookmarks, Downloads, Settings, dialogs, incognito page and onboarding; no per-frame redraw of chrome. Out of scope: Pin a site, any new data, feeds, voice/camera search, share; no schema change."

## Clarifications

### Session 2026-10-10 (pre-spec discussion with the product owner)

- Q: Why redesign at all, after v1.0 was declared feature-complete? → A: **The owner judged the shipped interface not good enough to submit**, and supplied a reference mockup as the target look. Rationale: the app's features are complete, but its first impression — default Material styling, an address bar drawn under the status bar — undercuts the product. **Consequence: Constitution v1.4.0 §X makes this Spec 019 and moves the optional tracker blocker to 020.**
- Q: Which visual direction? → A: **The approved design system and screens canvas are the source of truth.** A first, Material-default pass of the design system was rejected as "not modern like the reference"; the approved pass has white surfaces, large soft corners, thin line icons, a dark glowing hero with frosted-glass controls, image shortcut cards and a hub sheet. **Consequence: FR-001; any deviation from the canvas is either fixed or designed and approved first (Constitution §X, design before spec).**
- Q: Keep the Deep Teal brand or switch to the reference's violet? → A: **Violet**, including the launcher icon, monochrome icon and launch screen. Rationale: owner's choice; the brand mark keeps Spec 017's geometry and only changes colour. **Consequence: FR-003, FR-004.**
- Q: Does Back stay on the bottom bar? → A: **No.** The bar becomes Home · Search · New tab · Tabs · Menu. Back is the system back gesture (with predictive back, Spec 008); Back and Forward also appear in the hub. Rationale: matches the reference, frees the centre slot for the raised new-tab action, and the system gesture already does the job. **Consequence: FR-020, FR-021; this changes Spec 008's visible controls but not its navigation behaviour.**
- Q: The reference shows a news feed, weather, account avatar, camera and voice search. Which of these come across? → A: **None.** Each would need a server, an account or a new permission, contradicting Constitution §I/§II. The Home page uses only data already on the device: open tabs, bookmarks and history. **Consequence: FR-010 to FR-014 and the Out of scope list.**
- Q: The design shows a "Pin a site" card. Build it now? → A: **No — recorded in the roadmap as a later-version idea.** Pinning needs new stored data and was never specified. **Consequence: FR-013 hides that card.**
- Q: Redesign and performance work: one spec or two? → A: **One.** Every screen is being rewritten anyway, so it is written to the phase rules from the start rather than optimised later. **Consequence: User Story 6 and FR-060 to FR-068 are as binding as the visual requirements.**
- Q: What does a new tab show? → A: **The new Home page** (as on the approved canvas), instead of loading the configured home web address. The Home button shows the same page in the current tab. **Consequence: FR-010, FR-022; this replaces Spec 008 FR-016's "home = https://www.google.com/" for both new tabs and the Home button.**

### Session 2026-10-10

- Q: Should the Tabs screen gain a sideways swipe to close a tab? → A: **Yes — swipe to close, alongside the existing close button**, as the single permitted exception to FR-001's "no new actions". Rationale: it is a familiar gesture for an action the app already has (closing a tab), adds no data or permission, and is the hot interaction User Story 6 measures. **Consequence: FR-001 names the exception, FR-040 and User Story 4 scenario 1 require the gesture.**
- Q: With dynamic color on, does the Home hero follow the wallpaper colours? → A: **Partly — the hero's main glow takes the wallpaper-derived brand colour; its dark base and secondary rose glow stay fixed**, so the hero is always dark and its white text always legible. Rationale: a fixed violet hero would clash with an app otherwise recoloured from the wallpaper, while recolouring the whole hero could make it light and break the glass text. **Consequence: FR-005 includes the hero's main glow; SC-009's contrast check covers the hero under dynamic color.**
- Q: On the Tabs screen, what do the Open / Incognito chips control, and which is selected on entry? → A: **Each chip is a mode.** Under Open the grid shows normal tabs, the create card makes a normal tab and close-all closes normal tabs; under Incognito it shows incognito tabs, the create card reads "New incognito tab" and close-all closes only incognito tabs (Spec 012's confirmation). On entry the chip matching the active tab's kind is selected. Rationale: it matches Chrome and makes it impossible to open or close the wrong kind of tab by accident. **Consequence: FR-040 and A6 rewritten; the Incognito chip is hidden when no incognito tab is open.**

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The browser looks and feels new from the first launch (Priority: P1) 🎯 MVP

Someone opens ThirtySix and sees a coherent violet product: the new launcher icon and launch screen, the restyled onboarding, then the new Home page and browsing chrome. Nothing is drawn under the status bar or the navigation bar, and the whole app follows one visual language in light and dark.

**Why this priority**: The brand, the theme tokens and the browsing chrome are the foundation every other screen builds on; on their own they already fix the most visible defects (the teal default look and the address bar under the status bar).

**Independent Test**: Fresh install on a 360dp-wide device in light, then dark: launcher icon, launch screen, onboarding, Home and a loaded page all match the canvas, and no control overlaps a system bar.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user looks at the launcher, **Then** the icon is the violet ThirtySix mark, and on Android 13+ with themed icons on, the monochrome mark.
2. **Given** a cold start, **When** the launch screen shows, **Then** it shows the violet mark on the theme's background, with no teal anywhere.
3. **Given** a page is open, **When** the user looks at the top of the screen, **Then** the address well sits entirely below the status bar and the bottom bar entirely above the navigation bar or gesture area.
4. **Given** the device theme changes between light and dark, **When** the app is open, **Then** every screen switches with legible text on every surface.
5. **Given** dynamic color is on (Android 12+), **When** the user browses, **Then** wallpaper colours replace the violet, the surfaces and the Home hero's main glow, while the hero stays dark and the quick-setting pastels, the danger and success colours and the incognito plum stay as designed.

---

### User Story 2 - A new tab opens on a Home page made of my own things (Priority: P1)

When the user opens a new tab or presses Home, they see a calm Home page: a greeting for the time of day, a line reminding them that their browsing stays on the phone, a search bar, and cards for their open tabs, saved pages and recent sites, followed by a short list of recently visited pages.

**Why this priority**: It is the new face of the app and the screen users see most often between pages.

**Independent Test**: With a few open tabs, bookmarks and history entries, open a new tab: the Home page shows the greeting, search bar, shortcut cards and recently visited rows; tapping a card or row opens that page; with no data at all, the page shows only the greeting and search bar without empty boxes.

**Acceptance Scenarios**:

1. **Given** the time is 19:30, **When** the Home page shows, **Then** the greeting reads "Good evening" in the app's language.
2. **Given** the user has open tabs, bookmarks and history, **When** the Home page shows, **Then** shortcut cards appear in the order open tabs, saved pages, recent sites, each labelled with which kind it is, with no page appearing twice.
3. **Given** a tab has a cached preview, **When** its card shows, **Then** the preview fills the card; **Given** it has none, **Then** the card shows a colour field derived from the site with its first letter.
4. **Given** the user taps the search bar on the Home page, **When** they type and submit, **Then** the result is exactly what the address bar does today (Specs 009–010).
5. **Given** the user taps a shortcut card for an open tab, **When** the tap completes, **Then** that tab becomes active; for a saved or recent page, it opens in the current tab.
6. **Given** the user scrolls the Home page, **When** the list moves, **Then** the hero shrinks and fades smoothly.

---

### User Story 3 - Browsing chrome and the hub (Priority: P1)

While reading a page, the user has a compact top bar (lock and site name, bookmark, reload) and a bottom bar with Home, Search, a raised New-tab action, Tabs and Menu. Both bars slide away as they scroll down the page and return as they scroll up. Menu opens a hub sheet with Settings, Back and Forward, the privacy promise, an incognito shortcut, the three libraries (Downloads, History, Bookmarks) and quick settings.

**Why this priority**: Browsing is the core task; the hub replaces the overflow menu, which is the only route to the libraries and Settings.

**Independent Test**: Load a long page and scroll; open Menu and reach every destination from the hub; change the theme from the hub's Theme tile.

**Acceptance Scenarios**:

1. **Given** a page is loading, **When** progress advances, **Then** a thin violet line grows along the bottom of the address well and disappears when loading ends.
2. **Given** a page is open, **When** the user scrolls down, **Then** both bars slide out of view; **When** they scroll up or reach the top, **Then** both bars return.
3. **Given** the bottom bar is visible, **When** the user looks at it, **Then** it shows exactly Home · Search · New tab · Tabs (with the open-tab count) · Menu, and no Back button.
4. **Given** the user has navigated through several pages, **When** they use the system back gesture, **Then** the previous page loads exactly as today, with predictive back on Android 14+.
5. **Given** Menu is tapped, **When** the hub opens, **Then** Back and Forward are enabled or disabled to match the page's history, and every tile reaches the screen or chooser it names.
6. **Given** the hub is open, **When** the user drags it down or uses back, **Then** it closes.
7. **Given** the user taps Search on the bottom bar, **When** a page is open, **Then** the address well gains focus with the keyboard open.

---

### User Story 4 - Libraries, tabs and settings in the new style (Priority: P2)

Tabs, History, Bookmarks, Downloads and Settings use the new style: large titles, search wells, open rows on white with section headers, chips for quick filters, pastel icons in Settings that match the hub's quick tiles, and the new dialogs.

**Why this priority**: These screens work today; restyling them completes the redesign but carries no new behaviour.

**Independent Test**: Visit each screen and perform its existing main actions (open, delete, search, clear all, cancel a download, change each setting); every action behaves as in Specs 011–016.

**Acceptance Scenarios**:

1. **Given** the Tabs screen, **When** it shows, **Then** tabs appear as image cards (two columns at 360dp, more when wider) with title over the preview and a close button, the active tab ringed in violet, plus a create card; the chip for the active tab's kind is selected, and switching to Incognito shows only incognito tabs with a "New incognito tab" card; swiping a card sideways closes that tab, the same as tapping its close button.
2. **Given** History, **When** it shows, **Then** entries are grouped under Today, Yesterday and explicit dates exactly as in Spec 014, as open rows, with search at the top and clear-all in the title bar.
3. **Given** Bookmarks, **When** it shows, **Then** folders and saved pages are listed in separate sections, top-level folders also appear as chips, and inside a folder the breadcrumb remains.
4. **Given** Downloads, **When** it shows, **Then** each item states its state in words (for example "Complete", "Paused · Waiting for network", "Failed · No connection") with colour as a second cue, and in-progress items show progress and a cancel button.
5. **Given** Settings, **When** it shows, **Then** settings appear in groups (Appearance, Language and search, Privacy, About), each row with its pastel icon and current value.
6. **Given** any choice dialog (theme, language, search engine, history retention), **When** the user taps an option, **Then** it applies and the dialog closes with no OK button; **Given** any destructive confirmation, **Then** it shows what will be lost and a full-width button naming the action above Cancel.

---

### User Story 5 - Incognito and first run (Priority: P2)

An incognito tab looks unmistakably different: plum chrome and, when empty, a page explaining what is and is not kept. The four onboarding slides keep Spec 018's behaviour but use the new hero style.

**Why this priority**: Both are short, high-visibility moments; their behaviour already exists.

**Independent Test**: Open a new incognito tab from the hub banner; check chrome and explanation. Clear app data and walk the onboarding, including a language change on slide 2.

**Acceptance Scenarios**:

1. **Given** an incognito tab is active, **When** the user looks at the bars, **Then** both are deep plum, in light and dark themes alike.
2. **Given** a new incognito tab, **When** nothing is loaded yet, **Then** the page says history is not kept, cookies are cleared when incognito tabs close, screenshots are blocked while one is open, and downloads and bookmarks are kept.
3. **Given** the onboarding, **When** the user moves through it, **Then** each slide shows "Step N of 4", page dots, Skip and (from slide 2) Back, and the last slide's button reads "Start browsing"; all Spec 018 behaviours (save on select, Skip writes completion, slide kept across a language change, flow removed from back history) still hold.

---

### User Story 6 - Smooth while it moves (Priority: P1)

Loading, scrolling, dragging the hub and swiping tabs stay smooth: the parts of the screen that are not moving are not rebuilt every frame.

**Why this priority**: The redesign adds a hero, glass, shadows and animations; without this the new look would cost frames, and Constitution v1.4.0 makes it a hard gate.

**Independent Test**: For each hot interaction (page load, page scroll, Home scroll, hub drag, tab swipe, download progress), record how often the surrounding chrome is rebuilt; it does not increase with the number of frames.

**Acceptance Scenarios**:

1. **Given** a page is loading, **When** progress changes 50 times, **Then** the top bar, bottom bar and page area are rebuilt zero times because of progress; only the progress line is redrawn.
2. **Given** a page is scrolled for 5 seconds, **When** the bars slide, **Then** the bars' contents are not rebuilt per frame.
3. **Given** the hub is dragged, **When** it moves, **Then** its tiles and banners are not rebuilt per frame.
4. **Given** the History list has 10,000 entries, **When** it is scrolled, **Then** only rows entering the screen are built.

---

### Edge Cases

- **No data at all** (fresh install): Home shows only greeting, privacy line and search bar; no "Recently visited" heading and no empty card row.
- **Only incognito tabs open**: incognito tabs never appear on the Home page's cards or recent rows, and incognito history does not exist (Spec 012).
- **Home opened from an incognito tab**: shows the incognito page, not the Home page, so nothing personal is shown inside a private context.
- **Very long titles and German/Russian labels**: titles ellipsize on one line; tile and button labels wrap to two lines; nothing is cut off at 360dp.
- **Glass controls**: on every Android version they are a translucent fill with a hairline ring, without backdrop blur (research R5), and remain legible on the hero and on card images.
- **Reduced motion / animations off**: hero collapse, bar sliding and sheet animations jump to their end states.
- **Keyboard open on Home**: the search bar stays visible above the keyboard.
- **Downloads in progress while the hub is closed**: the hub's downloads button appears only while something is downloading.
- **Dynamic color on with a very light or dark wallpaper**: text contrast rules still hold because surfaces and their text colours come from the same generated scheme.
- **Landscape / large screens**: layouts stretch to the width; the Home cards row and tab grid show more items; no new layout is designed for tablets in this spec.
- **A tab's preview missing or deleted from cache**: card falls back to the site colour field without a broken image.

## Requirements *(mandatory)*

### Functional Requirements

**Design source and brand**

- **FR-001**: Every screen, dialog and sheet MUST match the approved screens canvas and design system for layout, colour, type, spacing, corner radius, elevation and iconography, in light and dark. Where the canvas shows an action the app does not have (for example retry on a failed download), the action MUST NOT be added. The one exception is swipe-to-close on tab cards (FR-040), a second way to perform the existing close-tab action.
- **FR-002**: Colours MUST come from the design system's tokens; light surfaces MUST be white with grey wells; the home hero MUST stay dark in both themes.
- **FR-003**: The brand colour MUST be violet in every themed surface; no Deep Teal may remain anywhere in the app.
- **FR-004**: The launcher icon (all densities, including the API 24–25 bitmaps), the monochrome themed icon and the launch-screen mark MUST be regenerated from the design system's violet mark, keeping Spec 017's geometry and its solid (not cut-out) numerals.
- **FR-005**: Dynamic color (Android 12+), when enabled, MUST replace only the brand colour, its tints, the surfaces and the Home hero's main glow; the hero's dark base and secondary glow, the quick-setting pastels, danger, success and incognito colours MUST stay fixed, so the hero stays dark.
- **FR-006**: Icons MUST be thin outlined line icons from one icon family, filled only to mean "on" or "current"; no emoji anywhere.
- **FR-007**: Typography MUST remain Poppins for display text and Inter for everything else, at the design system's type scale.
- **FR-008**: Every top bar, bottom bar, sheet and floating control MUST stay clear of the status bar, navigation bar, display cutout and on-screen keyboard.

**Home page**

- **FR-010**: Opening a new (non-incognito) tab and pressing Home MUST show the Home page in that tab instead of loading a web address. Back from a page reached from Home MUST return to Home.
- **FR-011**: The Home page MUST show a greeting for the time of day ("Good morning" 05:00–11:59, "Good afternoon" 12:00–17:59, "Good evening" otherwise), never a name, and one line stating that browsing stays on the phone.
- **FR-012**: The Home page's search bar MUST behave exactly as the address bar does (URL vs query, chosen engine).
- **FR-013**: The Home page MUST show up to 8 shortcut cards in the order open tabs (other than the current one), saved pages (most recently added), recent sites (most recently visited), each page at most once, each card labelled Tab, Saved or Recent. The "Pin a site" card MUST NOT be shown.
- **FR-014**: Below the hero the Home page MUST show up to 5 most recent history entries under "Recently visited" with a "See all" link to History; with no history the section MUST be absent.
- **FR-015**: Incognito tabs MUST NOT show the Home page; a new or Home-pressed incognito tab MUST show the incognito explanation page (FR-050). Nothing from incognito tabs may appear on the Home page.

**Browsing chrome**

- **FR-020**: The bottom bar MUST contain exactly Home, Search, New tab (raised, centre), Tabs (with count) and Menu. Back MUST NOT appear on it.
- **FR-021**: The system back gesture, including predictive back on Android 14+, MUST keep Spec 008's behaviour; Back and Forward MUST also be available in the hub.
- **FR-022**: Search MUST focus the address field (or the Home search bar) and open the keyboard.
- **FR-023**: The top bar MUST show a lock (secure page) or incognito mask, the hostname, the bookmark toggle and Reload, which becomes Stop while loading.
- **FR-024**: Page-load progress MUST be shown as a thin line on the bottom edge of the address well.
- **FR-025**: Both bars MUST slide out when the page scrolls down and return when it scrolls up or reaches the top; they MUST be visible whenever the page is not scrollable, an error is shown, or the keyboard is open.

**Hub**

- **FR-030**: Menu MUST open the hub sheet; the previous overflow menu MUST be removed.
- **FR-031**: The hub MUST contain: Settings; Back and Forward (enabled per page history); a downloads-in-progress button shown only while a download runs; the privacy banner leading to About; the incognito banner opening a new incognito tab; Downloads, History and Bookmarks tiles; and Theme, Language, Search engine and Clear data quick tiles that open the existing choosers.
- **FR-032**: The hub MUST close on drag down, back, scrim tap, or after any destination is chosen.

**Other screens**

- **FR-040**: Tabs MUST show image cards in an adaptive grid (two columns at 360dp, more on wider screens) with preview, title, favicon and close, the active tab marked; swiping a card sideways past half its width MUST close that tab exactly as its close button does (released earlier, it springs back); a create card and a close-all action. Open / Incognito chips MUST act as modes: the grid, the create card ("New tab" or "New incognito tab") and close-all all apply to the selected kind only; on entry the chip matching the active tab's kind is selected; the Incognito chip is shown only while an incognito tab is open, though "New incognito tab" stays reachable from the hub. All Spec 011–012 behaviours (caps, close all, confirmations, incognito never captured) MUST be kept.
- **FR-041**: History, Bookmarks and Downloads MUST use large titles, a search well where search exists today, open rows grouped under section headers and the existing long-press action sheets restyled; all Spec 013–015 behaviours MUST be kept.
- **FR-042**: Bookmarks MUST additionally offer top-level folders as chips at the root.
- **FR-043**: Downloads MUST state each state in words with colour as a secondary cue and keep progress and cancel for in-progress items.
- **FR-044**: Settings MUST group rows under Appearance, Language and search, Privacy and About, each row showing its current value; each row's icon colour MUST match its hub tile.
- **FR-045**: Choice dialogs MUST apply on selection without an OK button; destructive confirmations MUST name what is lost and present a full-width action button naming the action above Cancel.
- **FR-046**: Empty and no-match states of every list MUST be restyled with the same copy as today.

**Incognito and onboarding**

- **FR-050**: An empty incognito tab MUST show a page explaining that history is not kept, cookies and site data are cleared when incognito tabs are closed, screenshots are blocked while one is open, downloads and bookmarks are kept, and websites and the network can still see visits.
- **FR-051**: While an incognito tab is active the bars MUST use the incognito plum in both themes.
- **FR-052**: Onboarding MUST keep every Spec 018 requirement and adopt the hero style: step text, page dots, Skip on every slide, Back from slide 2, and "Start browsing" on the last slide's forward control.

**Language and accessibility**

- **FR-055**: Every new or changed string MUST exist in all 8 supported languages; existing wording MUST be reused where meaning is unchanged (including the privacy statement, which has one wording app-wide).
- **FR-056**: Text MUST hold 4.5:1 contrast (3:1 for large text, icons, control borders and focus) on its surface in both themes; touch targets MUST be at least 48dp; every icon-only control MUST have a spoken label.

**Performance (Constitution v1.4.0 §V)**

- **FR-060**: Values that change per frame (load progress, page scroll, Home scroll, sheet drag, tab swipe, download progress, running animations) MUST NOT cause the surrounding chrome or list rows to be rebuilt each frame; they MUST only reposition or redraw the moving part.
- **FR-061**: Load progress and scroll-driven bar offsets MUST be kept apart from the browser screen's general state so that a progress update reaches only the progress line.
- **FR-062**: Every list and grid MUST identify items by a stable identity and item kind.
- **FR-063**: The data handed to each screen element MUST be stable, so unchanged elements are skipped when a screen updates.
- **FR-064**: Build-time stability reports MUST be available on demand, and the spec's evidence MUST show no unexplained non-skippable screen elements in the files it touches.
- **FR-065**: For each hot interaction in User Story 6 the rebuild counts MUST be recorded before and after on the same device and attached to the tasks' gate results.

### Key Entities

- **Home shortcut**: a card on the Home page referring to an open tab, a saved page or a recent site; has a kind (Tab, Saved, Recent), title, host, and either a cached preview or a site colour seed. Derived from existing data; nothing new is stored.
- **Greeting period**: morning, afternoon or evening, derived from the device clock.
- **Theme tokens**: the named colours, type styles, spacing, radii and elevations from the design system, in light and dark.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A side-by-side review of every canvas artboard against the built screen at 360dp, light and dark, finds no difference in layout, colour, type or iconography beyond antialiasing (15 artboards, 0 open differences).
- **SC-002**: On 100% of screens, no control overlaps the status bar, navigation bar or keyboard (checked on API 24, 29, 36 with gesture and three-button navigation).
- **SC-003**: No Deep Teal value remains in the app's resources or code (0 occurrences of the old brand colours).
- **SC-004**: While a page loads, the browser chrome is rebuilt 0 times due to progress updates (from more than one rebuild per update today).
- **SC-005**: Scrolling a page, scrolling Home, dragging the hub and swiping a tab each run at p95 frame time ≤ 16 ms on Pixel 5-class hardware in a release build (DEFERRED to hardware per the existing release-seeding blocker if not available, never reported from an emulator).
- **SC-006**: Every list and grid identifies items by stable identity and kind (100% of lazy containers), confirmed by the stability report and code review.
- **SC-007**: Every existing instrumented and unit test either passes unchanged or is updated only for changed visuals, with no behaviour test deleted; the Spec 007–018 manual gates that concern behaviour still pass.
- **SC-008**: Every new string is translated in all 8 locales (lint parity at error severity), and a sweep of all screens in German and Russian at 360dp shows no clipped text.
- **SC-009**: Contrast checks pass for every text/surface pair in both themes, including the Home hero with dynamic color on under at least three contrasting wallpapers, and every icon-only control has a spoken label.
- **SC-010**: A new tab shows the Home page within 300 ms of tapping New tab (median of 10, tap to first Home frame) on the reference emulator, and cold start does not regress by more than 5% against Spec 018's measurement on the same device.
- **SC-011**: Release APK grows by no more than 400 KB over Spec 018's 3,134,976 B, and no new native library is added (16 KB gate stays green).

## Assumptions

- **A1**: Spec 008 FR-016's home web address stops being used for new tabs and the Home button (FR-010); the setting is not exposed anywhere, so no migration is needed.
- **A2**: All data on the Home page (tabs, bookmarks, history, cached previews and favicons) already exists from Specs 011–014; no Room schema change.
- **A3**: Material Symbols are added as individual vector assets; no icon library dependency is added. Any new dependency (for example a blur helper) is looked up and 16 KB-verified per Constitution §IX — none is expected.
- **A4**: "Frosted glass" is a translucent fill plus a hairline ring on every Android version; no backdrop blur is drawn (research R5: over the smooth hero mesh a blur is indistinguishable, and a blur library would be a new dependency).
- **A5**: The hub's privacy banner opens the existing About section of Settings.
- **A6**: Tab chips (Open / Incognito) are presentation modes over the existing tabs (FR-040); the separate tab caps, confirmations and incognito teardown of Specs 011–012 are unchanged.
- **A7**: Screens not drawn on the canvas (empty states, the folder picker, add/edit bookmark dialog, action sheets, the language/retention choosers) follow the design system's components and the nearest canvas screen.
- **A8**: SC-005 needs release-build seeding and real hardware, already a known blocker; it is recorded DEFERRED rather than measured on an emulator if that blocker is unresolved.
- **A9**: TalkBack verification remains a person's task, as in Specs 016–018.
- **A10**: The Home page is part of the app, not a web page: it has no URL in the address bar (the well shows the hint) and is not recorded in history.

## Out of Scope

- "Pin a site" and any other new stored data (roadmap idea for a later version).
- News, feeds, weather, accounts, avatars, voice or camera search, sharing.
- Retry for failed downloads, find in page, or any action the app does not already have.
- Tablet- or landscape-specific layouts.
- The tracker blocker (Spec 020).
