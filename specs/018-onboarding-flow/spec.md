# Feature Specification: Onboarding Flow

**Feature Branch**: `018-onboarding-flow`
**Created**: 2026-09-15
**Status**: Draft
**Input**: User description: "Spec 018 `onboarding-flow` — the first-run experience, and the last spec of Phase 4 and of v1.0. Scope below was decided with the product owner on 2026-09-15. 1. Four slides in order: a welcome slide introducing the app and its privacy promise (it collects nothing and sends nothing to any ThirtySix-run server — the app's main selling point, so it must be said); then app language (Follow system plus the eight supported languages, by endonym); then theme (Light / Dark / System default); then search engine (Google / DuckDuckGo / Bing). 2. A Skip control is shown on every slide. Skipping keeps the current defaults and MUST still mark first-run as done, or the flow reappears next launch — the opposite of what the user asked for. 3. Choices are saved the moment they are made and take effect immediately: choosing Dark turns the onboarding itself dark at once, exactly as the Settings screen already behaves. 4. The flow reuses the screen slot the app already has rather than adding a second entry point, and the app decides at start-up whether to open the flow or the browser. 5. Changing the app language restarts the screen; the flow MUST return to the slide the user was on, not to the first one — otherwise a user who picks a language is thrown back to the start after a visible flicker. Out of scope: re-running onboarding from Settings, any account or sign-in step, and any per-slide illustration beyond what the app already ships."

## Clarifications

### Session 2026-09-15 (pre-spec discussion)

- Q: Which slides does the flow contain? → A: **Four, in order: welcome, app language, theme, search engine.** Rationale: the welcome slide is where the app states that it collects nothing and sends nothing anywhere, which is its main selling point and is worth a slide of its own; the three settings that follow are the ones a user most plausibly wants different from the defaults on day one. A three-slide version without the welcome, and a two-slide version combining all three choices, were both rejected — the first drops the privacy message, and the second crowds three option groups onto one 360dp screen. **Consequence: FR-001 fixes the count and order, and the welcome slide's privacy statement is required rather than decorative (FR-004).**
- Q: Can the user skip the flow? → A: **Yes — a Skip control on every slide.** Skipping keeps whatever defaults are already in effect and still marks first-run as done. Rationale: a user who wants to start browsing should not be held for four screens, and every choice offered here is also available in Settings afterwards. Forcing the user through all four, and showing Skip only from the second slide onward, were both rejected as friction for no gain. **Consequence: FR-011 requires the flag to be written on skip — without it the flow returns at the next launch, which is precisely what the user declined.**
- Q: When are choices saved? → A: **The moment they are made, applied immediately.** Choosing Dark turns the onboarding dark at once. Rationale: the user sees the consequence of the choice while still able to change it, and it is the behaviour the Settings screen already has, so the app does not behave two different ways for the same setting. Holding the choices in memory and writing them only at the end was rejected: it makes a live language preview impossible and adds a second, divergent way of handling the same settings. **Consequence: FR-008 and FR-009 state save-on-select normatively, and it is what forces the restart handling in FR-015.**
- Q: How is the flow built and reached? → A: **Through the screen slot the app already has, with the app choosing at start-up between the flow and the browser.** Rationale: the app is deliberately a single-screen-host application; a second entry point would have to reproduce the launch screen and theme handling on its own and would break that model. **Consequence: FR-013 and FR-014; the start-up decision must be settled before the first screen is shown, or the browser appears briefly before the flow replaces it.**
- Q: Changing the app language restarts the screen — what should the user see? → A: **The flow returns to the slide the user was on, in the newly chosen language.** Rationale: the restart is unavoidable, since it is how the platform applies a language; without this rule a user who picks a language on the second slide is thrown back to the first after a visible flicker, which reads as the app losing their choice. Making the language slide the sole exception to save-on-select, and moving the language slide first so a restart costs nothing, were both rejected — the first removes the preview exactly where it is most useful, the second asks for settings before the app has introduced itself. **Consequence: FR-015 requires the current slide to survive the restart, and SC-005 verifies it.**

### Session 2026-09-15

- Q: What should the device's own Back gesture do while the onboarding flow is showing? → A: **Back moves to the previous slide; on the first slide it leaves the app without marking first-run complete, so the flow returns next launch.** Rationale: this is what Back already means everywhere else in the app — go back one step — and it never strands the user, because from the first slide there is nowhere further back inside the flow and leaving is the honest outcome. Ignoring Back entirely was rejected as a dead control on a screen that has an obvious "previous" step; making Back behave as Skip was rejected because leaving by the system gesture is not consent to never being asked again, and it would silently write the first-run flag. **Consequence: FR-002a states Back's behaviour normatively, and FR-012 already covers the first-slide case — leaving the flow this way does not record completion.**

