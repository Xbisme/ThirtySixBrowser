# Feature Specification: Downloads Manager

**Feature Branch**: `015-downloads-manager`
**Created**: 2026-09-10
**Status**: Draft
**Input**: User description: "downloads-manager — Cho phép người dùng tải file từ web xuống máy và quản lý danh sách file đã tải, dùng android.app.DownloadManager của hệ thống. Spec 015, spec cuối cùng của Phase 3 (Data Features). AppDestination.Downloads route đã tồn tại từ Spec 002 nhưng DownloadsScreen vẫn là placeholder Text. BrowserWebView chưa nối setDownloadListener. Tải file → snackbar + notification hệ thống; xem danh sách mới nhất trước với tên/kích thước/trạng thái/thời điểm; chạm mục đã xong → mở bằng app phù hợp qua Intent (bọc chống crash theo posture Spec 012); long-press → action sheet; huỷ download đang chạy; trạng thái rỗng. 4 quyết định đã chốt: (1) nguồn sự thật hybrid Room + DownloadManager, bắt buộc migration v1→v2 additive, cấm fallbackToDestructiveMigration; (2) lưu vào thư mục Downloads công khai, API 24–28 cần WRITE_EXTERNAL_STORAGE maxSdkVersion=28 — permission thứ 4, lệch Constitution, phải ghi Complexity Tracking, cấm MANAGE_EXTERNAL_STORAGE; (3) lối vào = overflow More menu gom Bookmarks/History/Downloads, bottom bar còn 5 nút điều hướng; (4) notification dựa vào hệ thống, câu hỏi POST_NOTIFICATIONS thành R-item verify trong plan. Ràng buộc: zero package mới, icon core-only, seam nền tảng qua interface + Hilt, runCatching mọi system service, SharedFlow one-shot event, Clean Architecture, no-hardcode, i18n 8 locale, gỡ downloads_screen_placeholder."

## Clarifications

### Session 2026-09-10

