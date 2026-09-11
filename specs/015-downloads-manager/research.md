# Phase 0 — Research: Downloads Manager

**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-09-10

Twelve research items. The spec's clarification session settled every decision-shaping product question, so **no item carries a `NEEDS CLARIFICATION` marker**. Three items were flagged in the spec itself as requiring platform verification (R2 ← FR-048, R3 ← FR-026, R4 ← FR-024a); each is resolved below with an explicit statement of how far the evidence goes and what must still be confirmed on a device.

**Evidence legend** — each item states its basis:
- **[verified-here]** — checked empirically in this repository during planning.
- **[documented]** — settled by the platform's documented contract; behaviour is stable and relied on widely.
- **[gate]** — a strong prior is stated, but the decision is only final after a named quickstart gate runs on a device.

---

## R1 — Where the download hand-off is intercepted

**Decision**: Attach a download listener to the `WebView` inside `BrowserWebView`'s factory block, alongside the existing `webViewClient` / `webChromeClient` assignments, and surface it as a fifth callback on `BrowserWebViewCallbacks`.

**Rationale**: `BrowserWebView.kt:159-169` already assigns both platform clients in one place and already has `state.isIncognito` in scope (`:99`). A download hand-off is the same shape of event as the four callbacks the bundle already carries, so it needs no new plumbing pattern. `BrowserWebViewCallbacks` goes from 4 fields to 5 — under detekt's `functionThreshold: 6`, and the rule ignores data classes anyway (`detekt.yml:151`). **[verified-here]**

**Alternatives considered**: Intercepting in `shouldOverrideUrlLoading` was rejected — that fires before the response headers exist, so `Content-Disposition` and the content type are unavailable and the classification would have to be guessed from the URL alone. Routing through `WebViewActionsHandle` was rejected because that handle is for imperative *commands into* the WebView, not events out of it.

**Note**: the incognito flag is in scope but **not consulted** — FR-014a records incognito downloads identically. No branch is added.

---

## R2 — Does the app need the notification permission for the system's own download notifications? *(FR-048 — BLOCKING)*

**Decision**: **Start from "not required" and do not declare it**, then confirm on a device at gate **G9** before the manifest is finalised. If G9 shows notifications are suppressed without it, declare it and add the runtime request; if not, drop it and amend the Constitution's permission table.

**Rationale**: the Android 13+ notification permission gates notifications posted *by the requesting app's own UID*. A download handed to the platform service is notified by the download provider process under its own identity, not ours, so our grant should be irrelevant. Declaring a permission the app does not use would itself violate Constitution §I ("MUST NOT request runtime permissions it does not actively use"), so the default must be *not declaring* and earning the declaration with evidence. **[gate — G9]**

**Why this cannot be settled by reasoning alone**: both outcomes are cheap to implement but the wrong choice is user-visible and permanent-ish — a missing progress notification on every modern device, or a permission prompt the app never needed. OEM builds also vary in how they route provider notifications. This is exactly the class of question the spec refused to guess at.

**Consequence either way**: no notification *channel* is created and no notification is ever composed by this app (FR-047). Even in the "declare it" branch, `core/constants/NotificationChannels.kt` — listed aspirationally in the Constitution's project tree but never created — stays absent.

**Alternatives considered**: declaring it unconditionally "to be safe" was rejected as a §I violation. Composing our own notification to sidestep the question was rejected by the spec's fourth clarification.

---

## R3 — Handing a completed file to an external app *(FR-026)*

**Decision**: Ask the platform download service for the download's content URI by its handle, and launch a view intent with that URI plus read permission granted to the receiver. Never construct a `file://` URI.

**Rationale**: since Android 7.0 — which is this project's **minSdk** — exposing a `file://` URI across an app boundary throws `FileUriExposedException` under the default StrictMode policy, so a raw path is not merely discouraged but fatal on every supported device. The download service exposes its own content provider and hands back a content URI for exactly this purpose, which means **no `FileProvider` needs to be declared in the manifest** and no `provider_paths.xml` is needed. **[documented]**

**Confirmation still required at gate G4**: that the returned URI resolves for a third-party viewer on both API 24 and a modern API level, and that "no app can open this" surfaces as a message rather than a crash (FR-027).

**Alternatives considered**: declaring our own `FileProvider` over the public Downloads directory — rejected as redundant machinery for files the download service already exposes, and it would add a manifest provider entry plus a paths XML for no gain.

---

## R4 — Detecting that a transfer handle is no longer known *(FR-024a)*

**Decision**: Query the platform service for the handle and treat an **empty result** as "not known". Do not treat it as an error, and do not depend on knowing *when* the service prunes.

**Rationale**: querying by id returns a cursor-shaped result; an id the service no longer knows yields zero rows rather than throwing. That gives a clean, allocation-free signal with no exception handling on the happy path. Crucially, **the design does not need to model the pruning schedule at all** — FR-024a is expressed as "handle unknown → fall back to file presence", which is correct whether the service prunes after a day, after a year, or never. Building against an assumed retention policy would have been the fragile choice. **[documented]** — with the "no exception thrown" half confirmed at gate **G8**.

