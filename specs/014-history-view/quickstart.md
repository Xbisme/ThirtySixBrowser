# Quickstart: History View — Manual User-Device Gates

**Feature**: 014-history-view
**Date**: 2026-05-08
**Target device**: Pixel 5+ (Android 14+) or AVD API 35/36 with 16 KB page size

> Run **after** `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug detekt ktlintCheck assembleRelease` are all green and the 16 KB CI gate passes (SC-009). These gates verify human-perceivable correctness that automated tests cannot.

---

## Pre-flight

1. `./gradlew installDebug` to a clean device (or `Settings → Apps → ThirtySix Browser → Storage → Clear data` to reset).
2. Confirm bottom-bar now shows a **History** icon button (mirror of the bookmarks entry from Spec 013).

---

## G1 — Auto-recording (FR-001 / FR-002 / Q1 incognito-context visibility)

**Steps**:
1. In a normal tab, visit three distinct URLs (e.g., `https://example.com`, `https://wikipedia.org`, `https://duckduckgo.com`) — wait for each to fully load.
2. Open the History screen via the bottom-bar.
3. Confirm: all three URLs appear under the "Today" header, in reverse-chronological order (most recently visited at top).
4. Open a **new incognito tab** (long-press tabs button → "New incognito tab" per Spec 012).
5. In the incognito tab, visit three distinct URLs (different from step 1).
6. Re-open History.
7. **PASS criteria**: incognito visits do NOT appear; only the normal-tab visits from step 1 are present.
8. While **still on the active incognito tab**, switch to History via the bottom-bar.
9. **PASS criteria (Q1 = A)**: the History screen opens normally and shows the full list from step 1; no "unavailable" placeholder, no lock-down. Top-bar, search field, and clear-all affordance all interactable.

---

## G2 — Day-grouping (FR-008)

> This gate requires temporary device-clock manipulation. Skip if QA policy forbids.

**Steps**:
1. With clean DB, set device date to 2 days ago (e.g., Settings → Date & time → disable Auto, set yesterday).
2. Visit `https://example.com` in a normal tab.
3. Set device date to yesterday.
4. Visit `https://wikipedia.org`.
5. Restore device date to today.
6. Visit `https://duckduckgo.com`.
7. Open History.

**PASS criteria**:
- Three section headers visible in this order: "Today" (with duckduckgo) · "Yesterday" (with wikipedia) · localized explicit date for the older bucket (with example.com).
- Within each bucket, single entry shown.
- Re-open History after restarting app — bucket assignment unchanged.

---

## G3 — Tap-to-replace-active-tab (FR-010 / FR-010a)

**Steps**:
1. With at least 3 history entries, in a fresh **normal** tab navigate to `https://example.com`.
2. Open History.
3. Tap a different entry's row (e.g., `wikipedia.org`).
4. **PASS criteria (normal-tab branch)**: the active tab navigates to wikipedia.org and the History screen dismisses; the address bar shows the new URL.
5. Now repeat starting from a fresh **incognito** tab.
6. **PASS criteria (incognito-tab branch, FR-010a)**: the URL still loads in that incognito tab. Re-open History → no new entry was recorded for this load (FR-002 still holds).

---

## G4 — Real-time search (FR-011 / FR-011a)

**Steps**:
1. Pre-populate ≥ 5 history entries with varied titles & hostnames.
2. Open History.
3. Type a single character (e.g., `e`) into the search box.
4. **PASS criteria (FR-011a)**: the list does NOT filter — full grouped list still visible.
5. Type a second character (`ex`).
6. **PASS criteria**: list shrinks immediately to entries whose title OR URL contains `ex` (case-insensitive).
7. Type non-matching text (e.g., `xyznomatchhere`).
8. **PASS criteria**: localized "no matches" message appears in place of the list.
9. Clear the search box (tap X or backspace to empty).
10. **PASS criteria**: full grouped list restored.
11. While search has 3+ chars and matches exist, in a **separate** normal tab visit a URL whose title matches the active query.
12. **PASS criteria (FR-017)**: the new entry appears in the filtered list within ~1 s without retyping.

---

## G5 — Long-press action sheet (FR-018 → FR-022)