- Q: What is the source of truth for the downloads list — the app's own database, or the system download service's own records? → A: **Hybrid.** The app's own database owns each download's identity and durable metadata (source URL, filename, MIME type, creation timestamp, the system download handle, and the resolved file location). Live transfer state (running / paused / failed / complete plus bytes-so-far) is read from the system download service at render time and is never persisted. Rationale: the system service prunes its own records on its own schedule, so an app-owned row is the only way the list can be a stable, app-controlled surface; but duplicating live progress into the database would guarantee stale reads and write amplification during every transfer. **Consequence: this is the project's first real database schema migration (v1 → v2). It MUST be additive and MUST NOT use destructive fallback (Spec 005 strict policy).**
- Q: Where do downloaded files land, given the project's minimum supported Android version is 7.0 (API 24)? → A: **The device's public Downloads folder on every supported version.** On Android 7.0–9.0 this requires the legacy external-storage write permission, declared with an explicit maximum-version ceiling so it is never requested on Android 10+ and never appears in the permission list on modern devices. Rationale: a browser whose downloads do not appear in the device's Downloads folder, and vanish when the app is uninstalled, contradicts the single most basic user expectation of the feature. **Consequence: a fourth permission, beyond the three enumerated in the Constitution's permission table — this MUST be recorded as a documented deviation in the plan's Complexity Tracking. Broad "manage all files" access remains forbidden.**
- Q: Where does the user enter the Downloads screen, given the bottom bar already carries seven affordances? → A: **A consolidated overflow menu.** Bookmarks, History and Downloads move behind a single overflow affordance, leaving the bottom bar with five pure navigation controls (Back, Forward, Reload/Stop, Home, Tabs). Rationale: seven affordances already crowd a 360dp-wide device and an eighth would overflow; consolidating now also reserves the slot Settings needs in Spec 016 instead of forcing the same refactor again one spec later. **Consequence: this is a deliberate regression of one tap for Bookmarks and History, which shipped with direct bottom-bar entries in Specs 013 and 014.**
- Q: Should the app post its own download notifications, or rely on the system's? → A: **Rely on the system's.** The download is handed to the system service with completion notifications enabled, and the system renders them. The app does not compose, post, or own any notification in v1.0. Rationale: the system notification already carries progress, completion and tap-to-open, and reproducing it would mean a notification channel, a runtime permission flow, a rationale UI and eight locales' worth of strings for no user-visible gain. **Consequence: whether the app must still hold the Android 13+ notification permission for the *system's* notification to be shown is NOT settled by this spec — it MUST be verified on a real device or emulator during planning, and only then is the permission declared or dropped.**
- Q: When the system download service has dropped its own record of a download but the app still holds its row, what state does that row display? → A: **Resolve it from the file on disk.** Handle unknown plus file present is shown as complete; handle unknown plus file absent is shown as missing, with the offer to remove the stale row. Nothing extra is persisted, so FR-015's "never persist live transfer state" stands, and the check reuses the file-existence test FR-029 already requires. Rationale: the presence of the file is the fact the user actually cares about, and inferring from it costs no new column, no completion listener that must run while the app is closed, and no new state machine. **Consequence: a download that failed long ago and was then pruned is indistinguishable from one that succeeded and whose file the user later deleted — both render as missing. Accepted knowingly; see A15.**
- Q: Where does the control for cancelling an in-flight download live? → A: **Directly on the row**, visible only while that entry is transferring, reachable in one tap. The action sheet stays openable on an in-flight entry but offers only the actions that apply to it. Rationale: cancelling is time-critical — its whole purpose is to stop the transfer before it consumes more data — so placing it behind a long-press contradicts the intent, and the row already has the space that the "open file" affordance leaves unused while a transfer is running. Matches the behaviour of the mainstream Android browsers. **Consequence: this resolves an internal inconsistency in the first draft, where FR-036 required cancellation but FR-030's four-item action sheet did not offer it and no requirement placed it anywhere.**
- Q: Is a file downloaded from an incognito tab recorded in the Downloads list? → A: **Yes, recorded identically to any other download**, with no incognito marking of any kind. Rationale: the file lands in the *public* Downloads folder where any file manager can see it, and the system notification names it, so omitting the row conceals nothing while removing the user's ability to cancel it, find it, or understand where it went. Constitution §I enumerates what incognito must not do — write history, retain cookies and cache past tab close — and downloads are not among them, because downloading is a deliberate act of writing a file to shared storage rather than a passively-accumulated browsing trace. Marking the row as incognito-originated was rejected as *worse* for privacy than not recording the distinction at all, since it would persist the one genuinely sensitive fact — that this file was fetched during a private session — into a long-lived list. Follows the Spec 014 Q1 precedent that the History screen behaves identically in either context. **Consequence: confirms assumption A6 and promotes it to FR-014a.**
- Q: Can the user remove an in-flight download from the list? → A: **No — "remove from list" is offered only once the entry has reached a terminal state.** A user who wants an in-flight download gone cancels it from the row control (FR-036) and then removes it: two deliberate acts, each with an unambiguous meaning. Rationale: removing the row discards the handle that identifies the transfer, so allowing it mid-flight would leave a transfer running that the app can neither cancel nor record on completion. Making removal silently cancel the transfer instead was rejected because it turns a list-tidying action into a destructive one, capable of discarding a nearly-finished file the user meant to keep. FR-034 already supplied the state-gating mechanism, so nothing new is introduced. **Consequence: the action sheet's contents are now specified as an explicit per-state matrix in FR-034a rather than left to the implementer's judgement.**
- Q: What does the user see when the platform's download service is unavailable — disabled by the user, or absent from the device? → A: **A clear localized message, and no record is created.** Rationale: no transfer was ever accepted, so writing a "failed" row would assert an attempt the system never made. More concretely, such a row would carry no transfer handle and would therefore be resolved by FR-024a as *missing*, indistinguishable from a genuinely deleted file — undermining the inference rule settled in the first clarification. Failing silently was rejected because the user is left tapping a link that visibly does nothing. **Consequence: establishes the invariant that the Downloads list contains only downloads the platform service actually accepted, which is what makes FR-024a's file-presence inference sound.**

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Download a file from a web page (Priority: P1) 🎯 MVP

A user browsing a page taps a link that points to a file rather than a page — a PDF, an image, an archive. Instead of the page navigating somewhere useless, the browser recognises it as a download, starts fetching it in the background, and immediately tells the user it has started. The user can carry on browsing, or leave the app entirely, and the transfer keeps going.

**Why this priority**: Without this, there is no feature. Every other story in this spec operates on a download that this story produced.

**Independent Test**: On a device with network access, open a page containing a direct file link, tap it, and confirm that (a) an immediate on-screen confirmation appears, (b) a system notification tracks the transfer, and (c) when it finishes, the file is present in the device's public Downloads folder with the expected name and is readable by another app such as a file manager.

**Acceptance Scenarios**:

1. **Given** a normal tab showing a page with a direct link to a downloadable file, **When** the user taps that link, **Then** the transfer begins in the background and a transient confirmation naming the file appears without blocking the page.
2. **Given** a download has started, **When** the user leaves the browser or switches apps, **Then** the transfer continues and its progress remains visible in the system notification area.
3. **Given** a download completes successfully, **When** the user inspects the device's Downloads folder from any file manager, **Then** the file is present, fully written, and openable.
4. **Given** the user is on Android 7.0–9.0 and has not yet granted storage access, **When** they tap a download link, **Then** they are asked for that access with a clear explanation before the transfer begins.
5. **Given** the user is on Android 10 or newer, **When** they tap a download link, **Then** no storage permission is requested at any point.

---

### User Story 2 - See my downloads with live status (Priority: P1)

A user who has downloaded things wants a single place inside the browser that lists them: what was downloaded, how big it is, whether it finished, and when. Transfers still in flight show live progress; failed ones say so rather than silently disappearing.

**Why this priority**: A download the user cannot find again is barely a download. Together with US1 this forms the smallest genuinely useful version of the feature.