**Alternatives considered**: registering for the service's completion broadcast and persisting the terminal outcome (spec option B) was rejected during clarification — it requires a manifest-registered receiver that runs while the app is closed, and it still needs the file-presence check for FR-029, so it adds a mechanism without removing one.

---

## R5 — The v1 → v2 schema migration *(FR-016, FR-017)*

**Decision**: One additive migration adding a single new table. No existing table, column, index, or foreign key is touched. Register it explicitly on the database builder; the strict policy forbidding destructive fallback stays as-is.

**Rationale**: this is the **project's first real migration**, and Spec 005 deliberately set up everything needed for it — `exportSchema = true`, the committed `app/schemas/…/1.json`, and a `DatabaseModule` that already documents "no `fallbackToDestructiveMigration*`: strict-no-destructive policy". A purely additive migration is the lowest-risk possible first exercise of that policy: existing rows cannot be affected because no existing table is mentioned. **[verified-here]**

**Zero new packages confirmed**: `androidx.room:room-testing` is **already declared** in `gradle/libs.versions.toml:115` pinned to the existing `room = "2.8.4"`, and **already wired** as `testImplementation` in `app/build.gradle.kts:207`. The migration test therefore adds no dependency at all — it uses machinery Spec 005 paid for and never exercised. **[verified-here]**

**Obligations this creates**: `app/schemas/…/2.json` must be generated and committed to git alongside the code (`1.json` is currently the only file there), and `git grep fallbackToDestructiveMigration app/src/main/` must still return zero matches at merge.

**Alternatives considered**: putting downloads in DataStore instead of Room, to dodge the migration entirely — rejected because downloads are a queryable, growing, relational list, which is precisely what Room is for, and dodging the project's first migration merely defers it to a spec with more at stake.

---

## R6 — Legacy storage permission on API 24–28 *(FR-010, FR-011, FR-012)*

**Decision**: Declare the legacy external-storage write permission with an explicit `maxSdkVersion` ceiling of 28, and request it at runtime only on API 23–28, only at the moment of the first download.

**Rationale**: writing to the *public* Downloads directory needed that permission before scoped storage arrived in API 29; from API 29 on it is neither needed nor granted. The version ceiling means the permission is absent from the manifest's effective set on every modern device, so it never appears in the Play listing's permission summary for those users and can never be requested there. Requesting at point-of-use rather than at launch matches Constitution §I's "MUST NOT request runtime permissions it does not actively use". **[documented]**

**This is the spec's one Constitution deviation** — a fourth permission against §II's three-row table. Recorded in [plan.md](plan.md) Complexity Tracking.

**Permanent denial handling**: when the user has denied permanently, the platform stops showing the dialog and the request returns immediately as denied. FR-012 therefore requires the message to direct the user to system settings rather than re-prompting into a void.

**Alternatives considered**: writing to app-private external storage on API ≤ 28 — rejected during clarification because files would vanish on uninstall and never appear in the device's Downloads folder, contradicting the basic expectation of the feature. Raising minSdk to 29 — rejected as reversing a foundational Constitution decision for one feature's convenience.

---

## R7 — Observing live progress *(FR-021, FR-023, SC-007)*

**Decision**: Poll the platform service on a fixed cadence **only while the Downloads screen is in the foreground and at least one entry is in flight**, and stop entirely otherwise. Fold each poll's result into the list at display time; persist nothing (FR-015).

**Rationale**: SC-007 requires visible advancement at least once per second, which sets the cadence floor; polling faster buys nothing a user can perceive. Gating on "screen visible **and** something is actually transferring" means the common case — a list of finished downloads — polls zero times, so the steady state costs nothing. A2 already assigns retry and background continuation to the platform, so there is nothing to observe when no transfer is live. **[documented]**

**Alternatives considered**: a content observer on the service's provider — rejected because its notification granularity is not contractually specified and it would still need a query to read progress, so it adds a mechanism without removing the query. A background worker tracking transfers while the screen is closed — rejected as out of scope (A3): the system notification already informs the user when they are not looking at our screen.

---

## R8 — Deriving and sanitising the filename *(FR-004, FR-005, SC-015)*

**Decision**: Derive the candidate name from the server's declared filename, then the address, then a generated fallback — and then **sanitise unconditionally** before use, treating the derived name as hostile input regardless of where it came from. Sanitisation reduces the value to a single path segment: separators removed, parent-directory references removed, leading dots neutralised, length bounded, and an empty result replaced by the generated fallback.

**Rationale**: the platform offers a helper that assembles a filename from the content-disposition header, the URL and the content type, and it is the right tool for *derivation*. It is not a security boundary, and SC-015 requires proof against at least six hostile names. Sanitising our own output afterwards is cheap, is independently unit-testable on the JVM with no device, and does not depend on the helper's undocumented internals staying constant across platform versions. **[documented]**

