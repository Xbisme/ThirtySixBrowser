# Feature Specification: Splash Screen

**Feature Branch**: `017-splash-screen`
**Created**: 2026-09-15
**Status**: Draft
**Input**: User description: "Spec 017 `splash-screen` — the second spec of Phase 4. A branded launch experience: the app's own mark shown while the app starts, plus the app identity mark that replaces the project-template artwork still shipping today. Scope below was decided with the product owner in a pre-spec discussion on 2026-09-15. 1. Brand mark — the app currently ships the artwork that came with the project template (a generic robot on a green field), which cannot go to store listing. The owner approved a mark in the app's existing brand colours: a filled circular disc carrying the numerals '36'. The numerals are filled solid, deliberately not cut out of the disc, because a cut-out reveals whatever sits behind it and turned the numerals dark against the disc on a dark background — the solid form looks identical on every background. The same mark serves both the launch screen and the app's identity on the device. 2. Duration — the launch screen is shown only while the app is genuinely starting and is dismissed the moment it is ready; it MUST NOT be held open artificially. 3. Motion — the mark animates on Android 12 and newer, and is shown still on Android 7.0 through 11, where the platform cannot animate it. Both paths MUST be verified. 4. Out of scope — deciding where to go after launch. Spec 018 (onboarding-flow) owns first-run routing and will add it on top of this spec. Standing constraints: no hardcoded values; 8 locales with lint-enforced parity for any new string; fully offline; Constitution v1.3.0."

## Clarifications

### Session 2026-09-15 (pre-spec discussion)

- Q: The app still ships the project template's artwork as its identity mark. Does replacing it belong in this spec, or in a separate one? → A: **It belongs in this spec.** Rationale: the launch screen's entire visible content is the identity mark, so a spec that adds a launch screen while leaving the template artwork in place would ship a branded launch experience showing another project's generic artwork — and the roadmap entry for this spec names branding explicitly. Deferring the mark to a later spec was rejected on that basis, and because the app cannot be submitted to the store with template artwork. **Consequence: the identity mark is replaced everywhere it appears (FR-001–FR-006), and this spec is the one that removes the last template artwork from the project.**
- Q: Should the numerals be cut out of the disc, letting the background show through, or filled solid? → A: **Filled solid.** Rationale: the cut-out form was built and rejected on inspection — against a dark background the numerals read as near-black against the coloured disc, so the mark changed character between light and dark and lost contrast in dark. The solid form renders identically on every background, which matters because the mark appears on a light launch screen, a dark launch screen, and against a device background the app does not control. **Consequence: FR-003 states the solid fill normatively, and SC-002 verifies the mark is visually identical across backgrounds.**
- Q: How long is the launch screen held? → A: **Only as long as the app genuinely needs to start; it is dismissed as soon as the app is ready, and is never held open to show the brand for longer.** Rationale: this is the platform's own guidance, and time added purely to display a logo is time taken from the user on every single launch. A deliberate minimum display time was offered and rejected on that basis. **Consequence: FR-008 forbids artificially extending the launch screen, and SC-004 measures that launch is no slower than it is today.**
- Q: Should the mark animate? → A: **Yes on Android 12 and newer; still on Android 7.0 through 11.** Rationale: the platform can only animate the launch mark from Android 12 onward, so animation is unavailable on roughly the older half of the supported range and the still mark is the only possible behaviour there. **Consequence: two visual variants exist and both MUST be verified on real Android versions (FR-009, FR-010, SC-005); the animation MUST NOT lengthen the launch screen, per FR-008.**
- Q: Does this spec decide what screen appears after launch? → A: **No.** The app opens to the browser, exactly as it does today. Rationale: first-run routing is the subject of Spec 018 (onboarding-flow), which depends on this spec; deciding it here would put one spec's work in another and break the phase order the project follows. **Consequence: the stored first-run flag is not read by this spec (FR-016), and what follows the launch screen is unchanged.**

### Session 2026-09-15

