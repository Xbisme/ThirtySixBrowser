# Quickstart: Verifying Spec 013 — Bookmarks CRUD

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-07

This document defines the manual user-device verification gates that MUST pass before Spec 013 is marked ✅ Done. The automated quality gates (`testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`, `assembleRelease`, 16 KB CI gate, Constitution Check) are run by CI / the implementer; the gates below are user-only because they require interactive on-device verification (mirrors Spec 011 / 012 manual-gate pattern per `.claude/claude-app/dev-workflow.md`).

---

## Pre-conditions

- Branch `013-bookmarks-crud` checked out and built: `./gradlew installDebug` succeeds.
- Test device: Pixel 5-class or emulator API 35 (16 KB page size emulator preferred).
- App language: change device system language for G8 sweep; otherwise system default.

---

## Gate G1 — Star toggle round-trip (FR-001..004, US1)

1. Cold-start the app on a fresh install (or wipe app data: `adb shell pm clear com.raumanian.thirtysix.browser`).
2. Wait for the browser screen to load `https://www.google.com/` (default home URL).
3. Verify the **star icon in the top bar is in the OUTLINE state** (not filled).
4. Tap the star icon.
5. **PASS** if all of the following happen within 200 ms (SC-001):
   - Star changes to FILLED state.
   - A snackbar / brief confirmation appears with text matching `bookmark_added_snackbar`.
6. Kill the app process: `adb shell am force-stop com.raumanian.thirtysix.browser`.
7. Re-launch the app from the launcher.
8. Verify the **star is still FILLED** for `https://www.google.com/`.
9. Open the bookmarks screen (via the navigation route — destination `AppDestination.Bookmarks`).
10. **PASS** if a single bookmark titled "Google" (or the page title at the time of bookmark) with URL `https://www.google.com/` is visible at the root level.
11. Return to browser. Tap the star **again**.
12. **PASS** if the star reverts to OUTLINE within 200 ms and a `bookmark_removed_snackbar` confirmation shows.
13. Open bookmarks screen — list is empty (with the localized empty state from `bookmarks_empty_*`).

**FAIL conditions**: star state not synchronized, snackbar missing or untranslated, bookmark missing after restart.

---

## Gate G2 — Folder navigation + breadcrumb (FR-008..015, US3)

1. Open the bookmarks screen.
2. Tap "+ New folder" (overflow or designated affordance), name it `Work`. **PASS** if the folder appears in the root list.
3. Tap into `Work`. The breadcrumb at the top of the screen shows: `Root / Work` (or localized equivalent).
4. Create another folder inside Work: `Project A`. Breadcrumb now `Root / Work / Project A`.
5. Tap into `Project A`. Add a bookmark manually via the FAB (URL `https://example.com/a`).
6. Tap the breadcrumb segment `Work` → screen jumps directly to the `Work` folder, showing `Project A` as a subfolder.
7. Tap "back to parent" affordance — returns to root.
8. Use the system back gesture from inside `Project A` — returns one level up to `Work` (matches Constitution §V predictive-back UX).

**PASS** if all 8 navigation transitions are correct and the breadcrumb is always tappable / accurate.

---

## Gate G3 — Real-time global search with folder paths (FR-024..028, US4)

1. Pre-seed the bookmarks store with at least 10 bookmarks distributed across 3 folders (root, `Work`, `Work / Project A`). E.g.:
   - Root: `https://google.com`, `https://duckduckgo.com`
   - `Work`: `https://github.com/anthropics`, `https://anthropic.com`
   - `Work / Project A`: `https://docs.anthropic.com`
2. Navigate INTO the `Work / Project A` folder.
3. Tap the search affordance.
4. Type `goo`.
5. **PASS** if the result list shows `https://google.com` (with inline path label `Root` from `bookmarks_breadcrumb_root_label`) — confirming **global** search scope worked even though the user was inside a leaf folder.
6. Type `git`.
7. **PASS** if the result shows the GitHub bookmark with inline path label `Root / Work`.
8. Clear the search query.
9. **PASS** if the screen returns to the unfiltered view of `Project A` (the folder the user was in when search was opened).
10. Re-open search, type `xyzzyz`.
11. **PASS** if the localized "no matches" state from `bookmarks_no_search_matches` appears (NOT the generic empty state).
12. Performance check: with `1000` seeded bookmarks (an automated seeding script may help), each keystroke produces results within `100 ms` (SC-003) — verify by typing fluidly without perceptible lag.

---

## Gate G4 — Cascade delete with confirm dialog (FR-019, FR-020, US6)

1. Build a folder tree:
   - `Cleanup` (root folder) containing:
     - 3 bookmarks at the top
     - sub-folder `Sub1` containing 2 bookmarks
     - sub-folder `Sub2` containing 1 bookmark and a sub-sub-folder `Sub2.1` containing 4 bookmarks
2. Long-press the `Cleanup` folder row → action sheet appears with `Open / Rename / Move / Delete` options.
3. Tap `Delete`.
4. **PASS** if the confirm dialog shows the **total descendant count** (3 + 2 + 1 + 4 = 10 bookmarks, 2 sub-folders excluding `Sub2.1` self-reference plus `Sub2.1` = 3 sub-folders) — the body text from `bookmarks_confirm_delete_folder_body` reads roughly "This will permanently delete 10 bookmarks and 3 sub-folders."
5. Tap `Delete folder and contents`.
6. **PASS** if `Cleanup` and every descendant disappear from the list within ~200 ms; no orphans remain at root.
7. Verify Room state: `adb shell sqlite3 /data/data/com.raumanian.thirtysix.browser/databases/thirtysix_browser.db 'SELECT COUNT(*) FROM bookmarks;'` — count should be 0 if no other bookmarks existed.