- Q: When the user finishes or skips the flow, can they get back to it with the Back gesture from the browser? → A: **No — the flow is removed from history, so Back from the browser's first page leaves the app exactly as it does on a normal launch.** Rationale: the flow is meant to appear once per install (A5), and leaving it behind the browser would let a user who just completed it land back in it by pressing Back, which contradicts that and introduces a screen they did not ask for. Keeping it in history was rejected on that basis; the first-run flag alone is not enough, because the flag governs the *next launch*, not the current back stack. **Consequence: FR-010a states the removal normatively for both exits, and SC-015 verifies it.**

- Q: Does the last slide need a distinct "Finish" control, or does the same Next control simply end the flow on the last slide? → A: **The same forward control, in the same position, relabelled on the last slide** — "Next" on slides 1–3 and a finishing label such as "Done" on slide 4. Rationale: it is the convention users already know, it makes the end of the sequence obvious instead of leaving them unsure whether another slide follows, and it removes a whole tap from the flow. Keeping the label "Next" everywhere was rejected because nothing then signals the last slide; adding a second, separate finish control was rejected as two controls doing one job on the one slide where clarity matters most. **Consequence: FR-002b states the relabelling normatively, and SC-008 was corrected from "at most 5 interactions — one per slide plus a finish" to exactly 4: the original wording assumed a separate finishing step, which this answer removes. Accepting every default is now 4 taps; each choice actually changed adds one.**

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Set up the browser on first launch (Priority: P1) 🎯 MVP

Someone opens the browser for the first time. Instead of landing straight on a page, they are welcomed, told the app keeps everything on their device, and then offered three choices — the language the app speaks, whether it is light or dark, and which search engine to use. When they finish, they land on the browser set up the way they chose, and the next time they open the app they go straight to browsing.

**Why this priority**: It is the whole feature and the smallest slice that delivers it — a flow that appears once, collects the choices, applies them, and never appears again. Every other story is a way of leaving the flow early or of surviving an interruption.

**Independent Test**: On a fresh install, open the app, step through all four slides making a choice on each, and finish. Confirm the browser opens with those choices in effect, and that relaunching the app goes straight to the browser.

**Acceptance Scenarios**:

1. **Given** the app has never been opened, **When** the user launches it, **Then** the flow appears instead of the browser.
2. **Given** the flow is showing, **When** the user looks at the first slide, **Then** it introduces the app and states that it collects no data and sends nothing to any ThirtySix-run server.
3. **Given** the user is on a slide, **When** they move forward, **Then** the next slide appears, and the slides come in the order: welcome, language, theme, search engine.
4. **Given** the user is past the first slide, **When** they move backward — by the on-screen control or the device's Back gesture — **Then** the previous slide appears with their choice on it unchanged.
5. **Given** the user is on the last slide, **When** they look at the forward control, **Then** it carries a finishing label rather than a "next" one, and tapping it opens the browser.
6. **Given** the user has finished the flow, **When** they close and relaunch the app, **Then** the browser opens directly and the flow does not appear.
7. **Given** the user made choices in the flow, **When** they open Settings afterwards, **Then** Settings shows exactly those choices.

---

### User Story 2 - See each choice take effect immediately (Priority: P1)

A user picking a theme sees the onboarding itself change colour as they pick it; a user picking a language sees the flow switch to that language; a user picking a search engine sees which one is selected. Nothing is deferred to the end, so the user knows what they are getting before they commit.

**Why this priority**: Tied for first because it is what makes the choices meaningful rather than a form to fill in — particularly for language, where a user who cannot read the current language needs to see the change to confirm they picked the right one. It is separate from Story 1 because it is verified by observing the flow itself, not by what happens after it.

**Independent Test**: In the flow, change the theme and confirm the flow's own colours change at once; change the language and confirm the flow's own text changes to that language; in each case confirm the choice is still shown as selected afterwards.

**Acceptance Scenarios**:

1. **Given** the theme slide is showing, **When** the user chooses Dark, **Then** the flow itself is rendered dark immediately.
2. **Given** the language slide is showing, **When** the user chooses a language, **Then** the flow's own text is shown in that language.
3. **Given** the user chose a search engine, **When** they look at the slide, **Then** the chosen engine is shown as the selected one.
4. **Given** the user made a choice and then moved to another slide and back, **When** they look at the slide, **Then** their choice is still the selected one.
5. **Given** the user changed their mind on a slide, **When** they pick a different option, **Then** the new choice replaces the old one and takes effect immediately.

