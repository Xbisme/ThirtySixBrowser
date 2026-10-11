# Research: Web Capabilities (Spec 021)

Checked against `main` at `1e96030` (2026-10-11). Today `BrowserChromeClient` denies every
permission (`onPermissionRequest` → `deny()`, geolocation → `invoke(origin, false, false)`), and
nothing implements file choosing, custom views, windows, JS dialogs, HTTP auth or long-press.

## R1 — Where page requests go: one queue, one host

**Decision**: Every request a page makes that needs the person — permission, file chooser, JS
dialog, HTTP auth, blocked/opened window, long-press menu — becomes a `PageRequest` pushed from
the WebView clients into a `PageRequestQueue` (presentation, per `BrowserScreen`). A single
`PageRequestHost` composable renders the head of the queue (one dialog/sheet at a time, FR-035)
and answers the platform callback held inside the request.

Each request carries the **tab id** and a **page generation** number. The generation advances on
every new main-frame navigation (`onPageStarted` for a new URL) and on tab switch. Requests
whose tab/generation no longer match are answered as refused/cancelled and dropped (FR-034).

**Rationale**: The platform callbacks (`PermissionRequest`, `ValueCallback<Array<Uri>>`,
`JsResult`, `HttpAuthHandler`, `GeolocationPermissions.Callback`) must each be answered exactly
once, on the main thread; centralising them makes "exactly once" and "drop when stale" one
mechanism instead of six, and keeps the WebView clients free of UI.

**Alternatives considered**: putting each flow in `BrowserViewModel` — rejected: platform
callback objects hold Android types and must not live in a ViewModel (§IV), and the view model
is already the largest in the app.

## R2 — Site permission decisions

**Decision**:
- Pure domain rule `ResolveSitePermissionUseCase(origin, capabilities, isIncognito, isSecure)`
  → `Grant(all)`, `Deny(all)` or `Ask(capabilities)`:
  1. not secure (scheme ≠ https) → `Deny` (FR-007);
  2. incognito → only one-time grants of the current page count; otherwise `Ask` (FR-004);
  3. normal → for each capability: remembered `Allow` → granted, remembered `Deny` → refused,
     one-time grant for this tab+page → granted, else ask. If any capability is refused → `Deny`
     (the page gets a single answer for a combined request, FR-005); if all granted → `Grant`;
     else `Ask(the undecided ones)`.
- **Remembered** decisions: Room table `site_permissions` (schema **v3**, R8).
- **One-time** grants ("Allow this time"): in-memory `OneTimeGrantStore` keyed by
  (tab id, page generation, origin, capability); cleared when the page generation changes, the
  tab closes, or the process ends (FR-003).
- Mapping platform → capability: `PermissionRequest.RESOURCE_VIDEO_CAPTURE` → camera,
  `RESOURCE_AUDIO_CAPTURE` → microphone, geolocation prompt → location. Any other resource in the
  request (`RESOURCE_PROTECTED_MEDIA_ID`, `RESOURCE_MIDI_SYSEX`, …) → the whole request is denied
  (FR-008).
- Android runtime permissions after the person allows: camera → `CAMERA`, microphone →
  `RECORD_AUDIO`, location → `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` requested
  together (Android 12+ lets the person pick approximate; either grant counts — edge case
  "location precision"). Requested through `rememberLauncherForActivityResult(
  RequestMultiplePermissions())`. **Permanently denied** = denied and
  `shouldShowRequestPermissionRationale` false afterwards → explanation dialog with "Open
  settings" (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) (FR-006). Same pattern as Spec 015's
  `DownloadPermissionGate`.
- Granting to the page: `PermissionRequest.grant(resources)`; geolocation
  `callback.invoke(origin, allow, retain = false)` — **retain is always false**: the app's own
  store is the single source of truth, never WebView's internal geolocation store.

**Rationale**: a pure rule makes every row of the decision table a JVM test (§VI) and keeps
"incognito never remembers" structural rather than conditional UI code.

## R3 — File chooser and capture

**Decision**: `onShowFileChooser(view, callback, params)`:
- Build the picker from `params.createIntent()` (respects accept types) with
  `EXTRA_ALLOW_MULTIPLE` when `params.mode == MODE_OPEN_MULTIPLE`.
- When `params.isCaptureEnabled` or the accept types are only image/video: wrap in a chooser
  that also offers `MediaStore.ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE` writing to a
  `FileProvider` URI under `cacheDir/capture/`.