**Independent Test**: Start two downloads — one that completes and one interrupted mid-transfer — then open the Downloads screen and confirm both appear, newest first, each showing its filename, size, state and time, and that the in-flight one visibly advances while watched.

**Acceptance Scenarios**:

1. **Given** three files were downloaded at different times, **When** the user opens the Downloads screen, **Then** all three are listed most-recent-first, each showing filename, file size, state and the time it was started.
2. **Given** a download is currently transferring, **When** the user views the Downloads screen, **Then** that entry shows a live-advancing progress indication rather than a static row.
3. **Given** a download failed, **When** the user views the Downloads screen, **Then** that entry is clearly marked as failed and is visually distinguishable from a completed one.
4. **Given** a download is in progress, **When** the app's process is killed and relaunched, **Then** the entry is still listed and its state reflects whatever the system service did in the meantime.

---

### User Story 3 - Reach Downloads from a consolidated overflow menu (Priority: P1)

The bottom bar has run out of room. Bookmarks, History and Downloads — the three "collections" of the browser — move behind one overflow affordance, so the bottom bar keeps only the controls used while actually navigating a page.

**Why this priority**: This is the entry point for US2; without it the Downloads screen is unreachable. It is separated from US2 because it independently changes how two already-shipped features are reached, and therefore needs its own verification.

**Independent Test**: On a 360dp-wide device, confirm the bottom bar shows exactly five navigation controls with no crowding or clipping, that the overflow affordance opens a menu listing Bookmarks, History and Downloads, and that each entry still reaches the same screen it reached before this change.

**Acceptance Scenarios**:

1. **Given** the browser screen on a 360dp-wide device, **When** the user looks at the bottom bar, **Then** exactly five navigation controls are shown, none clipped or overlapping, plus the overflow affordance.
2. **Given** the bottom bar, **When** the user taps the overflow affordance, **Then** a menu opens listing Bookmarks, History and Downloads with localized labels.
3. **Given** the overflow menu is open, **When** the user picks any entry, **Then** the corresponding screen opens and the menu closes.
4. **Given** the overflow menu is open, **When** the user taps outside it or uses the system back gesture, **Then** it closes with nothing else changed.
5. **Given** a user who previously reached Bookmarks or History from the bottom bar, **When** they look for those entries after this change, **Then** both are present in the overflow menu and reach the identical screens with identical behaviour.

---

### User Story 4 - Open a completed download (Priority: P2)

Having downloaded a file, the user taps it in the list and the device opens it with whatever app handles that kind of file. If nothing on the device can open it, the browser says so plainly instead of crashing.

**Why this priority**: Valuable, but a user can already reach the file through the system notification or a file manager, so this is a convenience rather than the core loop.

**Independent Test**: Download a PDF and an obscure file type on a device with a PDF viewer but no handler for the obscure type; tap each in the list and confirm the first opens externally and the second produces a clear message with no crash.

**Acceptance Scenarios**:

1. **Given** a completed download of a type the device can open, **When** the user taps its row, **Then** the file opens in the appropriate external app.
2. **Given** a completed download of a type nothing on the device can open, **When** the user taps its row, **Then** a clear localized message explains that no app can open it and the browser stays responsive.
3. **Given** a download that has not finished or has failed, **When** the user taps its row, **Then** no open attempt is made and the row's state is communicated instead.
4. **Given** a completed download whose file the user has since deleted outside the browser, **When** the user taps its row, **Then** a clear localized message explains the file is missing and offers to remove the stale entry.

---

### User Story 5 - Manage a download entry (Priority: P2)

Long-pressing an entry offers the housekeeping actions: open it, copy where it came from, take it out of the list, or delete the file itself. Removing an entry and deleting a file are offered as distinct choices, because they are genuinely different acts.

**Why this priority**: Needed for the list to stay useful over time, but the feature is coherent without it.

**Independent Test**: With several downloads listed, long-press one and confirm the action sheet appears with the expected actions; exercise each and confirm exactly the intended effect — in particular that removing an entry leaves the file on disk and deleting the file removes both.

**Acceptance Scenarios**:

1. **Given** the Downloads list, **When** the user long-presses an entry, **Then** an action sheet opens offering, in a stable order, opening the file, copying its source link, removing the entry from the list, and deleting the file.
2. **Given** the action sheet, **When** the user picks "remove from list", **Then** the row disappears and the file remains present in the device's Downloads folder.
3. **Given** the action sheet, **When** the user picks "delete file", **Then** they are asked to confirm, and on confirming both the row and the file are gone.
4. **Given** the action sheet, **When** the user picks "copy link", **Then** the original source address is placed on the system clipboard and a transient confirmation appears.
5. **Given** the action sheet, **When** the user taps outside it or uses the system back gesture, **Then** it closes and nothing happens.
6. **Given** an entry for a download that is still running, **When** the user long-presses it, **Then** only "copy link" is offered — opening, removing from the list, and deleting the file are all withheld, so an in-flight transfer can never be orphaned by having its record removed.

---

### User Story 6 - Cancel a running download (Priority: P2)

