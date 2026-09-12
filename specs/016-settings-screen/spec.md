# Feature Specification: Settings Screen

**Feature Branch**: `016-settings-screen`
**Created**: 2026-09-11
**Status**: Draft
**Input**: User description: "Spec 016 `settings-screen` — the first spec of Phase 4. A Settings screen, reached from the browser's overflow menu (the slot Spec 015 reserved beside Bookmarks · History · Downloads), replacing the Spec 002 placeholder screen. Scope below was decided with the product owner in a pre-spec discussion on 2026-09-11. 1. Theme — Light / Dark / System. The setting is already persisted and applied app-wide (Spec 006); this spec adds the control. A change applies immediately, without restarting the app. 2. Dynamic color — an on/off switch, shown only on Android 12+ where dynamic color exists. Default ON, which is today's behaviour. New persisted setting. 3. Search engine — Google / DuckDuckGo / Bing. Already persisted and already used when a query is submitted (Spec 010); this spec adds the control. 4. App language — "Follow system" plus the 8 supported locales (EN, VI, DE, RU, KO, JA, ZH, FR). MUST work on every supported Android version (7.0–16) and take effect without an app restart. Decision: use the platform/AndroidX per-app language mechanism that Constitution §VIII names (`AppCompatDelegate.setApplicationLocales`), which makes the SYSTEM the single source of truth — a language chosen in Android 13+ system settings and one chosen in-app can never disagree. Consequences to carry into planning: (a) adds `androidx.appcompat` (latest stable verified 2026-09-11 as 1.8.0; zero native libraries across its dependency graph — re-verify at implementation per §IX); (b) MainActivity must become an `AppCompatActivity` and its window theme must descend from an AppCompat theme, without breaking Spec 003's cold-start windowBackground fix; (c) the DataStore key `language_override`, stored since Spec 006 but never applied anywhere, is retired rather than kept as a second copy that would go stale (the app is unreleased, so no user data migrates); (d) a language change recreates the activity, so the page currently open reloads — the same thing a rotation does today. 5. History retention — a user-selectable retention window replacing the fixed 90 days (`BrowserLimits.MAX_HISTORY_DAYS`, pruned once per process start by `PruneOldHistoryUseCase` since Spec 014). A small, bounded set of choices; there MUST NOT be an unlimited / "keep forever" option, because Spec 014 measured an unbounded history table as a real memory risk on minSdk-24 devices. The default stays 90 days. 6. Clear browsing data — one Chrome-style dialog with checkboxes: browsing history · cookies and site data · cached images and files (which includes the app's own favicon cache and tab-screenshot previews). All-time only, with explicit confirmation. The Downloads list and downloaded files are NOT touched, and open tabs are not closed. Incognito decision: if cookies are cleared while incognito tabs are open, the Spec 012 cookie snapshot is emptied too, so closing the last incognito tab does not bring the cleared cookies back. 7. About — the app version, plus a short statement that the app collects no data and sends nothing to any ThirtySix-controlled server. Explicitly out of scope: a configurable home page URL; clearing the downloads list; time-ranged clearing (last hour / day / week); onboarding itself (Spec 018). The language, theme and search-engine choosers SHOULD be built so Spec 018's onboarding can reuse them. Standing constraints: all strings in 8 locales with lint-enforced parity; TalkBack labels and 48dp touch targets on every control; no hardcoded values; fully offline; Constitution v1.3.0."

## Clarifications

### Session 2026-09-11 (pre-spec discussion)

