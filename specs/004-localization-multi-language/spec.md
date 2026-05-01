# Feature Specification: Multi-Language Localization Foundation

**Feature Branch**: `004-localization-multi-language`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "Spec 004 — localization-multi-language. Mục tiêu: ThirtySixBrowser hỗ trợ 8 locale (EN default, VI, DE, RU, KO, JA, ZH, FR) qua Android resource system + per-app locale config, để app respect device/system locale setting. Trên Android 13+ user có thể đổi ngôn ngữ qua system Settings → Apps → ThirtySixBrowser → Language. Trên < API 33 app theo system locale. Scope chốt chặt — chỉ infra + baseline strings, KHÔNG có UI switcher trong app."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - App Speaks the User's Language by Default (Priority: P1)

A first-time user opens ThirtySixBrowser on a device whose system language is set to one of the eight supported languages (English, Vietnamese, German, Russian, Korean, Japanese, Chinese, French). Without any setup steps, the app's interface — including the app name in the launcher and every visible screen title — appears in that language. If the device language is not one of the eight supported, the app falls back to English.

**Why this priority**: Zero-friction localization is the foundation of the entire international product. A user in Vietnam should not have to find a setting to make the app readable; the app should simply speak Vietnamese when their phone speaks Vietnamese. This is the minimum viable internationalization and unblocks Google Play distribution to non-English markets. Without P1, the app is effectively English-only no matter how many translation files exist.

**Independent Test**: Switch the test device's system language to each of the seven non-English supported locales in turn, launch the app, and verify the launcher icon label and all visible screen titles render in the selected language. Then switch to an unsupported locale (e.g., Portuguese) and verify the app falls back to English. No code change or in-app interaction is required between locale switches.

**Acceptance Scenarios**:

1. **Given** the device system language is set to Vietnamese, **When** the user launches ThirtySixBrowser for the first time, **Then** the launcher icon label and every visible screen title render in Vietnamese.
2. **Given** the device system language is set to Korean, **When** the user navigates between the seven main placeholder screens, **Then** every screen title renders in Korean with no English leakage.
3. **Given** the device system language is set to Portuguese (an unsupported locale), **When** the user launches the app, **Then** all strings render in English without missing-string errors or placeholder text.
4. **Given** the user changes the device system language while the app is in the background, **When** the user returns to the app, **Then** all strings update to match the new locale on the next configuration change.

---

### User Story 2 - User Picks an App-Specific Language on Android 13+ (Priority: P2)

A user on Android 13 or newer who keeps their device system language in English but wants ThirtySixBrowser specifically to display in Japanese opens the system Settings, navigates to Apps → ThirtySixBrowser → Language, and sees a list of exactly the eight supported languages plus a "System default" option. They select Japanese; on returning to the app, every visible string is in Japanese while the rest of the device remains in English. The user can return to the same setting later and choose any of the other supported languages, including reverting to "System default."

**Why this priority**: This complements P1 by giving multilingual users (e.g., expats, language learners, bilingual households on a shared device) per-app control without forcing a device-wide change. It is the only mechanism in v1.0 for users to override their device locale because no in-app language switcher will exist until the future Settings Screen feature. Marked P2 because it depends on system-level functionality available only on Android 13 and newer.

**Independent Test**: On an emulator or device running Android 13 or newer, verify that the system per-app language picker (Settings → Apps → ThirtySixBrowser → Language) lists exactly the eight supported locales plus "System default." Pick each in turn and verify the app displays in the chosen language while device-wide language remains unchanged.

**Acceptance Scenarios**:

1. **Given** a device running Android 13 or newer with system language English and the app freshly installed, **When** the user opens system Settings → Apps → ThirtySixBrowser → Language, **Then** a picker appears listing all eight supported locales plus a "System default" option.
2. **Given** the per-app language picker is open, **When** the user selects French, **Then** ThirtySixBrowser displays all visible strings in French while the system Settings, status bar, and other apps remain in English.
3. **Given** the user previously selected French via the per-app picker, **When** they return to the picker and select "System default," **Then** the app reverts to following the device system language on next launch.

