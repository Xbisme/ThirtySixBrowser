# Feature Specification: Privacy Tools

**Feature Branch**: `024-privacy-tools`
**Created**: 2026-10-13
**Status**: Draft
**Input**: User description: "024-privacy-tools — Phase 6 spec 5/5 (Constitution v1.6.0 §X), builds on Spec 023 privacy-protection. Fire button that closes every tab and clears browsing data except fireproof sites; fireproof sites marked from a per-site privacy dashboard and listed in Settings; the dashboard opened from Spec 023's hub banner; cookie-banner auto-decline and basic fingerprinting protection through scripts bundled in the APK (Constitution §II). All offline, 8 locales, no new permission."

## Owner decisions taken before this spec (2026-10-13)

| Topic | Decision |
|---|---|
| Design | **Waived as for Specs 020–023** — built only from Spec 019 design-system components (see Assumptions). |
| Scope | **One spec**, not split further. Priorities: P1 Fire button and fireproof sites · P2 privacy dashboard · P3 cookie-banner auto-decline and fingerprinting protection. |
| What Fire clears | **DuckDuckGo-style**: closes every tab, normal and incognito, and clears cookies, site data, cached files and browsing history, except what belongs to fireproof sites. Bookmarks, downloads and settings are kept. |
| Fire entry points | A tile in the hub and a button on the Tabs screen. Always confirmed. A short animation that respects reduced motion. Ends on Home in one fresh normal tab. |
| Fireproofing | A switch in the per-site dashboard, and a list in Settings → Privacy. **No automatic prompt** after signing in. |
| Dashboard | A sheet opened by tapping Spec 023's "N trackers blocked on this page" line in the hub. |
| Cookie banners | Rules taken from an open-source project whose license allows bundling, chosen in research, trimmed to common consent tools. **At most +200 KB.** |
| Fingerprinting | **Basic level**: noise on canvas and audio read-outs, hide battery details and media-device details. |

## Clarifications

### Session 2026-10-13

- Q: How are blocked trackers grouped in the dashboard? → A: By site (registrable domain); no company names, because no license-compatible owner data exists (Spec 023 research R2).
- Q: Does Fire clear remembered camera/microphone/location decisions (Spec 021)? → A: Yes, for every site that is not fireproof; fireproof sites keep theirs (FR-004a).
- Q: Does Fire also reset in-memory traces of the session? → A: Yes — the per-site fingerprinting noise and Spec 023's failed-HTTPS-upgrade hosts are both reset, so no site can link visits before and after Fire (FR-003a).
- Q: What happens to saved site icons (favicons) on Fire? → A: All are removed except those of bookmarked hosts and fireproof sites (FR-008).
- Q: In a frame from another site, what decides whether the scripts run? → A: The top-level page: with protection on for it, they run in every frame of the page; with it off, in none (FR-020). Any engine limit found in research is documented in the plan.
- Research amendment (plan, 2026-10-13): the dashboard does not show whether a cookie banner was declined — with nothing coming back from the page (FR-021) the app cannot know (research R8). FR-016, User Story 3 and Key Entities updated.
- Analyze remediation (2026-10-13): FR-003 and FR-010 narrowed to what the web engine allows (research R2, R3) — site data is cleared for every site the app can find, and a few kinds of fireproof cookie cannot be kept; both are documented limitations (FR-003b, FR-010a). FR-025 now names the 10 consent tools, because no public ranking exists (research R7).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Burn everything in one action (Priority: P1)

A person has been browsing for a while: several tabs open, a few incognito tabs, a dozen sites have set cookies. They open the hub and tap **Fire**. A confirmation says what will be cleared and what will be kept. They confirm, a short animation plays, and the browser shows Home in a single new tab. Every site they visit next treats them as a new visitor, and History is empty.

**Why this priority**: Fire is the one-tap answer to "forget what I just did". It is the most visible privacy tool in the spec and works on its own without any other story.

**Independent Test**: Open several normal and incognito tabs on sites that set cookies and local storage, press Fire from the hub, confirm, and check that one Home tab remains, History is empty, and the sites no longer recognise the visitor; repeat from the Tabs screen.