**Alternatives considered**: trusting the platform helper alone — rejected; SC-015 demands a defence we can point at and test. Rejecting suspicious downloads outright — rejected as user-hostile when a safe name is trivially derivable.

---

## R9 — Icon pivot within the core glyph set *(A12)*

**Decision**: Overflow affordance = `MoreVert`. Downloads entry and empty state = `KeyboardArrowDown`. Cancel control on an in-flight row = `Close`. Failed state = `Warning`. Delete file = `Delete`. Open file, copy link, and remove-from-list carry **no leading icon**.

**Rationale**: `material-icons-core` 1.7.8 was unpacked and enumerated during planning — it ships **48 filled glyphs**, and contains no download glyph of any kind (`Download`, `GetApp`, `CloudDownload`, `SaveAlt` are all in `material-icons-extended`, which A13 forbids). Every glyph named above was confirmed present in that enumeration. Where no honest core glyph exists, no icon is used — the precedent Specs 013 and 014 both set, rather than pressing a misleading glyph into service. **[verified-here]**

**Alternatives considered**: adding `material-icons-extended` — rejected; it is a large artifact and A13/FR-057 forbid new dependencies. Shipping a hand-drawn vector drawable — rejected for v1.0 as a bespoke asset where a plain text row reads just as well.

---

## R10 — The overflow menu and the bottom-bar refactor *(FR-042 – FR-046)*

**Decision**: A Material 3 dropdown menu anchored to the overflow affordance in the bottom bar. `NavigationBottomBarCallbacks` sheds `onBookmarksClick` and `onHistoryClick` and gains a single `onOverflowClick`, going from 8 fields to 7; the menu's own entries are carried by a separate small callback bundle owned by the menu composable.

**Rationale**: a dropdown is the platform-conventional affordance for an overflow anchored to a specific control, it dismisses natively on outside-tap and system back (FR-045), and it needs no sheet state hoisting. Adding a fourth entry for Settings in Spec 016 is then one line (FR-046). **[documented]**

**Bottom-bar arithmetic**: seven affordances at the platform's minimum touch target already consume more than a 360dp-wide screen allows once side padding is counted; five plus the overflow fits with room to spare. **[verified-here — the seven current affordances were counted in `NavigationBottomBar.kt`]**

**Housekeeping folded in**: `NavigationBottomBarCallbacks`' KDoc currently claims the bundle "is now 6 fields — exactly at detekt's `functionThreshold = 6` (PASSES). Any future addition would have to re-bundle into nested groups." That is **stale and wrong** — Specs 013 and 014 each appended a field without updating it, so it stands at 8, and detekt passes only because `ignoreDataClasses: true`. Since this spec rewrites the file anyway, the KDoc is corrected in the same change. **[verified-here]**

**Alternatives considered**: a modal bottom sheet — rejected as heavier than the interaction warrants and inconsistent with anchoring to a specific bar control. Keeping direct entries and shrinking the icons — rejected; it degrades the touch target below the accessibility minimum.

---

## R11 — Duplicate filenames *(FR-007)*

**Decision**: Delegate collision resolution to the platform download service, which appends a numeric suffix rather than overwriting. Add no de-duplication logic of our own.

**Rationale**: A7 already assigns this to the platform; the requirement is only that neither file is lost, which the platform's behaviour satisfies. Reimplementing it would mean racing the service for the filesystem. **[documented — confirmed at gate G3]**

**Alternatives considered**: pre-checking for an existing file and renaming ourselves — rejected as a time-of-check-to-time-of-use race against the service that owns the write.

---

## R12 — Cancellation leaving no partial file *(FR-037, FR-039)*

**Decision**: Cancel by asking the platform service to remove the transfer by its handle, which both stops it and discards any partially-written file. Treat a cancel of an already-finished download as a no-op that leaves the file intact.

**Rationale**: removal is the service's own cancellation primitive and it owns cleanup of the partial file, so FR-037's "no partial file remains" is satisfied by the platform rather than by us deleting files we may not have permission to touch. FR-039's race — the transfer completing between the user reading the screen and tapping cancel — resolves naturally: our own record is only removed when the user removes it, and the state shown afterwards comes from re-reading the service, so a completed file is never falsely reported as cancelled. **[documented — confirmed at gate G6]**

**Alternatives considered**: stopping the transfer and deleting the file ourselves — rejected; on API 29+ we may hold no write access to a file in the public directory that we did not place there through the service.

---

## Summary of what remains device-bound

| Item | Gate | What is being decided |
|------|------|----------------------|
| R2 | **G9 (blocking)** | Whether the notification permission is declared at all. The manifest line is not written until this runs. |
| R3 | G4 | That the content URI resolves for third-party viewers on API 24 and on a modern API level. |
| R4 | G8 | That querying a forgotten handle returns empty rather than throwing. |
| R11 | G3 | That a duplicate filename yields two files rather than an overwrite. |
| R12 | G6 | That cancelling leaves no partial file. |

Every other decision above is settled by this document and needs no further input before implementation.