- ⚠️ Because the app now declares `CAMERA`, Android requires that permission to be **granted**
  before `ACTION_IMAGE_CAPTURE` may be used by this app. So capture is offered only after the
  camera permission is granted; if it is not, ask at that moment, and on refusal offer the
  picker alone (FR-013).
- Result: `FileChooserParams.parseResult(resultCode, data)` for picker results; the capture URI
  for camera results; `null` on cancel (FR-012). The callback is always answered, including when
  no activity can handle the intent.
- Captured files in `cacheDir/capture/` are deleted when the page generation changes.

**New manifest entries**: a `FileProvider` (`androidx.core` — already a dependency) with
`res/xml/file_paths.xml` exposing only `cache/capture/`.

## R4 — Fullscreen

**Decision**: `onShowCustomView(view, callback)` hands the view to a `FullscreenController`
state; `BrowserScreen` renders it in a full-window `AndroidView` above everything, hides system
bars with `WindowInsetsControllerCompat` (behaviour: show transiently on swipe, FR-014), and sets
`requestedOrientation = SCREEN_ORIENTATION_FULL_SENSOR`. `onHideCustomView` (or Back, FR-016)
removes the view, restores bars and `SCREEN_ORIENTATION_UNSPECIFIED`, and calls
`callback.onCustomViewHidden()`.

**Orientation — spec amended (FR-015)**: the app cannot learn a video's aspect ratio without
running script in the page, which Constitution v1.5.0 §II forbids outside bundled privacy
scripts. `FULL_SENSOR` follows the phone's physical orientation **even when auto-rotate is off**,
so turning the phone shows a wide video in landscape and a portrait video stays portrait — the
behaviour FR-015 wants, driven by the person instead of a guess. FR-015 and US3-2 are amended in
the same change.

**Rotation must not recreate the activity**: `MainActivity` today recreates on rotation (only
`locale|layoutDirection|screenLayout` are handled), which would destroy the WebView and the
fullscreen view mid-video. `configChanges` gains
`orientation|screenSize|smallestScreenSize|keyboardHidden`. Compose re-lays out from
`LocalConfiguration`; the WebView survives rotation everywhere, which also fixes the
long-standing reload-on-rotate behaviour (Spec 007 R3). Spec 018's slide index and Spec 020's
saved request remain correct for the remaining recreation paths (process death).

**Incognito**: `FLAG_SECURE` is a window flag (Spec 012), so the fullscreen overlay inside the
same window is covered (FR-017).

## R5 — New windows and pop-ups

