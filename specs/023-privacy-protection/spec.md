# Feature Specification: Privacy Protection

**Feature Branch**: `023-privacy-protection`
**Created**: 2026-10-11
**Status**: Draft
**Input**: User description: "023-privacy-protection — Phase 6 spec 4/5 (Constitution v1.6.0), depends on Spec 022. Network-level privacy protections working fully offline from data bundled in the APK: third-party tracker blocking from a bundled host list, HTTPS upgrade with fallback, tracking-parameter stripping and referrer trimming, Global Privacy Control. Every protection on by default, switchable off per site; a blocked-tracker count for the current page. Fire button, privacy dashboard and injected scripts are Spec 024."

## Owner decisions taken before this spec (2026-10-11)

| Topic | Decision |
|---|---|
| Scope | **The old 023 is split** (Constitution v1.6.0). This spec keeps the network-level protections; the Fire button, fireproof sites, privacy dashboard, cookie-banner and fingerprinting scripts are Spec 024 `privacy-tools`. |
| Defaults | **Every protection is on for everyone from the first launch.** There is no global off switch; the person turns protection off **for one site** when that site breaks. |
| Protection data | **Bundled inside the app**, changed only by an app release, never downloaded (Constitution §I). The source list is chosen in research; its license must allow bundling in a free Play Store app. |
| Size budget | **At most +500 KB** on the release app, lists included. |
| Design | **Waived as for Specs 020–022** (see Assumptions). |

## Clarifications

### Session 2026-10-11

- Q: How broad should the block list be? → A: Trackers and advertising networks that follow people across sites; hosts that serve content the person chose to see (embedded video, maps, sign-in buttons, CDNs) are not on the list.
- Q: Does Clear browsing data also clear the list of sites without protection? → A: Yes, as a new opt-in category, unticked by default, like Spec 021's Site permissions.
- Q: What does the blocked count on the hub count? → A: Distinct tracker hosts blocked on the page, not individual requests and not companies.
- Q: When a page ends up on `http://` (not encrypted), is the person told? → A: Yes, quietly: the hub shows a "Connection not secure" line for any `http://` page, including sites with protection off; no pop-up or notice while browsing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trackers on a page are blocked without anything to set up (Priority: P1)

A person installs ThirtySix and reads a news article. The page tries to load analytics scripts, advertising pixels and social-media trackers from other companies' servers. None of those requests leaves the phone. The article itself, its pictures and its video load normally. The person did not turn anything on.

**Why this priority**: Tracker blocking is the protection people expect from a privacy browser, and the one that makes every other page on the web lighter and more private. It is the core of this spec.

**Independent Test**: Load a local test page that requests resources from hosts on the bundled list and from hosts not on it; confirm only the listed third-party requests are refused and the rest of the page renders.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** a page loads a script, image, frame or background request from a third-party host on the bundled tracker list, **Then** that request is not made and the rest of the page keeps loading.
2. **Given** a host on the list, **When** a page requests a subdomain of it (for example `stats.tracker.example` when `tracker.example` is listed), **Then** that request is blocked too.
3. **Given** a page on `tracker.example` itself, **When** it loads its own resources from `tracker.example` or its subdomains, **Then** nothing is blocked (first-party).
4. **Given** the person types, taps or is sent a link to an address on a listed host, **When** the page opens, **Then** the page itself loads; only its third-party requests are subject to blocking.
5. **Given** an incognito tab, **When** a page in it loads trackers, **Then** they are blocked exactly as in a normal tab.
6. **Given** a blocked request, **When** it is refused, **Then** no error page, message or dialog appears.

---

### User Story 2 - See how many trackers were stopped and turn protection off for a broken site (Priority: P1)

A person opens the hub on a page and sees how many trackers were blocked there. A shopping site's checkout button does nothing; the person turns protection off for that site from the hub, the page reloads and checkout works. Every later visit to that site, even after restarting the phone, loads without protection, until the person turns it back on — from the hub on that site, or from a list in Settings.