- Q: How does in-app language switching work on Android 7.0–12, where the platform itself offers no per-app language setting? → A: **Through the per-app language mechanism Constitution §VIII names, with the platform as the single source of truth.** On Android 13+ the in-app choice and the device's own per-app language setting are one stored value, so they can never disagree; on Android 7.0–12 the same mechanism supplies the behaviour the platform lacks. Rationale: a second, app-owned copy of the preference would silently go stale the moment a user changed the language from system settings, and the platform's guidance is to use its public mechanism rather than custom logic. The alternative that avoided any supporting library — offering the in-app choice on Android 13+ only — was rejected because it leaves Android 7.0–12 users with no in-app control and would require amending §VIII. **Consequence: the language value Spec 006 stored but never applied is retired rather than kept (FR-017), and planning must carry the supporting-library addition together with the changes it forces on the app's main screen host and window theme (see the checklist's planning notes).**
- Q: Beyond theme, language, search engine and clearing data, what else belongs in this spec? → A: **An About section, a user-selectable history retention window, and a dynamic color switch.** A configurable home page was considered and excluded: today's home address is a fixed value consumed by the new-tab, Home-button and incognito-tab paths of three earlier specs, so making it editable is a cross-cutting change with no recorded user demand. **Consequence: the fixed home page stays, and clearing the Downloads list — which Spec 015 deferred to this spec — is deliberately not taken up either.**
- Q: What does clearing browsing data consist of? → A: **One dialog with three independently selectable categories — browsing history, cookies and site data, cached images and files — cleared together on a single confirmation.** The cache category includes the app's own cached site icons and tab preview images, because those are stored renderings of sites the user visited. Rationale: this is the model mainstream browsers have already taught users, and one confirmation for a multi-part destructive action is clearer than a separate button and confirmation per category. **Consequence: the Downloads list, downloaded files, bookmarks and open tabs are never touched by this action (FR-031).**
- Q: If the user clears cookies while incognito tabs are open, what happens when the incognito session ends? → A: **The normal-browsing cookies set aside for restoring at the end of the incognito session are discarded too, so nothing comes back.** Rationale: Spec 012 sets the normal-browsing cookies aside while incognito is active and restores them when the last incognito tab closes; without this rule, a user who cleared their cookies mid-session would find them silently restored minutes later — precisely the outcome they acted to prevent. Disabling the cookie option while incognito tabs are open, or force-closing those tabs before clearing, were both rejected as friction for a case the discard rule handles invisibly. **Consequence: the incognito tabs stay open and keep working; only the pending restore is emptied (FR-030).**

### Session 2026-09-11

- Q: Which history retention windows can the user choose? → A: **7, 30, 90 and 180 days, defaulting to 90.** Rationale: the set offers a short window for privacy-minded users and a longer one than today, while the 180-day ceiling stays within twice the 90-day window Spec 014 validated against its measurement of roughly 300 bytes of memory per history entry; a one-year ceiling was offered and not chosen on that basis. **Consequence: FR-020 names the four values normatively, and A1 is confirmed rather than assumed.**
- Q: When the user shortens the retention window, is the deletion of now out-of-window history confirmed first, and when does it happen? → A: **A warning is shown and nothing is deleted unless the user confirms; on confirmation the deletion happens immediately.** Rationale: deleted history cannot be recovered, so an accidental tap must not cost data, and deleting at once makes the result match the choice the user just made. Deleting silently on selection, and deleting silently at the next app start, were both rejected. **Consequence: FR-021 and FR-022 stand as written, and A2 is confirmed rather than assumed.**
- Q: On what device is the speed of clearing browsing data verified? → A: **The gate is one that can be verified on an emulator running the minimum supported Android version: with 10,000 history entries and 50 open tabs, clearing all three categories completes with no ANR, visible in-progress feedback and an app that responds throughout. The 3-second target on Pixel 5-class hardware with a release build is recorded when such hardware is available, and does not gate the spec.** Rationale: the project currently has no Pixel 5-class hardware, and Spec 014's T103b and Spec 015's SC-006 are both deferred for exactly that reason; a gate that can be closed now avoids a third deferral, while keeping the hardware figure honest instead of substituting an emulator number for it. **Consequence: SC-009 is split into a gating criterion and a recorded, non-gating hardware measurement.**
- Q: How is the APK size budget set when the size cost of the library needed for in-app language switching has not been measured yet? → A: **Measure first: planning measures that library's contribution to the release APK after shrinking, and the budget becomes that measured figure plus 200 KB for the feature itself.** Rationale: any fixed number chosen now would be a guess in one direction or the other, while the 200 KB allowance for the feature's own code keeps the discipline Specs 014 and 015 held to. A fixed +400 KB, a flat +200 KB that includes the library, and no budget at all were all rejected. **Consequence: SC-014 carries no numeric value until planning supplies the measurement, and it MUST be filled in before task generation.**
- Q: *(decided during planning, once the platform findings made it a product choice)* How are cookies and site data cleared, given that the platform's long-standing storage-clearing call leaves service workers and Cache Storage behind on every device? → A: **Through an additional platform-support library whose call removes cookies and every kind of site data together — including service workers and Cache Storage — wherever the device's web engine supports it; older engines fall back to clearing cookies and web storage only.** Rationale: the older call cannot meet FR-028 on any device, while the newer one meets it on every engine recent enough to provide it. Relying on the older call alone was rejected because FR-028 would have had to be narrowed. **Consequence: on supported engines, clearing cookies and site data also empties the web page cache, because the engine removes the two together (A16); on older engines service workers and Cache Storage survive a clear (A17); and the feature now adds two supporting libraries rather than one (A13, SC-014).**

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Switch the theme from Settings (Priority: P1) 🎯 MVP

A user who finds the browser too bright at night opens the overflow menu, goes to Settings, and switches the theme to Dark. The whole app changes at once, is still dark after they close and reopen it, and when they go back the tab they were reading is still open at the same address.