- Q: When the new mark replaces the old one, should the app keep shipping per-density bitmap copies and a separate round-icon variant, or drop them and ship the single scalable mark only? → A: **Regenerate the per-density bitmaps from the new mark, because the oldest supported Android version cannot read the scalable form, and retire the separate round variant by pointing both identity entries at the same mark.** Rationale: leaving the existing bitmaps in place would leave the template artwork shipping on Android 7.0 while FR-001 appeared to pass, and deleting them outright would leave Android 7.0 with the system's default placeholder instead of the app's mark. A separate round variant is a second copy of the same mark that can drift from it and that modern devices do not use, so a single mark for both entries removes a whole class of inconsistency. **Consequence: FR-004 is restated to distinguish the scalable mark from the bitmap fallback it requires, FR-004a and FR-004b are added, and SC-007 and SC-013 make both halves verifiable.**
- Q: How is "the app is ready" decided — the moment the first screen can be drawn, or the moment the browser has finished restoring the user's saved tabs? → A: **The moment the first screen can be drawn.** The launch screen is dismissed once the browser window renders; restored tabs appear as they load, exactly as they do today. Rationale: waiting for tab restoration would put reading from storage on the critical path of every launch, and the wait would grow with the number of saved tabs — up to the 50 the app allows — which is precisely the artificially extended launch screen FR-008 forbids. The intermediate state this accepts is the app's existing startup behaviour and is not introduced by this feature. **Consequence: FR-008 now defines "ready" normatively, and SC-004's measurement point is stated in the same terms.**
- Q: How should the launch screen behave when the user has chosen an in-app theme that differs from their device theme? → A: **Accept the mismatch: the launch screen follows the device theme, the app's chosen theme takes over at the first browser frame, and what is verified is that the change is one clean transition with no flash of a third colour.** Rationale: the system draws the launch screen before the app is running, so at that moment the app's stored theme is genuinely unavailable — no amount of app code can make the first frame match it. Holding the launch screen until the stored theme is read was rejected because it contradicts FR-008 and makes every launch slower for a cosmetic gain; a theme-neutral brand background was rejected because it would mismatch *both* themes rather than one, and would abandon the cold-start background colours Spec 003 introduced specifically to prevent a jarring launch. **Consequence: FR-012 is extended to state the accepted behaviour normatively, SC-003 is restated to test for a single clean transition rather than for a match that cannot exist, and A3 is confirmed rather than assumed.**
- Q: Should the launch-screen mark carry a spoken screen-reader description, or be marked as decorative so screen readers skip it? → A: **Decorative — screen readers skip it.** Rationale: the launch screen carries no control, cannot be acted on and dismisses itself, and the app's name has already been announced by the launcher, so a second announcement would interrupt without adding information. The Constitution's §VIII rule that decorative-only imagery be explicitly marked as such is therefore the rule that applies, and the original FR-018 contradicted it. Announcing the mark, or adding a description to the device identity mark separate from the app name, were both rejected as repeating what the system already says. **Consequence: FR-018 is replaced, the spec introduces no user-visible string at all (A6 confirmed), and SC-012 is restated to record that outcome rather than to test translations that do not exist.**

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the app's own mark while it starts (Priority: P1) 🎯 MVP

A user taps the app on their device. Instead of a blank or plain-coloured window while the app starts, they see the app's mark centred on a background that matches the theme they are using, and the browser appears as soon as the app is ready.

**Why this priority**: It is the whole point of the feature and the smallest slice that delivers it — a launch screen that appears, shows the brand, and gets out of the way. Every other story either supplies the artwork it displays or refines how it behaves.

**Independent Test**: Cold-start the app from the launcher — with the app not already running — on both a light and a dark device theme, and confirm the mark is shown centred on the matching background and that the browser appears without the launch screen lingering.

**Acceptance Scenarios**:

1. **Given** the app is not running, **When** the user launches it from the device's app list, **Then** the app's mark is shown centred on a background matching the active theme.
2. **Given** the device is in light theme, **When** the app is launched, **Then** the launch background is the app's light background colour; **And given** the device is in dark theme, the launch background is the app's dark background colour.
3. **Given** the launch screen is showing, **When** the app becomes ready, **Then** the launch screen is dismissed and the browser is shown.
4. **Given** the app is launched, **When** the transition from launch screen to browser occurs, **Then** no blank, white or mismatched-colour frame appears between them.
5. **Given** the app is already running in the background, **When** the user returns to it, **Then** no launch screen is shown.

---

### User Story 2 - Recognise the app by its own mark on the device (Priority: P1)

A user looking through their app list sees the browser represented by its own mark, in the app's colours, rather than by the generic artwork that ships with new projects. The mark is legible at the small size the device draws it.

**Why this priority**: It is tied for first because the launch screen has nothing to show without it, and because the app cannot be submitted to a store while it wears another project's template artwork. It is a separate story because it is verified in a different place — the device's app list and settings, not the app itself.

