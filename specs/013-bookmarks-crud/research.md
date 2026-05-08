# Phase 0 Research: Bookmarks CRUD

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-07

This file resolves the technical unknowns left after `/speckit-clarify`. Each item: **Decision**, **Rationale**, **Alternatives considered**.

---

## R1. Repository shape — combined vs split

**Question**: Should bookmarks and folders live in one `BookmarkRepository` (owning both DAOs) or be split into `BookmarkRepository` + `BookmarkFolderRepository`?

**Decision**: **One combined `BookmarkRepository` owning both `BookmarkDao` and `BookmarkFolderDao`**, with a single repository implementation in `data/repository/BookmarkRepositoryImpl.kt`.

**Rationale**:

1. **Cascade delete is transactional across both tables** (FR-020 → R7). A split repo would force `DeleteFolderUseCase` to coordinate two repositories under a Room `@Transaction`, which Room cannot easily provide across separate DAO boundaries from the use-case layer; Hilt-injected DAOs do not share a transactional context unless invoked from inside a single `@Transaction`-annotated method on a single class. Putting both DAO calls behind one repository keeps the transaction boundary at the repository (the natural unit of consistency).
2. **No Constitution §IV Repo→Repo violation**. §IV forbids "Repository → Repository" dependencies; one repository owning two DAOs is fine. DAOs are data sources, not repositories. The same pattern exists today in Spec 011 (`TabRepository` owns `TabDao`) and Spec 005 baseline (DAOs are injected directly into one `BookmarkRepository` would be future-Spec-013 — i.e. now).
3. **Bounded context**: bookmarks and folders form a single hierarchical concept; users do not think of "bookmarks data" and "folders data" as separate domains.
4. **Discoverability**: 13 use cases all reach for the same repo; a split adds ceremony with no readability gain.

**Alternatives considered**:

- **Split into two repositories** (`BookmarkRepository` + `BookmarkFolderRepository`) with `DeleteFolderUseCase` coordinating both. Rejected because the cascade transaction would either need a third coordinator class (`BookmarkRepositoryFacade`, smell) or hand-rolled `withTransaction { ... }` from the use case (couples the use case to Room internals — violates Constitution §IV's "domain pure Kotlin" rule).
- **One repository per layer with a separate transaction-coordinator service**. Rejected as over-engineered for a 2-DAO problem.

---

## R2. Edit / move / delete affordance — long-press vs row-tap menu

**Question**: How does the user invoke Edit / Move / Delete on a bookmark or folder row? (Spec defers to plan per `/speckit-clarify` Outstanding/Deferred.)

**Decision**: **Long-press a row → `ModalBottomSheet` action sheet** with options Open / Edit (or Rename for folders) / Move / Delete. Single tap remains the primary action (open bookmark, navigate into folder).

**Rationale**:

1. **Primary tap stays discoverable** — users get the "take me there" action without an extra disclosure level.
2. **Long-press is the Material 3 idiom** for row-level secondary actions (matches Files, Photos, Drive, Gmail).
3. **Bottom-sheet > popup menu** for accessibility — larger touch targets, easier dismissal, screen-reader announces sheet header.
4. **Composable reuse**: `BookmarkActionSheet` and `FolderActionSheet` are tiny Composables consuming the same `ModalBottomSheet` skeleton.

**Alternatives considered**:

- **Row-trailing kebab (3-dot menu)** — adds visual noise on every row; kebabs in long lists are easy to miss-tap. Rejected.
- **Swipe-to-reveal-actions (à la Mail)** — tempting but conflicts with future "drag-to-reorder" (deferred but not impossible); also harder to make accessible. Rejected.

---

## R3. Search implementation strategy — LIKE vs FTS

**Question**: For real-time title+url search across all bookmarks (FR-024..028), should we use SQL `LIKE` queries or migrate to FTS4/FTS5 virtual tables?

**Decision**: **`LIKE` query** with `LOWER(title) LIKE '%' || LOWER(:q) || '%' OR LOWER(url) LIKE '%' || LOWER(:q) || '%'` evaluated reactively as `Flow<List<BookmarkEntity>>`.

**Rationale**:

1. **SC-003 budget** is 100 ms per keystroke on 1,000 bookmarks. A `LIKE '%q%'` over an unindexed text column on 1,000 rows on a Pixel 5 typically completes in 5–15 ms — comfortable margin.
2. **No schema migration** required (SC-004). FTS would force adding a virtual table + triggers + a Spec 005 schema bump.
3. **Substring match semantics** — users expect "google" to find "https://google.com" and "Google Search Tips". `LIKE '%q%'` does this naturally; FTS prefix-match needs `q*` syntax and behaves differently for partial words.
4. **Case insensitivity** comes for free via `LOWER()` on both sides; Room's `COLLATE NOCASE` is an alternative but only works on ASCII — `LOWER()` handles Unicode case-folding for the supported locales adequately for v1.0.

**Alternatives considered**:

- **FTS5 virtual table** with `@Fts5` Room support. Rejected — premature optimization at the 1K-row scale, requires schema migration that violates SC-004.
- **In-memory search via `Flow` operator on `observeAll()`**. Rejected — pulls every bookmark into memory on every keystroke; works fine at 1K but doesn't scale, and the Compose recomposition cost dominates anyway. Easier to push the filter into SQL.

**Implementation note**: The `searchByTitleOrUrl` DAO method returns `Flow<List<BookmarkEntity>>`. The `SearchBookmarksUseCase` augments each entity into a `BookmarkSearchResult` by walking up the parent chain (via cached folder map) to build the inline path label per FR-026. With at most ~10 levels deep and ~50 results visible, this is O(results × depth) ≈ O(500) operations per emission — negligible.

---

## R4. Real-time search — debounce or no?

**Question**: Should the typing pipeline debounce keystrokes before hitting Room?

**Decision**: **No fixed debounce.** Use `MutableStateFlow<String>(query)` → `flatMapLatest { repo.searchByTitleOrUrl(it) }`. The latest-only semantics of `flatMapLatest` cancel stale queries; Room's reactive Flow will skip emissions that are immediately superseded.

**Rationale**:

- 5–15 ms query time at 1K rows means a user typing at 5 chars/sec sees 5 query cycles per second, all comfortably under the 100 ms SC-003 budget.
- `flatMapLatest` already delivers cancel-and-restart behaviour — debounce would only add latency without saving work.
- Avoids a `SEARCH_DEBOUNCE_MS` constant that adds cognitive load without measurable benefit.

**Alternatives considered**:

- **`.debounce(100.milliseconds)`** — adds 100 ms perceived latency to results; tested mentally: typing slows down enough that the cost outweighs the I/O savings.
- **`.throttleFirst`** — wrong semantics for a search field (would emit on first char, then cool-down).

---

## R5. URL validation — at what layer, with what rules?

**Question**: How does FR-006 / FR-022 reject malformed URLs? Where does validation live?

**Decision**: Domain-layer validation via a small `BookmarkUrlValidator` object (pure Kotlin, no Android imports) callable from use cases. Rules:

1. Trim leading/trailing whitespace.
2. If string does not start with `http://` or `https://` (case-insensitive), prepend `https://`.
3. Try `java.net.URI.create(normalized)`; if it throws or `host == null` or `host.isBlank()` → invalid.
4. Reject schemes other than `http`/`https` after normalization.

**Rationale**:

- **Pure Kotlin** keeps `domain/` layer Android-free per Constitution §IV (`java.net.URI` is JDK, not Android SDK).
- **Auto-prepend `https://`** matches user expectations — most people paste `example.com` not `https://example.com`. The address bar (Spec 009) already does this in `AddressBarInputClassifier`; we mirror that rule for consistency.
- **No external regex** — `URI.create` is cheap and well-tested.

**Alternatives considered**:

- **`android.webkit.URLUtil.isNetworkUrl(...)`** — works but pulls Android SDK into domain layer. Rejected per Constitution §IV. Could place in `data/` but then validation crosses a boundary unnecessarily.
- **Reuse `AddressBarInputClassifier` from Spec 009** — tempting but its mandate is "URL vs query" classification with auto-search-fallback; for the manual-add form we want strict reject, not fallback. Different semantics.

The validator emits a typed `BookmarkUrlValidationError` (sealed: `Empty`, `InvalidScheme`, `MissingHost`) so the UI can map to localized strings (FR-029).

---

## R6. Folder cycle prevention — when moving a folder

**Question**: How does FR-018 reject moving folder A into folder B when B is a descendant of A?

**Decision**: Inside `MoveFolderUseCase`, **walk B's ancestor chain** by repeatedly fetching `folder.parentId` until `null` (root) or `folder.id == A.id`. If A is found in the chain, reject with `BookmarkErrorEvent.FolderCycle`.

**Rationale**:

- O(depth) — at most ~10 reads up the tree even for absurdly deep trees. No O(n) enumeration of A's descendants.
- Uses already-required DAO method `getById(id: Long)` — no new query.
- Easy to test: synthesize a 5-deep chain, attempt forbidden move, assert error.

**Alternatives considered**:

- **Compute A's full descendant set, then check if B is in it**. Rejected — O(descendant-count) which can be 1000s of reads; ancestor walk is always faster.
- **Maintain a materialized path column** in `BookmarkFolderEntity`. Rejected — schema migration violates SC-004.

---

## R7. Cascade delete algorithm — correctness vs FK SET NULL

**Question**: Spec 005 schema sets `ON DELETE SET NULL` on both `BookmarkEntity.parent_folder_id` and `BookmarkFolderEntity.parent_id`. With Q3 resolved as cascade-delete, deleting a folder via `BookmarkFolderDao.delete` would orphan its bookmarks at root, not delete them. How does `DeleteFolderUseCase` cascade correctly?

**Decision**: **Depth-first traversal in app code** inside a Room `@Transaction`-annotated repository method. Algorithm:

```
fun deleteFolderCascade(folderId: Long) =
  withTransaction {
    // 1. Recurse into sub-folders first (depth-first).
    val children = folderDao.getDirectChildFolders(folderId)
    for (child in children) {
      deleteFolderCascade(child.id)
    }
    // 2. Delete leaf bookmarks under this folder.
    bookmarkDao.deleteByParentFolder(folderId)
    // 3. Delete this folder itself.
    folderDao.deleteById(folderId)
  }
```

**Rationale**:

1. **Depth-first** ensures child sub-folders are gone before their parent is deleted, so the FK rule never needs to fire.
2. **Single transaction** — the entire cascade is atomic. Either the whole subtree is gone or nothing is (per Constitution §VII offline-first / no-data-loss-on-crash guarantee).
3. **Bounded recursion** — at typical user depth (5 levels) Kotlin's stack handles this trivially. For pathological 1000-level depth, refactor to iterative BFS later (out of scope v1.0).
4. **Two new DAO methods** required: `BookmarkFolderDao.getDirectChildFolders(parentId)` (synchronous suspend, not Flow — different from the existing reactive `observeChildren`), and `BookmarkDao.deleteByParentFolder(folderId)` (single bulk-delete WHERE clause). Both trivial to add.

**Alternatives considered**:

- **Change FK rule from SET NULL to CASCADE** in a Spec 013 schema migration. Rejected — violates SC-004 zero-migration goal, and CASCADE+root-orphan-on-direct-delete diverges from the more common "preserve at root" intent if v1.0.x ever adds an "import bookmarks" feature.
- **Iterative BFS instead of recursive DFS**. Equivalent correctness; recursion is more readable for the typical small tree. Note in test plan to add "1000-level deep" chaos test once tests are written.

---

## R8. Confirm-delete dialog — count of descendants

**Question**: FR-020 confirmation dialog shows the **total descendant count** (per Q3 clarification). Where is this counted, and when?

**Decision**: A new `CountFolderDescendantsUseCase` returns a `BookmarkDescendantCount(bookmarks: Int, folders: Int)` for any folder ID. Computed via a single SQL query per type (recursive walk in app code, summing as we go). Result cached in `BookmarksUiState.pendingDeletionCount` once the user opens the confirm dialog; cleared when the dialog closes.

**Rationale**:

- Cached in UI state ≠ stale: the dialog is modal — the tree cannot change underneath it.
- Two simple queries (count bookmarks under each visited folder; count direct sub-folders to know depth termination) — runs in <50 ms even on huge trees.
- No new schema column.

**Alternatives considered**:

- **Compute lazily during the cascade itself** and report after deletion. Rejected — destroys the informed-consent flow.
- **Recursive CTE in SQL** to count in one query. SQLite WAL with FK enabled supports this, but Room needs raw query support which adds boilerplate. Reserve as a future optimization.

---

## R9. Star-icon "canonical bookmark" semantics — implementation

**Question**: Q4 resolved star toggle = canonical-most-recent-with-this-URL. How do `IsUrlBookmarkedUseCase` and `ToggleBookmarkUseCase` express this?

**Decision**:

- `IsUrlBookmarkedUseCase(url: String): Flow<Boolean>` → `bookmarkDao.observeByUrl(url).map { it.isNotEmpty() }`. Star is "filled" if **any** bookmark exists with the URL, regardless of folder.
- `ToggleBookmarkUseCase(url: String, fallbackTitle: String)`:
  - if `bookmarkDao.countByUrl(url) == 0` → insert one `BookmarkEntity(parentFolderId = null, title = title.takeIf { it.isNotBlank() } ?: url, sortOrder = currentTimestamp)` at root.
  - else → `bookmarkDao.deleteMostRecentByUrl(url)` (single SQL: `DELETE ... WHERE url = :url AND id = (SELECT id ... ORDER BY created_at DESC LIMIT 1)`).
- Both run inside `withTransaction` for atomicity.

**Rationale**:

- "Most recent" via `ORDER BY created_at DESC LIMIT 1` is unambiguous and survives the toggle behaviour required by Q4.
- Manual-add duplicates are untouched — `deleteMostRecentByUrl` removes exactly one.
- Star "filled" reflects "any exists", which is the user's mental model: if I see ANY bookmark for this URL, the star says "yes, you've saved this".

**Alternatives considered**:

- **Track which bookmark is "the canonical star-created one" via a flag column**. Schema migration → rejected.
- **Track via a `is_star_canonical` boolean in entity**. Same migration cost, plus complexity of keeping the flag consistent across edits/moves.

---

## R10. Folder picker UI — hierarchical list pattern

**Question**: FR-017 (move folder) and AddOrEditBookmarkDialog need a folder picker. What does it look like?

**Decision**: M3 `ModalBottomSheet` containing a `LazyColumn` of all folders, indented by depth, with the root represented as a synthetic "Root / Top-level" entry pinned at index 0. Tap = select; "+ New folder" inline button at the bottom of the sheet.

**Rationale**:

- Bottom sheet keeps the picker contextual; doesn't navigate away from the calling screen.
- Indentation by `depth × 16dp` (where depth comes from walking parent chain) is a known M3 pattern — see [Material 3 navigation drawer expandable items](https://m3.material.io/components/lists/specs).
- "+ New folder" inline keeps the user from being trapped if they want a destination that doesn't exist yet.
- No drag-to-reorder, no search (folders typically <50 in practice — much less than bookmarks).

**Alternatives considered**:

- **Full-screen folder picker** — heavyweight; users will be on small screens and the sheet pattern is more native.
- **Path-string text input** ("Work / Project A") — too prone to typos, doesn't scale.

---

## R11. Star icon placement on `BrowserTopBar`

**Question**: Where exactly does the star `IconButton` go in the existing `BrowserTopBar` (extracted in Spec 012)?

**Decision**: **Trailing position, immediately to the right of the address bar / clear-button area, before any incognito indicator.** Hidden via `if (canBookmark)` rather than disabled — keeps the top bar clean when the URL is not bookmarkable (incognito, error page, non-http(s) scheme per FR-004).

**Rationale**:

- Trailing matches Chrome Android (star) and DuckDuckGo (favorite icon).
- "Hide rather than disable" follows current `BrowserTopBar` convention used by the incognito indicator from Spec 012.
- `canBookmark` derived in `BrowserViewModel` from `(loadingState is Loaded || canGoBack) && !isIncognito && url startsWith http`. Single boolean keeps `BrowserUiState` clean (1 new field: `isBookmarked`; `canBookmark` is computed in the Composable from already-present state).

**Alternatives considered**:

- **Leading position before the address bar** — visually cluttered, conflicts with Spec 008 back-button conventions.
- **Inside the address-bar trailing icon row** alongside the clear button — the clear button is transient (only when text non-empty + focused); the star is persistent. Mixing them is confusing.

---

## R12. Locale strings — the new keys

**Question**: What are the exact resource keys to add, and roughly what do they say in EN?

**Decision**: 25 new keys. (Translations are produced at implementation time; this list is the contract for the i18n resource files.)

| Key | EN value (rough) | Used by |
|---|---|---|
| `bookmarks_screen_title` | Bookmarks | top bar title at root |
| `bookmarks_action_search_cd` | Search bookmarks | search icon contentDescription |
| `bookmarks_action_new_folder_cd` | New folder | new-folder overflow item contentDescription |
| `bookmarks_action_add_cd` | Add bookmark | FAB contentDescription |
| `bookmarks_action_back_to_parent_cd` | Up to parent folder | up-arrow contentDescription |
| `bookmarks_breadcrumb_root_label` | Root | breadcrumb root segment + search-result row inline path label |
| `bookmarks_empty_title` | No bookmarks yet | EmptyBookmarksState title |
| `bookmarks_empty_body` | Tap the star icon while viewing a page, or use + to add one. | EmptyBookmarksState body |
| `bookmarks_no_search_matches` | No bookmarks match "%1$s" | NoSearchMatchesState |
| `bookmarks_action_open` | Open | action sheet item |
| `bookmarks_action_edit` | Edit | bookmark action sheet |
| `bookmarks_action_rename` | Rename | folder action sheet |
| `bookmarks_action_move` | Move | both action sheets |
| `bookmarks_action_delete` | Delete | both action sheets |
| `bookmarks_dialog_add_title` | Add bookmark | add dialog title |
| `bookmarks_dialog_edit_title` | Edit bookmark | edit dialog title |
| `bookmarks_dialog_create_folder_title` | New folder | create folder dialog title |
| `bookmarks_dialog_rename_folder_title` | Rename folder | rename folder dialog title |
| `bookmarks_field_title_label` | Title | text input label |
| `bookmarks_field_url_label` | URL | text input label |
| `bookmarks_field_folder_label` | Folder | folder picker label |
| `bookmarks_validation_title_required` | Title cannot be empty | inline error |
| `bookmarks_validation_url_required` | URL cannot be empty | inline error |
| `bookmarks_validation_url_invalid` | Enter a valid http:// or https:// URL | inline error |
| `bookmarks_validation_folder_name_required` | Folder name cannot be empty | inline error |
| `bookmarks_dialog_folder_picker_title` | Pick folder | bottom sheet title |
| `bookmarks_picker_new_folder_inline` | + New folder here | bottom sheet inline button |
| `bookmarks_confirm_delete_folder_title` | Delete folder? | confirm dialog title |
| `bookmarks_confirm_delete_folder_body` | This will permanently delete %1$d bookmarks and %2$d sub-folders. | plurals — body text |
| `bookmarks_confirm_delete_folder_button` | Delete folder and contents | confirm button label |
| `bookmarks_error_folder_cycle` | A folder cannot be moved into itself or its sub-folder. | error snackbar |
| `bookmark_action_star_add_cd` | Add bookmark for this page | star icon when outline (BrowserTopBar) |
| `bookmark_action_star_remove_cd` | Remove bookmark for this page | star icon when filled (BrowserTopBar) |
| `bookmark_added_snackbar` | Bookmark saved | confirmation snackbar after star-add |
| `bookmark_removed_snackbar` | Bookmark removed | confirmation snackbar after star-remove |

**Note**: this list deliberately exceeds 25 because some require plurals format (`bookmarks_confirm_delete_folder_body`); the exact final count after consolidation is **~33 string entries** plus 1 plurals resource. Plan budgets are generous.

**Rationale**: explicit list lets the implementer (Copilot Pro per dev-workflow.md) generate all 8 locale variants in one batch using the established translation conventions from Spec 011/012 (brand "ThirtySix" stays in Latin script; descriptors translate naturally).

---

## R13. Constants additions — `core/constants/BrowserLimits.kt`

**Decision**: Add three constants:

- `MAX_BOOKMARK_TITLE_LENGTH = 200` — input field maxLength + DAO validation; prevents accidental DB bloat from paste-an-entire-page-of-text.
- `MAX_BOOKMARK_URL_LENGTH = 2048` — matches the de-facto limit modern browsers enforce.
- `MAX_FOLDER_NAME_LENGTH = 100`.
- `MAX_BOOKMARKS = 10_000` — power-user envelope from Spec 005 plan; enforced as soft warning only (snackbar at 90 % capacity) — no hard reject.
- `MAX_FOLDERS = 1_000` — same soft-warning policy.

**Rationale**: Constitution §III No-Hardcode Rule (categorized table) explicitly mandates `core/constants/BrowserLimits.kt` for "Magic numbers / limits". These constants live there with the existing `MAX_TABS = 50` and `MAX_INCOGNITO_TABS = 50`.

**Alternatives considered**: Tighter limits (e.g., title = 100). Rejected — a 200-char title accommodates non-Latin scripts where character counts can be ~60 % of byte counts, and validates user intent without surprising rejection.

---

## R14. APK size budget verification

**Decision**: All new code is Kotlin; no new dependencies; new strings add ~200 entries × ~30 bytes = ~6 KB to `resources.arsc` after R8 dedup; new Composables compile to ~50 KB of bytecode after R8. Net delta projected: **+50–80 KB**, well under SC-008 100 KB ceiling.

**Verification step**: After implementation, run `./gradlew assembleRelease` and compare APK size with Spec 012 baseline 2.1 MB. Reported in the post-implementation summary.

---

## R15. Test strategy split

**Decision**: Tests are deferred from the Claude/Planner phase (this plan) to the Copilot Pro / implementer track per `.claude/claude-app/dev-workflow.md`. The implementer's task list (Phase 2) will enumerate every test class explicitly. This mirrors Spec 011 / 012 patterns.

**Inventory of expected test files** (for the tasks-generator's reference):

- Domain: `domain/usecase/AddBookmarkUseCaseTest.kt`, `UpdateBookmarkUseCaseTest.kt`, `DeleteBookmarkUseCaseTest.kt`, `MoveBookmarkUseCaseTest.kt`, `ToggleBookmarkUseCaseTest.kt`, `IsUrlBookmarkedUseCaseTest.kt`, `ObserveBookmarksByFolderUseCaseTest.kt`, `SearchBookmarksUseCaseTest.kt`, `ObserveFolderPathUseCaseTest.kt`, `CreateFolderUseCaseTest.kt`, `RenameFolderUseCaseTest.kt`, `MoveFolderUseCaseTest.kt`, `DeleteFolderUseCaseTest.kt`, `CountFolderDescendantsUseCaseTest.kt` (14 files)
- Data: `BookmarkRepositoryImplTest.kt` (Robolectric + real Room), `BookmarkMapperTest.kt`, `BookmarkFolderMapperTest.kt`, `BookmarkUrlValidatorTest.kt`
- Presentation: `BookmarksViewModelTest.kt`, `BrowserViewModelStarToggleTest.kt` (additive to existing `BrowserViewModelTest`)
- Instrumented: `BookmarksScreenInstrumentedTest.kt` (happy path + folder navigation + breadcrumb), `BookmarksScreenSearchTest.kt`, `BookmarksScreenDeleteFolderTest.kt`, `BookmarkStarIconBrowserScreenTest.kt`

Roughly 25 new test files. Budgets: ≥ 70 % coverage for `domain/` + `data/` per Constitution §VI.

---

## R16. Manual user-device gates (Copilot/user track)

**Decision**: Following the Spec 011 / 012 pattern, these gates are documented in [quickstart.md](quickstart.md) for user verification post-implementation, not run by the planner:

- **G1**: Star toggle round-trip — bookmark a page, kill app, reopen, confirm in list, tap to navigate, star reflects "filled".
- **G2**: Folder navigation — create folder hierarchy 3 deep, breadcrumb interactive, back-to-parent works.
- **G3**: Search — global scope verified, folder path appears inline; clearing query returns to current folder view.
- **G4**: Cascade delete — non-empty folder with mixed contents; confirm dialog count accurate; deletion atomic.
- **G5**: Cycle prevention — try to move a folder into its own descendant; localized error; no DB change.
- **G6**: Manual-add validation — empty title / empty URL / malformed URL all rejected with localized inline error.
- **G7**: Incognito gate — open incognito tab, star icon hidden; switch to normal tab, star reappears.
- **G8**: 8-locale visual sweep — change device language to each of 8 locales, verify no truncation, no missing translations.
- **G9**: TalkBack accessibility — every action sheet item, every dialog field, every icon-button reachable and announced.

---

## Summary of decisions

| ID | Decision (one-liner) |
|---|---|
| R1 | One `BookmarkRepository` owns both DAOs |
| R2 | Long-press → ModalBottomSheet action sheet |
| R3 | LIKE-based search; no FTS |
| R4 | `flatMapLatest`, no debounce |
| R5 | Pure-Kotlin `BookmarkUrlValidator` using `java.net.URI` |
| R6 | Cycle prevention via ancestor walk |
| R7 | Cascade delete via depth-first DFS in `@Transaction` |
| R8 | `CountFolderDescendantsUseCase` powers confirm dialog |
| R9 | Star toggle = "any exists" + "delete most recent" |
| R10 | Folder picker = ModalBottomSheet hierarchical list |
| R11 | Star icon trailing in BrowserTopBar; hidden when not applicable |
| R12 | ~33 new string keys + 1 plurals × 8 locales |
| R13 | New `BrowserLimits` constants for length caps |
| R14 | APK delta ≤ 80 KB projected |
| R15 | Tests deferred to Copilot Pro / implementer phase |
| R16 | 9 manual user-device gates documented in quickstart |