A user who started a large download by mistake, or is on a metered connection, can stop it. Stopping it does not leave a half-written file lying around.

**Why this priority**: Prevents wasted data and storage, but only applies while a transfer is in flight.

**Independent Test**: Start a large download, cancel it partway through using the control on its own row, and confirm the transfer stops, the notification clears, no partial file remains in the Downloads folder, and the list reflects the cancellation.

**Acceptance Scenarios**:

1. **Given** a download in progress, **When** the user taps the cancel control on its row, **Then** the transfer stops and the system notification for it clears — with no long-press or intermediate menu required.
2. **Given** a download that has reached a terminal state, **When** the user looks at its row, **Then** no cancel control is present on it.
3. **Given** a cancelled download, **When** the user inspects the Downloads folder, **Then** no partial file remains.
4. **Given** a cancelled download, **When** the user views the Downloads screen, **Then** the entry is either gone or clearly marked as cancelled, and never shown as still running.
5. **Given** a download that finished in the moment between the user reading the screen and tapping cancel, **When** the cancel is processed, **Then** the completed file is left intact and the user is not shown a false "cancelled" state.

---

### User Story 7 - Empty state when nothing has been downloaded (Priority: P3)

A user opening Downloads on a fresh install, or after clearing the list, sees a purposeful empty state rather than a blank screen.

**Why this priority**: Polish. It affects first impressions but blocks nothing.

**Independent Test**: On a fresh install, open the Downloads screen and confirm a localized icon-plus-message empty state is shown, and that it is replaced by the list as soon as a download exists.

**Acceptance Scenarios**:

1. **Given** a fresh install with no downloads, **When** the user opens the Downloads screen, **Then** a localized empty-state composition is shown in place of the list.
2. **Given** the empty state is showing, **When** a download is started, **Then** the list replaces the empty state without the user having to reopen the screen.
3. **Given** a list where the user removes the last remaining entry, **When** the removal completes, **Then** the empty state reappears.

---

### Edge Cases

- **Storage full**: the device runs out of space partway through a transfer. The entry MUST end in a failed state with a message distinguishing "out of space" from a generic failure, and MUST NOT be shown as complete.
- **Network lost mid-transfer**: the entry MUST reflect the interruption rather than appearing stalled-but-healthy, and MUST recover to the system service's eventual outcome (resumed or failed) without the user reopening the screen.
- **Storage permission denied on Android 7.0–9.0**: the download MUST NOT start, and the user MUST be told why in a localized message. If the user has permanently denied it, the message MUST point them at the system settings rather than re-prompting fruitlessly.
- **Duplicate filename**: downloading a file whose name already exists in the Downloads folder MUST NOT overwrite the existing file; both MUST end up present and separately addressable.
- **Download started from an incognito tab**: the file is written to the public Downloads folder, exactly as a normal-tab download is, and the entry IS recorded in the Downloads list with no incognito marking (FR-014a; rationale in A6).
- **File deleted outside the app**: an entry whose file no longer exists MUST be detected at the moment the user acts on it, MUST NOT crash, and MUST offer to clear the stale entry.
- **System forgets a download**: the system download service prunes its own records on its own schedule, so an entry's transfer handle can stop being recognised at any time — including for a transfer that never finished. Such an entry MUST fall back to the file-presence resolution in FR-024a rather than being left showing whatever state was last observed.
- **Removing an in-flight download**: not possible by design — the removal action is withheld until the entry reaches a terminal state (FR-034a), so the app can never discard the handle of a transfer that is still running.
- **App killed mid-download**: the transfer MUST survive process death, and on relaunch the entry MUST show the state the system service actually reached, not the state at the time of death.
- **Download service unavailable**: the platform's download service can be disabled by the user or missing entirely. Tapping a download link in that state MUST produce a clear localized message and MUST leave the list unchanged — never a silent no-op, and never a phantom record (FR-008a, FR-008b).
- **Hostile or malformed filename**: a filename supplied by the server that contains path separators, parent-directory references, or otherwise attempts to escape the Downloads folder MUST be sanitised to a plain filename before use. No download may ever be written outside the intended directory.
- **Missing or wrong file type**: when the server declares no file type, or one that contradicts the filename, the system MUST fall back to a safe determination and MUST NOT let a declared type cause a file to be opened as something it is not.
- **Unnamed download**: when neither the server nor the address yields a usable filename, the system MUST generate a stable fallback name rather than failing.
- **Extremely long list**: the screen MUST remain responsive with a large number of entries (see SC-006).
- **Rapid repeated taps** on the same download link MUST NOT produce duplicated in-flight transfers of the same file from a single user gesture.

## Requirements *(mandatory)*

### Functional Requirements

#### Download initiation

