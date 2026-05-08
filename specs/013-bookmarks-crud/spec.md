# Feature Specification: Bookmarks CRUD

**Feature Branch**: `013-bookmarks-crud`
**Created**: 2026-05-07
**Status**: Draft
**Input**: User description: "Spec 013 — bookmarks-crud: full CRUD for bookmarks and unlimited-nesting folders, real-time title+url search, two add affordances (BrowserScreen star icon + BookmarksScreen FAB), localized empty state. Builds on Spec 005 schema."

## Clarifications

### Session 2026-05-07

- Q: When the user types a search query, should search scope be (A) all bookmarks across all folders, or (B) only within current folder and descendants? → A: All bookmarks across all folders, with each result row showing its folder path inline so the user knows where the bookmark lives.
- Q: When the user taps a bookmark in the list, should the URL load (A) into the active tab replacing it, or (B) into a new tab? → A: Replace the currently active tab's URL. "Open in new tab" is deferred to a future spec via a long-press / context-menu affordance.
- Q: When a non-empty folder is deleted, should descendant bookmarks be (A) re-parented to root, or (B) cascade deleted with the folder? → B: Cascade delete — folder + all descendant bookmarks + all descendant sub-folders are removed. The confirmation dialog displays the total descendant count (deep, not just immediate children) so the user has informed consent before deletion.
- Q: When the URL has multiple bookmarks (e.g., a manual-add duplicate exists alongside the star-created one), what does the star toggle delete? → A: The star icon manages only **one canonical bookmark per URL**. Tap star (when no bookmark for URL exists) creates one at root. Tap star again (when one or more exist) deletes only **the most recently created** bookmark for that URL, regardless of which folder it's in. Manual-added duplicates are not implicitly affected by star toggle.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bookmark the page I'm viewing (Priority: P1)

While viewing a web page, I want to save it as a bookmark with one tap so I can return to it later without retyping the URL or searching for it again.

**Why this priority**: This is the most common bookmarking entry point in any browser and the primary value driver of the feature — it converts "I might need this again" into one motion. Without it, users must remember and re-enter URLs, which defeats the purpose of a bookmarks feature.

**Independent Test**: With no other bookmarks UI implemented, a user on a loaded page can tap a star affordance, see visual confirmation that the page is now saved, close and re-open the app, and confirm via the bookmarks list that the entry persists with the correct title and URL.

**Acceptance Scenarios**:

1. **Given** the user is viewing a web page that has finished loading and is not yet bookmarked, **When** they tap the star affordance in the browser's top bar, **Then** the page's current title and URL are saved as a bookmark, the star changes to its "filled" state, and the user receives an unobtrusive confirmation that the page was bookmarked.
2. **Given** the user is viewing a page that is already bookmarked (star is filled), **When** they tap the star a second time, **Then** the bookmark is removed, the star reverts to its "outline" state, and the user receives confirmation that the bookmark was removed.
3. **Given** the user has bookmarked a page, **When** they kill the app and re-open it, **Then** the bookmark is still present in the bookmarks list with the same title, URL, and creation timestamp.
4. **Given** a page is still loading and has no title yet, **When** the user taps the star affordance, **Then** the bookmark is saved with the URL as the fallback title, and the user can edit the title later.

---

### User Story 2 - Browse and open my bookmarks (Priority: P1)

I want to open the bookmarks screen, see my saved pages in a clear list, and tap any one of them to open it in the browser without having to remember the URL.

**Why this priority**: Saving bookmarks is only valuable if I can find and use them. This story is what makes US1 deliver real value. Without it, bookmarks are write-only.

**Independent Test**: A user with several pre-seeded bookmarks can navigate to the bookmarks screen from the app's main navigation, see all bookmarks rendered as a list with their titles and URLs, tap any item, and observe the browser load that URL.

**Acceptance Scenarios**:

1. **Given** the user has at least one saved bookmark, **When** they open the bookmarks screen, **Then** they see a list of bookmarks at the root level showing each item's title and URL.
2. **Given** the user is viewing the bookmarks list, **When** they tap a bookmark, **Then** they are returned to the browser screen and the selected URL begins loading in the active context.
3. **Given** the user has zero saved bookmarks, **When** they open the bookmarks screen, **Then** they see a localized empty-state message explaining that no bookmarks exist yet and how to add one.

---

### User Story 3 - Organize bookmarks into folders (Priority: P2)

I want to group related bookmarks into folders, and to nest folders inside folders, so I can keep larger collections (e.g., "Work", "Work / Project A", "Work / Project A / References") tidy.

