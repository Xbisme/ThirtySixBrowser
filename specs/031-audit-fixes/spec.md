# Feature Specification: Audit Fixes

**Feature Branch**: `031-audit-fixes`
**Created**: 2026-10-20
**Status**: Draft
**Input**: User description: "Fix every defect found in the 2026-10-20 repo audit, no new features. Each fix needs a regression test where the logic is non-trivial; device checks on an emulator for the error screen, the backup rules and sub-resource certificate errors."

## Context

A whole-repo audit on 2026-10-20 found 20 ranked defects and 9 smaller ones. They fall into three groups: safety (pages opening other apps, script typed into the address bar), lost data (cookies, downloaded files, photos for an upload), and things that quietly do not work (the error screen, opening history from incognito, logged-in downloads). This spec fixes all of them and adds no feature. The only new UI is the two prompts that make the link fix usable (see Clarifications). The table at the end maps each audit item to its requirement.

What the app does today, where it matters here:

- **Links to other apps.** Any address that is not a web address goes to the system to open in another app, with no check that the person tapped anything or that it came from the page itself and not an embedded frame.
- **Fireproof sites** keep their sign-ins when the person uses Fire. The list loads in the background when the app starts.
- **Downloads** are handed to the system download service. The app remembers each one by name and asks the service for its state.
- **Error screen.** A failed load is meant to show the app's own error screen with a retry button.
- **Backup.** The app allows Android backup and excludes only its database.

## Clarifications

### Session 2026-10-20

- Q: When a site returns its own HTTP error page (404, 500), does the app show that page or cover it with the app's error screen? → A: Show the site's page. The app's error screen is only for network, host-name and certificate failures.
- Q: When a page tries to open another app without a tap, is it blocked silently or does the person get a choice? → A: Blocked, and a snackbar says the page wants to open another app, with an "Open" button. Only for the main page; attempts from embedded frames are dropped silently.
- Q: In an incognito tab, does a tapped link to another app open straight away? → A: No. A dialog asks first, saying the other app will see the link, with Open and Cancel. Normal tabs open straight away.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pages cannot open other apps on their own (Priority: P1)

A page, or an advert embedded in it, tries to open the Play Store, the dialer, a messaging app or another app without the person tapping anything. Nothing opens. When the page itself tried it, a snackbar says so and offers "Open", so a payment or sign-in hand-off still works with one tap. When the person taps a link to another app on the page, it opens as before. "Open in app" links (`intent:` links) work, and fall back to the web page the link names when no app can open them.

**Why this priority**: Today any page controls which apps start on the person's phone, in incognito too.

**Independent Test**: Load a test page that redirects to a store link on load, and an embedded frame that does the same. Nothing opens. Tap a store link on the page. The store opens.

**Acceptance Scenarios**:

1. **Given** a page that sends itself to another app's address while loading, **When** no tap happened, **Then** no other app opens, the page stays, and a snackbar says the page wants to open another app, with an "Open" button.
2. **Given** that snackbar, **When** the person taps "Open", **Then** the app opens under the same rules as a tapped link. **When** the snackbar times out or is dismissed, **Then** nothing opens.
3. **Given** an embedded frame that sends itself to another app's address, **When** the person taps inside it or not, **Then** no other app opens and no snackbar shows.
4. **Given** a link to another app on the page, **When** the person taps it, **Then** the app opens, and only apps that accept links from browsers are offered.
5. **Given** an "open in app" link naming a fallback web page, **When** the person taps it and no app can open it, **Then** the fallback page loads in the same tab, if it is a web address.
6. **Given** an "open in app" link that names a specific internal screen of another app, **When** the person taps it, **Then** the app may open, but never at a screen that does not accept browser links.
7. **Given** an incognito tab, **When** the person taps a link to another app or taps "Open" on the snackbar, **Then** a dialog says the other app will see the link and offers Open and Cancel. Open opens the app; Cancel leaves the page as it was.
8. **Given** a normal tab, **When** the person taps a link to another app, **Then** it opens without a dialog.

---

### User Story 2 - The address bar never runs script and searches what the person typed (Priority: P1)

The person pastes text into the address bar. A web address loads. Script disguised as an address is searched as text instead of running. A sentence that happens to contain a dot is searched instead of being treated as a broken address.