- **FR-001**: The system MUST detect when a resource requested inside a web page is a download rather than a navigable page, and MUST hand it to the device's system download service instead of attempting to render it.
- **FR-002**: The system MUST give immediate on-screen confirmation, naming the file, at the moment a download is accepted — without blocking the page the user is on.
- **FR-003**: The system MUST allow the transfer to proceed in the background, continuing while the user browses elsewhere, leaves the app, or the app's process is killed.
- **FR-004**: The system MUST derive the filename from the server's declared filename when present, falling back to the address, and finally to a generated name; the resulting name MUST always be a plain filename.
- **FR-005**: The system MUST sanitise any server-supplied filename so that path separators and parent-directory references cannot cause a file to be written outside the intended Downloads folder.
- **FR-006**: The system MUST determine the file type from the server's declaration where trustworthy and from the filename otherwise, and MUST record the determined type with the entry.
- **FR-007**: The system MUST NOT overwrite an existing file of the same name; both the pre-existing file and the new download MUST end up present and separately addressable.
- **FR-008**: A single user gesture on a download link MUST result in at most one transfer.

- **FR-008a**: When the platform's download service is unavailable — disabled by the user, absent from the device, or otherwise refusing the request — the system MUST show a clear localized message explaining that the download could not be started, and MUST NOT create a record for it.
- **FR-008b**: The system MUST record a download only after the platform service has accepted it and returned a transfer handle. A record MUST NOT exist for a transfer the service never accepted; this invariant is what makes the file-presence inference in FR-024a sound.

#### Storage location & permissions

- **FR-009**: Downloaded files MUST be written to the device's public Downloads folder on every supported Android version, so they are visible to other apps and survive uninstallation of the browser.
- **FR-010**: On Android versions that require it (7.0 through 9.0), the system MUST request the legacy external-storage write permission before the first download, with a localized explanation of why it is needed.
- **FR-011**: That permission MUST be declared with an explicit version ceiling so it is neither requested nor present on Android 10 and newer.
- **FR-012**: If the user declines the permission, the system MUST NOT start the download and MUST explain the outcome; if the user has declined permanently, the explanation MUST direct them to the system settings rather than re-prompting.
- **FR-013**: The system MUST NOT request broad "manage all files" access under any circumstance.

#### Persistence & schema evolution

- **FR-014**: The system MUST durably record, for each download it initiates, the source address, the filename, the determined file type, the creation timestamp, the handle identifying the transfer to the system download service, and the resolved location of the finished file.
- **FR-014a**: Downloads initiated from an incognito tab MUST be recorded exactly as downloads from a normal tab are, and the record MUST NOT carry any indication that it originated in an incognito context.
- **FR-015**: The system MUST NOT persist live transfer state (progress, bytes transferred, running/paused status); that state MUST be read from the system download service at the moment it is displayed.
- **FR-016**: Extending the local database for this feature MUST be done as an additive schema migration that preserves all existing data. Destructive fallback MUST NOT be used under any circumstance.
- **FR-017**: The upgraded schema MUST be exported and committed alongside the code, and an automated test MUST prove that a database created under the previous schema upgrades successfully with its existing rows intact.
- **FR-018**: Downloads data MUST remain on-device only, excluded from cloud backup and device-to-device transfer on the same terms as the rest of the browser's data (Constitution §I / §VII).

#### Downloads list & status

- **FR-019**: The system MUST provide a Downloads screen listing recorded downloads in reverse-chronological order, most recent first.
- **FR-020**: Each row MUST show the filename, the file size, the current state, and the time the download was started.
- **FR-021**: A row for a transfer still in flight MUST show live-advancing progress that updates while the screen is open, without the user re-entering the screen.
- **FR-022**: A row for a failed download MUST be clearly marked as failed and visually distinguishable from a completed one; where the cause is known and user-actionable (such as running out of space), the row MUST say so.
- **FR-023**: The list MUST reflect downloads started elsewhere in the app while the screen is open, and MUST reflect completions and failures that occur while the screen is open, in both cases without manual refresh.
- **FR-024**: The list MUST show the state the system download service actually reached after the app's process was killed and relaunched — never a stale state captured before the kill.
- **FR-024a**: When the system download service no longer recognises an entry's transfer handle, the system MUST resolve that entry's displayed state from whether its recorded file is present on the device: present is shown as complete, absent is shown as missing. The system MUST NOT show such an entry as running, pending, or paused.
- **FR-024b**: An entry resolved as missing under FR-024a MUST offer the user the same removal path FR-029 provides, and MUST NOT be removed automatically.
- **FR-024c**: Resolving an entry under FR-024a MUST NOT cause any live transfer state to be written to storage; the resolution is computed at display time (FR-015).

#### Opening a completed download

- **FR-025**: Tapping a completed entry MUST open the file with an appropriate external app on the device.
- **FR-026**: The file MUST be handed to the external app in a form that does not expose a raw filesystem path, so that opening never fails due to the platform's file-exposure restrictions.
- **FR-027**: When no app on the device can open the file, the system MUST show a clear localized message and MUST NOT crash.
- **FR-028**: Tapping an entry that is not complete MUST NOT attempt to open anything; the row's state MUST be communicated instead.
- **FR-029**: Tapping an entry whose underlying file no longer exists MUST produce a clear localized message and MUST offer to remove the stale entry.

#### Per-entry actions