**Why this priority**: It is the smallest slice that proves the whole path — a reachable Settings screen, a control, an immediate app-wide effect, and persistence — on a setting that already exists underneath. Every later story adds a control to the screen this one establishes.

**Independent Test**: From the browser, open the overflow menu, open Settings, and choose Light, Dark and System in turn. Confirm each is applied to the whole app at once without the app restarting or its screens being rebuilt, and that the last choice survives force-stopping and relaunching the app.

**Acceptance Scenarios**:

1. **Given** the browser is showing a page, **When** the user opens the overflow menu, **Then** a Settings entry is listed after Bookmarks, History and Downloads.
2. **Given** the Settings screen is open, **When** the user looks at the theme setting, **Then** it shows the currently active choice without having to be opened.
3. **Given** the theme is Light, **When** the user chooses Dark, **Then** every screen of the app renders in the dark theme immediately, without the app restarting or its screens being rebuilt.
4. **Given** the user chose System, **When** the device switches between light and dark mode, **Then** the app follows the device.
5. **Given** the user chose Dark, **When** the app is force-stopped and relaunched, **Then** it opens in the dark theme.

---

### User Story 2 - Choose the search engine (Priority: P1)

A user who prefers DuckDuckGo opens Settings, picks it as the search engine, goes back and types a query into the address bar. The results come from DuckDuckGo, and keep coming from DuckDuckGo after a restart.

**Why this priority**: The search engine is the setting most directly tied to everyday browsing, and it already works underneath — it has simply never had a control.

**Independent Test**: Choose each engine in turn, submit the same query from the address bar, and confirm the results page belongs to the chosen engine; relaunch and confirm the choice persisted.

**Acceptance Scenarios**:

1. **Given** the search engine is Google, **When** the user chooses DuckDuckGo and then submits a query from the address bar, **Then** the query is sent to DuckDuckGo.
2. **Given** an engine has been chosen, **When** the app is relaunched, **Then** the Settings screen still shows that engine and queries still go to it.
3. **Given** results pages from the previous engine are open in tabs, **When** the engine is changed, **Then** those pages are left as they are; only queries submitted afterwards use the new engine.

---

### User Story 3 - Change the app language (Priority: P1)

A user whose phone is set to English but who prefers to read the browser in Vietnamese opens Settings, chooses Tiếng Việt, and the browser switches to Vietnamese straight away — on an old Android 7 phone just as on a new one. On a newer phone the choice also appears in the device's own per-app language settings, and changing it there changes it in the browser too.

**Why this priority**: Constitution §VIII promises in-app language switching without a restart, and today the browser can only follow the device language. It is also the story with the widest technical reach in this spec, so it needs to land early rather than last.

**Independent Test**: On Android 7.0 and on Android 16, choose each of the eight languages and "Follow system" in turn, confirming each time that the whole app switches without a restart and that open tabs survive. On Android 13 or newer, change the browser's language from the device's per-app language settings and confirm the Settings screen shows the same choice; then change it in-app and confirm the device settings show it.

**Acceptance Scenarios**:

1. **Given** the app language is "Follow system" on an English device, **When** the user chooses Tiếng Việt, **Then** every screen switches to Vietnamese without the user restarting the app.
2. **Given** the language chooser is open, **When** the user reads it, **Then** each language is named in its own language and script, so it stays recognisable whatever language the app is currently in.
3. **Given** three normal tabs and one incognito tab are open, **When** the language is changed, **Then** all four tabs are still open afterwards; the page on screen may reload.
4. **Given** a device running Android 13 or newer, **When** the user changes the browser's language from the device's per-app language settings, **Then** the browser's Settings screen shows that same language as selected.
5. **Given** the user chose a specific language, **When** the device language later changes, **Then** the browser stays in the chosen language.
6. **Given** the user chose "Follow system", **When** the device language changes, **Then** the browser follows it.
7. **Given** "Follow system" is selected on a device whose language is not one of the eight supported, **When** the app is shown, **Then** it appears in English.

---

### User Story 4 - Clear browsing data (Priority: P2)

A user about to lend their phone wants to wipe what they browsed. They open Settings, choose to clear browsing data, see the three categories already selected, confirm, and the history, the cookies that kept them signed in, and the cached files are gone — while their bookmarks, downloads and open tabs are exactly where they left them.

**Why this priority**: A core privacy control for a privacy-positioned browser, but the settings above are changed more often, and this story carries the most edge cases (partial failure, the incognito interaction).

**Independent Test**: Visit several sites, signing in to one, then clear all three categories and confirm: History is empty, the signed-in site shows a signed-out state on its next load, tab previews show placeholders until their pages are revisited, and the Downloads list, bookmarks and open tabs are unchanged.

**Acceptance Scenarios**:

1. **Given** the user opens "Clear browsing data", **When** the dialog appears, **Then** it offers browsing history, cookies and site data, and cached images and files, all selected.
2. **Given** every category is deselected, **When** the user looks at the confirm action, **Then** it is disabled.
3. **Given** only browsing history is selected, **When** the user confirms, **Then** every history entry is removed and cookies and cached files are untouched.
4. **Given** the user was signed in to a site, **When** they clear cookies and site data and then reload that site, **Then** the site no longer recognises them as signed in.
5. **Given** cached images and files were cleared, **When** the user opens the tab switcher, **Then** tab previews and site icons show their placeholders until the pages are loaded again.
6. **Given** any combination of categories was cleared, **When** the user checks the Downloads list, their downloaded files, their bookmarks and their open tabs, **Then** all are unchanged.
7. **Given** an incognito tab is open, **When** the user clears cookies and site data and later closes the last incognito tab, **Then** no cookie that existed before the clear comes back.
8. **Given** clearing has finished, **When** the dialog closes, **Then** the user sees a confirmation that the data was cleared.

---

### User Story 5 - Choose how long history is kept (Priority: P2)

A user who never wants more than a week of history opens Settings, changes history retention from 90 days to 7 days, is warned that older history will be deleted, confirms, and from then on the browser never keeps more than a week of history.

**Why this priority**: Spec 014 fixed retention at 90 days and deferred the choice to this spec. It matters to privacy-minded users, but it is set once and rarely revisited.

**Independent Test**: With history spanning more than 90 days, shorten retention to 7 days, confirm, and verify that nothing older than 7 days remains in History — both immediately and after a relaunch. Then lengthen it and confirm no warning is shown.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user opens Settings, **Then** history retention shows 90 days.
2. **Given** retention is 90 days and history exists from 60 days ago, **When** the user chooses 7 days, **Then** they are told that history older than 7 days will be deleted, and nothing changes until they confirm.
3. **Given** the user confirmed a shorter window, **When** they open History, **Then** no entry older than the new window remains.
4. **Given** the warning is shown, **When** the user cancels it, **Then** retention is unchanged and no history was deleted.
5. **Given** retention is 7 days, **When** the user chooses 90 days, **Then** the change takes effect without a warning.
6. **Given** the retention chooser is open, **When** the user reads the choices, **Then** exactly four are offered — 7, 30, 90 and 180 days — and none is unlimited.

---

### User Story 6 - Turn dynamic color on or off (Priority: P3)

A user on Android 12 or newer who prefers the browser's own teal look to colours drawn from their wallpaper turns dynamic color off, and the app switches to its own palette immediately.

**Why this priority**: A cosmetic preference with no effect on browsing itself — valuable to some users, but the least critical control in the spec.

**Independent Test**: On Android 12+, toggle the switch and confirm the palette changes immediately in both light and dark themes and that the choice persists; on Android 11 or older, confirm the switch is not shown.

**Acceptance Scenarios**:

1. **Given** a fresh install on Android 12 or newer, **When** the user opens Settings, **Then** dynamic color is shown as on.
2. **Given** dynamic color is on, **When** the user turns it off, **Then** the app immediately uses its own palette in both light and dark themes.
3. **Given** a device running Android 11 or older, **When** the user opens Settings, **Then** no dynamic color control is shown.

---

### User Story 7 - See the app version and privacy statement (Priority: P3)

A user reporting a problem, or checking what the browser does with their data, opens About and sees the exact version installed and a plain statement that the browser collects nothing and sends nothing to its makers.

**Why this priority**: Informational only. Useful for support and for trust, but nothing else depends on it.

**Independent Test**: Open About and compare the version shown with the installed build's version; read the privacy statement in each of the eight languages, including with the device offline.

**Acceptance Scenarios**:

1. **Given** the Settings screen, **When** the user opens About, **Then** the app's name and the exact installed version are shown.
2. **Given** About is open, **When** the user reads it, **Then** it states that the app collects no personal data, sends nothing to any ThirtySix-controlled server, and keeps browsing data on the device.
3. **Given** the device is offline, **When** About is opened, **Then** everything in it is shown.

---

### Edge Cases

