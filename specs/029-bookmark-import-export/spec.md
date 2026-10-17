# Feature Specification: Bookmark Import and Export

**Feature Branch**: `029-bookmark-import-export`
**Created**: 2026-10-17
**Status**: Draft
**Input**: User description: "Let the user move bookmarks into and out of ThirtySix with the standard bookmark file that Chrome, Firefox, Safari, Samsung Internet, Edge and DuckDuckGo all export and import."

## Context

Bookmarks live only on the device and the app has no sync, by design.

- **Coming from another browser.** Someone switching to ThirtySix has to add every bookmark again by hand.
- **Leaving or changing phones.** The browsing database is excluded from device backup and transfer, so bookmarks are lost with the phone. A file the person keeps is the only way to carry them.
- **Every major browser reads and writes the same bookmark file** (the "Netscape bookmark file", an HTML page of links and folders). Supporting it covers all of them with one format.

Owner decisions, 2026-10-17: this is spec 029. Password manager and history, password or settings import stay out of scope. The design follows the §X waiver used since Spec 020: screens are built from the existing Spec 019 components.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bring bookmarks from another browser (Priority: P1)

The person exported their bookmarks from another browser and has the file on the phone. In ThirtySix they choose Import and pick the file with the system file picker. A moment later they see how many bookmarks came in. All of them sit in one new folder named with today's date, with the original folders inside it.

**Why this priority**: This is how people move to ThirtySix. Without it, switching costs a long manual retype, and the people with the most bookmarks are the ones most put off.

**Independent Test**: Export bookmarks from Chrome on a computer (folders, nested folders, about 200 links), copy the file to the phone, import it. The new folder holds the same folders and links, in the same order.

**Acceptance Scenarios**:

1. **Given** a bookmark file from another browser, **When** the person imports it, **Then** a new folder named like "Imported 17 Oct 2026" appears at the top level of Bookmarks, and it holds the file's folders and links in the file's order.
2. **Given** the person already has bookmarks, **When** they import a file, **Then** none of their existing bookmarks or folders are changed, moved or removed.
3. **Given** a file whose folders are nested several levels deep, **When** it is imported, **Then** the same nesting appears inside the new folder.
4. **Given** a file that holds the same address twice in one folder, **When** it is imported, **Then** that folder holds it once.
5. **Given** a file that holds entries which are not web pages (for example `javascript:` bookmarklets, `place:` queries, `file:` or `data:` links), **When** it is imported, **Then** those entries are left out and the rest are imported.
6. **Given** an import has finished, **When** the result is shown, **Then** it states how many bookmarks were imported, how many duplicates were skipped and how many unsupported entries were left out, and offers to open the new folder.
7. **Given** the same file is imported twice, **When** the second import finishes, **Then** a second dated folder exists. The first one is left unchanged.

---

### User Story 2 - Keep a copy of my bookmarks (Priority: P1)

The person chooses Export, picks where to save the file with the system file picker, and gets a single file with all their bookmarks and folders. They can keep it, move it to a new phone, or open it in another browser.

**Why this priority**: The app promises that data stays on the device. That promise is only fair if the person can still take their own bookmarks with them. Export is also the only way to keep bookmarks through a phone change, because browsing data is not in device backups.

**Independent Test**: With 3 folders (one nested) and 30 links, export to a file, then import that file into Chrome or Firefox on a computer. The same folders and links appear in the same order.

**Acceptance Scenarios**:

1. **Given** bookmarks in folders and at the top level, **When** the person exports, **Then** the file holds every bookmark and folder, nested as in the app and in the app's order.
2. **Given** an exported file, **When** it is imported into Chrome, Firefox or Samsung Internet, **Then** that browser shows the same folders and links with the same titles.
3. **Given** an exported file, **When** it is imported back into ThirtySix, **Then** the new dated folder holds the same tree as the export.
4. **Given** the file picker opens, **When** it suggests a name, **Then** the name says it is ThirtySix bookmarks and includes the date.
5. **Given** the export has finished, **When** the result is shown, **Then** it states how many bookmarks were saved.
6. **Given** the person has no bookmarks, **When** they choose Export, **Then** the app tells them there is nothing to export and does not open the file picker.

---

### User Story 3 - Bad or unexpected files never hurt my bookmarks (Priority: P1)

The person picks the wrong file, a damaged file, or a huge one. The app tells them clearly what went wrong and their bookmarks stay exactly as they were.

**Why this priority**: An import touches the person's saved data. A half-finished import or a crash would cost more trust than the feature gains.

**Independent Test**: Import a photo, an empty file, a truncated bookmark file, and a file far above the size limit. Each shows a clear message. The bookmark list is identical before and after.

**Acceptance Scenarios**:

1. **Given** a file that is not a bookmark file (a photo, a PDF, a plain text note), **When** it is imported, **Then** the app says the file is not a bookmark file and nothing is added.
2. **Given** a bookmark file with no links in it, **When** it is imported, **Then** the app says there was nothing to import and no folder is created.
3. **Given** a bookmark file cut off part-way or with broken markup, **When** it is imported, **Then** every link that can be read is imported in its folder, and nothing else is changed.
4. **Given** a file larger than the size limit, or with more entries than the entry limit, **When** it is imported, **Then** the app says the file is too large and nothing is added.
5. **Given** an import is running, **When** the person leaves the screen, rotates the phone or switches apps, **Then** the import either completes fully or adds nothing. It never leaves part of a file imported.
6. **Given** an import or export is running, **When** the person keeps using the app, **Then** the app stays responsive.
7. **Given** the person cancels the file picker, **When** they return, **Then** nothing happens and no message is shown.
8. **Given** the chosen save location cannot be written (for example it was removed), **When** the export runs, **Then** the app says the file could not be saved.

---

### User Story 4 - Find Import and Export where I expect them (Priority: P2)

The person finds Import and Export from the Bookmarks screen's menu, and also in Settings, where other browsers keep them.

**Why this priority**: Both actions are rare, so they should not crowd the screens, but they must be easy to find when needed.

**Independent Test**: From the Bookmarks screen, open the top bar menu: Import and Export are there. From Settings, open the bookmarks row: the same two actions are there.

**Acceptance Scenarios**:

1. **Given** the Bookmarks screen, **When** the person opens its top bar menu, **Then** it offers Import bookmarks and Export bookmarks.
2. **Given** Settings, **When** the person looks for bookmarks, **Then** a row leads to Import bookmarks and Export bookmarks.
3. **Given** an import finished from Settings, **When** the person chooses to open the new folder, **Then** the Bookmarks screen opens on that folder.
4. **Given** the active tab is incognito, **When** the person imports or exports, **Then** it works the same, because bookmarks are not incognito data.

---

### Edge Cases

- **Titles.** An entry with no title gets its address as its title. Very long titles and folder names are cut to the app's existing limits. Encoded characters (`&amp;`, `&#39;`, non-Latin text in any of the 8 app languages) come out as the person sees them in the other browser.
- **Address forms.** Addresses are kept as written. An address with no scheme or an unreadable address counts as unsupported.
- **Empty folders.** Empty folders in the file are imported, so the tree looks like the source. Export writes the app's empty folders too.
- **Other browsers' top folders.** Files from desktop browsers wrap everything in folders such as "Bookmarks bar" and "Other bookmarks". These become ordinary folders inside the dated folder.
- **Extra data in the file.** Icons, tags, keywords, descriptions, add and modify dates, and separators are ignored. Nothing in the file causes a network request.
- **Same address in different folders.** Kept in each folder. Only a repeat within one folder is a duplicate.
- **Same address as an existing bookmark.** Imported anyway, because it goes into a new folder. The bookmark star on that page stays filled, as for any bookmarked address.
- **The dated folder name already exists.** A number is added ("Imported 17 Oct 2026 (2)").
- **A very deep tree.** Folders deeper than the depth limit are flattened into their deepest allowed parent, and their links are kept.
- **Low storage while importing.** The import fails as a whole with a clear message and adds nothing.
- **Export while bookmarks change.** The file reflects the bookmarks at the moment Export started.
- **A file that looks like a web page from the internet.** Its scripts and styles are never run or loaded. Only the links and folders are read.
- **Accessibility and languages.** Every new menu item, row, button and message has text in all 8 languages and a TalkBack label. The summary is announced when it appears.

## Requirements *(mandatory)*

### Functional Requirements

**Import (Stories 1 and 3)**

- **FR-001**: The app MUST let the person pick a file to import with the system file picker, without asking for any storage permission.
- **FR-002**: The app MUST read the Netscape bookmark file format as exported by current Chrome, Firefox, Safari, Edge, Samsung Internet and DuckDuckGo.
- **FR-003**: An import MUST create one new top-level folder named "Imported" followed by the date in the app's language, adding a number when that name is taken. Everything from the file MUST go inside it.
- **FR-004**: The import MUST keep the file's folder tree and the order of folders and links within each folder.
- **FR-005**: The import MUST keep only `http` and `https` addresses. Every other entry MUST be counted as unsupported and left out.
- **FR-006**: Within one folder of the import, a repeat of an address already added to that folder MUST be skipped and counted as a duplicate.
- **FR-007**: An entry with no title MUST take its address as its title. Titles and folder names MUST be cut to the existing bookmark limits. Character references MUST be decoded.
- **FR-008**: The import MUST NOT change, move or remove any bookmark or folder that existed before it.
- **FR-009**: The import MUST be all or nothing. If it fails or is interrupted, no folder and no bookmark from it remain.
- **FR-010**: The import MUST refuse files larger than 10 MB and files with more than 20,000 entries (links plus folders), with a message, before adding anything.
- **FR-011**: Folders deeper than 32 levels below the dated folder MUST be flattened into their deepest allowed parent. Their links MUST be kept.
- **FR-012**: A file that is not a bookmark file, or holds no importable link, MUST produce a clear message and add nothing. No empty dated folder may be left behind.
- **FR-013**: A partly broken bookmark file MUST import every link that can be read, in the folder it can be placed in.
- **FR-014**: When the import finishes, the app MUST show the number of bookmarks imported, duplicates skipped and unsupported entries left out, and offer to open the new folder.