**Acceptance Scenarios**:

1. **Given** normal and incognito tabs are open, **When** the person taps Fire in the hub, **Then** a confirmation appears naming what is cleared (tabs, history, cookies and site data, cached files, site permissions) and what is kept (bookmarks, downloads, settings, fireproof sites).
2. **Given** the confirmation is shown, **When** the person cancels or presses Back, **Then** nothing is closed or cleared.
3. **Given** the person confirms, **When** clearing finishes, **Then** every previous tab is gone, exactly one normal tab shows Home, and incognito is no longer active.
4. **Given** a site had set cookies and stored data, **When** the person visits it again after Fire, **Then** it has none of its earlier cookies or stored data (it is not a fireproof site).
5. **Given** Fire was used, **When** the person opens History, **Then** it is empty.
6. **Given** bookmarks, downloads and settings existed and a non-fireproof site had remembered camera access, **When** Fire finishes, **Then** bookmarks, downloads and settings are unchanged, a download in progress keeps going, and the site asks for camera access again on its next request.
7. **Given** the Tabs screen is open, **When** the person taps its Fire button and confirms, **Then** the result is the same as from the hub.
8. **Given** the device asks apps to reduce motion, **When** Fire runs, **Then** no animation plays beyond a simple fade.

---

### User Story 2 - Keep signed in on sites I trust (Priority: P1)

A person uses their webmail and bank every day and does not want to sign in again after every Fire. On the webmail page they open the privacy dashboard and turn on **Fireproof this site**. Later they press Fire: the tabs close and history is gone, but when they open the webmail again they are still signed in. In Settings → Privacy → **Fireproof sites** they see the webmail listed and can remove it.

**Why this priority**: Without fireproofing, Fire signs the person out of everything, so most people would never press it. Fireproofing is what makes Fire usable day to day.

**Independent Test**: Sign in to a local test site, fireproof it, press Fire, and confirm the session survives while a non-fireproof test site's session does not; then remove it in Settings, press Fire again, and confirm the session is gone.

**Acceptance Scenarios**:

1. **Given** a web page in a normal tab, **When** the person turns on Fireproof in the dashboard, **Then** the site (its registrable domain and all subdomains) is remembered on the device as fireproof.
2. **Given** a fireproof site with cookies and stored data, **When** Fire runs, **Then** that site's cookies and stored data are still there afterwards.
3. **Given** Settings → Privacy → Fireproof sites, **When** the person opens it, **Then** every fireproof site is listed, each can be removed, all can be removed together after a confirmation, and an empty list shows an empty-state message.
4. **Given** a site was removed from the list, **When** Fire next runs, **Then** its data is cleared like any other site's.
5. **Given** an incognito tab, **When** the person opens the dashboard, **Then** the fireproof switch is not offered there.
6. **Given** a fireproof site, **When** the person uses Clear browsing data (Spec 016) with Cookies and site data ticked, **Then** that site's data is cleared too — fireproofing protects only against Fire.

---

### User Story 3 - See what is protected on this page (Priority: P2)

A person on a news site opens the hub and taps "14 trackers blocked on this page". A sheet opens showing which tracker hosts were blocked, whether the connection is encrypted, and the switches for protection and fireproofing on this site.

**Why this priority**: The count from Spec 023 tells people protection works; the dashboard shows what it did and puts the per-site controls in one place. It is useful but not essential to the protections themselves.

**Independent Test**: On a local test page with known trackers, open the dashboard and compare the listed hosts with the ones the page requested; flip both switches and confirm their effect.

**Acceptance Scenarios**:

1. **Given** a web page with blocked trackers, **When** the person taps the blocked-trackers line in the hub, **Then** the dashboard opens showing the site's name, the blocked hosts grouped by site (registrable domain), and the total.
2. **Given** a page shown over `https://`, **When** the dashboard is open, **Then** it says the connection is encrypted; over `http://` it says it is not.
3. **Given** a page with no blocked trackers, **When** the person opens the dashboard, **Then** it says no trackers were blocked and still shows the switches.
4. **Given** the dashboard is open, **When** the person turns protection off or on, **Then** it behaves exactly as Spec 023's hub switch (closes, reloads the page with the new setting).
5. **Given** protection is off for the site, **When** the dashboard is open, **Then** it shows protection off and no blocked trackers.
6. **Given** Home or the incognito start page, **When** the hub is open, **Then** there is no dashboard entry.
7. **Given** the dashboard is open, **When** the page navigates or another tracker is blocked, **Then** the dashboard reflects the current page.