- **Language changed from the device's settings while the app is in the background** (Android 13+): on return the app is in the new language and the Settings screen shows it as selected; no stale in-app value can contradict it (FR-016, FR-017).
- **Choosing the language that is already active**: nothing happens — the screens are not rebuilt and no page reloads (FR-006).
- **Regional device languages** under "Follow system", such as Canadian French or Traditional Chinese: the app resolves to the nearest supported language under the fallback rules Spec 004 established; an unsupported device language resolves to English (FR-018).
- **A user picks a language they cannot read**: every language is listed under its own name, and the Settings entry and the language control keep their positions, so the user can find their way back (FR-014).
- **Language changed while an incognito tab is active**: the incognito session survives — its tabs stay open, its cookie isolation from Spec 012 is intact, and screenshot protection stays in force (FR-019).
- **Clearing data while a page is loading or playing media**: clearing completes without crashing; open pages are not force-reloaded and reflect the cleared state on their next load (A4).
- **Part of a clear fails** because a platform facility refuses or throws: the other selected categories are still cleared, the app does not crash, and the user is told in a localized message that some data could not be cleared (FR-033).
- **Repeated taps on confirm**: a clear already in progress cannot be started a second time, and the dialog shows that work is under way (FR-032).
- **Cookies cleared while incognito tabs are open**: nothing is restored when the incognito session ends (FR-030).
- **A web engine too old for complete site-data removal**: cookies and web storage are cleared, service workers and Cache Storage remain, and the clear is still reported as successful because nothing more is possible on that device (A17).
- **Retention shortened with a very large history** of 10,000 entries: the deletion completes without an ANR (FR-022, SC-010).
- **Retention changed while History is further back in the navigation stack**: returning to History shows the pruned list, never a stale one.
- **Process death while on the Settings screen**: on relaunch, whatever is shown reflects the persisted values; no change is ever half-applied.
- **A stored dynamic-color value on Android 11 or older**, for example restored from a backup made on a newer device: the value is ignored and no control is shown (FR-010).
- **Rapid successive choices** in any chooser: the last choice wins and is the one persisted.

## Requirements *(mandatory)*

### Functional Requirements

#### Entry point & screen

- **FR-001**: The system MUST offer a Settings entry in the browser's overflow menu, positioned after Bookmarks, History and Downloads, with a localized label.
- **FR-002**: The Settings screen MUST replace the existing placeholder screen, and MUST group its controls as appearance (theme, dynamic color), language, search, privacy and data (history retention, clear browsing data), and About.
- **FR-003**: Every setting that has a value MUST display its current value on the Settings screen without the user having to open it.
- **FR-004**: The Settings screen MUST be dismissable via its back affordance and via the system back gesture, returning to the browser on the same tab at the same address. As with every other screen reached from the overflow menu, the page itself may reload on return.
- **FR-005**: Every change to theme, dynamic color, search engine or history retention MUST be persisted, and MUST survive app restart and process death.
- **FR-006**: Choosing the value that is already selected, in any chooser, MUST have no effect.

#### Theme & dynamic color

- **FR-007**: Users MUST be able to choose Light, Dark or System; System MUST follow the device's dark-mode setting.
- **FR-008**: A theme change MUST apply to every screen of the app within 100 ms of the choice (Constitution §VII), without restarting the app or rebuilding its screens — unlike a language change, which does rebuild them (A6).
- **FR-009**: On Android 12 and newer, users MUST be able to turn dynamic color on or off. It MUST default to on, and turning it off MUST switch the app to its own palette immediately, in both light and dark themes.
- **FR-010**: On Android 11 and older, the dynamic color control MUST NOT be shown, and any stored value for it MUST be ignored.

#### Search engine

- **FR-011**: Users MUST be able to choose Google, DuckDuckGo or Bing, and every query submitted after the change MUST go to the chosen engine.
- **FR-012**: Changing the search engine MUST NOT change the address of any open tab; only queries submitted afterwards use the new engine.

#### App language

- **FR-013**: Users MUST be able to choose "Follow system" or one of the eight supported languages: English, Vietnamese, German, Russian, Korean, Japanese, Chinese and French.
- **FR-014**: Each language MUST be listed under its own name, in its own script, independent of the app's current language. The "Follow system" option MUST be labelled in the app's current language.
- **FR-015**: A language change MUST take effect across the whole app without the user restarting it, on every supported Android version from 7.0 to 16.
- **FR-016**: On Android 13 and newer, the language chosen in-app and the language set for the browser in the device's per-app language settings MUST always be the same value: a change made in either place MUST be reflected in the other.
- **FR-017**: The app MUST hold exactly one language preference. The stored language value introduced by Spec 006, which was never applied, MUST be retired rather than kept alongside it.
- **FR-018**: Under "Follow system", the app MUST follow the device language using the fallback rules Spec 004 established, resolving an unsupported device language to English.
- **FR-019**: All open tabs, normal and incognito, MUST remain open across a language change, and the incognito session's isolation and screenshot protection MUST be unaffected. The page currently on screen MAY reload (A6).

#### History retention