---

### User Story 3 - No String Falls Back Silently (Priority: P3)

A maintainer adds a new user-facing string to the app. Before the change can be merged, automated quality checks flag any of the eight supported locales that is missing a translation for that key, blocking the merge. As a result, end users in any supported locale never encounter mixed-language screens where some labels render in their language while others silently fall back to English.

**Why this priority**: Translation quality is what separates a "technically localized" app from one that feels native. A single missing translation in a key flow (e.g., a button label) breaks the user's confidence. Marked P3 because the failure mode is detectable post-hoc and patchable, whereas P1/P2 failures would leave users completely unable to read the app. This priority establishes the discipline (lint enforcement) up front so it survives every future feature.

**Independent Test**: Temporarily remove one entry from a non-English string resource file and run the project's lint task. Verify the build fails with a translation-gap warning naming the deleted key and the affected locale. Restore the entry and verify lint passes.

**Acceptance Scenarios**:

1. **Given** the project's eight string resource files all contain matching keys, **When** the project's lint task runs, **Then** the lint task completes with zero translation-gap issues for the baseline catalog.
2. **Given** a maintainer omits a translation for one key in one non-English locale, **When** the lint task runs, **Then** the lint task reports a translation gap identifying the key and the locale, and the build pipeline blocks merge.
3. **Given** a maintainer adds a new English string, **When** they propose the change without the seven matching translations, **Then** the lint task reports seven gaps (one per non-English locale).

---

### Edge Cases

- **Locale region variants** (e.g., user device is set to Quebec French `fr-CA`): The app falls back to the closest supported translation (`fr`) rather than to English.
- **Chinese region variants** (`zh-CN`, `zh-TW`, `zh-HK`): The app provides a single Chinese translation tagged `zh` and lets the operating system's resource resolver match all Chinese region variants to it.
- **Right-to-left rendering**: None of the eight supported locales is RTL, so RTL layout testing is out of scope for this feature. Future RTL locale support (e.g., Arabic, Hebrew) would require additional work and is not in the v1.0 plan.
- **Per-app language picker on Android 12 and earlier**: The picker does not exist on API 24–32 devices. Those users are limited to device-wide system language and cannot override per-app — by design.
- **Mid-session language change** (Android 13+ per-app picker): On selecting a new per-app language, the operating system relaunches the activity (standard platform behavior); the app does not need custom handling.
- **Locale with broken OS rendering** (e.g., user picks Korean but the OS itself is missing Korean fonts on a stripped device): Out of scope — this is an OS/device issue, not an app issue.
- **String key collision with future features**: Resource keys must follow the agreed naming convention so future feature specs can extend without renaming existing keys.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide complete user-facing string sets in eight languages: English, Vietnamese, German, Russian, Korean, Japanese, Chinese, and French.
- **FR-002**: System MUST automatically display strings in the device's active language when that language matches one of the eight supported locales, without requiring any user setup or in-app action.
- **FR-003**: System MUST fall back to English when the device's active language is not one of the eight supported locales.
- **FR-004**: System MUST declare its supported-locales list in a manner the operating system can read so that, on Android 13 and newer, the system per-app language picker appears for the app and lists exactly the eight supported locales.
- **FR-005**: System MUST honor the user's per-app language selection (Android 13+) for all visible strings within the app, while leaving the rest of the device's language unaffected.
- **FR-006**: System MUST cover, at minimum, the following baseline string keys with translations in all eight locales: the application's display name (used by the launcher) and one screen-title key for each of the seven placeholder destinations currently defined in the navigation graph (Browser, Tabs, Bookmarks, History, Downloads, Settings, Onboarding).
- **FR-007**: All user-facing string resource keys introduced in this feature MUST follow the naming convention `feature_section_purpose` (lowercase, underscore-separated), so future feature specs can extend the catalog without renaming existing keys.
- **FR-008**: System MUST fail the build's lint stage when any user-facing string is present in the English baseline but missing from any of the seven non-English locale files.
- **FR-009**: System MUST NOT include an in-app language switcher in this feature; per-app language control in this milestone is exclusively provided via the operating-system-level picker on Android 13 and newer. (In-app switcher is deferred to the future Settings Screen feature.)