**Steps**:
1. With at least 3 history entries, long-press any row.
2. **PASS criteria**: action sheet appears with exactly three actions in this order: "Open in new tab" / "Delete entry" / "Copy URL".
3. Pick **Open in new tab** while currently in a **normal** tab.
4. **PASS criteria**: a new normal tab opens loading that URL; History dismisses; new tab is active.
5. Repeat from step 1 starting from an **incognito** tab.
6. **PASS criteria (Q2 = A)**: the new tab is **non-incognito** even though the calling context was incognito.
7. Long-press a different row → pick **Delete entry**.
8. **PASS criteria**: only that single row disappears from the list; other rows for the same URL (if any) at different timestamps remain. List updates within 500 ms.
9. Long-press a third row → pick **Copy URL** → snackbar / brief confirmation appears.
10. Open address bar of any tab and paste.
11. **PASS criteria**: pasted text is the exact URL of the long-pressed row.
12. Long-press again → tap outside the sheet (or system back).
13. **PASS criteria**: sheet dismisses with no action taken.

---

## G6 — Clear-all (FR-023 → FR-026)

**Steps**:
1. With at least 5 entries, in History tap the "Clear all" affordance in the top bar.
2. **PASS criteria**: confirmation dialog appears with localized title, body explaining destructiveness, destructive-styled "Clear" button, and "Cancel" button.
3. Tap **Cancel**.
4. **PASS criteria**: dialog dismisses; list unchanged.
5. Re-tap "Clear all" → tap **Clear**.
6. **PASS criteria**: every history entry is removed; empty-state composition (icon + message) is shown; "Clear all" affordance is hidden.
7. Restart app, re-open History.
8. **PASS criteria**: still empty.

---

## G7 — 8-locale visual sweep + accessibility (FR-028 / FR-029 / FR-030)

**Steps**:
1. With ≥ 3 history entries spanning multiple days, switch device language sequentially through: VI, DE, RU, KO, JA, ZH, FR (return to EN at the end).
2. For each locale verify on the History screen:
   - Top-bar title is translated.
   - "Today" / "Yesterday" / explicit-date headers are localized.
   - Time-of-visit format respects locale conventions (e.g., 14:32 for VI/DE/RU/KO/JA/ZH/FR; 2:32 PM for EN).
   - Search placeholder, "no matches" message, action-sheet items, clear-all dialog title/body/buttons, and empty-state message are all translated.
3. Enable TalkBack (Settings → Accessibility → TalkBack ON).
4. Swipe-navigate through History.
5. **PASS criteria**: every interactive element (rows, search field, top-bar icons, action-sheet items) announces a meaningful localized label; favicon images announce as image with hostname context, not as "unlabeled image".

---

## G8 — 10K-row performance benchmark (SC-005 / SC-006)

> Required to mark SC-005 / SC-006 PASS in the PR body. Skip only if QA capacity does not allow seed-time setup; in that case mark SC-005 / SC-006 as DEFERRED in the PR body.

**Pre-flight**: Use a debug-only seeder route or `adb shell` invocation of a test endpoint to bulk-insert 10,000 history rows spanning 30 days (≈ 333 rows per day) — title and URL strings approximately 80 chars each. Restart the app cleanly after seed.

**Steps**:
1. Open History screen for the first time after seed.
2. **PASS criteria (SC-005)**: screen reaches first interactive frame in under ≈ 1.5 s on a Pixel 5-class device; while scrolling top-to-bottom, no visible jank, no ANR. Optionally enable Settings → Developer options → "Profile GPU rendering" → on-screen bars and confirm the green 16 ms line is rarely crossed during normal scroll.
3. Scroll back to the top, tap **Clear all** → confirm.
4. **PASS criteria (SC-006)**: every row removed and the empty state visible within 1 s of confirm tap.
5. Type a 3-character query into the search field (e.g., `exa`).
6. **PASS criteria (SC-003 reaffirmed)**: filtered list visible within 1 s; subsequent keystrokes feel responsive.

**Implementation note**: a debug-only `HistorySeederActivity` or an `IsDebuggable`-gated dev menu route is the simplest path. NOT shipped in release builds. Document in the PR body which seeding mechanism was used.

---

## Done / Sign-off

When all 8 gates PASS on a real device, mark the spec as ✅ Done in CLAUDE.md / sdd-roadmap.md and open the PR `014-history-view → main` referencing this quickstart in the body.
