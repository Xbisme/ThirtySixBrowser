# Feature Specification: WebView Compose Wrapper

**Feature Branch**: `007-webview-compose-wrapper`
**Created**: 2026-05-01
**Status**: Draft
**Input**: User description: "webview-compose-wrapper: `BrowserWebView` Composable bọc `android.webkit.WebView` qua `AndroidView`. User mở app → load `https://example.com` → render full HTML. Đây là spec đầu tiên có UI thực sự (Phase 2 mở đầu) — wrap WebView lifecycle, JavaScript enable/disable, basic load URL, error/loading state cơ bản. Không bao gồm navigation controls (Spec 008), address bar (Spec 009), tabs (Spec 011) — chỉ một WebView duy nhất load URL hardcoded để verify pipeline."

## Clarifications

### Session 2026-05-01

- Q: Trong v1 Spec 007, cookies do WebView nhận từ server có persist qua app restart không? → A: Persist cookies across app restart (standard browser; `CookieManager.setAcceptCookie(true)` default + no manual flush on close). Clear-data UI handed off to Spec 016, incognito separation handed off to Spec 012.
- Q: Khi web origin request runtime permission (geolocation / camera / microphone), v1 xử lý thế nào? → A: Silently deny all permission requests (`onPermissionRequest.deny()`, `onGeolocationPermissionsShowPrompt` invoked with `allow=false, retain=false`). Manifest stays at 3 permissions (INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS) per Constitution.
- Q: Loading indicator hiển thị thế nào trong v1 khi chưa có address bar? → A: Top linear progress bar — Material3 `LinearProgressIndicator` ở edge trên cùng của BrowserScreen, hiển thị determinate progress 0..1 từ `WebChromeClient.onProgressChanged`, ẩn khi `onPageFinished`. Không che WebView content.
- Q: Mixed content (HTTP-on-HTTPS) policy cho v1? → A: `MIXED_CONTENT_NEVER_ALLOW` — block all HTTP resources on HTTPS pages. Strictest, matches modern browser default + Constitution security posture.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — First Page Renders (Priority: P1)

User mở app lần đầu. Một trang web mặc định (`https://example.com`) tự động load và render đầy đủ HTML + CSS trên màn hình Browser. Người dùng thấy được rằng app thực sự là một trình duyệt — không còn placeholder text.

**Why this priority**: Đây là lần đầu tiên app vượt khỏi placeholder Compose Screen từ Spec 002. Nếu chỉ ship được story này, user vẫn có MVP "open browser → see web page render" — chứng minh toàn bộ pipeline (Activity → Compose → ViewModel → WebView surface → network → HTML render) hoạt động end-to-end. Đây là gate quan trọng nhất cho Phase 2.

**Independent Test**: Cài app debug → mở app → trong vòng vài giây trang `example.com` hiển thị đúng (heading "Example Domain", paragraph mô tả). Verify trên emulator API 29 qua instrumented test.

**Acceptance Scenarios**:

1. **Given** app vừa được cài đặt và launcher icon được tap lần đầu, **When** Browser screen được render, **Then** WebView load trang mặc định và toàn bộ nội dung HTML hiển thị (text + layout) trong vòng tối đa 5 giây trên Wi-Fi.
2. **Given** user xoay device trong khi trang đã load xong, **When** orientation thay đổi (portrait ↔ landscape), **Then** current URL trong `BrowserUiState` được giữ nguyên (no URL change). Full DOM/scroll preservation is deferred to Spec 011 (multi-tab persistence) — for v1 a fresh reload of the same URL is acceptable since the default page is < 1 KB.
3. **Given** user đóng app rồi mở lại, **When** Browser screen được tạo lại, **Then** trang mặc định được load lại từ đầu (chưa có persistence — sẽ thêm ở Spec 011).

---

### User Story 2 — Loading Feedback (Priority: P2)

Trong khi trang đang load (đặc biệt trên kết nối chậm), user thấy được một indicator rõ ràng để biết app đang xử lý chứ không phải đứng hình.

**Why this priority**: Mạng di động ở Việt Nam (target market chính) thường có latency cao. Một blank WebView 3-5 giây trông như crash. P2 vì story P1 vẫn shippable mà không có loading indicator (page eventually renders), nhưng UX kém.