**Export (Story 2)**

- **FR-015**: The app MUST let the person choose where to save the file with the system file picker, without asking for any storage permission. The suggested name MUST identify ThirtySix bookmarks and the date.
- **FR-016**: The export MUST write every bookmark and folder in the Netscape bookmark file format, with the app's nesting and order, empty folders included.
- **FR-017**: The exported file MUST import with the same folders, links and titles into Chrome, Firefox and Samsung Internet, and back into ThirtySix.
- **FR-018**: Titles and folder names MUST be escaped so that any title, including ones holding markup characters or non-Latin text, reads back exactly.
- **FR-019**: The export MUST reflect the bookmarks at the moment it starts.
- **FR-020**: With no bookmarks and no folders, Export MUST say there is nothing to export instead of opening the file picker.
- **FR-021**: When the export finishes, the app MUST show how many bookmarks were saved. When the file cannot be written, it MUST say so.

**Both directions (Stories 3 and 4)**

- **FR-022**: Reading, parsing and writing MUST NOT block the screen. The app stays responsive and shows that work is in progress.
- **FR-023**: An import or export MUST survive rotation and the person switching apps. It finishes or fails as a whole and reports its result when the person comes back.
- **FR-024**: Neither direction MUST make any network request. Icons and other links inside the file MUST NOT be fetched, and scripts in the file MUST NOT run.
- **FR-025**: The Bookmarks screen's top bar menu MUST offer Import bookmarks and Export bookmarks.
- **FR-026**: Settings MUST have a row that leads to Import bookmarks and Export bookmarks.
- **FR-027**: Choosing to open the imported folder MUST show the Bookmarks screen on that folder, from either entry point.
- **FR-028**: Cancelling the file picker MUST do nothing and show nothing.
- **FR-029**: Every new text MUST exist in all 8 app languages, and every new control MUST have a TalkBack label. The result message MUST be announced.

### Key Entities

- **Bookmark file**: a file the person picks or saves. It holds a tree of folders and links, each with a title. ThirtySix reads and writes only the titles, the addresses and the tree.
- **Import result**: what one import did: the new folder, the number of bookmarks imported, duplicates skipped and unsupported entries left out, or the reason it failed.
- **Export result**: the number of bookmarks saved, or the reason the file could not be written.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Files exported by Chrome, Firefox, Safari, Edge, Samsung Internet and DuckDuckGo each import with 100% of their web links present, in the right folders and order.
- **SC-002**: A ThirtySix export imported into Chrome, Firefox and Samsung Internet shows 100% of the bookmarks with the same titles and folders.
- **SC-003**: Export then import back into ThirtySix yields an identical tree (titles, addresses, nesting, order) for a set of 1,000 bookmarks that includes markup characters, emoji and text in all 8 app languages.
- **SC-004**: Importing 5,000 bookmarks finishes within 5 seconds, and exporting 5,000 bookmarks within 3 seconds, on the reference device. The screen keeps responding during both.
- **SC-005**: Over a set of at least 10 bad files (not a bookmark file, empty, truncated, oversized, too many entries, 100-level nesting, interrupted mid-import), the app never crashes, and the bookmark data is byte-for-byte unchanged after each failed import.
- **SC-006**: No network request is made during any import or export, checked with a proxy on a file full of icon and image links.
- **SC-007**: A person who has never used the feature finds Import from the Bookmarks screen in under 30 seconds.
- **SC-008**: The release package grows by no more than 60 KB, and cold start stays within 5% of the previous build.

## Assumptions

- **Format only.** The Netscape bookmark file is the only format. Browser-specific formats (Firefox JSON backups, Chrome's internal `Bookmarks` file, CSV) are out of scope.
- **Bookmarks only.** History, passwords, open tabs, settings and Fire-proof sites are not imported or exported.
- **One import folder.** Merging into existing folders is out of scope; the person can move folders afterwards with the existing bookmark tools.
- **No scheduled or automatic export.** Export happens only when the person asks.
- **Where the file comes from.** The person gets the file onto the phone themselves (download, cable, cloud drive app). Any location the system file picker shows is accepted, including cloud drive providers; reading from them is the provider's business.
- **Reference device.** Timing in SC-004 is measured on the release build on the owner's Galaxy A55, or recorded as deferred per `docs/adr/0017`.
- **Bookmark star.** The star on a page reflects whether its address is bookmarked anywhere, as today, so imported duplicates of existing bookmarks keep it filled.