**Why this priority**: Power users with dozens or hundreds of bookmarks need this to stay productive. P1 users can survive with a flat list, so it ships after the core flows but is essential for the feature to scale beyond casual use.

**Independent Test**: A user can create a folder at the root, navigate into it, create another folder inside it, add a bookmark inside the nested folder, and navigate back to the root using a breadcrumb or back affordance — all while seeing the correct list contents at each level.

**Acceptance Scenarios**:

1. **Given** the user is on the bookmarks screen, **When** they create a new folder named "Work", **Then** the folder appears in the current list and they can tap into it to view its (empty) contents.
2. **Given** the user is inside a folder, **When** they create a sub-folder, **Then** the sub-folder appears in the parent's list and the user can navigate into it.
3. **Given** the user is several folders deep, **When** they look at the screen, **Then** they can see a breadcrumb or path indicator showing the current location and tap any segment to jump to that level.
4. **Given** the user is inside any folder, **When** they perform the "back to parent" action, **Then** they return one level up in the folder hierarchy.

---

### User Story 4 - Search bookmarks by title or URL (Priority: P2)

When my bookmark list grows large, I want to type a few characters into a search field and immediately see only the bookmarks whose title or URL matches, so I can find what I'm looking for without scrolling.

**Why this priority**: Search becomes essential once a user has more than ~20 bookmarks. Below that, scrolling is fine; above that, search is what keeps the feature usable.

**Independent Test**: A user with at least 10 seeded bookmarks can tap a search affordance, type a few characters, and observe that the list filters in real-time to only show bookmarks whose title or URL contains the typed text (case-insensitive).

**Acceptance Scenarios**:

1. **Given** the bookmarks screen is showing a list of bookmarks, **When** the user taps the search affordance and types a query, **Then** the visible list filters in real-time as each character is typed, showing only bookmarks whose title or URL contains the query (case-insensitive).
2. **Given** a search query is active, **When** the user clears the query, **Then** the list returns to the unfiltered view of the current folder.
3. **Given** the user has typed a query that matches no bookmarks, **When** the search settles, **Then** they see a localized "no matches" empty state.

---

### User Story 5 - Edit a saved bookmark (Priority: P2)

I want to edit a bookmark's title, URL, or parent folder so I can correct mistakes, rename for clarity, or reorganize without having to delete and re-add.

**Why this priority**: Without edit, every change requires delete + re-add, which is painful and loses the original creation timestamp. It's lower than P1 because the core save/open flow works without it, but it's table stakes for any usable bookmarks feature.

**Independent Test**: A user can long-press or open a context menu on any bookmark, choose "Edit", change the title and/or URL and/or move it to a different folder via a folder picker, save, and observe the change reflected in the list immediately.

**Acceptance Scenarios**:

1. **Given** a saved bookmark, **When** the user opens the edit dialog, changes the title, and confirms, **Then** the new title is saved and visible in the list immediately.
2. **Given** a saved bookmark, **When** the user opens the edit dialog and changes the URL to a malformed value, **Then** they are blocked from saving with a localized validation error explaining the issue.
3. **Given** a saved bookmark in folder A, **When** the user edits it and selects folder B as the new parent via a folder picker, **Then** the bookmark disappears from folder A's list and appears in folder B's list.

---

### User Story 6 - Delete bookmarks and folders safely (Priority: P2)

I want to delete a bookmark in one tap, and delete a folder with explicit confirmation if it contains items, so I can clean up without accidental data loss.

**Why this priority**: Deletes are unavoidable maintenance and risky if implemented carelessly. The confirmation gate on non-empty folders prevents accidental loss of dozens of bookmarks at once.

**Independent Test**: A user can delete a single bookmark and see it removed from the list. The user can also delete an empty folder with no prompt, and a non-empty folder with a confirmation dialog that, when confirmed, deletes the folder while keeping its child bookmarks (now visible at the root).

**Acceptance Scenarios**:

1. **Given** a saved bookmark, **When** the user deletes it, **Then** it is removed from the list immediately.
2. **Given** an empty folder, **When** the user deletes it, **Then** it is removed from the list immediately without a confirmation prompt.
3. **Given** a folder containing one or more bookmarks or sub-folders, **When** the user deletes it, **Then** they see a confirmation dialog stating the **total descendant count** (all nested bookmarks plus all nested sub-folders), and only on confirmation are the folder and every descendant deleted in one cascading operation.

---

### User Story 7 - Add a bookmark manually (Priority: P3)