**Why this priority**: Blocking will occasionally break a site. Without a per-site switch the only way out would be to stop using the browser for that site, so the switch is what makes on-by-default safe to ship. The count is how the person knows the protection is working.

**Independent Test**: On a test page with known trackers, read the count in the hub, turn protection off for the site, confirm the trackers load and the count reads zero, restart the app, confirm it is still off, then turn it back on from Settings.

**Acceptance Scenarios**:

1. **Given** a loaded web page on which 40 requests to 7 different tracker hosts were blocked, **When** the person opens the hub, **Then** it shows that 7 trackers were blocked on this page and that protection is on for this site.
2. **Given** a new page starts loading in the tab, **When** the person opens the hub, **Then** the count reflects only that new page.
3. **Given** protection is on for `shop.example`, **When** the person switches it off in the hub, **Then** the hub closes, the page reloads with no protection applied, and the hub now shows protection off for this site.
4. **Given** protection is off for `shop.example`, **When** the person opens any page of `shop.example` (or `www.shop.example`) in a normal tab, now or after an app restart, **Then** no protection is applied to it, and the hub says so.
5. **Given** protection is off for one or more sites, **When** the person opens Settings → Privacy → Sites without protection, **Then** each such site is listed and can be removed, which turns protection back on for it.
6. **Given** the person switches protection off for a site in an incognito tab, **When** incognito browsing ends (the last incognito tab is closed), **Then** that choice is forgotten and was never saved; normal tabs were never affected by it.
7. **Given** protection is off for a site in normal browsing, **When** the person opens that site in an incognito tab, **Then** protection is on there (incognito never reads the remembered list).
8. **Given** the Home page or the incognito start page is shown, **When** the person opens the hub, **Then** no tracker count and no per-site switch are shown.

---

### User Story 3 - Insecure addresses open securely when the site supports it (Priority: P2)

A person taps an old `http://` link in a chat app. ThirtySix first tries the secure `https://` version; the site supports it, so the page opens securely and the address bar shows `https://`. Another, older site only works over `http://`; the secure attempt fails, and the browser opens the original `http://` page without the person doing anything, and does not retry the secure version for that site again during this session.

**Why this priority**: Most sites support HTTPS but many links still point at `http://`. Upgrading them protects what the person reads and types from anyone on the network, at no cost to them. It is ranked below tracker blocking because the web is already mostly HTTPS.

**Independent Test**: Open `http://` addresses of a site that serves HTTPS, one that serves only HTTP, and one that redirects HTTPS back to HTTP; confirm the first opens as `https://`, the other two open over `http://` once, without a loop.

**Acceptance Scenarios**:

1. **Given** the person opens `http://example.org/path?q=1` (typed, from a link on a page, from a bookmark or history entry, or from another app), **When** the site serves the same page over HTTPS, **Then** `https://example.org/path?q=1` loads and the address bar shows it.
2. **Given** the secure attempt fails because the site does not answer securely, has an invalid certificate, or redirects back to `http://` for the same host, **When** that happens, **Then** the original `http://` address loads, with no error shown for the failed secure attempt.
3. **Given** a host whose secure attempt failed, **When** the person opens another `http://` address on that host during the same app session, **Then** it opens over `http://` directly, without a new secure attempt.
4. **Given** the secure version answers with an error page (for example "not found"), **When** it loads, **Then** it is shown as the site's answer; there is no fallback to `http://`.
5. **Given** an address on `localhost`, an IP address, or a single-label name (for example `http://router/`), **When** it is opened, **Then** it is not upgraded.
6. **Given** protection is off for the site, **When** its `http://` address is opened, **Then** it is not upgraded.
7. **Given** a page shown over `http://` (after a fallback, a non-upgradable host, or a site with protection off), **When** the person opens the hub, **Then** it shows that the connection is not secure; no notice appears while browsing.

---

### User Story 4 - Links no longer carry tracking tags (Priority: P2)