- **FR-030**: Long-pressing an entry MUST open an action sheet presenting, in a stable order: open the file, copy the source link, remove the entry from the list, and delete the file.
- **FR-031**: "Remove from list" MUST delete only the list entry; the downloaded file MUST remain on the device.
- **FR-032**: "Delete file" MUST require an explicit confirmation, and on confirmation MUST remove both the file and the list entry.
- **FR-033**: "Copy link" MUST place the original source address on the system clipboard and show a transient confirmation.
- **FR-034**: Actions that cannot apply to an entry in its current state MUST NOT be offered for that entry.
- **FR-034a**: Action availability MUST follow exactly this gating:
  - *Open the file* — offered only when the entry is complete **and** its recorded file is present.
  - *Copy the source link* — always offered, in every state.
  - *Remove the entry from the list* — offered only when the entry has reached a terminal state (complete, failed, cancelled, or missing). Never offered while in flight.
  - *Delete the file* — offered only when the entry's recorded file is present on the device.
- **FR-034b**: Because removal is withheld while a transfer is in flight (FR-034a), no in-flight transfer can be left running without a record that can cancel it. Removing an in-flight download therefore requires cancelling it first (FR-036), and removal MUST NOT itself cancel anything.
- **FR-035**: The action sheet MUST be dismissable by tapping outside it or by the system back gesture, in which case nothing happens.

#### Cancelling

- **FR-036**: The system MUST present a cancel control directly on the row of any transfer that is still in flight, reachable in a single tap without opening the action sheet.
- **FR-036a**: That control MUST be shown only while the entry is in flight, and MUST disappear once the entry reaches any terminal state (complete, failed, cancelled, or missing).
- **FR-036b**: The action sheet MUST remain openable on an in-flight entry, offering only the actions that apply in that state (FR-034). The inline control adds to the action sheet rather than replacing it.
- **FR-037**: Cancelling MUST stop the transfer, clear its system notification, and leave no partial file behind.
- **FR-038**: A cancelled entry MUST never subsequently be shown as still running.
- **FR-039**: A cancel issued against a transfer that has already completed MUST leave the completed file intact and MUST NOT report a false cancellation.

#### Empty state

- **FR-040**: When there are no entries — on a fresh install or after the last one is removed — the system MUST show a localized empty-state composition in place of the list.
- **FR-041**: The empty state MUST be replaced by the list as soon as an entry exists, without the user reopening the screen.

#### Navigation & overflow menu

- **FR-042**: Bookmarks, History and Downloads MUST be reachable from a single overflow affordance on the browser's bottom bar, with localized labels.
- **FR-043**: After this change the bottom bar MUST carry exactly five navigation controls — Back, Forward, Reload/Stop, Home and Tabs — plus the overflow affordance, and MUST render without clipping or overlap at 360dp width.
- **FR-044**: Bookmarks and History MUST reach exactly the screens and behaviour they reached before this change; only the entry point moves.
- **FR-045**: The overflow menu MUST be dismissable by tapping outside it or by the system back gesture, with no side effects.
- **FR-046**: The overflow menu MUST be structured so that a further entry can be added without another redesign (Settings arrives in Spec 016).

#### Notifications

- **FR-047**: The system MUST enable the system download service's own progress and completion notifications, and MUST NOT compose, post, or own any notification of its own in v1.0.
- **FR-048**: The app MUST NOT declare a notification permission it does not actually require; whether the platform requires one for the system service's notifications to appear MUST be determined by verification on a real device before the declaration is settled.

#### Localization & accessibility

- **FR-049**: Every user-facing string introduced by this feature MUST be localized in all 8 supported locales (EN, VI, DE, RU, KO, JA, ZH, FR).
- **FR-050**: File sizes, dates and times MUST be rendered according to the user's locale conventions.
- **FR-051**: All interactive elements — rows, the overflow affordance and its entries, action-sheet items, cancel and confirm controls — MUST expose localized accessibility labels suitable for screen readers.
- **FR-052**: The obsolete placeholder string for the Downloads screen MUST be removed from all 8 locales once the real screen replaces it, leaving no unused string resources behind.

#### Architecture & policy

- **FR-053**: The feature MUST follow the project's Clean Architecture pattern: a downloads repository interface in the domain layer, a single implementation in the data layer, distinct use cases for the observable read path and for each mutation, a feature view-model exposing an immutable state object, and a dedicated one-shot event channel for transient signals.
- **FR-054**: Every interaction with the platform's download service, clipboard, and external-app launching MUST sit behind an abstraction owned by the app, so that the feature's decision logic is testable without a device and the presentation layer holds no platform dependencies.
- **FR-055**: Every call into a platform service MUST be defensively wrapped so that a service that is missing, disabled, or throwing cannot crash the browser. Not crashing is the floor, not the requirement: where the failure blocks something the user asked for, it MUST also be surfaced as a localized message (FR-008a, FR-012, FR-027, FR-029).
- **FR-056**: The feature MUST NOT introduce any user-facing string, dimension, colour, size threshold, address, storage key, or magic number outside the project's existing constants, theme tokens, and string resources (Constitution §III).
- **FR-057**: The feature MUST NOT introduce any new external library dependency.