When I'm not currently viewing the page I want to bookmark (e.g., I have a URL in my clipboard or written down), I want to type or paste the title and URL into a form to save it directly from the bookmarks screen.

**Why this priority**: The star-icon entry point in US1 covers ~95% of real-world add scenarios. Manual add covers the remaining edge cases (planning ahead, importing one URL by hand) and is straightforward to add once the data layer exists.

**Independent Test**: A user can tap a "+" affordance on the bookmarks screen, fill a form with title + URL + optional folder picker, save, and observe the new bookmark appear in the list at the chosen location.

**Acceptance Scenarios**:

1. **Given** the user is on the bookmarks screen, **When** they tap the add affordance, fill in title + URL, optionally pick a folder, and confirm, **Then** the new bookmark appears in the chosen folder's list immediately.
2. **Given** the user submits the form with an empty title or empty URL, **When** they confirm, **Then** they see a localized validation error and the bookmark is not saved.
3. **Given** the user submits the form with a malformed URL, **When** they confirm, **Then** they see a localized validation error explaining that the URL is invalid.

---

### Edge Cases

- **Bookmarking the same URL twice**: tapping the star on an already-bookmarked page removes only the most recently created bookmark for that URL (per FR-003). Manually adding a bookmark whose URL already exists is allowed and creates a separate entry (no UNIQUE constraint on URL — matches the data layer behaviour shipped in Spec 005). When multiple bookmarks share a URL, manual duplicates are unaffected by star toggle and must be deleted via the bookmarks list directly.
- **Deleting a folder containing nested folders**: the confirmation dialog must reflect the **total descendant count** (every nested bookmark plus every nested sub-folder). On confirmation, the folder and every descendant are deleted in one cascading operation. Because the underlying FK rule is `SET NULL` (not `CASCADE`), the application code MUST walk the tree depth-first and delete bookmarks before sub-folders before the parent folder, otherwise the FK rule will leave child bookmarks orphaned at root instead of deleting them.
- **Folder cycle prevention**: when moving folder A into folder B via the move action, the system must reject the move if B is a descendant of A, with a localized error.
- **Page with no title**: when bookmarking a page whose title is empty or whitespace, the URL is used as the title fallback; the user can edit it later.
- **Search query with no current folder match**: search is global (always across all folders); a bookmark in any folder will appear in results regardless of which folder the user is currently viewing.
- **Open bookmark while in a search-filtered view**: tapping a result in search mode opens the bookmark normally and exits the search.
- **Star state on incognito tab**: bookmarking is **disabled** in incognito tabs (the star icon is hidden or non-interactive) — incognito mode (Spec 012) is explicitly "no persistence", and storing a bookmark would violate that contract.
- **Star state on `about:blank` or error pages**: bookmarking is disabled when there is no real loaded URL (e.g., the page is in a `Failed` loading state with no successful URL, or the URL is a non-http(s) scheme).
- **Very long bookmark or folder names**: titles and URLs are truncated with ellipsis in list rows but remain fully visible in edit dialogs.

## Requirements *(mandatory)*

### Functional Requirements

#### Adding bookmarks

- **FR-001**: The browser top bar MUST expose a star affordance that, when tapped on a normally-loaded non-incognito page, saves the current URL and title as a new bookmark at the root level.
- **FR-002**: The star affordance MUST reflect whether **at least one** bookmark exists for the current page's URL, using a "filled" visual state when so and an "outline" state when not.
- **FR-003**: Tapping the star affordance on an already-bookmarked page MUST remove only **the most recently created** bookmark whose URL matches the current page (regardless of which folder it lives in). Other bookmarks with the same URL — typically created via manual add — MUST be left untouched.
- **FR-004**: The star affordance MUST be disabled (hidden or non-interactive) when (a) the active tab is an incognito tab, (b) the current page has no successfully loaded URL, or (c) the current URL is a non-http(s) scheme.
- **FR-005**: The bookmarks screen MUST expose an add affordance (e.g., a floating action button) that opens a form for manually entering a title, URL, and optional parent folder.
- **FR-006**: The manual-add form MUST reject submissions with empty title, empty URL, or malformed URL (i.e., a URL that does not parse as a valid http or https URL), showing a localized inline validation message.
- **FR-007**: When a page's title is empty or whitespace at the moment of star-tap, the system MUST use the URL string as the bookmark title.

#### Viewing and opening bookmarks