### Key Entities

- **Supported Locale Set**: The fixed list of eight BCP-47 language tags the app commits to translating into. Membership in this set is the gate for both translation completeness checks and the per-app language picker contents.
- **Baseline String Catalog**: The minimal set of user-facing strings shipped in this feature — the app display name plus one screen-title per current placeholder destination — used as the seed that all eight locales must cover. Future features extend this catalog; each extension carries the obligation to translate into all eight locales at the moment of addition.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When the device system language is set to any of the seven non-English supported locales, 100% of strings visible on the launcher icon and across the seven placeholder screens render in that locale, with zero strings falling back to English.
- **SC-002**: On a device running Android 13 or newer, the system per-app language picker for ThirtySixBrowser shows exactly nine entries: the eight supported locales plus the "System default" option — no missing entries, no spurious entries.
- **SC-003**: Selecting any of the eight locales via the per-app language picker results in the app displaying in that locale on next launch, with the rest of the device's user interface remaining in its original system language.
- **SC-004**: A first-time user whose device is configured for any of the seven non-English supported locales can launch the app and read every screen's title without performing any setup, configuration, or in-app action.
- **SC-005**: The project's automated lint stage fails any change set that introduces a user-facing string in the English baseline without matching translations in all seven non-English locales.
- **SC-006**: When a user's device locale is a regional variant of a supported language (e.g., `fr-CA`, `zh-TW`, `de-AT`), the app resolves to the closest supported translation rather than to English.

## Assumptions

- **Chinese variant**: The single supported Chinese translation is tagged `zh` (no region qualifier) and is intended to be Simplified Chinese. Regional variants such as `zh-TW` and `zh-HK` resolve to it via the operating system's resource matching.
- **Translation source**: Initial baseline translations may be seeded by machine translation or off-the-shelf glossaries; refinement to native-speaker quality is acceptable to defer until each feature spec adds its user-facing copy. The contract this feature establishes is *coverage*, not *literary quality*.
- **No right-to-left support in v1.0**: All eight chosen locales are left-to-right languages. Adding RTL locales (e.g., Arabic) is not in scope and would require additional layout testing.
- **Per-app picker reach**: The per-app language picker is an Android 13 (API 33) feature. Users on Android 7 through 12 (API 24–32) — the project's lower minSdk range — must rely on device-wide system language. This split is accepted as a deliberate tradeoff: covering API 24+ matters more than offering per-app override on every API level.
- **No in-app switcher in this milestone**: An in-app Settings screen language picker is explicitly out of scope here and is owned by the future Settings Screen feature. The persistence mechanism (writing the user's choice to durable settings storage) is owned by the future settings persistence feature.
- **Future strings owned by their feature spec**: Each subsequent feature that introduces user-facing strings carries the obligation to translate those strings into all eight locales at the moment of introduction. This feature establishes only the minimal placeholder catalog.
- **No translation for non-user-facing strings**: Internal-only strings (e.g., log messages, exception messages used solely for diagnostics) are not in the localized catalog and remain English-only.
- **Existing placeholder destinations are stable**: The seven placeholder screens currently defined in the navigation graph (Browser, Tabs, Bookmarks, History, Downloads, Settings, Onboarding) are the canonical list at the time this feature ships. If the navigation graph adds or removes destinations in a later feature, that feature owns adding or removing the corresponding title key in all eight locales.