- **FR-020**: Users MUST be able to choose a history retention window of 7, 30, 90 or 180 days, defaulting to 90 days. The chooser MUST offer exactly these four values, and no unlimited or "keep forever" option.
- **FR-021**: Choosing a shorter window MUST first warn the user that history older than the new window will be deleted, and MUST change nothing unless the user confirms.
- **FR-022**: On confirmation, history older than the new window MUST be deleted immediately, not merely at the next app start, and the deletion MUST NOT make the app unresponsive — no ANR occurs, including with 10,000 history entries (SC-010).
- **FR-023**: Choosing a longer window MUST take effect without a warning. History already deleted is not recovered.
- **FR-024**: The chosen window MUST continue to be enforced automatically at every app start, whether or not the user opens History, preserving Spec 014's guarantee.

#### Clear browsing data

- **FR-025**: The system MUST offer a single "clear browsing data" dialog with three independently selectable categories: browsing history; cookies and site data; cached images and files. All three MUST be selected each time the dialog opens.
- **FR-026**: Clearing MUST require an explicit confirmation, and the confirm action MUST be disabled while no category is selected.
- **FR-027**: Clearing browsing history MUST remove every history entry.
- **FR-028**: Clearing cookies and site data MUST remove all cookies and all data stored by websites — including service workers and Cache Storage wherever the device's web engine supports removing them (A17) — so that sites no longer recognise the user on their next load. Where the engine removes the web page cache in the same operation, that cache is emptied as well (A16); the app's own cached site icons and tab preview images are cleared only by FR-029.
- **FR-029**: Clearing cached images and files MUST remove the web cache together with the app's own cached site icons and tab preview images; affected previews and icons MUST show their placeholders until regenerated by revisiting pages.
- **FR-030**: If cookies and site data are cleared while incognito tabs are open, the normal-browsing cookies that Spec 012 set aside for restoring at the end of the incognito session MUST be discarded as well, so that closing the last incognito tab restores nothing. The incognito tabs themselves MUST stay open.
- **FR-031**: Clearing MUST NOT touch the Downloads list, downloaded files, bookmarks, open tabs, or any setting.
- **FR-032**: While clearing is in progress, the dialog MUST show that work is under way and MUST prevent a second submission, and no ANR may occur (SC-009).
- **FR-033**: On completion the user MUST see a localized confirmation. If any selected category could not be cleared, the remaining categories MUST still be cleared, the app MUST NOT crash, and the user MUST be told in a localized message that some data could not be cleared.

#### About

- **FR-034**: The About section MUST show the app's name and the exact version of the installed build.
- **FR-035**: The About section MUST state that the app collects no personal data, sends nothing to any ThirtySix-controlled server, and keeps browsing data on the device.
- **FR-036**: Everything in About MUST be available offline.

#### Reuse by onboarding

- **FR-037**: The theme, language and search-engine choosers MUST be usable from a screen other than Settings, with identical behaviour, so that Spec 018's onboarding can present them without duplicating their logic.

#### Localization & accessibility

- **FR-038**: Every user-facing string introduced by this feature MUST be localized in all 8 supported locales, with translation parity enforced at build time.
- **FR-039**: Every control — rows, choosers and their options, the switch, checkboxes, and confirm and cancel actions — MUST expose a localized accessibility label, MUST announce its selected or checked state to screen readers, and MUST offer a touch target of at least 48×48dp.
- **FR-040**: The obsolete placeholder string for the Settings screen MUST be removed from all 8 locales once the real screen replaces it, leaving no unused string resources behind.

#### Architecture & policy

- **FR-041**: The feature MUST follow the project's Clean Architecture pattern: settings reads and writes go through repository interfaces in the domain layer and distinct use cases, the feature view-model exposes an immutable state object, and transient signals travel on a dedicated one-shot event channel.
- **FR-042**: Every interaction with a platform facility — the per-app language setting, web cookie and site-data storage, and cached files — MUST sit behind an abstraction owned by the app, so that decision logic is testable without a device and the presentation layer holds no platform dependencies.
- **FR-043**: Every call into a platform facility MUST be defensively wrapped so that a missing, failing or throwing facility cannot crash the browser. Where the failure blocks something the user asked for, it MUST also be surfaced as a localized message (FR-033).
- **FR-044**: The feature MUST NOT introduce any user-facing string, dimension, colour, retention value, storage key or magic number outside the project's constants, theme tokens and string resources (Constitution §III).
- **FR-045**: Any new external dependency MUST be justified in the plan and verified at the moment of addition as the latest stable release, with no native code or only 16 KB-aligned native code (Constitution §IX).

### Key Entities *(include if feature involves data)*