---

### User Story 3 - Skip the flow and start browsing (Priority: P1)

A user who does not want to be set up taps Skip, lands on the browser immediately with sensible defaults, and is not asked again the next time they open the app.

**Why this priority**: Tied for first because the alternative is holding a user hostage for four screens on an app whose appeal is that it is minimal — and because a Skip that forgets it was used is worse than no Skip at all. It is a separate story because it exits the flow by a different path and is verified by relaunching.

**Independent Test**: On a fresh install, tap Skip on the first slide, confirm the browser opens, then close and relaunch the app and confirm the flow does not reappear.

**Acceptance Scenarios**:

1. **Given** the flow is showing, **When** the user looks at any slide, **Then** a Skip control is available.
2. **Given** the user is on any slide, **When** they skip, **Then** the browser opens immediately.
3. **Given** the user skipped, **When** they close and relaunch the app, **Then** the browser opens directly and the flow does not appear.
4. **Given** the user skipped without making any choice, **When** they open Settings, **Then** the settings are at their defaults, unchanged by the skip.
5. **Given** the user made choices on earlier slides and then skipped, **When** they open Settings, **Then** the choices they did make are kept.

---

### User Story 4 - Keep my place when the language change restarts the flow (Priority: P2)

A user on the language slide picks their language. The screen flickers as the app switches over, and they find themselves still on the language slide — now in the language they chose — rather than back at the welcome slide.

**Why this priority**: It is a refinement of Story 2 that only matters on one slide, but without it that slide's behaviour is actively confusing: the user appears to lose their place as a direct result of making a choice. It is P2 rather than P1 because the choice itself is still saved and applied; only the position is at risk.

**Independent Test**: Advance to the language slide, choose a different language, and confirm that after the screen restarts the flow is still on the language slide, in the new language, with that language shown as selected.

**Acceptance Scenarios**:

1. **Given** the user is on the language slide, **When** they choose a different language, **Then** after the screen restarts the flow is still on the language slide.
2. **Given** the flow returned after a language change, **When** the user looks at the slide, **Then** the newly chosen language is shown as the selected one and the flow's text is in that language.
3. **Given** the flow returned after a language change, **When** the user moves forward, **Then** they continue to the next slide rather than repeating earlier ones.
4. **Given** the user chose a language, **When** the screen restarts, **Then** the choice is not lost and does not have to be made again.

---

### Edge Cases

- **The user rotates the device mid-flow**: the flow stays on the same slide with the same choices selected (FR-016).
- **The user leaves the app mid-flow and returns**: the flow is still showing, on the same slide. Leaving does not count as finishing, so the flag is not written (FR-012).
- **The user force-stops the app mid-flow**: on the next launch the flow appears again from the beginning, because it was never completed or skipped (FR-012). Any choice already made is kept, since choices are saved as they are made.
- **The user chooses the language already in use**: the flow does not restart unnecessarily, and the slide behaves as if a choice were made (FR-015a).
- **The user picks a language, and the screen restarts while they are on the language slide**: they return to that slide, not to the first (FR-015).
- **The user reaches the last slide and moves backward**: earlier slides are shown with the choices they made (FR-006).
- **The user uses the device's Back gesture on the first slide**: the app closes, and because that is neither finishing nor skipping, the flow appears again at the next launch (FR-002a, FR-012). Any choice already made is kept.
- **The user skips on the last slide** rather than finishing: the outcome is identical to finishing — the browser opens and the flag is written (FR-011).
- **The user presses Back on the browser right after leaving the flow**: the app closes. The flow is not behind the browser and cannot be returned to (FR-010a).
- **The device is set to a language the app does not support**: the language slide shows "Follow system" as the current selection, and the flow is displayed in the app's default language (FR-007a).
- **The device has a very small screen or a large font setting**: every slide's content remains reachable, scrolling if necessary, and no control is cut off (FR-019).

## Requirements *(mandatory)*

### Functional Requirements

#### The flow and its slides