**Why this priority**: "Paste this into your address bar" is a known trick to steal a person's sign-in.

**Independent Test**: Paste `javascript://%0Aalert(1)`. A search runs and no alert appears. Type `how to use node.js`. A search runs. Type `example.com`. The site loads.

**Acceptance Scenarios**:

1. **Given** input that starts with a script address, **When** submitted, **Then** it is searched as text and nothing runs on the current page.
2. **Given** input with any other non-web scheme followed by `://`, **When** submitted, **Then** it is searched as text.
3. **Given** input with a space and a dot, **When** submitted, **Then** it is searched as text.
4. **Given** `example.com`, `http://a.b/c d` with a scheme, or `https://x.y`, **When** submitted, **Then** they load as addresses, as today.

---

### User Story 3 - Sign-ins and private data stay where the person put them (Priority: P1)

The person's fireproof sites keep their sign-ins through every Fire, including one that was interrupted and finishes at the next launch. Cookies and site storage are not copied to cloud backup or to a new phone. Cookie contents never appear in the system log. Incognito pages never write into normal history, icons, previews or the normal cookie jar, even while switching between incognito and normal tabs quickly. A closed incognito tab leaves nothing of its back and forward history in memory.

**Why this priority**: The app is sold as private. Each of these breaks that promise or deletes something the person chose to keep.

**Independent Test**: Mark a site fireproof and sign in. Start Fire and kill the app part-way. Relaunch. The site is still signed in. Inspect the backup contents. No web data is in them.

**Acceptance Scenarios**:

1. **Given** a fireproof site with a sign-in and a Fire that was interrupted, **When** the app relaunches and Fire finishes, **Then** the site is still signed in and every other site is signed out.
2. **Given** Fire's cookie removal takes longer than its time limit, **When** Fire ends, **Then** the fireproof sites' cookies are still there.
3. **Given** the app's data is backed up or moved to a new device, **When** it is restored, **Then** no cookie, site storage or web cache comes with it. Settings still come with it.
4. **Given** any cookie operation fails, **When** the failure is logged, **Then** the log names no site, cookie name or cookie value.
5. **Given** the person opens an incognito tab and quickly returns to a normal one, **When** pages, icons or previews finish loading in the middle of the switch, **Then** nothing from the incognito tab lands in normal history, icons, previews or cookies, and the incognito tab never uses the normal cookie jar.
6. **Given** a tab preview is being taken when the person switches tabs, **When** it finishes, **Then** it is saved for the tab it shows, or not at all.
7. **Given** an incognito tab was closed, **When** its last state arrives after closing, **Then** that state is dropped.
8. **Given** the cookie engine never answers a request to restore cookies, **When** the time limit passes, **Then** incognito tabs can still be opened, switched and closed.

---

### User Story 4 - Files are never deleted or broken by mistake (Priority: P1)

The person deletes a download from the Downloads screen. Only that download is removed, never an older file with the same name. Files sent as a generic type open in a suitable app. Files that need a sign-in download with that sign-in. Names with `+`, long Chinese or Vietnamese names, and emoji save correctly. Two photos taken for one form both upload.

**Why this priority**: Deleting the wrong file cannot be undone. The other cases make downloads and uploads fail for common sites.

**Independent Test**: Download `report.pdf` twice so the second is saved under a new name. Delete the second outside the app. Delete it in the app. The first file is untouched.

**Acceptance Scenarios**:

1. **Given** the system no longer knows a download and the app only has the requested name, **When** the Downloads screen shows it, **Then** it is shown as missing and its delete action does not touch any file with that name.
2. **Given** a download the system still knows, **When** the person deletes it, **Then** that file is removed, as today.
3. **Given** a file served as a generic binary type or with no type, **When** the person opens it, **Then** the type is taken from the file name and a suitable app is offered.
4. **Given** an attachment behind a sign-in in a normal tab, **When** downloaded, **Then** the request carries that tab's cookies and the page address as referrer. In an incognito tab it carries the incognito cookies and never the normal ones.
5. **Given** a download from an address the download service cannot fetch (a page-made `blob:` or `data:` address), **When** the person starts it, **Then** the message says this kind of download is not supported, not that the service is unavailable.
6. **Given** a server-suggested name containing `+`, **When** saved, **Then** the `+` is kept.
7. **Given** a server-suggested name longer than the file system allows in bytes, **When** saved, **Then** the name is shortened to fit, the extension is kept, and no character is cut in half.
8. **Given** a form with two camera inputs, **When** the person takes a photo for each and sends the form, **Then** both photos upload.
9. **Given** the phone runs out of space while saving a site icon or a tab preview, **When** it is shown later, **Then** a broken image is never shown. The app falls back as if there were none.

