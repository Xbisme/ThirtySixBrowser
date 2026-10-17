# Quickstart: validating Bookmark Import and Export

The commands are in `AGENTS.md`. Devices: `TA016_API36_16K`, plus a smoke run on `TA016_API24`, with animations off.

## G1. Every commit

`detekt ktlintCheck testDebugUnitTest` and `compileDebugAndroidTestKotlin`, all green.

## G2. Unit tests

- **Parser**:
  - every fixture in research R10, with expected counts;
  - entity decoding;
  - depth flattening;
  - truncated file;
  - not-a-bookmark-file.
- **Writer**: escaping, and the round trip `parse(write(tree)) == tree` over a generated tree of 1,000 links with markup characters, emoji and text in all 8 locales (SC-003).
- **Repository on in-memory Room**:
  - `importTree` keeps link order and nesting;
  - the name suffix works ("(2)");
  - a failure injected part-way leaves both tables unchanged (SC-005);
  - `getBookmarkTree` orders folders by name, then links.
- **Use cases**: every `BookmarkImportResult` and `BookmarkExportResult` branch, including both limits.
- **ViewModel** (Turbine): the transfer state machine; one transfer at a time; export with nothing to export; open the imported folder.

## G3. Device walk

1. **US1**: push `chrome.html` to Downloads with `adb push`. Bookmarks → ⋮ → Import → pick it.
   - The summary shows the counts.
   - "Open folder" shows the tree, with links in file order.
2. **US2**: Export to Downloads, then pull the file with `adb pull`.
   - Open it in desktop Chrome and Firefox (Import bookmarks). The same folders and titles appear (SC-002).
3. **US3**: import `photo.jpg`, `empty.html`, `truncated.html` and an 11 MB file.
   - Each shows the right message.
   - `sqlite3` row counts are unchanged after each refused or failed import.
   - Rotate during a 5,000-link import: the import completes and the summary shows. Press Back during it: no folder is added.
4. **US4**: Settings → Bookmarks row → the Bookmarks screen opens with the sheet showing. Import from there, then "Open folder" works.
5. **API 24**: the import and export pickers both open and return.
6. **TalkBack**: the ⋮ button, sheet rows and summary dialog are labelled, and the summary is announced.

## G4. Real exports (SC-001)

Import real exports from Chrome, Firefox, Edge and Safari on a computer, and from Samsung Internet and DuckDuckGo on the A55 (owner present, ADR 0017). Every web link is present, in the right folder.

## G5. Performance and size (SC-004, SC-006, SC-008)

- **A55, release**: time import and export of 5,000 links from a generated file, median of 3. The screen keeps scrolling during both.
- **Network**: with a proxy on the AVD, import `firefox.html` (`ICON` URIs): 0 requests.
- **Size and start**: the APK grows ≤ 60 KB. The cold start is interleaved against `main`.
- **Release checks**: `lintDebug`, `assembleRelease`, 16 KB alignment, the Compose report with zero unexplained non-skippable composables, the Layout Inspector count for opening the sheet, and CI 6/6.