### Key Entities *(include if feature involves data)*

- **Download Record**: The app's durable record of one download it initiated. Attributes: a unique identifier, the source address the file came from, the filename as written to disk, the determined file type, the creation timestamp, the handle that identifies the transfer to the system download service, and the resolved location of the finished file. Persisted locally; never contains live progress. This is the new entity that drives the schema migration.
- **Download Status**: The live, non-persisted state of a transfer, obtained from the system download service and combined with a Download Record for display. Attributes: a state (pending, running, paused, complete, failed, cancelled, **missing**), bytes transferred so far, total bytes when known, and a failure cause when applicable. Recomputed on each read; never stored. When the service does not recognise the transfer handle, the state is derived instead from whether the recorded file is present — complete if it is, missing if it is not (FR-024a).
- **Downloads List Item**: The presentation-only pairing of a Download Record with its current Download Status, which is what a row on the screen renders. Has no persistent storage.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a fresh install, a user can tap a file link on a web page and see confirmation that the download has started in under 1 second, with no visible interruption to the page they are on.
- **SC-002**: 100% of downloads that the system reports as complete are present in the device's public Downloads folder, fully written and openable by an unrelated app, verified across at least 10 downloads spanning at least 3 different file types.
- **SC-003**: 100% of downloads survive the browser's process being killed mid-transfer — the transfer continues and the entry shows the correct final state on relaunch, verified across at least 5 attempts.
- **SC-004**: A user can locate any previously downloaded file in the Downloads screen within 3 taps from a page they are browsing.
- **SC-005**: On a device running Android 10 or newer, the app requests zero storage permissions across the entire download flow; on Android 7.0–9.0 it requests exactly one, exactly once, and never again after it is granted.
- **SC-006**: The Downloads screen opens and scrolls without ANR or visible jank (frame budget ≤ 16 ms p99) on a Pixel 5-class device with 500 recorded downloads, of which at least 5 are actively transferring.
- **SC-007**: An in-flight download's progress on the Downloads screen visibly advances at least once per second while the screen is open.
- **SC-008**: Cancelling an in-flight download stops the transfer and leaves zero partial files in the Downloads folder, verified across at least 5 cancellations at different points of progress.
- **SC-009**: A database created under the previous schema version upgrades to the new one with 100% of its existing rows intact and readable, verified by an automated test.
- **SC-010**: 100% of user-facing strings on the Downloads screen and the overflow menu are translated in all 8 supported locales, verified by build-time enforcement (no missing or extra translation keys, and no unused keys left behind).
- **SC-011**: On a 360dp-wide device the bottom bar renders all five navigation controls plus the overflow affordance with zero clipping or overlap.
- **SC-012**: APK release size delta versus the Spec 014 baseline of 2.36 MB is within +200 KB.
- **SC-013**: Native library 16 KB page-size alignment remains green: every native library entry in the release build is 16 KB-aligned (Constitution §IX).
- **SC-014**: Constitution Check returns 11/11 PASS both pre- and post-implementation, with the storage-permission deviation recorded and justified rather than silently taken.
- **SC-015**: No download can be written outside the intended Downloads folder, verified against a set of at least 6 hostile server-supplied filenames including path separators and parent-directory references.

## Assumptions