**Independent Test**: Install the app and inspect its mark in the device's app list, in the device's application settings, and in the recent-apps view, on a device whose app-icon shape differs from a plain circle. Confirm it shows the app's own mark and that the numerals are legible.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user views it in the device's app list, **Then** it is represented by the app's own mark and no template artwork appears anywhere.
2. **Given** the device applies its own shape to app marks, **When** the mark is drawn in that shape, **Then** nothing meaningful is cut off.
3. **Given** the mark is drawn at the small size used in a device app list, **When** the user looks at it, **Then** the numerals are legible.
4. **Given** the device offers a themed or monochrome treatment of app marks, **When** that treatment is active, **Then** the app's mark remains recognisable.

---

### User Story 3 - See the mark animate on a modern device (Priority: P2)

A user on a recent Android version sees the mark animate briefly as the app starts, giving the launch a finished feel. A user on an older Android version sees the same mark, still, and their app starts just as quickly.

**Why this priority**: It is a refinement of a launch screen that already works without it. It ranks below the first two because it is invisible on roughly the older half of the supported Android range and must never delay the launch.

**Independent Test**: Cold-start the app on a device running Android 12 or newer and confirm the mark animates; cold-start it on a device running Android 7.0 and confirm the same mark is shown still, with no error and no visible delay.

**Acceptance Scenarios**:

1. **Given** a device running Android 12 or newer, **When** the app is cold-started, **Then** the mark animates.
2. **Given** a device running Android 7.0 through 11, **When** the app is cold-started, **Then** the mark is shown still and the launch completes normally.
3. **Given** the animation has not finished, **When** the app becomes ready, **Then** the launch screen is still dismissed without waiting for the animation to complete.
4. **Given** either device, **When** the user compares launch time with and without this feature, **Then** launch is not measurably slower.

---

### Edge Cases

- **The app is launched while already running in the background**: no launch screen is shown; the user returns to what they were doing (FR-011).
- **The device theme is changed while the app is not running**: the next launch shows the launch background for the newly active theme (FR-007).
- **The user has chosen an in-app theme that differs from the device theme** (possible since Spec 016): the launch screen follows the device theme, and the app's own theme takes over at the first browser frame. The user sees exactly one colour change, with no third colour and no repeated flip, in both mismatch directions (FR-012a, SC-003).
- **The app is launched on the oldest supported Android version (7.0)**: the launch screen appears with the still mark, with no error and no crash (FR-010).
- **The device applies an aggressive shape mask to app marks** (circle, squircle, teardrop, rounded square): the mark stays within the region that is always visible and nothing meaningful is clipped (FR-005).
- **The device draws app marks in a themed or monochrome treatment**: the mark remains recognisable rather than becoming a solid blob (FR-006).
- **The app is relaunched immediately after being dismissed**, while the system may still hold it warm: no launch screen flicker or double-draw occurs.
- **The user has many saved tabs** — up to the 50 the app allows: the launch screen is dismissed when the browser window can be drawn, not when the tabs have finished restoring, so a large number of saved tabs does not lengthen the launch screen (FR-008, SC-004).
- **Animation is unavailable or disabled** — an older Android version, or a device where the user has turned animations off in accessibility settings: the still mark is shown and launch proceeds normally (FR-010).

## Requirements *(mandatory)*

### Functional Requirements

#### Brand mark

- **FR-001**: The app MUST NOT ship any artwork originating from the project template. After this feature, no template artwork remains in the project.
- **FR-002**: The app's identity mark MUST be a filled circular disc carrying the numerals "36", drawn in the app's existing brand colours — a gradient from the app's primary brand colour to its accent colour, as already defined by the app's theme.
- **FR-003**: The numerals MUST be filled solid in the app's off-white surface colour. They MUST NOT be cut out of the disc, and no part of the mark may derive its colour from whatever is behind it.
- **FR-004**: The mark MUST be defined as scalable artwork that renders at every size the device requests. Scalable artwork is the form used everywhere the device can read it, including the launch screen.
- **FR-004a**: Because the oldest supported Android version cannot read the scalable form for the app's identity, per-density bitmap copies MUST be provided for it, and each MUST be generated from the same approved mark so it cannot differ from the scalable form. Every pre-existing bitmap carrying template artwork MUST be replaced, not merely supplemented — none may survive in the shipped build.
- **FR-004b**: The app MUST NOT ship a separate round variant of the mark. Both identity entries MUST resolve to the same mark, so the two can never diverge.
- **FR-005**: The mark MUST stay within the region that remains visible under every shape the device may apply to app marks, so that no part of the numerals or the disc edge is clipped.
- **FR-006**: The mark MUST supply the monochrome treatment the device uses for themed app marks, and MUST remain recognisable — not a filled silhouette — under it.
- **FR-007**: The same mark MUST be used for both the launch screen and the app's identity on the device, so the two can never diverge.