A person taps a link from a newsletter: `https://shop.example/item?id=42&utm_source=mail&utm_campaign=fall&fbclid=AbC`. The page opens as `https://shop.example/item?id=42`. The tags that only exist to tell marketers where the person came from are removed before the request is made, so they are not sent, not stored in history and not shared if the person shares the page.

**Why this priority**: Tracking tags follow people from site to site and end up in history, bookmarks and shared links. Removing them is cheap and invisible, but it is less impactful than blocking trackers outright.

**Independent Test**: Open an address containing a mix of listed tracking parameters and ordinary parameters; confirm only the listed ones are removed and the ordinary ones and the fragment are kept in order.

**Acceptance Scenarios**:

1. **Given** an address with listed tracking parameters, **When** the person opens it (typed, link on a page, bookmark, history, another app), **Then** the request is made without those parameters, and the address bar, history and Share show the cleaned address.
2. **Given** an address with only tracking parameters (`https://a.example/?utm_source=x`), **When** cleaned, **Then** it becomes `https://a.example/` with no dangling `?`.
3. **Given** parameters that are not on the list, and a `#fragment`, **When** the address is cleaned, **Then** they are kept unchanged and in their original order.
4. **Given** protection is off for the destination site, **When** the address is opened, **Then** no parameter is removed.

---

### User Story 5 - Sites are told not to sell or share the person's data (Priority: P3)

Every site the person visits receives the Global Privacy Control signal, the standard way for a browser to say "do not sell or share my personal data", which some sites and some laws honour.

**Why this priority**: It costs nothing, cannot break a site, and gives people a legal opt-out where it is recognised. It ranks last because the effect depends on the site.

**Independent Test**: Load a local test page that records the request headers it receives; confirm the signal is present on the page request, in normal and incognito tabs, and also on a site with protection turned off.

**Acceptance Scenarios**:

1. **Given** any page load, **When** the request is sent, **Then** it carries the Global Privacy Control signal.
2. **Given** protection is off for a site, **When** a page of that site loads, **Then** the signal is still sent (it is not a protection that can break a site).

---

### User Story 6 - Cross-site requests reveal only where the person came from, not what they were reading (Priority: P3)

When a page links to or loads something from another site, the other site learns only the origin of the page (`https://news.example/`), never the full address with the article path and any query (`https://news.example/2026/health/my-condition?id=…`).

**Why this priority**: Full referrers leak what the person was reading to every third party. Modern web defaults already trim most of them, so this mostly closes the gaps where pages ask for full referrers.

**Independent Test**: From a local page that asks to send its full address as referrer, open a link to and load an image from a second local site; confirm the second site receives only the origin. A same-site link receives the full address.

**Acceptance Scenarios**:

1. **Given** a page on site A, **When** it navigates to or loads a resource from site B, **Then** site B receives at most A's origin as the referrer, even if the page asked to send more.
2. **Given** a page on site A, **When** it navigates within site A, **Then** the referrer behaves as the web normally does.
3. **Given** protection is off for site A, **When** its pages make cross-site requests, **Then** referrers are not trimmed.

---

### Edge Cases

- **Tracker list matches the page's own site via a different hostname** (for example the page is `www.example.com` and the request goes to `cdn.example.com`, both on `example.com`): same site, not blocked.
- **Companies that use several registrable domains** (for example a site and its separate CDN domain owned by the same company): treated as third party unless the bundled data says they belong to the same owner; if the list provides ownership data, requests to the same owner as the page are first-party.
- **Embedded content the person chose to see** (embedded video, maps, sign-in buttons, content delivery networks): not on the list (FR-004a), so it loads. If a listed host still serves something a site needs, the person turns protection off for the site; no per-request allow is offered.
- **Downloads** started by the person are never blocked, even from a listed host.
- **A page of the excepted site embedded in another site's frame**: the exception follows the top-level page's site, not the frame's.
- **Pop-ups and new tabs opened by a page** (Spec 021): protections follow the new tab's own page.
- **Page loaded from a saved state after the app is killed**: the count starts at zero for the restored page.
- **HTTPS upgrade during airplane mode**: both attempts fail; the person sees the normal offline error for the original address, once.
- **A site that redirects `http://` → `https://` itself**: unchanged behaviour; the upgrade just saves a round trip.
- **Typed address with a port** (`http://example.org:8080/`): upgraded keeping the port; if that fails, falls back like any other failure.
- **Tracking parameter inside the fragment** (`#utm_source=x`): not touched; fragments are never sent to servers.
- **An excepted site's entry and the www prefix**: one site entry covers the registrable domain and all its subdomains.
- **List of sites without protection is empty**: Settings shows an empty-state message.
- **In-app language change or rotation while the hub is open**: the count and switch state survive.
- **Many trackers on one page** (hundreds): the count keeps up and the page is not slowed by counting.