---

### User Story 5 - Errors are shown correctly, and a bad settings file does not lock the person out (Priority: P1)

A page that cannot load (no network, unknown host, certificate problem on the page itself) shows the app's error screen and keeps it until the person retries or goes elsewhere. A broken image or script from another host with a bad certificate does not take over a page that works. If the settings file is damaged, the app opens with default settings instead of crashing at every launch.

**Why this priority**: The error screen almost never stays up today. A damaged settings file leaves clearing all app data, bookmarks included, as the only way out.

**Independent Test**: Load a host that does not exist. The app's error screen shows and stays. Load a working page that embeds an image from a host with an expired certificate. The page shows without an error screen.

**Acceptance Scenarios**:

1. **Given** a main page that fails to load, **When** the browser engine then reports that its own error page finished loading, **Then** the app's error screen stays.
2. **Given** the error screen, **When** the person retries or loads another address, **Then** the screen leaves as the new load starts.
3. **Given** a site's own "not found" or server-error page, **When** it loads, **Then** the site's page is shown, not the app's error screen.
4. **Given** a certificate error on something embedded in a page, **When** the main page itself is fine, **Then** the page shows normally and its history entry is kept.
5. **Given** a certificate error on the main page, **When** it loads, **Then** the app's error screen shows.
6. **Given** a settings file that cannot be read, **When** the app starts, **Then** it opens with default settings and the person's bookmarks, history and tabs are untouched.

---

### User Story 6 - History, bookmarks and search work in every mode and at any size (Priority: P2)

From an incognito tab, tapping a history entry or a bookmark opens it in that incognito tab. Searching bookmarks stays quick after importing thousands of them. Importing a file with small mistakes in it still brings in the bookmarks after the mistake, and every imported address can be edited later.

**Why this priority**: These features quietly fail today, but nothing is lost.

**Independent Test**: In an incognito tab, open History and tap an entry. It loads in the incognito tab and is not added to normal history. Import 10,000 bookmarks and type two letters in bookmark search. Results show without a noticeable stall.

**Acceptance Scenarios**:

1. **Given** an incognito tab is current, **When** the person taps a history entry or a bookmark, **Then** it loads in that incognito tab, and nothing is written to normal history.
2. **Given** 10,000 bookmarks in nested folders, **When** the person types in bookmark search, **Then** results, each with its folder path, appear in under 300 ms per keystroke on the reference device.
3. **Given** a search shorter than the minimum length, **When** typed, **Then** no search runs, as on the History screen.
4. **Given** a search matching more results than the shown limit, **When** typed, **Then** the newest matches up to the limit are shown.
5. **Given** an imported file where a folder name or link title is not closed, **When** imported, **Then** the bookmarks and folders after it are still imported with their own titles.
6. **Given** an imported address that a manually added bookmark would reject, **When** imported, **Then** it is skipped and counted as skipped.

---

### User Story 7 - Limits hold under pressure (Priority: P3)

Opening tabs quickly never goes past the tab limit. Hundreds of downloads still show the right status. Tapping "Get started" twice in onboarding leaves one browser screen. A page that opens a pop-up that never shows does not keep memory forever. Saving tab state with no normal tabs left never wipes the saved state of other tabs.

**Why this priority**: Each needs an unusual moment to show, and none loses user data in normal use.

**Independent Test**: With 49 tabs, trigger a page pop-up and an "open in background" at the same moment. 50 tabs exist and no two share a position.

**Acceptance Scenarios**:

1. **Given** 49 normal tabs, **When** two tabs are created at the same moment, **Then** exactly one is created and the other is refused with the usual limit message.
2. **Given** more than 1,000 downloads on record, **When** the Downloads screen opens, **Then** every download shows its true status.
3. **Given** the last onboarding slide, **When** the person taps the finish button twice quickly, **Then** one browser screen exists and Back leaves the app.
4. **Given** a pop-up window that is never shown as a tab, **When** its opener tab closes or its screen goes away, **Then** the pop-up is released.
5. **Given** a request to keep the saved state of no normal tabs, **When** it runs, **Then** it removes nothing.

---

### User Story 8 - Less code to keep correct (Priority: P3)

Code that nothing uses is removed, three copies of the same helper become one, and use cases that only pass one call along are replaced by the call. Nothing the person sees changes.

**Why this priority**: About 180 lines that must be read, tested and fixed while doing nothing.

**Independent Test**: The full unit and architecture test suite passes, the app behaves the same in a smoke walk, and the listed items no longer exist in the source tree.

**Acceptance Scenarios**:

1. **Given** the cleanup, **When** the static checks and unit tests run, **Then** they pass with no new entries in any known-violations list.
2. **Given** each removed function, **When** the tree is searched, **Then** no caller remains in app, test or instrumented test code, except tests that were only testing the removed function, which are removed with it.

---

### Edge Cases

- A tapped link to another app that no installed app can open, with no fallback page: nothing happens and the page stays. The failure is logged without the address.
- An "open in app" fallback page that is not a web address (`javascript:`, `file:`): it is ignored.
- A redirect to another app that follows a tap within the same navigation, for example a tap on a link that redirects to a store: it counts as tapped and opens.
- Fire is interrupted before the fireproof list was ever loaded: the list is read from storage before any cookie is cleared.
- The fireproof list is empty: Fire clears everything, as today.
- A download whose name was updated to the system's real name earlier: deleting it still works when the system later forgets it.
- A file name made only of characters that do not fit in the byte limit: the base name is shortened to at least one character and the extension is kept.
- A server-suggested name of more than 255 bytes in the extension alone: the extension is dropped rather than going over the limit.
- A page that fails, then the person goes Back to a page that loads: the error screen leaves.
- An HTTPS-upgrade certificate failure on the main page: it still falls back to `http://` as today, without showing the error screen.
- A damaged settings file: the person sees onboarding again. Their language and theme fall back to the defaults.
- Bookmark search with a query that matches only folders: no results, as today.
- An imported file that is entirely broken: the result stays "nothing to import", as today.

## Requirements *(mandatory)*

### Functional Requirements

**Links to other apps**

- **FR-001**: The browser MUST open another app from a page only when the navigation came from the main page and followed a tap. An untapped attempt from the main page MUST open nothing and MUST show a snackbar with an "Open" action that opens the app under FR-002 and FR-003. While several attempts arrive, only the latest is offered. Attempts from embedded frames MUST be dropped silently. The snackbar text MUST exist in all 8 locales and be announced by TalkBack.
- **FR-002**: An address handed to another app MUST be limited to apps that accept links from browsers, and MUST NOT name a specific screen or component.
- **FR-002a**: In an incognito tab, every hand-off to another app MUST first show a confirmation dialog saying the other app will see the link, with Open and Cancel. Normal tabs MUST NOT show it. Loading an "open in app" link's web fallback is not a hand-off and needs no dialog. The dialog text MUST exist in all 8 locales and be reachable with TalkBack.
- **FR-003**: "Open in app" (`intent:`) links MUST be understood. When no app can open one, the browser MUST load its fallback page in the same tab if that fallback is an `http` or `https` address.

**Address bar**

- **FR-004**: Address bar input MUST load as an address only when it has no scheme and looks like a host, or its scheme is `http`, `https` or `about`. Any other input MUST be searched as text.
- **FR-005**: Input containing whitespace and no scheme MUST be searched as text.

**Privacy and sign-ins**