**FAIL conditions**: orphan bookmarks left at root, dialog count wrong, partial deletion (some descendants survive).

---

## Gate G5 — Folder cycle prevention (FR-018, US3 edge)

1. Build folder hierarchy: `A` → `A/B` → `A/B/C`.
2. Long-press folder `A` → action sheet → `Move`.
3. Folder picker bottom sheet opens. Try to navigate into `A/B/C` and select it.
4. **PASS** if the picker either prevents the selection or shows the `bookmarks_error_folder_cycle` localized error and refuses to commit the move.
5. Verify Room state: `parent_id` of `A` remains NULL.
6. Repeat trying to move `A` into `B` — same expected error.

---

## Gate G6 — Manual add validation (FR-006, FR-022, US7)

For each row below, attempt the manual-add flow with the given title/URL pair and confirm the expected outcome:

| Title input | URL input | Expected |
|---|---|---|
| `My Site` | `https://example.com` | ✅ saved at root |
| (empty)   | `https://example.com` | ❌ `bookmarks_validation_title_required` inline error |
| `My Site` | (empty)               | ❌ `bookmarks_validation_url_required` inline error |
| `My Site` | `not-a-url`           | ❌ `bookmarks_validation_url_invalid` inline error |
| `My Site` | `example.com`         | ✅ saved (auto-prepends `https://` per R5) |
| `My Site` | `ftp://example.com`   | ❌ `bookmarks_validation_url_invalid` inline error |

**PASS** if every row matches the expected outcome AND every error message renders in the device's current locale (test with VI device language for at least one row).

---

## Gate G7 — Incognito tab disables star + bookmark-tap stays incognito (FR-004, FR-010a, US1 edge)

1. From the tab switcher, create a new **incognito** tab (Spec 012).
2. Load any normal page (e.g., `https://example.com`).
3. **PASS** if the star icon is **hidden** (not just disabled-grey) from the BrowserTopBar — per R11.
4. Switch back to the original normal tab.
5. **PASS** if the star icon reappears with state correctly synchronized for that tab's URL.
6. **FR-010a check** — While in the incognito tab from step 1, open the bookmarks screen and tap any bookmark.
7. **PASS** if (a) the URL loads in the **same incognito tab** (verify by checking the tab switcher count: still 1 incognito + N normal, no new tab created), and (b) the FLAG_SECURE indicator (recents thumbnail blocked) remains active.

---

## Gate G8 — 8-locale visual sweep (FR-029, SC-010)

For each locale `en, vi, de, ru, ko, ja, zh, fr`:

1. Change device language: `Settings → System → Languages` to that locale (or use per-app language picker on Android 13+).
2. Open the app, navigate through:
   - Bookmarks screen empty state.
   - Bookmarks screen with at least 1 bookmark + 1 folder.
   - Add bookmark dialog (FAB).
   - Edit bookmark dialog (long-press → Edit).
   - Create folder dialog.
   - Folder picker bottom sheet.
   - Delete folder confirm dialog (with non-empty folder).
   - Search "no matches" state.
   - Browser star icon long-press content description (use TalkBack to verify the contentDescription string).
3. **PASS** if every label renders in the target locale, no text truncation at default font size on a 360 dp-wide screen, no missing-resource fallback to EN, no raw resource keys.

---

## Gate G10 — Deep-tree rendering performance (SC-007)

1. Pre-seed Room with a 5-level-deep folder tree, ~50 items per level (mix of folders and bookmarks). A `./gradlew` debug-build seeder script or `adb shell am broadcast` helper is acceptable; an in-app dev-flag-gated seeder is not required.
2. Open the bookmarks screen; navigate from root through all 5 levels, scrolling each level fully top-to-bottom and back.
3. **PASS** if no perceptible scroll-frame stutter (eyeball, 60 fps target on Pixel 5-class device); on emulator API 35 use Android Studio's Layout Inspector / Profiler to verify no dropped frames > 16 ms.
4. Tap into the deepest folder, then use the breadcrumb to jump back to root in one tap; verify no layout flash > 200 ms.

**FAIL conditions**: visible jank during scroll; navigation lag > 200 ms.

---

## Gate G9 — TalkBack accessibility sweep (FR-029, SC-009)

1. Enable TalkBack: `Settings → Accessibility → TalkBack ON`.
2. Navigate the bookmarks screen end-to-end:
   - Each bookmark row announces title + URL.
   - Each folder row announces folder name + (optionally) child count.
   - Each icon-only affordance (star, FAB, search, back) announces a non-empty localized label.
   - Action sheet items announce their text.
   - Confirm dialog announces title + body text.
3. **PASS** if zero affordances announce as "Button" / "Image" with no further description.

---

## Final sign-off

When G1–G10 all PASS, post a comment on PR `013-bookmarks-crud → main` listing each gate and PASS state, then update:

- `CLAUDE.md` Active Spec section: 🟡 → ✅
- `.claude/claude-app/project-context.md` Spec 013 entry: status PASS, APK delta measured
- `.claude/claude-app/sdd-roadmap.md`: Spec 013 row → ✅ Done

Then merge.