## Requirements *(mandatory)*

### Functional Requirements

**Protection data**

- **FR-001**: The tracker list, the tracking-parameter list and any ownership data used by this spec MUST ship inside the app and MUST change only with an app release. The app MUST NOT download, update or verify them over the network.
- **FR-002**: Protections MUST make no network request of their own.
- **FR-003**: If the bundled source requires attribution, Settings → About MUST show it in every supported language.

**Tracker blocking**

- **FR-004**: A request made by a page to a host on the tracker list, or a subdomain of a listed host, MUST NOT be made when that host belongs to a different site than the top-level page, unless protection is off for the top-level page's site.
- **FR-004a**: The list MUST cover analytics, tracking pixels and advertising networks that follow people across sites. It MUST NOT include hosts whose main purpose is serving content the person chose to see — embedded video and maps, sign-in services, content delivery networks. Research records how the chosen source is filtered to meet this.
- **FR-005**: The main-frame navigation the person asked for (typed address, tapped link, bookmark, history entry, link from another app) MUST NOT be blocked, whatever its host.
- **FR-006**: Downloads the person starts MUST NOT be blocked.
- **FR-007**: A blocked request MUST fail silently: no error page, message or dialog, and the rest of the page MUST continue loading.
- **FR-008**: Two hosts MUST be treated as the same site when they share a registrable domain (the part a person registers, such as `example.co.uk`); if the bundled data groups domains by owner, hosts with the same owner MUST also count as first-party.
- **FR-009**: Tracker blocking MUST apply in incognito tabs exactly as in normal tabs.

**Blocked-tracker count**

- **FR-010**: For the page currently shown in a tab, the app MUST count the distinct tracker hosts that had at least one request blocked by FR-004 (several requests to one host count once). The count MUST restart at zero when a new page starts loading in the tab (not on an in-page address change of the same document).
- **FR-011**: While a web page is shown, the hub MUST show that page's count and whether protection is on for its site. On the Home page and the incognito start page, the hub MUST show neither.

**Per-site switch**

- **FR-012**: The hub MUST let the person turn protection off, and back on, for the current page's site. Changing it MUST close the hub and reload the current page with the new setting.
- **FR-013**: In a normal tab, turning protection off MUST be remembered on the device for that site (its registrable domain and all subdomains) across app restarts, until the person turns it back on.
- **FR-014**: In an incognito tab, the switch MUST affect only incognito browsing, MUST NOT be saved, MUST be forgotten when the last incognito tab closes, and incognito MUST NOT read the remembered list from normal browsing (mirrors Spec 021 FR-004).
- **FR-015**: Settings → Privacy MUST contain **Sites without protection**, listing every remembered site with protection off, each removable; removing one turns protection back on for it. An empty list MUST show an empty-state message.
- **FR-016**: When protection is off for a site, tracker blocking, HTTPS upgrade, tracking-parameter stripping and referrer trimming MUST NOT be applied to its pages; Global Privacy Control MUST still be sent.
- **FR-016a**: Clear browsing data (Spec 016) MUST offer a **Sites without protection** category, unticked by default, as Spec 021 does for Site permissions; when ticked, every remembered site without protection is removed, so protection is on again everywhere.
- **FR-017**: There MUST be no global switch turning all protections off.