- **User Settings**: The app-owned, persisted preferences this feature exposes — theme mode (Light / Dark / System), whether dynamic color is enabled (default on), search engine (Google / DuckDuckGo / Bing), and the history retention window (default 90 days). Extends the settings record introduced in Spec 006. Deliberately does **not** contain the app language.
- **App Language Preference**: Either "Follow system" or one of the eight supported languages. Held solely by the platform's per-app language setting, which is the single source of truth (FR-016, FR-017); the app reads and writes it but keeps no copy of its own.
- **History Retention Window**: One of 7, 30, 90 or 180 days (FR-020). Governs both the automatic sweep at every app start and the immediate deletion that follows a confirmed shortening.
- **Clear Browsing Data Request**: The set of categories selected in the dialog and, once executed, an outcome per category — cleared or failed — which decides between the completion message and the partial-failure message. Transient; never persisted.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From any page in the browser, a user reaches the Settings screen in at most 2 taps.
- **SC-002**: A theme or dynamic color change is visible on every on-screen element within 100 ms — measured from the moment the choice is made to the first frame showing the new colours — without the app's screens being rebuilt, verified for all three themes and both dynamic color states.
- **SC-003**: After a search engine change, 100% of subsequently submitted queries go to the chosen engine, verified across all 3 engines × 3 representative queries.
- **SC-004**: A language change shows the whole app in the new language within 1 second — measured from the moment the choice is made to the first frame fully in the new language — and without a restart, verified for all 8 languages plus "Follow system" on both Android 7.0 and Android 16.
- **SC-005**: On Android 13 and newer, the in-app language selection and the device's per-app language setting agree in 100% of checks, verified by changing the language from each side at least 3 times.
- **SC-006**: 100% of open tabs — at least 3 normal and 1 incognito — are still open after every language change.
- **SC-007**: After clearing all three categories, zero history entries remain, a previously signed-in site shows a signed-out state on reload, and the counts of downloads, downloaded files, bookmarks and open tabs are identical to before, verified on at least 3 runs.
- **SC-008**: After clearing cookies and site data while an incognito tab is open and then closing the last incognito tab, zero cookies that existed before the clear are present, verified on at least 3 runs.
- **SC-009**: With 10,000 history entries and 50 open tabs, clearing all three categories on a device or emulator running the minimum supported Android version (7.0) completes without an ANR, with the dialog showing that work is under way until it finishes and the app responding to input throughout. This is the gating criterion. Completion within 3 seconds on a Pixel 5-class device running a release build is recorded whenever such hardware is available but does not gate the spec, and a debug-build or emulator timing MUST NOT be reported against that 3-second target.
- **SC-010**: After confirming a shorter retention window, zero history entries older than that window remain — immediately and after relaunch — verified for each of the four choices, including with 10,000 entries without an ANR; a fresh install shows 90 days.
- **SC-011**: 100% of strings introduced by this feature are translated in all 8 locales, enforced at build time, with no missing, extra or unused keys.
- **SC-012**: 100% of interactive controls on the Settings screen and its dialogs have a localized screen-reader label, announce their state, and meet the 48×48dp touch-target minimum, verified by a TalkBack pass in at least 2 locales, one of them in a non-Latin script.
- **SC-013**: The version shown in About matches the installed build's version in 100% of checks.
- **SC-014**: The release APK grows by at most **699,913 bytes** over the Spec 015 baseline of 2,558,178 bytes (reported as 2.44 MB) — that is, it is at most **3,258,091 bytes**. The budget is the measured contribution of the two supporting libraries after release shrinking, 495,113 bytes as measured during planning, plus 200 KB (204,800 bytes) for the feature itself.
- **SC-015**: Every native library entry in the release build remains 16 KB-aligned (Constitution §IX).
- **SC-016**: Constitution Check returns 11/11 PASS both pre- and post-implementation, with any deviation recorded and justified rather than silently taken.

## Assumptions