---

### User Story 4 - Cookie banners are declined for me (Priority: P3)

A person opens a European news site that shows a large cookie-consent banner. Within a moment the banner disappears; the browser chose "reject non-essential cookies" on their behalf. On a site whose consent tool the browser does not recognise, the banner stays and the person handles it as usual.

**Why this priority**: Banners are an everyday annoyance and accepting them by reflex hands data to trackers. It depends on Spec 023's per-site switch and is the riskiest to page compatibility, so it comes after the core tools.

**Independent Test**: Serve local test pages that imitate the most common consent tools; confirm each banner is dismissed with the reject choice and the page's stored consent records a refusal; confirm nothing happens with protection off for the site.

**Acceptance Scenarios**:

1. **Given** a page using a supported consent tool, **When** its banner appears, **Then** the browser selects the option that refuses non-essential cookies and the banner closes, without the person doing anything.
2. **Given** a consent tool offers only "accept" on its first layer, **When** the banner appears, **Then** the browser opens the settings layer and refuses there if the rules support it; otherwise it leaves the banner alone. It MUST NOT accept on the person's behalf.
3. **Given** an unsupported consent tool, **When** its banner appears, **Then** nothing is changed.
4. **Given** protection is off for the site, **When** its banner appears, **Then** nothing is changed.
5. **Given** a web engine that cannot run bundled scripts at page start, **When** a banner appears, **Then** nothing is changed and no error is shown.

---

### User Story 5 - Harder to fingerprint (Priority: P3)

A person visits a site that tries to identify their device by drawing hidden graphics and sound and reading the exact result, and by reading battery and media-device details. The site still works, but the values it reads differ between sites and sessions, so it cannot use them to recognise the person elsewhere.

**Why this priority**: Fingerprinting replaces cookies once cookies are blocked. The basic level covers the most used techniques with little risk of breaking pages, but it is the least visible protection.

**Independent Test**: On a local fingerprinting test page, read the canvas and audio results twice on the same site and once on another site and compare; check battery and media-device answers; repeat with protection off.

**Acceptance Scenarios**:

1. **Given** protection is on, **When** a page reads back a drawing or an audio rendering, **Then** the values carry small noise that is stable for that site until the app restarts or Fire runs, and differs between sites.
2. **Given** protection is on, **When** a page asks for battery status, **Then** it gets no real battery details.
3. **Given** protection is on, **When** a page lists media devices without having been granted camera or microphone access, **Then** it gets no device names or identifiers.
4. **Given** a site granted camera or microphone access (Spec 021), **When** it lists devices, **Then** it gets the real list, so calls keep working.
5. **Given** protection is off for the site, **When** the page reads any of these, **Then** it gets the real values.
6. **Given** canvas-based images people see (charts, games, maps), **When** they are drawn, **Then** they look the same as without protection.

---

### Edge Cases

- **Fire while a page is loading or a download is starting**: loading stops, the download started before Fire continues.
- **Fire while a dialog, the find bar, fullscreen video or a permission prompt is open**: all are closed; nothing from the old tabs survives.
- **Fire with only one Home tab and no data**: still confirms and ends in the same state.
- **Fire pressed twice quickly**: runs once.
- **App killed during Fire**: on the next start no old tab returns and clearing completes or is repeated; the person never sees a half-cleared state presented as done.
- **Fireproof site that uses a sign-in service on another domain** (for example a mail site signed in through `accounts.example`): only the fireproof site's own data is kept; the person fireproofs the sign-in domain too if they want it kept (documented in the dashboard's help text).
- **Fireproofing a subdomain**: fireproofs the whole registrable domain, like Spec 023's switch.
- **Rotation or language change while the dashboard or the Fire confirmation is open**: both survive with their state.
- **Consent banner inside a frame from another site**: handled when protection is on for the top-level page and the rules cover that consent tool (FR-020).
- **Page that checks for tampering with drawing functions**: may detect the noise; documented limitation.
- **Clear browsing data and fireproof sites**: Clear browsing data ignores fireproofing (US2 scenario 6).