**HTTPS upgrade**

- **FR-018**: When the person opens an `http://` address in the main frame — typed, a link on a page, a bookmark, a history entry, an address from another app, or a page-opened tab — the app MUST first load the same address with `https://` (host, port, path, query and fragment unchanged).
- **FR-019**: If the secure attempt fails to connect, fails certificate validation, or is redirected to `http://` on the same host, the app MUST load the original `http://` address instead, with no error shown for the secure attempt.
- **FR-020**: A host whose secure attempt failed MUST NOT be upgraded again until the app process ends; this memory MUST NOT be saved to disk.
- **FR-021**: If the secure attempt returns any page, including an error page, that page MUST be shown and no fallback MUST occur.
- **FR-022**: Addresses whose host is `localhost`, an IP address, or a single-label name MUST NOT be upgraded.
- **FR-022a**: While a web page shown over `http://` is current, the hub MUST show a "Connection not secure" line, whatever the reason the page is not encrypted; the app MUST NOT show a separate notice or dialog for it.
- **FR-023**: Requests made by a page for its resources MUST NOT be upgraded by this spec; the existing mixed-content behaviour is unchanged.

**Tracking parameters**

- **FR-024**: Before a main-frame navigation is requested (from the same sources as FR-018), the app MUST remove every query parameter whose name matches the bundled tracking-parameter list (exact names, and name prefixes such as `utm_` where the list says so).
- **FR-025**: Parameters not on the list MUST be kept, with their values and order unchanged; the fragment MUST be kept; a query left empty MUST be removed entirely, including its `?`.
- **FR-026**: The cleaned address MUST be the one requested, shown in the address bar, recorded in history (non-incognito) and offered by Share.
- **FR-027**: Redirects issued by servers MAY carry tracking parameters; this spec does not rewrite them (documented limitation).

**Referrer trimming**

- **FR-028**: For a request from a page on one site to a different site, the referrer sent MUST be at most the origin of the page, regardless of the page's own referrer settings. Same-site requests MUST keep the web's normal referrer behaviour.
- **FR-029**: FR-028 MUST hold for cross-site main-frame navigations; for page resources it MUST hold wherever the web engine allows, and research MUST document any request type it cannot cover.

**Global Privacy Control**

- **FR-030**: Every page request MUST carry the Global Privacy Control signal (`Sec-GPC: 1`), in normal and incognito tabs, including sites with protection off. Page resources MUST carry it wherever the web engine allows; research MUST document gaps.
- **FR-031**: The script-visible form of the signal is out of scope unless the web engine exposes it without injecting a script (script injection belongs to Spec 024, Constitution §II).

**General**

- **FR-032**: Protections MUST work with no network connection for locally served pages and MUST NOT need any account or setup.
- **FR-033**: Every new piece of text MUST be translated into all 8 supported languages.
- **FR-034**: The app MUST request no new permission.
- **FR-035**: Deciding whether to block a request MUST NOT noticeably slow pages: see SC-005 and SC-006.

### Key Entities