- **FR-001**: The flow MUST consist of exactly four slides, in this order: welcome, app language, theme, search engine.
- **FR-002**: The user MUST be able to move forward through the slides, and backward to any slide already seen.
- **FR-002a**: The device's own Back gesture MUST move to the previous slide. On the first slide it MUST leave the app, and leaving this way MUST NOT record first-run as complete (FR-012), so the flow appears again at the next launch.
- **FR-002b**: A single forward control MUST occupy the same position on every slide. On the last slide it MUST carry a finishing label rather than a "next" one, so the end of the sequence is visible before the user taps, and tapping it there MUST complete the flow (FR-010).
- **FR-003**: The flow MUST show the user where they are in the sequence and how many slides there are.
- **FR-004**: The welcome slide MUST introduce the app and MUST state that it collects no personal data and sends nothing to any ThirtySix-run server. This is a requirement, not decoration.
- **FR-005**: The language slide MUST offer "Follow system" plus the eight supported languages, each written in its own language.
- **FR-006**: Each choice slide MUST show the option currently in effect as selected, including when the user returns to it from a later slide.
- **FR-007**: The theme slide MUST offer Light, Dark and System default; the search engine slide MUST offer Google, DuckDuckGo and Bing.
- **FR-007a**: Where the device is set to a language the app does not support, the language slide MUST show "Follow system" as selected, and the flow MUST be displayed in the app's default language.

#### Saving and applying choices

- **FR-008**: A choice MUST be saved the moment it is made. It MUST NOT be held until the end of the flow.
- **FR-009**: A choice MUST take effect immediately and visibly within the flow itself: a theme choice recolours the flow, a language choice changes the flow's own text.
- **FR-010**: On finishing the last slide, the app MUST record that first-run is complete and open the browser.
- **FR-010a**: On leaving the flow by finishing or by skipping, the flow MUST be removed from the navigation history. Using the Back gesture from the browser's first page MUST leave the app, exactly as on a normal launch, and MUST NOT return to the flow.
- **FR-011**: Skipping MUST also record that first-run is complete, and MUST open the browser. Skipping MUST NOT change any setting, and MUST NOT undo a choice already made.
- **FR-012**: Leaving the flow without finishing or skipping — by force-stopping the app, or by leaving and not returning — MUST NOT record first-run as complete. Choices already made are kept, because they are saved as they are made.

#### Reaching the flow

- **FR-013**: On start-up the app MUST open the flow when first-run has not been recorded as complete, and the browser when it has.
- **FR-014**: The start-up decision MUST be settled before the first screen is shown. The browser MUST NOT appear, even briefly, before being replaced by the flow.
- **FR-014a**: A Skip control MUST be available on every slide, including the first and last.

#### Surviving interruption

- **FR-015**: Changing the app language restarts the screen. The flow MUST return to the slide the user was on, with the new language selected and the flow's text in that language.
- **FR-015a**: Choosing the language already in effect MUST NOT cause an unnecessary restart.
- **FR-016**: Rotating the device MUST keep the flow on the same slide with the same choices selected.

#### Standing constraints

- **FR-017**: Every user-visible string introduced by this feature MUST be provided in all 8 supported locales, with parity enforced at build time.
- **FR-018**: Every interactive control MUST carry a localized screen-reader label, announce its selected state where it has one, and meet the minimum touch-target size.
- **FR-019**: Every slide MUST remain fully usable at the smallest supported screen width and at large font settings, scrolling where needed, with no control cut off or unreachable.
- **FR-020**: The flow MUST NOT require or wait on any network access, and MUST behave identically with no connectivity.
- **FR-021**: The flow MUST NOT ask for any account, sign-in, personal detail or permission.

### Key Entities

