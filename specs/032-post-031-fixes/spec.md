# Feature Specification: Post-031 Audit Fixes

**Feature Branch**: `032-post-031-fixes`
**Created**: 2026-10-21
**Status**: Draft
**Input**: User description: "Fix every finding of the 2026-10-21 audit of the Spec 031 code, no new features. Most are regressions introduced by Spec 031. Each fix needs a failing-first regression test where the logic is non-trivial; device checks on an emulator for the tap rule, long-press download cookies, tab restore after an error, certificate errors after a redirect, and the error-page title."

## Context

Spec 031 (PR #54, merged) fixed the first audit. A second audit of the merged code on 2026-10-21 found 20 ranked defects and 9 smaller ones. Twelve of them were introduced by Spec 031 itself:

- the redirect tap window;
- cookies on long-press downloads;
- the sticky error state blocking tab restore;
- the Fire keep-set read;
- the import scan cost;
- the cache temp file;
- the redirect certificate check;
- the cookie restore timeout;
- the referrer;
- the import address check;
- capture cleanup;
- the one-letter search message.

This spec fixes all of them and adds no feature. The table at the end maps each audit item to its requirement.

What the app does today, where it matters here:

- **A tap that redirects to another app.** It counts as a tap when any finger lift on the page happened in the last second, including the end of a scroll.
- **Downloads** carry the site's cookies and the full page address. On a long-press "Download link", the cookies are read for the link itself, before the download service follows any redirect.
- **The error screen** stays until a new page starts loading. While it is up, switching tabs skips restoring the other tab's back and forward history.
- **Fire** reads the fireproof list from storage before clearing.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pages still cannot open other apps on their own (Priority: P1)

The person scrolls or swipes a page. Nothing opens, even if the page reacts to the scroll by redirecting to another app. A real tap on a link that redirects to another app still opens it. "Open in app" links can never send the person back into this browser as a new tab.

**Why this priority**: Spec 031's tap window let any scroll count as a tap. That reopens the hole Spec 031 closed.

**Independent Test**: On a test page whose `touchend` handler redirects through a server 302 to another app, scroll the page. No app opens and the offer snackbar shows. Tap a link that 302s to another app. The app opens.

**Acceptance Scenarios**:

1. **Given** a page that redirects to another app's address when a scroll or swipe ends, **When** the person scrolls, **Then** no app opens, and the "wants to open another app" offer shows.
2. **Given** a link that the server redirects to another app's address, **When** the person taps it, **Then** the app opens, as in Spec 031.
3. **Given** a page script that starts a navigation with no tap, **When** the server redirects it to another app's address, **Then** no app opens and the offer shows.
4. **Given** an "open in app" link that names this browser as its target or carries a web address, **When** the person taps it, **Then** the web address loads in the same tab, and no new tab is created.

---

### User Story 2 - Sign-ins and private data stay put (Priority: P1)

Downloading a link by long-press never hands the site's cookies to another site. Downloads never tell another site the full address of the page the person was on, and never tell any site from incognito. Fire never crashes the app. If its cookie step cannot finish, it reports that instead of claiming success. Photos taken for an upload do not outlive the page session, even when the app is swiped away. Clearing browsing data finishes even if the person leaves Settings, and also clears the hosts remembered for the HTTPS upgrade.

**Why this priority**: Each item leaks a sign-in, a browsing trace, or a private photo, or leaves the app unusable.

**Independent Test**: Long-press a link on site A that redirects to site B and choose Download. B receives no cookie of A. Make the fireproof list unreadable and start Fire. The app does not crash, and Fire reports the failed steps.

**Acceptance Scenarios**:

1. **Given** a long-pressed link, **When** the person chooses Download, **Then** the request carries no cookie, in normal and incognito tabs alike.
2. **Given** a download started by the page itself, **When** it begins, **Then** it carries the tab's own cookies for the final address, as in Spec 031.
3. **Given** a download from a page on another site, **When** it begins, **Then** the referrer is only the page's origin. It is nothing when the page is not `http(s)`, when an `https` page downloads over `http`, or when the tab is incognito.
4. **Given** the fireproof list cannot be read, **When** Fire runs, **Then** the app does not crash. Fire skips every step that needs the list, reports those steps as failed, and never treats the list as empty.
5. **Given** an interrupted Fire and an unreadable fireproof list at the next launch, **When** the app starts, **Then** it opens normally.
6. **Given** clearing the incognito cookies times out while leaving incognito, **When** Fire runs, **Then** the close-incognito step is reported as failed and the cookie step wipes all non-fireproof cookies.
7. **Given** photos were taken for an upload and the app was swiped away, **When** the browser screen opens again or Fire runs, **Then** those photos are gone.
8. **Given** the person starts clearing browsing data, **When** they leave Settings before it finishes, **Then** every chosen category is still cleared.
9. **Given** the person clears history, **When** it finishes, **Then** the list of hosts where the HTTPS upgrade failed is empty too.
10. **Given** Fire removes site permissions, **When** any removal fails, **Then** that step is reported as failed. All removals happen together or not at all.

---

### User Story 3 - Tabs and errors behave (Priority: P1)

After a page fails, switching to another tab keeps that tab's back and forward history. A certificate error on the page a redirect lands on shows the error screen. The error page's own title never replaces the real title of a page in history or in the tab. A retry from the error screen shows that it is loading. Tapping a tab in the switcher always makes it the current tab, even if a background tab reports a new title at that moment.

**Why this priority**: Losing back and forward history cannot be undone. A blank page in place of a certificate warning hides a security signal.

**Independent Test**: Open tab A with some history. In tab B, load a host that does not exist. Switch to A and press Back. A's previous page shows.

**Acceptance Scenarios**:

1. **Given** the current page has failed, **When** the person switches to another tab, **Then** that tab is restored with its back and forward history.
2. **Given** an address that redirects to a host with a bad certificate, **When** it loads, **Then** the error screen shows.
3. **Given** a page that loaded, then failed on reload, **When** the engine reports its error page's title, **Then** the history entry and the tab keep the real title.
4. **Given** the error screen, **When** the person taps Retry, **Then** a loading indication shows within one second until the page loads or fails again.
5. **Given** a tab reports a new title at the moment the person picks it in the switcher, **When** both updates land, **Then** the picked tab is current and the title is updated.

---

### User Story 4 - Files are never lost, mixed or disguised (Priority: P1)

Deleting a download removes only that download's own file. Site icons and tab previews stay whole even when two saves of the same one overlap. A downloaded file's name cannot pretend to have a different extension. Downloads served with any common "generic" type still open in a suitable app. A second download of a very long name does not fail.

**Why this priority**: A wrong delete cannot be undone, and a disguised extension leads people to open the wrong kind of file.

**Independent Test**: Download `report.pdf`, delete it in the Files app, and download `report.pdf` again. Delete the first entry in the app. The second file stays.

**Acceptance Scenarios**:

1. **Given** a download the download service no longer knows, **When** the person deletes it in the app, **Then** no file on disk is deleted, and only the entry is removed.
2. **Given** a download the service still knows, **When** the person deletes it, **Then** its file is removed, as today.
3. **Given** two saves of the same icon or preview at the same time, **When** both finish, **Then** the stored image is one of the two, whole.
4. **Given** a server-suggested name with a right-to-left override or other invisible formatting characters, **When** saved, **Then** those characters are removed and the shown name ends with the real extension.
5. **Given** a download served as `binary/octet-stream`, `application/force-download`, `application/x-download` or `application/download`, **When** the person opens it, **Then** the type comes from the file extension.
6. **Given** a server-suggested name at the length limit, **When** it is downloaded twice, **Then** the second download succeeds with the system's numbered name.
7. **Given** a tab preview captured after its tab was closed or after Fire, **When** it arrives, **Then** it is not saved.

---

### User Story 5 - Import and search stay quick and keep every bookmark (Priority: P2)

Importing a 10,000-link file finishes quickly, and a broken or hostile file cannot run for minutes. Links with a second `#`, square brackets, international host names or underscores in the host are imported. Typing one letter in bookmark search does not claim that nothing matches.

**Why this priority**: These features work, but slowly or with small losses.

**Independent Test**: Import a 10,000-link Chrome-style export. It completes in under 2 seconds on the reference device. A link to `https://münchen.de/` is imported.

**Acceptance Scenarios**:

1. **Given** a 10,000-link file, **When** imported, **Then** parsing takes under 2 seconds on the reference device.
2. **Given** a 10 MB file with no closing tags, **When** imported, **Then** parsing ends within 5 seconds on the reference device, with whatever it could read.
3. **Given** links such as `https://a.example/#x#y`, `https://a.example/p[1]`, `https://münchen.de/` and `https://my_site.example.com/`, **When** imported, **Then** each is kept, with the address encoded where needed.
4. **Given** the person types one character in bookmark search, **When** the search does not run, **Then** no "no bookmarks match" message shows.

---

### User Story 6 - Smaller fixes and less code (Priority: P3)

Download statuses stay right when one status lookup fails. Lists render without blocking on disk reads. Code that only forwards, or that repeats a decision made elsewhere, is removed.

**Why this priority**: These need unusual conditions to show, or only cost maintenance.

**Independent Test**: The full unit and architecture suites pass, and the removed items are gone from the source tree.

**Acceptance Scenarios**:

1. **Given** more than 500 downloads and one failing status lookup, **When** the Downloads screen shows, **Then** only the downloads in the failed lookup lack a fresh status. The others are correct.
2. **Given** history, tab or shortcut rows with images, **When** they render, **Then** no file metadata is read on the main thread during composition.
3. **Given** the cleanup, **When** the static checks and tests run, **Then** they pass with no new allowlist entries.

---

### Edge Cases

- A tap on a link that runs script, which then starts a navigation that the server redirects to another app: this counts as tapped, because the navigation the redirect belongs to came from the tap.
- A redirect chain longer than one hop: the tap counts for every hop of the same navigation and for no later navigation.
- A long-press download of a link on the same site: still no cookie. Logged-in long-press downloads may need the person to open the link instead. This is accepted for safety.
- The fireproof list is readable but empty: Fire clears everything, as today.
- A tab switch while the new tab's own last state was an error: it is restored from its saved state, which the engine shows as it was.
- A certificate error on a sub-resource of the redirect target: still no error screen (Spec 031 FR-023).
- An imported link whose IDN host cannot be converted: it is skipped and counted as skipped.
- A file name that is only formatting characters plus an extension: it falls back to the default download name with that extension.

## Requirements *(mandatory)*

### Functional Requirements

**Links to other apps**

- **FR-001**: A redirect to another app MUST count as tapped only when the main-frame navigation it belongs to started from a tap. Finger lifts that end a scroll, swipe or fling MUST NOT count. The time window from Spec 031 MUST be removed.
- **FR-002**: An "open in app" link MUST NOT be handed to this browser. When the link names this browser's package, or carries an `http(s)` address, that address MUST load in the current tab.

**Privacy**

- **FR-003**: A download started from a long-press MUST NOT carry cookies.
- **FR-004**: A download's referrer MUST be the page's origin when the download is on another site, and the page's full address only when it is on the same site. It MUST be absent when the page is not `http(s)`, when an `https` page downloads from `http`, or when the tab is incognito.
- **FR-005**: If Fire cannot read the fireproof list, it MUST NOT crash or treat the list as empty. It MUST skip the steps that depend on the list and report them as failed. A resumed Fire at launch MUST NOT stop the app from opening.
- **FR-006**: Restoring the normal cookie jar after incognito MUST report failure when clearing times out. Fire MUST then report the close-incognito step as failed and wipe all non-fireproof cookies in its cookie step.
- **FR-007**: Fire MUST remove site permissions for all affected origins in one all-or-nothing operation, and report the step as failed if it does not succeed.
- **FR-008**: Photos taken for an upload MUST be deleted when a page session starts and when Fire runs.
- **FR-009**: Clearing browsing data MUST run to completion independently of the Settings screen.
- **FR-010**: Clearing history MUST also clear the hosts remembered as having failed the HTTPS upgrade.

**Tabs and errors**

- **FR-011**: Switching tabs MUST restore the tab's saved back and forward history regardless of the previous page's loading state.
- **FR-012**: A certificate error MUST fail the page when it is for the address the main frame is navigating to, including the target of a server redirect.
- **FR-013**: While the page is in the error state, titles reported by the engine MUST NOT change the tab or its history entry.
- **FR-014**: A retry from the error screen MUST show a loading indication within one second.
- **FR-015**: Choosing a tab and updating a tab's address or title MUST each change only their own fields, so neither undoes the other.
- **FR-016**: A tab preview MUST NOT be saved for a tab that no longer exists.
- **FR-017**: The record of closed incognito tabs MUST only gain tabs that the app knew were open and are now closed.

**Files**

- **FR-018**: Deleting a download MUST delete a file by name only after the download service confirms it removed that download.
- **FR-019**: Icon and preview writes MUST use a unique temporary file per write.
- **FR-020**: Download file names MUST NOT contain invisible formatting characters. If nothing remains of the base name, the default name MUST be used with the extension.
- **FR-021**: `binary/octet-stream`, `application/force-download`, `application/x-download` and `application/download` MUST be treated as generic types when opening a download.
- **FR-022**: Download file names MUST leave room for the system's de-duplication suffix (at most 245 UTF-8 bytes).
- **FR-023**: A failing status lookup for one group of downloads MUST NOT clear the statuses of the other groups.

**Import and search**

- **FR-024**: Parsing a bookmark file MUST take time proportional to its size.
- **FR-025**: Import MUST encode a second `#`, and `[` and `]` after the host, and convert international host names, before validating. Host names with underscores MUST be accepted.
- **FR-026**: Bookmark search MUST NOT show "no matches" for queries shorter than the minimum search length.

**Rendering and cleanup**

- **FR-027**: Image rows MUST NOT read file metadata on the main thread during composition.
- **FR-028**: Every lazy-list item MUST have a content type.
- **FR-029**: The wrong unused-parameter suppression and the pass-through `activeTab` property in the page holder MUST be removed. The duplicated hand-off decision in the offer effect MUST reuse the WebView client's.
- **FR-030**: Every fix with a branch, a parser, a file write or a privacy rule MUST come with a unit test that fails before the fix. FR-001, FR-003, FR-011, FR-012 and FR-013 MUST also be checked on an emulator.

### Key Entities

- **Navigation gesture**: whether the main-frame navigation a request belongs to started from a tap. It holds for the redirects of that navigation only.
- **Referrer**: the address a download names as its source. It is the origin, the full address or nothing, following FR-004.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across 10 scripted scroll-triggered redirect attempts to another app, 0 apps open. A tap on a link that redirects to an app opens it 10 out of 10 times.
- **SC-002**: A long-press download through a cross-site redirect delivers 0 cookies to either site.
- **SC-003**: After a failed page, 10 out of 10 tab switches keep the target tab's back history.
- **SC-004**: With an unreadable fireproof list, 10 out of 10 launches with an interrupted Fire open the app.
- **SC-005**: Importing a 10,000-link file parses in under 2 seconds on the reference device. A 10 MB file with no closing tags parses in under 5 seconds.
- **SC-006**: 0 wrong file deletions in the regression test set for downloads the service has forgotten.
- **SC-007**: 0 audit items remain open. Each is fixed with a test or recorded as checked on a device.

## Assumptions

- No new screen, setting or string is added.
- The referrer follows the common browser default: origin only across sites, nothing on a downgrade. Incognito sends none, as it already sends no cookie of the normal profile.
- Long-press downloads lose cookies. A signed-in file can still be downloaded by opening the link, which starts a page download with cookies.
- The reference device for SC-005 is the `TA016_API36_16K` emulator. The owner's phone is not used for instrumented tests.
- Page-started downloads from an incognito tab keep their incognito cookies, which signed-in downloads need. The system download service keeps a download's request headers until that download is removed from its list. That is outside the app's storage and accepted here.
- No database schema change. FR-007's single delete is a new query on the existing table.

## Audit traceability

| Audit item (2026-10-21) | Requirement |
|---|---|
| 1. Any finger lift counts as a tap | FR-001 |
| 2. Long-press download sends cookies through redirects | FR-003 |
| 3. Tab switch after a failure loses history | FR-011 |
| 4. Fire crashes when the fireproof list cannot be read | FR-005 |
| 5. Deleting a forgotten download deletes another file | FR-018 |
| 6. Import scan is quadratic | FR-024 |
| 7. Fixed temp file name in cache writes | FR-019 |
| 8. Certificate error after a redirect shows a blank page | FR-012 |
| 9. `intent:` link can target this app | FR-002 |
| 10. Cookie restore timeout leaves incognito cookies | FR-006 |
| 11. Full referrer across sites and from incognito | FR-004 |
| 12. Import drops `#`, `[]`, IDN and underscore links | FR-025 |
| 13. Upload photos outlive the session | FR-008 |
| 14. Clear browsing data cancelled by leaving Settings | FR-009 |
| 15. Tab switch undone by a title update | FR-015 |
| 16. Formatting characters in file names | FR-020 |
| 17. One-letter search says "no matches" | FR-026 |
| 18. Fire site-permission errors ignored, one delete per origin | FR-007 |
| 19. HTTPS-upgrade failures not cleared with history | FR-010 |
| 20. Generic types missing | FR-021 |
| Late preview saved for a closed tab | FR-016 |
| Error page title overwrites the real title | FR-013 |
| No loading indication on retry | FR-014 |
| Closed-incognito race | FR-017 |
| File metadata read on the main thread | FR-027 |
| Lazy item without content type | FR-028 |
| One failing status chunk clears all | FR-023 |
| No room for the de-duplication suffix | FR-022 |
| Wrong suppression, pass-through property, duplicated hand-off | FR-029 |