#### Launch screen behaviour

- **FR-008**: The launch screen MUST be dismissed as soon as the app is ready to show its first screen, where **ready means the browser window can be drawn** — not that saved tabs have finished being restored. It MUST NOT be held open for a minimum display time, MUST NOT be extended to let an animation finish, and MUST NOT wait on reading anything from storage.
- **FR-009**: On Android 12 and newer, the mark MUST animate.
- **FR-010**: On Android 7.0 through 11, the mark MUST be shown still, with no error, no crash and no additional delay. This is the only possible behaviour on those versions and MUST be verified rather than assumed.
- **FR-011**: The launch screen MUST appear only when the app is starting from cold. Returning to an already-running app MUST NOT show it.
- **FR-012**: The launch screen background MUST match the app's existing launch background colours for light and dark, and the handover to the first app screen MUST NOT show a blank, white or mismatched-colour frame.
- **FR-012a**: The launch screen MUST follow the **device** theme, because the system draws it before the app can read its own stored theme. Where the user's in-app theme differs from the device theme, the app's theme MUST take over at the first browser frame, and the change MUST be a single clean transition — light to dark, or dark to light, with no intermediate third colour and no repeated flip. The app MUST NOT delay the launch screen in order to make the first frame match (FR-008).
- **FR-013**: The launch screen MUST NOT require or wait on any network access, and MUST appear identically with no connectivity.
- **FR-014**: The launch screen MUST NOT read, display or transmit any user data.

#### Preserving existing behaviour

- **FR-015**: The app MUST continue to launch successfully on every supported Android version. In particular, the launch window's styling MUST remain compatible with the app's existing screen host, whose requirements Spec 016 established; an incompatible style crashes the app at launch and MUST be verified against.
- **FR-016**: This feature MUST NOT read the stored first-run flag and MUST NOT change what screen the app opens to. The app continues to open to the browser. First-run routing belongs to Spec 018.
- **FR-017**: This feature introduces no new user-visible string. No string is added, and the existing 8-locale set MUST remain at full parity, enforced at build time. Should planning find that a string is unavoidable after all, it MUST be provided in all 8 locales.
- **FR-018**: The launch-screen mark MUST be treated as decorative and MUST be explicitly marked so that screen readers skip it, per Constitution §VIII. It MUST NOT be given a spoken description, because the launch screen carries no control, cannot be acted on, and the app's name is already announced by the device when the app is started.

### Key Entities