- **FR-008**: The bookmarks screen MUST display all bookmarks at the current folder level, ordered with the most recently created at the top.
- **FR-009**: The bookmarks screen MUST display all sub-folders at the current folder level, listed above or visually distinguished from bookmarks.
- **FR-010**: Tapping a bookmark MUST close the bookmarks screen and load that bookmark's URL into the **currently active tab**, replacing the active tab's previous URL. The active tab's existing forward / back history is preserved up to that point (the bookmark URL becomes the next history entry).
- **FR-010a**: When the active tab is an **incognito tab** at the moment a bookmark is tapped, the bookmark's URL MUST still load into that incognito tab (preserving privacy isolation). The tap action does not switch to or create a non-incognito tab.
- **FR-011**: When the current folder contains zero bookmarks and zero sub-folders, the screen MUST display a localized empty-state explaining the situation and pointing the user to the add affordance(s).

#### Folder management

- **FR-012**: Users MUST be able to create a new folder at the current level by tapping a "new folder" affordance and entering a name.
- **FR-013**: Folders MUST support unlimited nesting depth.
- **FR-014**: The bookmarks screen MUST display a breadcrumb or path indicator showing the user's current location in the folder hierarchy, with each ancestor segment tappable to jump directly to that level.
- **FR-015**: A "back to parent" affordance MUST be available whenever the user is inside any non-root folder.
- **FR-016**: Users MUST be able to rename any folder.
- **FR-017**: Users MUST be able to move a folder into a different parent folder via a folder picker.
- **FR-018**: The system MUST prevent a folder from being moved into itself or any of its own descendants, showing a localized error if attempted.
- **FR-019**: Users MUST be able to delete an empty folder without a confirmation prompt.
- **FR-020**: Users MUST be able to delete a non-empty folder only after confirming via a dialog that states the **total descendant count** (every nested bookmark plus every nested sub-folder, recursively). On confirmation, the folder and every descendant MUST be deleted in one operation. Because the underlying foreign-key rule is `SET NULL`, application code MUST cascade the deletion explicitly (depth-first: delete leaf bookmarks → delete leaf sub-folders → ... → delete the target folder).

#### Editing and moving bookmarks

- **FR-021**: Users MUST be able to edit any saved bookmark's title, URL, and parent folder via an edit dialog.
- **FR-022**: The edit dialog MUST reject saves with empty title, empty URL, or malformed URL using the same validation rules as the manual-add form (FR-006).
- **FR-023**: Users MUST be able to delete any saved bookmark.

#### Search

- **FR-024**: The bookmarks screen MUST expose a search affordance that opens a single-line text input.
- **FR-025**: As the user types into the search input, the visible list MUST filter in real-time to show only bookmarks whose title or URL contains the typed query (case-insensitive substring match). Search scope is **global** — all bookmarks across all folders are searched, regardless of the folder the user was viewing when search was opened.
- **FR-026**: While search is active, each result row MUST display the bookmark's folder path (e.g., `Work / Project A`) inline so the user can see where the bookmark lives. Bookmarks at the root level show a localized "Root" / equivalent label.
- **FR-027**: When the search query is empty, the screen MUST return to its unfiltered view of the current folder.
- **FR-028**: When the search query has no matches, the screen MUST show a localized "no matches" message that is distinct from the empty-state message in FR-011.

#### Localization & accessibility

- **FR-029**: All user-visible strings introduced by this feature (button labels, dialog titles, validation messages, empty states, content descriptions) MUST be translated for all 8 supported locales (EN, VI, DE, RU, KO, JA, ZH, FR).
- **FR-030**: Every interactive icon-only affordance (star, FAB, search icon, etc.) MUST expose a localized content description for accessibility services.

### Key Entities *(include if feature involves data)*

- **Bookmark**: a user-saved web page reference, with a human-readable title, an http/https URL, an optional parent folder, a creation timestamp, and a sort order. Two bookmarks may share the same URL (no uniqueness constraint).
- **Folder**: a named container that may hold any combination of bookmarks and sub-folders. Folders may be nested to unlimited depth. A folder may have at most one parent folder; a folder with no parent is at the root.