**Independent Test**: Throttle network qua emulator settings xuống 2G → mở app → loading indicator xuất hiện trong vòng 200ms và biến mất khi page render xong.

**Acceptance Scenarios**:

1. **Given** WebView đang load trang, **When** thời gian load > 200ms, **Then** một top-anchored linear progress indicator hiển thị ở edge trên cùng Browser screen với determinate progress (không che WebView content).
2. **Given** trang load xong (`onPageFinished`), **When** WebView idle, **Then** loading indicator biến mất.

---

### User Story 3 — Error State (Priority: P3)

Khi trang không load được (mất mạng, DNS failure, server lỗi), user thấy một thông báo lỗi rõ ràng và đã được dịch sang ngôn ngữ của họ — không phải blank screen hay raw `net::ERR_*` text từ Chromium.

**Why this priority**: Improves trust khi things-go-wrong, nhưng nếu thiếu, user có thể ít nhất đoán được "không có mạng" qua context. P1 và P2 vẫn deliver value khi network OK. P3 hoàn thiện experience khi network fails.

**Independent Test**: Tắt Wi-Fi/cellular trên emulator → mở app → thay vì blank/Chromium error → thấy localized error message kèm icon trong vòng 5 giây.

**Acceptance Scenarios**:

1. **Given** device không có internet, **When** WebView cố load trang mặc định, **Then** thay cho rendered page hoặc Chromium default error, một error state localized hiển thị (text từ `strings.xml`, theo locale hiện tại).
2. **Given** error state đang hiển thị, **When** user toggle Wi-Fi/cellular ON, **Then** **không** auto-retry (manual retry deferred to Spec 008 reload control); user sẽ tự refresh ở các spec tương lai. Lưu ý cho user: error message phải gợi ý hành động "kiểm tra mạng".

---

### Edge Cases

- **Slow network → page partially rendered**: WebView phải vẫn hiển thị partial content; loading indicator vẫn còn miễn `onPageFinished` chưa fire.
- **HTTPS certificate invalid**: WebView default behavior là show error → user sees error state (no override that bypasses cert validation — Constitution security).
- **Mixed content (HTTP image trong HTTPS page)**: Blocked via `MIXED_CONTENT_NEVER_ALLOW` (FR-018); HTTP sub-resources fail silently on HTTPS pages.
- **Configuration change trong khi đang load**: Activity recreated; WebView nên restart load nếu state mất, hoặc restore từ savedInstanceState (preferred).
- **Extremely large page (>10MB HTML)**: Out of scope; default WebView memory limits apply.
- **JavaScript-heavy page**: JS phải execute (default enabled) nhưng KHÔNG có Java/Kotlin bridge expose tới JS (Constitution: `addJavascriptInterface` forbidden).
- **App backgrounded mid-load**: WebView pause khi Activity stops; resume khi return foreground.
- **System Dark Mode toggle while page loaded**: Page chỉ là HTML từ `example.com` — app theme thay đổi nhưng web content không bị forced dark (out of scope cho v1).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST render a single web page from a default URL inside the Browser screen the moment the screen is shown.
- **FR-002**: System MUST use exactly ONE WebView surface in v1 — multi-tab handling is deferred to Spec 011.
- **FR-003**: System MUST display a top-anchored Material3 `LinearProgressIndicator` while a page is loading. The indicator MUST be determinate, driven by `WebChromeClient.onProgressChanged(view, newProgress)` (mapped 0..1), positioned at the top edge of the Browser screen, and MUST NOT overlap or visually cover the WebView content area. The indicator MUST hide when `onPageFinished` fires (or progress reaches 100). When Specs 008/009 introduce a toolbar/address bar, the indicator anchors directly beneath them without structural rewrite.
- **FR-004**: System MUST display a localized, user-friendly error state when a page fails to load (network failure, DNS failure, HTTP error ≥ 400, certificate error). The error UI MUST NOT be a blank screen and MUST NOT show raw `net::ERR_*` strings.
- **FR-005**: System MUST execute JavaScript on loaded pages by default (matching user expectation of a modern browser).
- **FR-006**: System MUST NOT expose any Java/Kotlin object to JavaScript via `addJavascriptInterface` in v1 (Constitution security gate).
- **FR-007**: WebView **URL** in `BrowserUiState` MUST survive device configuration changes (rotation, locale change, dark-mode toggle) — the user does not see the URL revert. Full DOM / scroll / forward-back history preservation is deferred to Spec 011 (`tabs-management`); a fresh page reload after rotation is acceptable for v1 (research R3).
- **FR-008**: WebView lifecycle MUST be tied to the host Activity/Composable lifecycle: pause when not visible, resume when visible, destroy when host is destroyed — no native leaks.
- **FR-009**: All user-visible strings (loading text if any, error message, error retry hint) MUST be defined in `strings.xml` and resolved via `stringResource(R.string.*)`. All 8 supported locales MUST contain the keys (lint enforcement from Spec 004 already in place).
- **FR-010**: The default URL MUST be a named constant in `core/constants/UrlConstants.kt` — no inline string literal in Composable, ViewModel, or anywhere outside `core/constants/`.
- **FR-011**: Browser screen architecture MUST follow Clean Architecture + MVVM:
  - `BrowserScreen` Composable lives under `presentation/browser/`
  - `BrowserViewModel` exposes `StateFlow<BrowserUiState>`; UiState is an immutable `data class`
  - `BrowserWebView` is the inner Composable that wraps `android.webkit.WebView` via `AndroidView`
  - No DAO / DataStore access from ViewModel directly; if persistence needed in this spec, it MUST go through a Repository (none required for v1 of this spec — page state is in-memory only)