**Decision**: `settings.setSupportMultipleWindows(true)` and
`javaScriptCanOpenWindowsAutomatically = true`, so **every** window attempt reaches
`onCreateWindow(view, isDialog, isUserGesture, resultMsg)` and the app decides:
- `isUserGesture` → create a **new WebView**, hand it to the page through
  `WebViewTransport.webView`, register it in a `PopupWebViewRegistry` under the id of a new tab
  created for it (incognito if the opener is), and make that tab active. `BrowserWebView`, when
  composing a tab id the registry holds, **adopts** that WebView instead of building one — the
  window keeps its link to the opener (FR-025, sign-in flows). It is released like any WebView
  when the person switches away (the app's existing one-WebView-per-screen model, Spec 011).
- not a user gesture → return `false` (blocked). To offer **Open** (FR-026a) the app needs the
  pop-up's address: a throw-away WebView is attached through the transport, its first
  navigation is intercepted in `shouldOverrideUrlLoading` / `onPageStarted`, the address is
  captured and the WebView destroyed without loading. Blank or unsafe addresses → notice
  without Open.
- `onCloseWindow(window)` on an adopted pop-up WebView → close its tab and activate the opener
  tab if it still exists (FR-027). Tabs not created by `onCreateWindow` never receive it.
- Tab cap (Spec 011 / 012) checked before creating; at the cap → existing message, `false`.

**Alternatives considered**: capturing the address and loading it in an ordinary tab for user-
gesture pop-ups too — rejected: it breaks `window.opener` / `postMessage`, which is exactly what
"Sign in with Google" pop-ups use; FR-025's acceptance scenario would fail.

## R6 — JS dialogs and HTTP auth

**Decision**:
- `onJsAlert` / `onJsConfirm` / `onJsPrompt` / `onJsBeforeUnload` return `true` and enqueue a
  `PageRequest.JsDialog` carrying the `JsResult`/`JsPromptResult`; the host answers
  `confirm()` / `confirm(text)` / `cancel()` (FR-029, FR-030).
- Flood control: a per-tab `DialogSuppression` counts dialogs in the current page generation;
  from the 2nd the dialog shows the "Don't let this site show more dialogs" checkbox; once set,
  later dialogs are `cancel()`-ed without showing until the generation changes (FR-031).
- `onReceivedHttpAuthRequest(view, handler, host, realm)` → `PageRequest.HttpAuth`; Sign in →
  `handler.proceed(user, password)`, Cancel/Back → `handler.cancel()`. Never calls
  `setHttpAuthUsernamePassword` / `WebViewDatabase` storage, and `useHttpAuthUsernamePassword()`
  is ignored; the typed strings live only in the dialog's state and are cleared on dismiss
  (FR-032, SC-007).

## R7 — Long-press menu

**Decision**: `WebView.setOnLongClickListener` reads `hitTestResult`:
- `SRC_ANCHOR_TYPE` → link menu (extra = link URL);
- `IMAGE_TYPE` → image menu (extra = image URL);
- `SRC_IMAGE_ANCHOR_TYPE` → extra is the image URL; the link URL comes from
  `requestFocusNodeHref(Message)` (async, delivered to a main-thread `Handler`); both groups.
- anything else → return `false` so text selection keeps working (edge case).

The menu content is built by a pure `BuildLongPressMenuUseCase(type, linkUrl, imageUrl)` (JVM-
tested): action order per FR-018/FR-019, only Copy for non-http(s) addresses (FR-023).
Actions reuse existing pieces: background tab creation (R9), incognito tab creation (Spec 012),
`StartDownloadUseCase` (Spec 015), `ClipboardWriter` (Spec 014), `Intent.ACTION_SEND` chooser.

## R8 — Storage: Room v3

**Decision**: new entity `SitePermissionEntity(origin TEXT, capability TEXT, decision TEXT,
decidedAt INTEGER)`, primary key `(origin, capability)`. `AppDatabase.SCHEMA_VERSION` 2 → 3 with
an explicit additive `MIGRATION_2_3` (creates `site_permissions` only; names no existing table),
`3.json` exported and committed; `fallbackToDestructiveMigration` stays at zero call sites.
Migration test in the Spec 015 style (real `Room.databaseBuilder` on the JVM).

`SitePermissionRepository` (domain) / `SitePermissionRepositoryImpl` (data) / `SitePermissionDao`.
Clear browsing data gains `ClearBrowsingDataCategory.SitePermissions`, unticked by default
(FR-010a); `ClearBrowsingDataUseCase` calls `SitePermissionRepository.clearAll()` and
`OneTimeGrantStore.clear()` when it is selected.

## R9 — Background tabs

**Decision**: `TabRepository` and `IncognitoTabRepository` gain
`suspend fun createBackgroundTab(url: String): Result<Tab>` **with a default body** in the
interface that delegates to `createTab(url)`. Production implementations override it to insert
with `lastActiveAt = currentActive.lastActiveAt - 1` (normal) / the in-memory equivalent
(incognito), so the active tab does not change and the new tab sits second in recency order.
The default body keeps the six existing test doubles compiling unchanged (the Spec 018 lesson:
one new interface member broke four fakes).

## R10 — Manifest and Play declarations

**Decision**:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.microphone" android:required="false" />
<uses-feature android:name="android.hardware.location" android:required="false" />
```
The `uses-feature … required="false"` lines are mandatory: declaring `CAMERA` alone makes Play
treat a camera as **required** and hide the app from devices without one.

⚠️ **To verify at implementation**: WebView audio capture (WebRTC) has historically needed
`MODIFY_AUDIO_SETTINGS` (a normal, install-time permission). If the A55 microphone gate fails
without it, it is added with a Constitution table row and a note — not silently.

Constitution v1.5.0 permission table gets rows for CAMERA, RECORD_AUDIO, ACCESS_FINE/COARSE
_LOCATION (FR-037), as its own note anticipated.

## R11 — Dependencies and size

**Decision**: **no new dependency.** All APIs are platform (`WebChromeClient`, `WebViewClient`,
`PermissionRequest`, `FileProvider` from `androidx.core`, `WindowInsetsControllerCompat` from
`androidx.core`, activity result contracts from `activity-compose`, Room already present).
16 KB unaffected. Expected APK delta well under SC-009's 150 KB.

## R12 — Privacy

**Decision**: no new log line carries an origin, URL, file name or credential (FR-036). The
pre-existing `FaviconCache` URL log (noted in Spec 020 R11) is still out of scope.