- **A1 — Download detection boundary**: A "download" is a resource the web engine itself declines to render and hands off, which is the same boundary the platform already exposes. This spec does not introduce heuristics of its own for classifying links before they are followed, and does not add a long-press "save link" affordance (see Out of Scope).
- **A2 — Transfer engine**: The actual transfer, retry, and background continuation are the platform download service's responsibility. The browser does not implement its own transfer loop, connection management, or retry policy, and inherits the platform's behaviour for those.
- **A3 — Live status read model**: Live progress is polled or observed from the platform service while the screen is open, and stops being read when it is not. No background service, worker, or persistent observer is introduced to track transfers when no one is looking at them.
- **A4 — Notification ownership**: All notification presentation belongs to the platform. Consequently no notification channel, no notification permission rationale UI, and no notification strings are part of this feature — unless planning discovers the platform requires the app to hold the notification permission for the platform's own notifications to appear, which is an open verification item (FR-048).
- **A5 — Legacy storage permission scope**: The legacy external-storage write permission is requested only on the Android versions that require it, only at the point of first use, and its declaration carries a version ceiling so modern devices never see it. This is the fourth permission in the project and a deliberate, documented deviation from the Constitution's three-permission table.
- **A6 — Downloads from incognito tabs are recorded**: An incognito download is written to the *public* Downloads folder, where it is plainly visible to any file manager and survives the tab. Omitting its row from the browser's own list would therefore conceal nothing while breaking the user's ability to cancel it, find it, or understand where it went. The Constitution's incognito rules enumerate what incognito must not do — write history, retain cookies and cache past tab close — and downloads are not among them, because a download is an explicit, deliberate act of writing a file to shared storage. This spec therefore records incognito downloads like any other. It follows the same reasoning as the Spec 014 Q1 decision that the History screen behaves identically in either context. **Confirmed by the product owner during clarification and promoted to FR-014a; no longer an open assumption.**
- **A7 — Duplicate-name resolution**: Resolving a filename collision is delegated to the platform download service's existing behaviour rather than reimplemented, so long as FR-007's guarantee holds.
- **A8 — No pause and resume**: The user can start and cancel a transfer, but v1.0 exposes no pause or resume control, because the platform service does not offer a dependable user-facing pause. Interruptions and automatic retries remain the platform's business.
- **A9 — No list cap or retention policy**: Download records persist until the user removes them. Unlike history — which Spec 014 had to bound at 90 days after measuring unbounded per-row growth — downloads accrue at a rate governed by deliberate user acts and are orders of magnitude fewer, so no automatic pruning is introduced. A user-configurable policy, if ever wanted, belongs with the other data controls in Spec 016.
- **A10 — Clipboard and one-shot feedback surfaces**: Copying a link and confirming it reuse the abstractions and the one-shot event pattern the project already established in Specs 013 and 014, rather than introducing new mechanisms.
- **A11 — Overflow menu contents**: The menu ships with exactly Bookmarks, History and Downloads. Settings is deliberately not pre-added as a disabled or placeholder entry; Spec 016 adds it when it exists.
- **A12 — Icon availability**: The project's icon set is limited to the core glyph set by standing policy, and it contains no download glyph. A substitute from the available core glyphs is used, and where no honest substitute exists an action carries no icon rather than a misleading one — the precedent set by Specs 013 and 014.
- **A13 — Zero new packages**: The platform download service, media store, clipboard, and external-app launching are all provided by the operating system, and the existing UI, database, and dependency-injection stack covers everything else. No new dependency is expected, and therefore no new native code enters the build.
- **A14 — Performance envelope**: The 500-record figure in SC-006 is taken as a realistic upper bound for a browser's download list, in contrast to the 10,000-row figure used for history in Spec 014, because downloads are created by deliberate user action rather than automatically on every page load.
- **A15 — Pruned records conflate two outcomes**: because a forgotten entry's state is inferred purely from file presence (FR-024a), a download that failed long ago and was subsequently pruned by the system renders identically to one that succeeded and whose file the user later deleted — both appear as missing. This was accepted deliberately in preference to persisting a terminal outcome, which would have required a completion listener capable of running while the app is closed. The user-facing consequence is limited: the remedy offered in both cases is the same, namely removing the stale row.

## Dependencies

- **Spec 002 — Clean Architecture skeleton**: provides the `Downloads` route and the placeholder screen this feature replaces, along with the base view-model, result, and error abstractions.
- **Spec 005 — Room database schema**: provides the database this feature extends, and the strict no-destructive-migration policy that governs how (FR-016, FR-017). This is the first spec to actually exercise that policy.
- **Spec 007 — WebView Compose wrapper**: provides the web engine and the hand-off point at which a resource is recognised as a download rather than a page (FR-001), and the existing crash-safety wrapper around launching external apps.
- **Spec 008 — Navigation controls**: owns the bottom bar this feature restructures (FR-042 – FR-046).
- **Spec 011 — Tabs management**: provides the active-tab context a download is initiated from, and the platform-seam-behind-an-interface pattern (FR-054) that the download service abstraction mirrors.
- **Spec 012 — Private/incognito mode**: provides the per-tab incognito flag consulted by A6, and the defensive posture toward platform services that FR-055 generalises.
- **Spec 013 — Bookmarks CRUD**: provides the action-sheet pattern (FR-030), the confirmation-dialog pattern (FR-032), and the one-shot event channel policy (A10). Loses its direct bottom-bar entry to FR-042.
- **Spec 014 — History view**: provides the clipboard abstraction reused by FR-033 and the list, empty-state and long-press conventions this screen follows. Loses its direct bottom-bar entry to FR-042.

## Out of Scope (v1.0)

- Pausing and resuming transfers (A8).
- A browser-implemented transfer engine, retry policy, or connection manager — the platform service owns all of it (A2).
- A long-press "save link as" or "save image" affordance on page content; only resources the engine hands off as downloads are covered (A1).
- Letting the user choose the destination folder or edit the filename before a download starts.
- A "clear all downloads" action. Bulk data clearing belongs with the other data controls in Spec 016.
- Automatic retention, pruning, or a cap on the number of records (A9).
- App-composed notifications of any kind, including a completion notification with a tap-to-open action (A4).
- Search or filtering within the Downloads list.
- Sharing a downloaded file through the Android share sheet.
- Opening a download from within the browser itself (in-app PDF or image viewer) — files are always handed to an external app.
- Any cloud, sync, or cross-device visibility of downloads — excluded by the project's privacy posture.
- Undo or soft-delete for removing an entry or deleting a file.
- Download-specific settings such as "ask where to save" or "only download on Wi-Fi" — Spec 016 territory if ever wanted.