- **FR-012**: An instrumented test MUST verify on an Android emulator (API 29+) that opening the Browser screen successfully loads the default URL and the rendered DOM contains an expected element (e.g., text "Example Domain"). Test MUST be added to the `connectedDebugAndroidTest` task.
- **FR-013**: WebView settings MUST disable all four file-access vectors per Constitution §I to reduce attack surface: `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, `setAllowFileAccessFromFileURLs(false)`, `setAllowUniversalAccessFromFileURLs(false)`.
- **FR-014**: System MUST handle WebView destruction without throwing on Composable disposal (cleanup lifecycle observer correctly).
- **FR-015**: Loading and error UI MUST adhere to existing theme tokens (Spec 003): colors via `MaterialTheme.colorScheme.*`, spacing via `Spacing.*`, typography via `MaterialTheme.typography.*`. NO inline `Color(0xFF...)`, no inline `.padding(16.dp)`, no inline `TextStyle(...)`.
- **FR-016**: Cookies received from web origins MUST persist across app process death and restart (`CookieManager.setAcceptCookie(true)` — default behavior, no manual `removeAllCookies` on close). Clear-data UX is deferred to Spec 016 (settings), incognito separation to Spec 012. Cookies are stored locally only — no upload to any ThirtySix-controlled server (Constitution privacy gate).
- **FR-017**: System MUST silently deny all web-origin runtime permission requests in v1. Specifically: `WebChromeClient.onPermissionRequest(request)` MUST call `request.deny()`; `onGeolocationPermissionsShowPrompt(origin, callback)` MUST call `callback.invoke(origin, false, false)` (deny + do not retain). The manifest MUST remain at the Constitution-mandated 3 permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`); no `CAMERA`, `RECORD_AUDIO`, or `ACCESS_FINE_LOCATION` declarations in v1.
- **FR-018**: System MUST set `WebSettings.setMixedContentMode(MIXED_CONTENT_NEVER_ALLOW)` — HTTPS pages MUST NOT load HTTP sub-resources (images, scripts, styles, iframes). Sites that mix protocols will silently fail to render those resources; this is acceptable for v1 and matches modern browser defaults.

### Key Entities

- **BrowserUiState**: Represents the UI snapshot of the Browser screen.
  - `currentUrl: String` — the URL currently loaded or being loaded
  - `loadingState`: one of {Idle, Loading(progress), Loaded, Failed(reason)}
  - `errorMessageRes: Int?` — string resource ID of localized error text when in Failed state
  - Immutable `data class`, mutated via `MutableStateFlow.update { }`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cold-start time from launcher tap to first paint of `example.com` content ≤ 5 seconds on a stock emulator (API 29, Wi-Fi).
