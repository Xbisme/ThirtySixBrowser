# Research: Bookmark Import and Export

Evidence tags: **[A]** read in this repository, **[B]** reasoned or from format knowledge, to be confirmed by the fixtures and device gates.

## R1. Parsing the Netscape bookmark file

**Decision**: A small hand-written, tolerant tag scanner in pure Kotlin (`NetscapeBookmarkParser`). It reads only these:

- `<DL>` and `</DL>` push and pop a folder level;
- `<H3 …>text</H3>` starts a folder, which the next `<DL>` opens;
- `<A HREF="…" …>text</A>` adds a link.

Everything else is skipped: `<DT>`, `<p>`, `<DD>` descriptions, `<HR>` separators, `<META>`, `<TITLE>`, `<H1>`, comments, and attributes other than `HREF` (`ICON`, `ICON_URI`, `ADD_DATE`, `LAST_MODIFIED`, `TAGS`, `SHORTCUTURL`, `PERSONAL_TOOLBAR_FOLDER`, `FOLDED`). Tag and attribute names are matched case-insensitively. Attribute values may be double-quoted, single-quoted or unquoted. Character references are decoded: the five XML names, `&nbsp;`, decimal and hex. Text inside `<script>` and `<style>` is skipped whole.

**Tolerance**:
- An unclosed `<DL>` at end of file closes implicitly.
- A stray `</DL>` at the top level is ignored.
- An `<H3>` with no following `<DL>` is an empty folder.
- A link outside any `<DL>` goes to the top level of the import.

That gives FR-013 (a truncated file imports what was read) without a recovery mode.

**Rationale**:
- The format is a fixed, decades-old subset of HTML that every browser writes the same way [B].
- A general HTML parser (jsoup, about 400 KB before shrinking) would cost most of the 60 KB budget (SC-008) and adds a dependency for 3 tags.
- Never building a DOM keeps memory linear in the number of entries.

**Alternatives rejected**:
- jsoup: size, and the DOM.
- `XmlPullParser`: the format is not XML (unclosed `<DT>`, `<p>`).
- `android.text.Html`: no structure, and Android-only (the parser stays testable on the JVM).

## R2. "Is this a bookmark file?"

**Decision**: A file is a bookmark file if its first 1 KB, case-insensitive and ignoring leading whitespace or a BOM, contains `NETSCAPE-Bookmark-file-1`, **or** the scan finds at least one `<DL>` and at least one `<A HREF`. Otherwise the result is `NotABookmarkFile` (FR-012). A bookmark file with zero importable links is `NothingToImport`.

Bytes that are not valid UTF-8 are decoded with replacement characters, so a photo yields garbage text without the markers: `NotABookmarkFile`. Files from current browsers are UTF-8 with the `<META … charset=UTF-8>` line [B]. Other declared charsets are out of scope.

## R3. Which entries are imported

**Decision**:
- Keep `HREF` values whose scheme, case-insensitive, is `http` or `https`, after trimming whitespace. Everything else is counted as unsupported (FR-005), including `javascript:`, `place:`, `data:`, `file:`, `about:`, `chrome:`, `edge:`, `feed:`, a missing scheme, an empty value, and an address longer than `BrowserLimits.MAX_BOOKMARK_URL_LENGTH`.
- Addresses are otherwise stored as written (Edge Cases).
- Title: decoded, whitespace collapsed, trimmed. When blank, the address. Cut to `MAX_BOOKMARK_TITLE_LENGTH`.
- Folder names are cut to `MAX_FOLDER_NAME_LENGTH`; a blank name falls back to a localized "Untitled folder".
- **Duplicates** (FR-006): within one folder of the import, a later link with an address already added to that folder is skipped. The comparison is exact on the stored string.

## R4. Limits

**Decision** (FR-010, FR-011):

| Limit | Value | Where |
|---|---|---|
| File size | 10 MiB, counted while reading; stop at 10 MiB + 1 byte → `TooLarge` | `BookmarkTransferLimits.MAX_IMPORT_FILE_BYTES` |
| App totals after import | existing bookmarks + imported ≤ `BrowserLimits.MAX_BOOKMARKS` (10,000); existing folders + imported + 1 ≤ `MAX_FOLDERS` (1,000) → else `TooLarge` | checked in the use case before writing |
| Depth | 32 levels below the dated folder; deeper folders' links go to the level-32 ancestor | parser |

The two `BrowserLimits` caps exist today but nothing enforces them [A: `grep` finds no use]. This spec enforces them for import only; manual adds stay as they are.

A 10 MiB file holds roughly 50–80k links [B]. The parser only keeps entries, and the totals check runs before any write, so memory stays bounded.

## R5. Writing all or nothing

**Decision**: a new `BookmarkRepository.importTree(rootName, tree): Result<ImportOutcome>`.
- It runs in one `database.withTransaction`: insert the dated folder, then walk the tree depth-first, inserting each folder to get its id, then its links.
- `sortOrder` is a running counter from the start time, so links keep file order under `ORDER BY sort_order, id` [A: `BookmarkDao.observeByFolder`]. `createdAt` is the same start time for all rows.
- Any exception rolls everything back (FR-009).
- **Unique dated folder name**: the transaction reads the root folder names and adds " (2)", " (3)" … to the base name. The base name comes from the UI, already localized (R8).
- **Cancellation**: the import runs in `viewModelScope`. Leaving the screen cancels it, and Room rolls the transaction back. Rotation and switching apps keep the ViewModel (FR-023). Process death mid-transaction leaves nothing, because SQLite never committed.