- **FR-006**: Fire, whether started by the person or resumed at launch, MUST read the fireproof list from storage before clearing anything. The same applies wherever the fireproof list is used to choose what incognito keeps.
- **FR-007**: When Fire's cookie removal runs out of time, the fireproof cookies read before removal MUST be written back.
- **FR-008**: Backup and device transfer MUST exclude the web engine's data folder (cookies, site storage, web cache). Settings MUST still be included.
- **FR-009**: No log line MAY contain a site address, cookie name or cookie value.
- **FR-010**: Restoring or removing cookies for incognito MUST give up after the existing web-data time limit, and later incognito operations MUST proceed.
- **FR-011**: A page's incognito state MUST come from the same source and moment as the page's tab, so that a tab and its incognito flag can never disagree.
- **FR-012**: Tab previews and site icons MUST be saved for the tab that produced them, and never for an incognito tab.
- **FR-013**: State that arrives for an incognito tab after it was closed MUST be dropped.

**Files**

- **FR-014**: When the download service no longer knows a download, the app MUST show it as complete only if it holds the file's own address from an earlier lookup. Otherwise it MUST show it as missing, and deleting it MUST NOT delete any file by name.
- **FR-015**: Opening a download whose stored type is empty or generic binary MUST use the type implied by its file extension, falling back to "any type".
- **FR-016**: A download MUST carry the cookies of the tab's own profile (normal or incognito) for its address, and the page address as referrer.
- **FR-017**: A download from an address the download service cannot fetch MUST report "not supported", distinct from "service unavailable". Every locale MUST have that message.
- **FR-018**: Saved download names MUST fit in 255 bytes of UTF-8, keep the extension where it fits, never split a character, and keep `+` signs.
- **FR-019**: A photo taken for a page upload MUST stay available until the page's session ends, even if more photos are taken.
- **FR-020**: Site icons and tab previews MUST become visible only after they are fully written. A failed write MUST leave no file behind.

**Errors and start-up**

- **FR-021**: Once a main-page load has failed, its error state MUST stay until a new navigation starts. Later progress and "finished" reports for the browser engine's own error page MUST NOT clear it.
- **FR-022**: HTTP status errors (4xx, 5xx) MUST show the site's own page, not the app's error screen.
- **FR-023**: A certificate error MUST fail the page only when it is for the main page's address. It MUST NOT drop the history entry of a working page.
- **FR-024**: A settings file that cannot be read MUST be replaced with defaults instead of stopping the app.

**History, bookmarks and import**

- **FR-025**: Opening a history entry or bookmark MUST load it in the current tab, normal or incognito. In incognito it MUST NOT write to normal history or normal tabs.
- **FR-026**: Bookmark search MUST read the folder tree once per result set, MUST return at most 50 results, newest first, and MUST NOT run for queries shorter than the existing minimum search length.
- **FR-027**: The bookmark file parser MUST end a folder name or link title at the next entry or list tag when its closing tag is missing.
- **FR-028**: An imported address MUST pass the same validation as a manually added bookmark, or be counted as skipped.

**Limits**

- **FR-029**: Creating a normal tab MUST check the tab limit and assign the position as one step, so that concurrent creates never exceed the limit or share a position.
- **FR-030**: Download status lookups MUST work for any number of downloads on record.
- **FR-031**: Finishing onboarding MUST leave exactly one browser screen, however many times the finish button is tapped.
- **FR-032**: A pop-up waiting for its tab MUST be released when that tab closes or its opener's screen goes away.
- **FR-033**: Keeping the saved state of an empty set of normal tabs MUST remove nothing.

**Cleanup (no visible change)**

- **FR-034**: The unused functions, forwarding use cases, duplicated helpers, duplicate check and private scope listed under "Audit traceability" MUST be removed or merged, and the misleading comment corrected.
- **FR-035**: Every fix with a branch, a parser change, a file write or a privacy rule MUST come with a unit test that fails before the fix. Requirements FR-001, FR-021 and FR-023 MUST also be checked on an emulator. FR-008 MUST be checked with a real backup run.

### Key Entities