- **Brand mark**: the app's visual identity — a filled circular disc in the brand gradient carrying solid numerals "36". Used for both the launch screen and the app's identity on the device. Has a still form, an animated form used only where the platform supports it, and a monochrome form for themed treatments.
- **Launch screen**: the system-drawn window shown while the app starts, consisting of the brand mark on a theme-matched background. It has no content of its own and no interaction.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a cold start, 100% of launches show the app's mark on a background matching the active device theme, verified in both light and dark on Android 7.0 and on Android 16.
- **SC-002**: The mark is visually identical on a light background and a dark background — the numerals keep the same colour and the same contrast against the disc in both — verified by side-by-side comparison.
- **SC-003**: The handover from launch screen to the first app screen shows zero blank, white or mismatched-colour frames, verified by frame-by-frame inspection of a recorded cold start in both light and dark, on Android 7.0 and on Android 16. Where the in-app theme differs from the device theme, the recording shows exactly one colour change — device theme to app theme — with no third colour and no repeated flip, verified for both mismatch directions.
- **SC-004**: Cold-start time to the first drawn browser frame — the same moment at which FR-008 dismisses the launch screen — is no more than 5% slower than before this feature, measured as the median of at least 10 cold starts on the same device and build type, on the minimum supported Android version. The measurement MUST NOT include the time taken for saved tabs to finish restoring, which this feature does not change.
- **SC-005**: The mark animates on 100% of cold starts on Android 12 and newer, and is shown still with no error on 100% of cold starts on Android 7.0, verified on a real device or emulator of each.
- **SC-006**: The app launches successfully on 100% of attempts on both the minimum and the maximum supported Android versions, with zero crashes attributable to the launch window's styling.
- **SC-007**: Zero artwork originating from the project template remains in the project, verified by inspection of the shipped build — including every per-density bitmap copy, each of which shows the new mark.
- **SC-008**: The mark's numerals are legible at the smallest size the device draws it in its app list, verified on at least 2 devices or emulators with different screen densities.
- **SC-009**: Nothing meaningful is clipped from the mark under all 4 of the common device mark shapes — circle, squircle, rounded square and teardrop.
- **SC-010**: The release APK grows by at most **204,800 bytes** over the Spec 016 baseline of 3,122,115 bytes — that is, it is at most **3,326,915 bytes**. The supporting library for the launch screen contributes negligibly, so the entire allowance is for the feature's own artwork.
- **SC-011**: The launch screen appears identically with the device fully offline, verified in airplane mode.
- **SC-012**: This feature adds zero new strings, and the existing 8-locale set still has no missing, extra or unused keys, enforced at build time. A screen-reader pass over a cold start announces nothing for the launch screen itself.
- **SC-013**: The app's identity mark is the same on the oldest and the newest supported Android versions, and exactly one mark is shipped — no separate round variant exists in the build.

## Assumptions

- **A1**: The app's existing brand colours — the primary brand colour, the accent colour and the off-white surface colour already defined by the theme — are the correct palette for the mark. No new colour is introduced.
- **A2**: The existing launch background colours for light and dark, added when the app's cold-start appearance was first addressed, are the correct launch backgrounds and are reused unchanged.
- **A3**: Confirmed by clarification, not assumed. The launch screen follows the **device** theme, not the app's own stored theme, because the system draws it before the app can read stored settings. A user whose in-app theme differs from their device theme therefore sees the launch background change once when the browser appears. This is inherent to how launch screens work on Android and is accepted rather than worked around; FR-012a states the accepted behaviour and SC-003 verifies it.
- **A4**: The supporting library for the launch screen contains no native code and therefore does not affect the project's native-library alignment requirement. This MUST be re-verified at the moment it is added, per Constitution §IX.
- **A5**: The supporting library brings with it a component the project already includes at a newer version, so its size contribution to the shipped app is negligible. This is confirmed by measurement during planning, not assumed at merge.
- **A6**: The feature introduces no new user-visible text. This is now settled rather than assumed: the launch screen shows no text, and its mark is decorative and deliberately unannounced (FR-018). Note that a screen-reader pass is still required by SC-012 — to confirm that nothing is announced, which is the opposite of the usual check.
- **A7**: Verification is done on the emulators the project already uses — one at the minimum supported Android version and one at the maximum — as with every prior spec. No Pixel 5-class hardware is required for any criterion in this spec; none of them is a hardware-class performance target.
- **A8**: The animated form of the mark is a short, simple motion — the disc scaling in and the numerals fading in. Exact timing is a planning detail, bounded by FR-008: it must never delay the launch.
- **A9**: The mark is authored from the artwork approved by the product owner on 2026-09-15. The numerals are converted to outlines in the final artwork, because the platform's scalable-artwork format cannot draw live text.
- **A10**: The per-density bitmap copies required by FR-004a are generated from the approved mark rather than drawn by hand, so they cannot drift from the scalable form. The set of densities already shipped is the correct set; no density is added or removed.

## Dependencies

- **Spec 002** (clean-architecture-skeleton-di) — supplies the app's single screen host, which the launch screen attaches to. This is the dependency the roadmap records.
- **Spec 003** (theme-typography-darkmode) — supplies the brand colours the mark uses and the launch background colours for light and dark, which this feature reuses unchanged.
- **Spec 016** (settings-screen) — established the requirement that the launch window's styling stay compatible with the app's screen host. FR-015 exists because of it.

## Out of Scope

- Deciding what screen the app opens to, including any first-run or onboarding routing — this is **Spec 018** (onboarding-flow), which depends on this spec.
- Any launch-time work to make the app start faster. This spec must not make launch slower (SC-004) but does not set out to make it faster.
- A store listing graphic, promotional artwork, or any asset not shipped inside the app.
- Changing the app's name, or any text shown alongside the mark.