> The underlying persistence schema for both entities was already shipped in Spec 005 ([BookmarkEntity](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkEntity.kt) + [BookmarkFolderEntity](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkFolderEntity.kt)) and requires zero schema migration in this spec.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can save the page they are currently viewing in **one tap** with no intermediate dialog or form, and see visual confirmation within 200 ms.
- **SC-002**: After tapping a bookmark in the list, the browser begins loading the corresponding URL within 500 ms on the user's device.
- **SC-003**: With 1,000 bookmarks present, typing a search query returns the filtered list within 100 ms of each keystroke on a Pixel 5-class device.
- **SC-004**: Across all 8 supported locales, no user-visible string in this feature is shown as a missing-resource fallback or a raw resource key.
- **SC-005**: A user with no prior exposure to the app can find and use the "save this page" action within 30 seconds of opening any web page (measured by a small-N usability check, not automated).
- **SC-006**: 100% of bookmark and folder data created in this feature persists across an app process kill (cold restart) and across an app upgrade that does not change the data schema.
- **SC-007**: A folder hierarchy 5 levels deep, with ~50 items per level, can be navigated, expanded, and rendered without visible scroll-frame stutter on a Pixel 5-class device.
- **SC-008**: The feature adds zero new third-party dependencies (no new package entries in the version catalog), so the released app size grows by less than 100 KB compared to the prior release.
- **SC-009**: Every interactive control in the feature is reachable and operable via a screen reader, and announces a non-empty localized label.
- **SC-010**: The visual layout of all bookmark-related screens, dialogs, and empty states renders without text truncation or overflow in all 8 locales at the default system font size on a 360 dp-wide screen.

## Assumptions

- **Persistence layer reuse**: this spec reuses the bookmark and folder schema shipped in Spec 005 verbatim. No schema migration is performed in this spec.
- **Active-tab reuse**: this spec reuses the active-tab plumbing from Spec 011 to deliver the loaded URL when a bookmark is tapped. Per Q2 resolution, the URL replaces the active tab's content; "open in new tab" via long-press / context-menu is deferred to a future spec.
- **Sort order**: bookmarks within a folder are always shown in "most recently created first" order in v1.0. Manual reordering (drag-to-reorder) is explicitly out of scope and deferred to a later spec.
- **Tags / labels**: out of scope — folders are the only organizational primitive in v1.0.
- **Import / export**: out of scope — bookmarks are local-only and on-device, consistent with the project's no-cloud-sync stance.
- **Bookmark thumbnails / favicons in list rows**: out of scope — Spec 005 already excluded a favicon URL from the schema; bookmarks render as text-only rows.
- **Bulk select / batch delete**: out of scope.
- **Long-press a link in the WebView to bookmark it**: out of scope — that affordance requires WebView hit-test work and is deferred to a later spec.
- **Drag-to-reorder bookmarks across folders**: out of scope — the move-to-folder picker (FR-017) is the only reorganization mechanism in v1.0.
- **Folder deletion semantics**: when a non-empty folder is deleted via FR-020, the folder and every descendant (nested bookmarks and nested sub-folders, recursively) are deleted in one cascading operation. The underlying FK rule is `SET NULL` (Spec 005 default), so application code MUST cascade the deletion explicitly via depth-first traversal — relying on the FK rule alone would leave descendant bookmarks orphaned at root instead of deleting them.
- **Star icon disable in incognito**: this spec assumes the active-tab incognito flag from Spec 012 is observable from the browser screen state, which it is via the existing active-tab observer.
- **Confirmation language**: the count shown in the "delete non-empty folder" confirmation dialog is the **total descendant count** (recursive — every nested bookmark and every nested sub-folder), so the user has informed consent before a cascading delete.

## Dependencies

- **Spec 005** (Room database schema): bookmark and folder entities, DAOs, and the database module. Must be merged.
- **Spec 007** (WebView Compose wrapper): the browser screen, view model, UI state, and the extracted top bar. Must be merged.
- **Spec 011** (Tabs management): the active-tab abstraction used to deliver the URL when a bookmark is tapped.
- **Spec 012** (Incognito mode): the active-tab incognito flag used to disable bookmarking in private tabs.
- **Spec 002** (Clean architecture skeleton + DI): the base view model, result wrapper, and error type.
- **Spec 004** (8-locale localization foundation): the per-locale string resource files into which this spec adds new keys.

## Open Questions for Clarification

All open questions resolved 2026-05-07 via `/speckit-clarify`. Spec is ready for `/speckit-plan`.

- ✅ **Q1**: Search scope is global across all folders — see Clarifications and FR-025 / FR-026.
- ✅ **Q2**: Bookmark tap replaces the active tab's URL — see Clarifications and FR-010 / FR-010a.
- ✅ **Q3**: Folder delete cascades — folder + all descendants removed; confirmation dialog shows total descendant count. See Clarifications and FR-020.
- ✅ **Q4**: Star toggle manages one canonical bookmark per URL; deletes the most recently created entry for that URL on toggle-off; manual duplicates untouched. See Clarifications and FR-002 / FR-003.