- **Onboarding flow**: the four-slide first-run sequence. It owns no settings of its own — it is a presentation of settings that already exist — and its only persistent effect is recording that first-run is complete.
- **First-run flag**: the existing record of whether the flow has been completed or skipped. Default is "not complete". Written exactly once, by finishing or by skipping; never written by leaving.
- **Current slide position**: which slide the user is on. Not persisted beyond the flow, but MUST survive a screen restart and a rotation (FR-015, FR-016).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh install, 100% of launches show the flow rather than the browser; after finishing or skipping, 100% of subsequent launches show the browser directly, verified over at least 3 relaunches.
- **SC-002**: Zero frames of the browser appear before the flow on a first launch, verified by frame-by-frame inspection of a recorded cold start.
- **SC-003**: A theme or language choice is visible in the flow itself within 1 second of being made, verified for all 3 themes and at least 3 languages.
- **SC-004**: 100% of choices made in the flow are shown identically in Settings afterwards, verified across all 3 settings.
- **SC-005**: After a language change, the flow returns to the same slide it was on in 100% of attempts, verified for at least 3 different languages from at least 2 different slides.
- **SC-006**: Rotating the device keeps the flow on the same slide with the same selections in 100% of attempts, verified on at least 2 slides.
- **SC-007**: Skipping from any of the 4 slides opens the browser and prevents the flow reappearing on relaunch, in 100% of attempts, and leaves settings exactly as they were.
- **SC-008**: A user who accepts the default on every slide completes the whole flow in exactly **4 interactions** — one forward tap per slide, the fourth of which finishes — and can leave it in **1** interaction from any slide. Each choice the user actually changes adds exactly one interaction, so changing all three settings costs 7.
- **SC-009**: 100% of strings introduced by this feature are translated in all 8 locales, enforced at build time, with no missing, extra or unused keys.
- **SC-010**: 100% of interactive controls in the flow have a localized screen-reader label, announce their selected state, and meet the minimum touch-target size, verified by a screen-reader pass in at least 2 locales, one of them in a non-Latin script.
- **SC-011**: Every slide is fully usable at the smallest supported width and at the largest font setting, with zero controls cut off or unreachable, verified on all 4 slides.
- **SC-012**: The flow behaves identically with the device fully offline, verified in airplane mode.
- **SC-013**: The release APK grows by at most **204,800 bytes** over the Spec 017 baseline of 3,126,212 bytes — that is, it is at most **3,331,012 bytes**.
- **SC-014**: The app launches successfully on 100% of attempts on both the minimum and the maximum supported Android versions, in both the first-run and the already-completed state.
- **SC-015**: After leaving the flow by finishing or by skipping, using the Back gesture from the browser's first page leaves the app and never returns to the flow, in 100% of attempts, verified for both exits.

## Assumptions

- **A1**: The first-run flag, the three settings and the means of changing them all already exist and are reused unchanged. This feature adds no new setting and no new stored value.
- **A2**: The option lists and their labels — the eight languages by endonym, the three themes, the three search engines — are the ones the Settings screen already uses, so the two screens can never disagree about what is on offer.
- **A3**: The choices offered here are the three the Settings screen already exposes and that a user is most likely to want different on day one. Others that Settings offers (history retention, dynamic colour, clearing data) are deliberately not part of the first-run flow.
- **A4**: Skipping leaves the settings untouched rather than writing the current defaults explicitly. The observable result is the same, and it keeps skip from being a disguised write.
- **A5**: The flow appears once per install. It is not re-runnable from Settings; a user who wants to change something goes to Settings, where all three choices already live. Once left, it is also unreachable by the Back gesture (FR-010a) — the first-run flag governs the next launch, and removing the flow from history governs the current one.
- **A6**: A language change restarting the screen is inherent to how the platform applies a per-app language, and is accepted rather than worked around. What is required is that the user does not lose their place (FR-015).
- **A7**: No new dependency is expected. If planning finds one is unavoidable, its version is looked up at the moment of addition and its native-code status verified, per Constitution §IX.
- **A8**: Verification is done on the emulators the project already uses — one at the minimum supported Android version and one at the maximum. No criterion here is a hardware-class performance target, so this spec adds nothing to the project's deferred hardware-measurement backlog.
- **A9**: The welcome slide's privacy statement says the same thing the About section already says. Re-stating it rather than inventing a second wording keeps the two from drifting apart.
- **A10**: The flow carries no illustration beyond what the app already ships. The app's own mark is available from Spec 017 if the welcome slide needs one.

## Dependencies

- **Spec 006** (datastore-settings) — supplies the first-run flag and the stored settings this flow presents, along with the means of writing them.
- **Spec 003** (theme-typography-darkmode) — supplies the themes offered on the theme slide and applied live.
- **Spec 004** (localization-multi-language) — supplies the eight locales the flow is translated into and the languages it offers.
- **Spec 016** (settings-screen) — supplies the option lists and labels reused here, and established the save-on-select behaviour this flow matches.
- **Spec 017** (splash-screen) — deliberately did **not** read the first-run flag, leaving the start-up decision to this spec. It also supplies the app's mark, should the welcome slide use it.

## Out of Scope

- Re-running the flow from Settings, or any "show onboarding again" control.
- Any account, sign-in, or personal detail.
- Any permission request. The app asks for none at first run today, and this flow does not change that.
- Settings beyond the three named: history retention, dynamic colour and clearing data stay in Settings only.
- Custom illustrations or animation per slide beyond the artwork the app already ships.
- Changing what the browser shows after the flow finishes. It opens exactly as it does today.