- **Download record**: the app's note of one download. It holds the requested name, the system's handle and, once known, the file's own address and real name. Only the last two may prove that a file belongs to the download.
- **Fireproof site**: a site whose sign-ins survive Fire. Stored, and read from storage whenever Fire decides what to keep.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a test page set that tries 10 kinds of untapped app launches (page script, redirect, embedded frame), 0 apps open. Tapped links to an installed app open 10 out of 10 times.
- **SC-002**: Across 20 interrupted-Fire runs on an emulator, every fireproof site is still signed in after relaunch, and every other site is signed out.
- **SC-003**: Deleting a forgotten download never removes a file other than that download's own. 0 wrong deletions in the regression test set.
- **SC-004**: A failed main-page load shows the app's error screen and it is still showing 5 seconds later in 10 out of 10 emulator runs.
- **SC-005**: A restored backup contains 0 files from the web engine's data folder.
- **SC-006**: With 10,000 imported bookmarks, a bookmark search keystroke shows results in under 300 ms on the reference device.
- **SC-007**: The app starts with a deliberately damaged settings file in 10 out of 10 launches.
- **SC-008**: 0 audit items remain open. Each is either fixed with a test or explicitly recorded as checked on a device.
- **SC-009**: The cleanup change alone removes at least 150 more lines of main source than it adds.

## Assumptions

- No new screen or setting is added. The only new UI is the "page wants to open another app" snackbar, which uses the existing snackbar host, and the incognito "open in another app?" dialog. The only new texts are those two plus the "not supported" download message, in all 8 locales.
- The reference device for SC-006 is the `TA016_API36_16K` emulator. The owner's phone is not used for instrumented tests.
- Excluding web data from backup means a restored phone has no sign-ins. This is the intended behavior for a private browser.
- No database schema change is needed. If one turns out to be, it follows the additive migration rule.

## Audit traceability

| Audit item | Requirement |
|---|---|
| 1. Pages open other apps without a tap; `intent:` ignored | FR-001 – FR-003, FR-002a |
| 2. Resumed Fire wipes fireproof cookies | FR-006 |
| 3. Deleting a forgotten download deletes another file | FR-014 |
| 4. Error screen cleared by the engine's own error page | FR-021, FR-022 |
| 5. Web data in cloud backup | FR-008 |
| 6. Damaged settings file crashes every launch | FR-024 |
| 7. Bookmark search query per result, no limit | FR-026 |
| 8. History or bookmark tap does nothing in incognito | FR-025 |
| 9. Address bar runs `javascript://`; sentences with a dot become addresses | FR-004, FR-005 |
| 10. Incognito flag from a separate source; preview saved under the wrong tab | FR-011, FR-012 |
| 11. Embedded certificate error fails the page | FR-023 |
| 12. Downloads without cookies and referrer; `blob:`/`data:` misreported | FR-016, FR-017 |
| 13. Second photo deletes the first | FR-019 |
| 14. Cookie contents in the log | FR-009 |
| 15. Cookie restore can hang forever | FR-010 |
| 16. Icon and preview caches write in place | FR-020 |
| 17. Download names cut by characters; `+` lost | FR-018 |
| 18. Generic-type downloads cannot be opened | FR-015 |
| 19. Unused code, forwarding use cases, duplicates | FR-034 |
| 20. Tab limit exceeded by concurrent creates | FR-029 |
| Empty keep-set wipes saved tab state | FR-033 |
| Incognito state re-added after close | FR-013 |
| Pop-up kept in memory | FR-032 |
| Download status above 999 records | FR-030 |
| Onboarding double tap | FR-031 |
| Imported addresses skip validation | FR-028 |
| Unclosed folder name or title in import | FR-027 |
| Fire cookie timeout loses fireproof cookies | FR-007 |
| Misleading comment on closing a tab | FR-034 |

Items for FR-034, as found by the audit: unused `TabSessionRepository.delete` and `TabSessionDao.delete`, `HistoryDao.getRecent`, `getById` and `delete(entry)`, `TabRepository.getTabCount`, `IncognitoTabRepository.getCount`, `DownloadManagerGateway.isAvailable` and its helpers, `DownloadsRepository.count`, `BookmarkRepository.getFolder`; forwarding `RemoveSitePermissionsUseCase`, `RemoveProtectionExceptionsUseCase` and `RemoveFireproofSitesUseCase`; three copies of the `runIo` wrapper and of the fireproof `www.` expansion; the duplicate "nothing to export" check in `BookmarkTransferViewModel`; the private scope in `ThirtySixApplication` in place of `@ApplicationScope`; the comment in `CloseTabUseCase` about reused tab ids.