## Requirements *(mandatory)*

### Functional Requirements

**Fire**

- **FR-001**: The hub MUST contain a Fire action, and the Tabs screen MUST contain a Fire button.
- **FR-002**: Fire MUST always ask for confirmation, naming what is cleared and what is kept. Cancelling MUST change nothing.
- **FR-003**: On confirmation, Fire MUST close every normal and incognito tab, end incognito browsing (Spec 012's full incognito teardown), and then clear: all browsing history; all cookies of every site that is not fireproof; the site data (local storage, databases, service workers, cache storage) of every site that is not fireproof and that the app can find — from history, open tabs, remembered site permissions and the web engine's list of sites holding storage (research R3); and the web page cache. When no site is fireproof, all cookies and site data are cleared without exception.
- **FR-003b**: Documented limitations, not failures of FR-003: site data of a site found in none of those sources (for example, its history already removed by the retention window) may remain; on web engines without per-site deletion, local storage and service workers may remain (as Spec 016 A17).
- **FR-003a**: Fire MUST also reset, for every site, the fingerprinting noise (FR-026) and Spec 023's in-memory list of hosts whose HTTPS upgrade failed, so values a site reads after Fire do not match those before.
- **FR-004**: Fire MUST NOT change bookmarks, downloads (records, files and transfers in progress), settings, or sites without protection (Spec 023).
- **FR-004a**: Fire MUST remove remembered camera, microphone and location decisions (Spec 021) and this session's one-time grants for every site that is not fireproof; those of fireproof sites MUST be kept.
- **FR-005**: After Fire the browser MUST show Home in exactly one new normal tab.
- **FR-006**: Fire MUST play a short animation; when the device asks for reduced motion, it MUST use at most a simple fade.
- **FR-007**: Fire MUST run once even if triggered twice, and MUST NOT leave old tabs restorable if the app is stopped part-way; any clearing left undone MUST complete on the next start before tabs are shown.
- **FR-008**: Fire MUST remove every cached tab preview, and every saved site icon except those of hosts that have a bookmark or belong to a fireproof site.

**Fireproof sites**

- **FR-009**: In a normal tab, the dashboard MUST offer a Fireproof switch for the current page's site (registrable domain, as in Spec 023 FR-008). Turning it on MUST be remembered on the device across restarts until turned off.
- **FR-010**: A fireproof site's cookies and site data MUST survive Fire, including those set on its subdomains, with their attributes (expiry, `Secure`, `HttpOnly`, `SameSite`) unchanged where the web engine can report them.
- **FR-010a**: Documented limitations, not failures of FR-010: a fireproof cookie limited to a narrower path on a subdomain the app has not seen may be lost; on web engines that cannot report cookie attributes, kept cookies lose them and a sign-in may not survive an app restart (research R2).
- **FR-011**: The dashboard in an incognito tab MUST NOT offer the Fireproof switch, and incognito data is never kept by Fire.
- **FR-012**: Settings → Privacy MUST contain **Fireproof sites**, listing every fireproof site, each removable, plus a remove-all action that asks for confirmation; an empty list MUST show an empty-state message.
- **FR-013**: Clear browsing data (Spec 016) MUST NOT honour fireproofing, and MUST offer a **Fireproof sites** category, unticked by default, that empties the list (like Spec 021's Site permissions and Spec 023's Sites without protection).
- **FR-014**: The app MUST NOT suggest fireproofing on its own (no prompt after sign-in).

**Privacy dashboard**

- **FR-015**: On a web page, tapping Spec 023's blocked-tracker line in the hub MUST open the dashboard. Home and the incognito start page MUST NOT offer it.
- **FR-016**: The dashboard MUST show: the site's name; the number and list of distinct tracker hosts blocked on the current page (Spec 023 FR-010), grouped by site (registrable domain), not by company; whether the connection is encrypted; the protection switch; and, in normal tabs, the Fireproof switch.
- **FR-017**: The dashboard's protection switch MUST behave exactly as Spec 023 FR-012–FR-014.
- **FR-018**: The dashboard MUST stay current with the page while open, and MUST survive rotation and an in-app language change.

**Bundled scripts (common rules, Constitution §II)**

- **FR-019**: Cookie-banner and fingerprinting scripts MUST be bundled in the app, MUST change only with an app release, and MUST NOT be downloaded or built from remote data.
- **FR-020**: Whether the scripts run MUST be decided by the top-level page's site, as for Spec 023's tracker blocking: when protection is on for it, they MUST run at the start of the page and of every frame in it, whatever site a frame comes from; when it is off, they MUST run in none of its frames. This applies in normal and incognito tabs; the scripts MUST NOT run on Home or the incognito start page. The plan MUST document the exact origin rules, and any case the web engine does not allow is a documented limitation.
- **FR-021**: The scripts MUST NOT give the page any way to call into the app or send it data, and the app MUST NOT receive anything from them.
- **FR-022**: On a web engine that cannot install scripts at page start, both protections MUST simply be off, with no fallback and no message.
- **FR-023**: If the bundled rules require attribution, Settings → About MUST show it in every supported language (as Spec 023 FR-003).

**Cookie-banner auto-decline**

- **FR-024**: For consent tools covered by the bundled rules, the app MUST choose the option that refuses non-essential cookies and close the banner. It MUST NEVER choose an accept option. When refusing is not possible from the rules, the banner MUST be left untouched.
- **FR-025**: The rules MUST cover at least these 10 consent tools: OneTrust, Cookiebot, Didomi, Quantcast Choice, Usercentrics, TrustArc, Sourcepoint, Complianz, CookieYes and Iubenda (research R7), and their size MUST keep the app within +200 KB for this spec.

**Fingerprinting protection**

- **FR-026**: Values a page reads back from drawings and audio renderings MUST carry slight noise that is stable for a site until the app process ends or Fire runs, and differs between sites; what is shown on screen MUST look unchanged.
- **FR-027**: Battery status MUST be withheld from pages.
- **FR-028**: Pages without camera or microphone access MUST NOT get media-device names or identifiers; pages with access MUST get the real list.

**General**

- **FR-029**: Everything in this spec MUST work offline and make no network request of its own.
- **FR-030**: Every new piece of text MUST be translated into all 8 supported languages.
- **FR-031**: The app MUST request no new permission.

### Key Entities

- **Fireproof site**: A registrable domain the person marked in normal browsing; saved on the device; created and removed from the dashboard, removed from Settings or Clear browsing data.
- **Fire run**: One confirmed clearing; transient, except for a marker that lets an interrupted run finish on the next start.
- **Page protection summary**: For the current page — blocked tracker hosts, encryption state, protection state and fireproof state; in memory only.
- **Cookie-banner rules**: Bundled, read-only description of how to refuse on each supported consent tool.
- **Fingerprinting noise seed**: Per site, per app session, in memory only; replaced on Fire.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From any web page, Fire is reachable and confirmed in at most 3 taps, and the browser shows Home within 2 seconds of confirming with 20 tabs and 1,000 history entries.
- **SC-002**: After Fire, on 5 local test sites (not fireproof) that set cookies, local storage, a database, a service worker and cache storage, 100% of that data is gone; on 2 fireproof test sites 100% of it remains.
- **SC-003**: Signed in to 3 real sites, 2 of them fireproof, after Fire the person is still signed in to the 2 fireproof sites and signed out of the third.
- **SC-004**: After Fire, bookmarks, downloads, settings and the sites-without-protection list are byte-for-byte unchanged, and a download started beforehand completes.
- **SC-004a**: After Fire, a non-fireproof site's remembered camera decision is gone and a fireproof site's is kept.
- **SC-004b**: After Fire, the only saved site icons left on the device belong to bookmarked hosts and fireproof sites.
- **SC-005**: Interrupting the app during Fire 5 times never brings an old tab back, and History is empty on every restart.
- **SC-006**: The dashboard lists exactly the tracker hosts blocked on the SC-001 test page of Spec 023 (12), and opens in at most 1 tap from the hub.
- **SC-007**: On 20 real sites that show a consent banner, at least 12 have it refused automatically, none has an accept option chosen, and none is left unusable.
- **SC-008**: On a fingerprinting test page, the canvas and audio values read twice on one site in one session are equal and differ from another site's, and differ again after Fire; with protection off they equal the unprotected values.
- **SC-009**: On 10 popular real sites with drawing-heavy features (maps, charts, games, video calls), all work as with protection off.
- **SC-010**: Median page-load time on 10 typical news sites is no more than 5% worse than before this spec, on a mid-range device.
- **SC-011**: App cold start, first run and returning, is no more than 5% slower than before this spec.
- **SC-012**: The release app grows by at most 200 KB.
- **SC-013**: In airplane mode, Fire, fireproofing and the dashboard behave the same on locally served pages, and a network capture shows no request made by these features.

## Assumptions

- **Design waiver (owner decision, 2026-10-13, as for Specs 020–023)**: Constitution §X's "Design before spec" rule is waived. The Fire tile and button, its confirmation and animation, the dashboard sheet and the Settings → Privacy → Fireproof sites screen are built from existing Spec 019 components (hub tiles and rows, bottom sheets, list rows, switches, empty states, the shared confirm dialog) and tokens. A later visual change MUST NOT change the behaviour specified here.
- **"Site"** means registrable domain, using Spec 023's bundled public-suffix data.
- **History is cleared in full by Fire**, fireproof sites included: fireproofing keeps people signed in, it does not keep a record of visits (as in DuckDuckGo).
- **The web page cache is cleared in full**, fireproof sites included: it holds no sign-in state and the engine offers no per-site cache clearing.
- **Downloads are never touched**: files are the person's, and Fire is about browsing traces.
- **Fire does not touch Spec 023's sites without protection**: that list is a compatibility setting, not a trace.
- **Fingerprinting noise resets with the app process and with Fire** (FR-003a), so a site cannot link two sessions by it; it is never saved.
- **Cookie-banner rules source**: chosen in research among open-source rule sets whose license allows bundling in a free Play Store app (candidates: DuckDuckGo's autoconsent, MPL-2.0; Consent-O-Matic); the choice, license and trimming are recorded in research.
- **Minimum Android version stays 24**; scripts are off on web engines without page-start script support (FR-022), and research records which versions that covers.
- **Technical risks for research** (not requirements):
  - The web engine's cookie store has no per-site delete; keeping fireproof cookies may mean expiring each non-fireproof cookie individually, which cannot see cookie attributes (Spec 012 R1).
  - Spec 016's "complete" site-data clearing removes all sites at once; research checks whether site data can be cleared per site, and how to keep fireproof sites' data otherwise.
  - Old web engines (API 24 WebView 53, CI's API 29 WebView 74) may not support page-start scripts.

## Dependencies

- **Spec 023** — blocked-tracker count and hub line, per-site protection switch, public-suffix data, Settings → Privacy (FR-015–FR-017, FR-020).
- **Spec 019** — hub, Tabs screen and design system (FR-001, FR-006, FR-012).
- **Spec 016** — Clear browsing data, the site-data clearing paths (FR-003, FR-013).
- **Spec 012** — incognito teardown (FR-003, FR-011).
- **Spec 011 / 014** — tabs, previews, favicons, history (FR-003, FR-005, FR-008).
- **Spec 021** — site permissions and media access (FR-004, FR-028).

## Out of Scope

- Automatic fireproof suggestions after sign-in; a Fire timer or clear-on-exit; clearing per tab.
- Stronger fingerprinting protection (WebGL, fonts, screen size, hardware details); a user-visible fingerprinting switch separate from the per-site protection switch.
- Updating cookie-banner rules without an app release; hiding page elements other than consent banners; accepting banners on the person's behalf.
- Grouping or naming trackers by company: no license-compatible owner data exists (Spec 023 research R2); trackers are grouped by site.