- **SC-002**: Loading indicator appears within 200 ms of any page-load start and disappears within 200 ms of `onPageFinished`.
- **SC-003**: When network is unavailable, a localized error UI is visible within 5 seconds of page-load attempt — verified across all 8 supported locales (no missing translation key, no raw `net::ERR_*` text).
- **SC-004**: Rotating the device while page is loaded preserves the current URL in `BrowserUiState` (verified via instrumented test that asserts the URL value is unchanged across configuration change). A fresh content reload after rotation is acceptable for v1 — see research R3.
- **SC-005**: 100% pass rate of the new instrumented test on emulator API 29 in CI's `instrumented-test` job. PR is blocked if the test fails.
- **SC-006**: Zero `addJavascriptInterface` call sites in the codebase — verified via static check (`grep -r "addJavascriptInterface" app/src/main/`).
- **SC-007**: Zero hardcoded URL string literals in `presentation/` or `domain/` packages — all URLs sourced from `core/constants/UrlConstants.kt`. Verified via `grep`.
- **SC-008**: APK release size delta ≤ 200 KB compared to Spec 006 baseline (1.56 MB) — Compose-based wrapper should add minimal weight; system WebView is provided by OS, not bundled.
- **SC-009**: 16 KB native-lib alignment CI gate continues to pass (no new `.so` from this spec; system WebView is OS-provided).

## Assumptions

- **Default URL is `https://example.com`** for v1 only — chosen because the page is stable, lightweight, has well-known content for assertion in instrumented tests, and is acceptable for documentation/demos. The constant will be replaced by the user-entered URL flow in Spec 009 (address bar) and home-page setting in Spec 016 (settings screen).
- **JavaScript enabled by default** — modern browsers do; disabling would break majority of websites. The `addJavascriptInterface` ban is independent: JS runs, but no Kotlin/Java objects are exposed to it.
- **No automatic retry on error** — manual reload control is Spec 008's responsibility. The error UI in this spec will display a hint to the user (e.g., "Check your connection") but no Retry button.
- **WebView disk cache uses Android default** (`LOAD_DEFAULT` cache mode) — privacy-conscious users will get a "Clear data" option in Spec 016 (settings screen). For v1 pipeline verification this is acceptable.
- **Cookies persist across app restart** (clarified 2026-05-01) — standard browser behavior. All persistence is on-device only; cookie data never leaves the device. Spec 012 introduces a separate cookie/storage profile for incognito tabs, and Spec 016 adds an explicit "Clear cookies / Clear all browsing data" UI.
- **Web-origin runtime permissions denied silently in v1** (clarified 2026-05-01) — manifest stays at 3 Constitution permissions. Granting geolocation/camera/microphone to web origins is out of scope for v1.0; if added later, it requires a dedicated spec (manifest declaration + Android runtime permission flow + per-origin allow-list UI). Until then, websites that request these will be told no without user interaction.
- **No download handling** — file downloads triggered by clicked links are out of scope (Spec 015 will introduce DownloadManager integration). For v1, clicked download links are simply ignored or rejected.
- **No multi-tab persistence** — WebView state is in-memory only; closing the app discards page state. Spec 011 (`tabs-management`) introduces persistence.
- **No incognito mode separation** — single WebView shares a single cookie jar/cache. Spec 012 introduces incognito.
- **Android system WebView is acceptable as the engine** — Constitution mandates `android.webkit.WebView` only (no GeckoView, no Custom Tabs). Version follows the OS's WebView component.
- **No navigation buttons (Back/Forward/Reload/Home)** — those land in Spec 008. v1 of this spec is "open app, see one page, that's it."
- **No address bar** — v1 has no UI for the user to type a URL. Spec 009 introduces the omnibox.
- **`instrumented-test` CI job is already re-enabled** in `.github/workflows/ci.yml` (uncommitted change carried into this branch from `main`). The first real instrumented test from this spec validates that the workaround for the previously-disabled job works on real CI infrastructure.
- **No analytics, no crash reporting, no telemetry** added (Constitution privacy gate).
- **Target device floor**: minSdk 24 (Android 7.0). System WebView APIs used in this spec are available since API 24.