**Alternatives rejected**:
- Running in `@ApplicationScope` so Back does not cancel: the result would have nowhere to show, and "nothing added" already satisfies the spec.
- One `insertAll` per level: the ids of new folders are needed for their children, and a transaction already makes it atomic.

## R6. Exporting

**Decision**:
- `BookmarkRepository.getBookmarkTree(): BookmarkTreeNode.Folder` reads all folders and bookmarks in one transaction (FR-019), then builds the tree in memory. That is at most 1,000 folders and 10,000 bookmarks.
- Order (FR-016): child folders by name, as `BookmarkFolderDao.observeChildren` sorts them [A], then links by `sortOrder, id`.
- `NetscapeBookmarkWriter` streams to a `Writer`:

```
<!DOCTYPE NETSCAPE-Bookmark-file-1>
<!-- This is an automatically generated file. … -->
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
<TITLE>Bookmarks</TITLE>
<H1>Bookmarks</H1>
<DL><p>
    <DT><H3 ADD_DATE="…">Folder</H3>
    <DL><p>
        <DT><A HREF="…" ADD_DATE="…">Title</A>
    </DL><p>
</DL><p>
```

- `ADD_DATE` is in seconds. Text escapes `&`, `<`, `>`, `"` and `'` (FR-018); attribute values are escaped the same way.
- The doctype comment is the one Chrome writes. Chrome's and Firefox's importers key on the doctype and the `DL`/`DT` structure [B; confirmed by gate G4].
- No `PERSONAL_TOOLBAR_FOLDER` is written, so another browser puts the import under its "Imported" folder and nothing lands in its bookmarks bar.

## R7. The system file picker

**Decision**: the Compose activity-result launchers in the bookmarks route.
- `ActivityResultContracts.OpenDocument()` with `arrayOf("text/html", "application/xhtml+xml", "text/plain", "application/octet-stream")`.
  - Providers label `.html` files inconsistently [B], so the list is wide.
  - A wrong file is caught by R2.
  - `*/*` was rejected: it lets photos and videos look like valid choices.
- `ActivityResultContracts.CreateDocument("text/html")` with the suggested name `thirtysix-bookmarks-YYYY-MM-DD.html` (FR-015).
- No permission is needed. Only the returned URI is used, once, so no persistable grant is taken.
- `null` (picker cancelled) does nothing (FR-028).
- I/O goes through `BookmarkFileDataSource`, which uses `ContentResolver.openInputStream` / `openOutputStream("wt")` on the IO dispatcher. `FileNotFoundException`, `SecurityException` and `IOException` map to `ReadFailed` / `WriteFailed`.

**Note**: the API 24 `DocumentsUI` supports both contracts [B, checked on `TA016_API24` in G3].

## R8. Dated folder name and messages

**Decision**:
- The route builds the base name with `stringResource(R.string.bookmarks_import_folder_name, date)`, where the date comes from `DateFormat.getDateInstance(DateFormat.MEDIUM, locale)` in the app locale, and passes it in the action. The domain receives a plain string and stays free of `android.*`.
- The result is a `ConfirmDialog` (Spec 019 component). It shows the counts with plurals, and has "Open folder" and "Done". It is announced through `liveRegion = Polite` (FR-029).
- Progress is a non-dismissable dialog with `ProgressLine`.

## R9. Entry points

**Decision**:
- **Bookmarks top bar**: a `MoreVert` `RoundIconButton` opens `BookmarkTransferSheet`: `SheetFrame` with two `SheetActionRow`s, Import and Export, using two new Material Symbols drawables, `ic_file_open` and `ic_file_save`.
- **Settings**: a new "Bookmarks" section with one `SettingsValueRow`, "Import and export bookmarks". It calls a new `SettingsDestinations.onBookmarkTransfer`, which `AppNavHost` fills with `navController.navigateToBookmarks(openTransfer = true)`. The bookmarks route gains an optional boolean argument that opens the sheet once (FR-026). Settings never imports the bookmarks feature (Principle I).
- **Opening the imported folder** (FR-027): the result dialog's "Open folder" sends `BookmarksAction.OpenImportedFolder(id)`. The ViewModel navigates into that folder through the existing folder path. Both entry points end on the Bookmarks screen, so one path covers both.

## R10. Fixtures for SC-001

**Decision**: test resources under `app/src/test/resources/bookmarks/`:

- `chrome.html`, `firefox.html`, `safari.html`, `edge.html`, `samsung.html`, `duckduckgo.html`. Each mirrors that browser's export shape:
  - Chrome: toolbar flag;
  - Firefox: `ICON` data URIs, `place:` queries, `<HR>`, `<DD>`, tags;
  - Safari: `FOLDED`, Reading List with `<DD>`;
  - Samsung Internet and DuckDuckGo: the Chrome shape.
- Plus bad files: `empty.html`, `truncated.html`, `no-links.html`, `deep-100.html`, `photo.jpg` (a few KB of random bytes), and an oversized file generated in the test.

Real exports from the owner's browsers replace the hand-made ones when available (quickstart G4).

## R11. Performance

**Decision**:
- Parsing is a single pass over a `BufferedReader`.
- One transaction per import. Room inserts of 5,000 rows inside one transaction take on the order of 100 ms [B].
- Export streams.

SC-004 (import 5,000 in ≤ 5 s, export 5,000 in ≤ 3 s) is measured on the A55 release build in G5, or recorded as deferred per ADR 0017. All work runs on the injected IO dispatcher (FR-022).