- **Tracker list**: Bundled set of hosts known to track people across sites, optionally with the company that owns each; read-only, versioned with the app.
- **Tracking-parameter list**: Bundled set of query-parameter names and name prefixes that exist only to track; read-only.
- **Site without protection**: A registrable domain the person turned protection off for in normal browsing; saved on the device; created from the hub, removed from the hub, Settings or Clear browsing data.
- **Incognito site exception**: Same as above for incognito browsing; held in memory only, discarded when incognito ends.
- **Page block count**: Number of distinct tracker hosts blocked for the page currently shown in a tab; in memory only, reset per page.
- **Failed-upgrade host**: A host whose HTTPS attempt failed in this app session; in memory only.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a local test page that makes 30 requests to 12 different listed third-party hosts and 10 requests to unlisted hosts, all 30 listed requests are blocked, all 10 others load, and the hub shows 12.
- **SC-002**: On 10 popular real sites (news, shopping, search, video, social), every site shows at least one blocked tracker, and on at least 9 of them the main content, navigation and sign-in link work as with protection off.
- **SC-003**: Of 10 real `http://` addresses whose sites support HTTPS, 10 open as `https://`; of 3 sites that support only HTTP (or redirect back to it), all 3 open over `http://` within one extra attempt and never loop.
- **SC-004**: 10 test addresses mixing listed tracking parameters, ordinary parameters and fragments are cleaned exactly as FR-024/FR-025 specify, in the address bar, history and Share.
- **SC-005**: On 10 typical news sites, median page-load time over 5 loads each with protection on is no worse than with protection off for the same site, on a mid-range device.
- **SC-006**: App cold start, both first run and returning, is no more than 5% slower than before this spec on the same device and build type.
- **SC-007**: Turning protection off for a site from the hub takes at most 2 taps from the page, and the choice survives 3 app restarts; removing it from Settings restores protection on the next load.
- **SC-008**: A site turned off in an incognito tab is protected again in a new incognito tab after all incognito tabs were closed, and was never protected-off in normal tabs.
- **SC-009**: A header-recording test page receives the Global Privacy Control signal on 100% of page loads, in normal tabs, incognito tabs and sites with protection off.
- **SC-010**: The release app grows by at most 500 KB.
- **SC-011**: With the phone in airplane mode, the local test page of SC-001 gives the same result.
- **SC-012**: A network capture during SC-001 and SC-002 shows no request made by the protections themselves.

## Assumptions

- **Design waiver (owner decision, 2026-10-11, as for Specs 020–022)**: Constitution §X's "Design before spec" rule is waived because Claude Design is not available on the owner's account. The hub's tracker count and per-site switch, and the Settings → Privacy → Sites without protection screen, are built from existing Spec 019 design-system components (hub rows/tiles, Settings rows, list rows, empty states, shared dialogs) and tokens. The owner will supply designs later; a later visual change MUST NOT change the behaviour specified here.
- **"Site"** means registrable domain (eTLD+1), determined from a public-suffix list bundled in the app or already available on the device; no lookup goes over the network.
- **The count is of distinct hosts** (clarified); grouping by company is the dashboard's job in Spec 024.
- **Global Privacy Control stays on for excepted sites** because it cannot break a page and is a legal opt-out in some places; the per-site switch exists to fix broken sites.
- **The failed-upgrade memory is per app process**, shared by normal and incognito tabs, and holds hosts only; it is never written to disk.
- **Web-engine limits**: some request types (for example requests made by service workers or WebSockets) may not be interceptable on every Android/WebView version. Research documents which are covered; uncovered types are a known limitation, not a failure of this spec.
- **List source and size**: the tracker list is chosen in research among public lists whose license permits bundling in a free app on Google Play (candidates include EasyPrivacy, Disconnect and DuckDuckGo's tracker data); the choice and its license are recorded in research. Lists may be reduced to host-level entries to fit the size budget.
- **No cosmetic filtering**: page elements are not hidden; only requests are blocked.
- **Minimum Android version** stays 24; protections that need a newer web engine feature are off on older versions and research records which.

## Dependencies

- **Spec 019** — hub sheet, Settings rows and the design system (FR-011, FR-012, FR-015).
- **Spec 020** — addresses arriving from other apps (FR-005, FR-018, FR-024).
- **Spec 021** — Settings → Privacy, page-opened tabs, incognito site rule, cleartext allowed in release builds (FR-014, FR-018, FR-019).
- **Spec 012** — incognito lifecycle (FR-014).
- **Spec 014 / Spec 022** — history recording and Share use the cleaned address (FR-026).

## Out of Scope

- Fire button, fireproof sites, per-site privacy dashboard, cookie-banner auto-decline, fingerprinting protection, and any injected script — **Spec 024 `privacy-tools`**.
- Updating lists without an app release; any server-backed protection; VPN or app-level tracking protection; cosmetic ad filtering; per-request allow-listing; a global off switch.