- **A1 — Retention choices** *(confirmed during clarification, 2026-09-11)*: 7 days, 30 days, 90 days and 180 days, defaulting to 90 — now stated normatively in FR-020. The 180-day ceiling follows from Spec 014's measurement of roughly 300 bytes of memory per history entry, with the History screen holding the whole history in memory: doubling the previous window is a bounded risk on minimum-spec devices, whereas a year or more of heavy browsing is not.
- **A2 — Shortening is confirmed and immediate** *(confirmed during clarification, 2026-09-11)*: shortening the window deletes history irreversibly, so it is confirmed and applied at once rather than silently deferred to the next app start — now stated normatively in FR-021 and FR-022. Lengthening deletes nothing and needs no confirmation.
- **A3 — Clearing is all-time and preselected**: the dialog offers no time range, and all three categories are selected every time it opens; the previous selection is not remembered.
- **A4 — Clearing does not reload open pages**: pages open in tabs keep what they have already loaded and reflect the cleared state on their next load, as in mainstream browsers.
- **A5 — Cookies and site data are cleared for all sites together**: there is no per-site data management.
- **A6 — A page reload on language change is accepted**: changing the language rebuilds the app's screens, which reloads the page on screen in the same way a rotation already does (Spec 007). Preserving scroll position or in-page state across the change is not required.
- **A7 — Web content is not themed**: the theme applies to the browser's own interface; websites render as they choose.
- **A8 — Settings are global and context-independent**: the Settings screen behaves identically whether it is opened from a normal or an incognito tab. While incognito is active, screenshot protection applies to it exactly as to every other screen (Spec 012).
- **A9 — Backup posture is unchanged**: the new persisted settings follow the existing settings backup posture from Spec 006; the language preference follows the platform's own behaviour for per-app languages.
- **A10 — About is minimal**: About carries the app name, version and privacy statement only — no licence list, links, feedback or rating prompt.
- **A11 — The dynamic color default preserves today's look**: dynamic color is already in effect for every Android 12+ user, so defaulting the new switch to on changes nothing for existing installs.
- **A12 — One screen, choosers over it**: Settings is a single scrollable screen, and each multi-choice setting opens a chooser over it. There are no sub-screens and no search within Settings.
- **A13 — Dependency expectation** *(revised during planning, 2026-09-11)*: two additional platform-support libraries — one for in-app language switching on Android 7.0–12, and one for complete removal of site data (A16, A17). No other new dependency is expected. Each library's version and native-code status is verified during planning and again at implementation (FR-045).
- **A14 — No existing users to migrate**: the app has not been released, so retiring the unused stored language value (FR-017) needs no data migration.
- **A15 — Onboarding reuse is structural**: FR-037 only requires that the choosers be reusable; how Spec 018 presents them is decided in Spec 018.
- **A16 — Site data and the web page cache are removed together** *(planning, 2026-09-11)*: on engines that support complete site-data removal, the engine deletes the web page cache in the same operation and offers no way to keep it. Selecting cookies and site data therefore also empties that cache, even when cached images and files is not selected. The only user-visible effect is a slower first load of previously visited pages; the app's own site icons and tab previews are unaffected unless their own category is selected.
- **A17 — Older web engines clear less** *(planning, 2026-09-11)*: where the device's web engine predates complete site-data removal, clearing cookies and site data removes cookies and web storage (local and session storage, IndexedDB and file-system storage) but not service workers or Cache Storage. The category is still reported as cleared, because nothing further is possible on that device, and the limitation is recorded here rather than shown as a failure. Which engine a device has — and therefore which behaviour applies — is recorded on each test device in quickstart G5.

## Dependencies

- **Spec 002 — Clean Architecture skeleton**: provides the Settings route and the placeholder screen this feature replaces, along with the base view-model, result and error abstractions.
- **Spec 003 — Theme, typography & dark mode**: provides the light/dark/system theme and dynamic color behaviour this feature exposes, and the cold-start window background that the work here must not regress.
- **Spec 004 — Localization**: provides the eight locales, their fallback rules (FR-018), and the Android 13+ per-app language registration that FR-016 builds on.
- **Spec 006 — DataStore settings**: provides the persisted settings record holding theme and search engine, which this feature extends, and the never-applied language value it retires (FR-017).
- **Spec 010 — Search engine**: reads the engine choice at the moment a query is submitted (FR-011).
- **Spec 011 — Tabs management**: provides the site-icon and tab-preview caches cleared by FR-029, and the open tabs that FR-019 and FR-031 preserve.
- **Spec 012 — Private/incognito mode**: provides the cookie set-aside-and-restore behaviour that FR-030 amends, and the screenshot protection FR-019 and A8 preserve.
- **Spec 014 — History view**: provides the clear-all capability FR-027 reuses and the start-up retention sweep that FR-020 – FR-024 make configurable.
- **Spec 015 — Downloads manager**: provides the overflow menu structured to accept Settings (FR-001); its data is explicitly untouched by clearing (FR-031).
- **Spec 018 — Onboarding flow** *(downstream)*: the intended consumer of the reusable choosers (FR-037).

## Out of Scope (v1.0)

- A configurable home page (see Clarifications).
- Clearing the Downloads list or downloaded files (FR-031).
- Time-ranged clearing, such as the last hour, day or week (A3).
- Per-site data, cookie or permission management (A5).
- Clearing browsing data automatically on exit.
- Forcing a dark rendering of web content (A7).
- Custom accent colours or palettes beyond the dynamic color switch.
- A licence list, feedback, rating prompt or external links in About (A10).
- Search within Settings, and import, export or sync of settings.
- The onboarding flow itself, including any way to reset onboarding (Spec 018).
- Tracker blocking and its toggle (Spec 019).
- Download-related settings, such as a save location or Wi-Fi-only downloads.
