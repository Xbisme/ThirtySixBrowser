# ThirtySixBrowser — Lộ Trình Spec (SDD Roadmap)

> Cập nhật lần cuối: 2026-10-13 (Spec 024 implemented trên branch, chưa mở PR)
> v1.0: Android Native Browser — ✅ Specs 001–016 done (013, 014, 015, 016 đều đã merge vào `main`; 016 qua PR #18, merge commit `3083941`, 2026-09-12). **Phase 3 3/3 · Phase 4 1/3 merged + 017 đã implement** (Spec 016 ở 112/113 — còn TalkBack pass của T109, cần người thật). ✅ **017 `splash-screen` merged 2026-09-15 — PR #20, merge commit `cca5c0c`, CI xanh 6/6, 35/35 tasks.** **Phase 4 giờ 3/3 — 🎉 v1.0 FEATURE-COMPLETE.** ✅ **018 `onboarding-flow` merged 2026-09-15 — PR #24, merge commit `492e4f3`, CI xanh 6/6, 41/41 tasks. Specs 001–018 đều đã merge vào `main`.** Sau khi merge 018, Specs 001–018 đủ; còn lại là các mục nợ trước khi nộp store (số đo phần cứng Spec 014/015, screen-reader pass Spec 016/017/018).

## Nguyên tắc Roadmap

**Spec-driven**: Mỗi spec hoàn thành → mở app lên → test được ngay.
Tách theo trải nghiệm người dùng tích lũy, không phải theo technical component.

## Dependency Graph

```
Constitution
  │
  └── Phase 1: Foundation
        │
        ├── 001 project-init-build-config
        │     │
        │     └── 002 clean-architecture-skeleton-di
        │           │
        │           ├── 003 theme-typography-darkmode
        │           ├── 004 localization-multi-language
        │           ├── 005 room-database-schema
        │           └── 006 datastore-settings
        │
        └── Phase 2: Core Browser
              │
              ├── 007 webview-compose-wrapper
              │     │
              │     ├── 008 navigation-controls
              │     │     │
              │     │     ├── 009 address-bar-omnibox
              │     │     │     │
              │     │     │     └── 010 search-engine-google
              │     │     │
              │     │     └── 011 tabs-management
              │     │           │
              │     │           └── 012 private-incognito-mode
              │     │
              │     └── Phase 3: Data Features
              │           │
              │           ├── 013 bookmarks-crud
              │           ├── 014 history-view
              │           └── 015 downloads-manager
              │
              └── Phase 4: Settings & Polish
                    │
                    ├── 016 settings-screen
                    ├── 017 splash-screen
                    └── 018 onboarding-flow
                          │
                          └── Phase 5
                                │
                                └── 019 ui-redesign (design system trước, rồi mới specify)
                                      │
                                      └── Phase 6 "Trình duyệt thật" (thứ tự bắt buộc)
                                            │
                                            ├── 020 default-browser-entry-points
                                            ├── 021 web-capabilities
                                            ├── 022 page-tools
                                            ├── 023 privacy-protection
                                            └── 024 privacy-tools
```

## Spec List (v1.0)

| # | Tên | User Story chính | Test được gì? | Dependencies | Status |
|---|-----|-----------------|---------------|-------------|--------|
| — | Constitution | Nguyên tắc nền tảng v1.5.0 (§V Compose Phase Discipline; §X Phase 5 = 019 redesign, Phase 6 = 020–023 "trình duyệt thật"; §II chỉ cho tiêm script đóng gói sẵn, một chiều) | — | — | ✅ Done 2026-05-01 · amended 2026-10-11 |
| 001 | `project-init-build-config` | Setup Gradle Kotlin DSL + version catalog + 16KB-ready | `./gradlew assembleDebug` pass, lint clean, `.so` align 16KB | Constitution | ✅ Done 2026-05-01 |
| 002 | `clean-architecture-skeleton-di` | Module structure + Hilt + base classes | App build + Hilt graph valid | 001 | ✅ Done 2026-05-01 |
| 003 | `theme-typography-darkmode` | Material3 + light/dark/system | Toggle theme → app re-compose đúng | 002 | ✅ Done 2026-05-01 |
| 004 | `localization-multi-language` | 8 locales (EN/VI/DE/RU/KO/JA/ZH/FR) | Đổi locale → string thay đổi | 002 | ✅ Done 2026-05-01 |
| 005 | `room-database-schema` | Bookmark/BookmarkFolder/History/Tab entities + DAOs + WAL + DB excluded from backup | Insert/query/observer + FK orphan-to-root + WAL concurrency tests pass | 002 | ✅ Done 2026-05-01 |
| 006 | `datastore-settings` | DataStore Preferences cho settings | Read/write settings persist qua restart | 002 | ✅ Done 2026-05-01 |
| 007 | `webview-compose-wrapper` | `BrowserWebView` Composable + `LinearProgressIndicator` + localized error UI | Load `https://example.com` → render; throttled-load shows top progress bar; airplane-mode shows localized error | 005, 006 | ✅ Done 2026-05-01 — instrumented-test CI job re-enabled; Espresso-Web + Hilt URL injection wired; **T042 manual UX gate verified on device 2026-05-01** |
| 008 | `navigation-controls` | Back/Forward/Reload/Stop/Home + predictive back | Bấm back/forward → URL thay đổi; system back gesture honor history; predictive preview Android 14+ | 007 | ✅ Implemented 2026-05-01 — 41/51 tasks; 102/102 unit tests pass; APK 1.67 MB; 16KB green; 4 instrumented integration tests + 3 manual gates deferred (mirrors Spec 007 T042 pattern) |
| 009 | `address-bar-omnibox` | TextField nhập URL/query + suggest | Nhập URL → load; nhập query → search | 008 | ✅ Done 2026-05-03 — 143/143 unit tests; APK 2.05 MB; 9 manual user-device gates verified |
| 010 | `search-engine-google` | Build search URL via `SearchEngineRepository` | Nhập "android" → Google; flip engine via Settings → DuckDuckGo / Bing | 009 | ✅ Done 2026-05-03 — 162/162 unit tests; APK 2.0 MB (R8 -50 KB delta); 16KB ✅; Constitution 11/11; 5 manual gates verified |
| 011 | `tabs-management` | Multi-tab grid switcher + persist | Tạo/đóng/chuyển 3 tabs, kill app, mở lại còn nguyên | 007 | ✅ Done 2026-05-03 — 64/64 tasks; 201/201 unit tests; APK 2.1 MB; 5 manual gates verified |
| 012 | `private-incognito-mode` | Tab incognito riêng biệt | Mở incognito → cookie/history không lưu | 011 | ✅ Done 2026-05-03 — PR #13 merged into `main`; APK 2.1 MB (no delta); 16KB ✅; Constitution 11/11 PASS; documented §IV exception for `IncognitoTabRepositoryImpl` reading from `TabRepository` for cookie origin enumeration |
| 013 | `bookmarks-crud` | Star icon save current page + bookmarks bottom-bar entry → list/folder browse + add/edit/delete + nested folders + global search | Long-press bookmark → action sheet; tap folder → into folder; breadcrumb tap → jump; cascade delete confirm | 005, 007, 011, 012 | ✅ Done 2026-05-07 — PR #14 merged into `main` |
| 014 | `history-view` | Auto-record + group theo ngày + search + per-entry actions + clear-all | Visit 3 URLs → list theo ngày → tap mở lại; long-press → action sheet; clear all | 005, 007, 011, 012 | ✅ **Done 2026-09-10 — PR #15 merged into `main`** (commit `2e7c633`) — **111/113 tasks** (US1–US5 + Polish); chỉ còn T103b (SC-005 p99 ≤ 16 ms, cần release build trên máy thật cỡ Pixel 5 — deferred, flagged trong PR body). 379/379 unit tests pass (+34 Spec 014 US2–US4 + pass-3 regression cases: HistoryViewModelTest 23 / DeleteHistoryEntryUseCaseTest 3 / ClearAllHistoryUseCaseTest 2); lintDebug ✅ · detekt ✅ · ktlintCheck ✅ · assembleRelease ✅ · 16KB ✅ (all `.so` `align=0x4000`); APK **2.36 MB** (-20 KB vs Spec 013 baseline 2.38 MB; SC-008 ≤ +200 KB budget); Constitution 11/11 PASS. Build-time tool `desugar_jdk_libs` 2.1.5 for `java.time` at minSdk 24; icon-set pivots to core-only glyphs (`DateRange` / `Add` / `Delete`). 4 instrumented test files written + compile-verified (not executed — no device). **Pass 3**: emulator run (API 36) found and fixed a real FR-001/FR-003 recorder defect — `onLoadFinished`'s `(loadingState, currentUrl)` guard misfired against Chromium's real callback order, suppressing every genuine write while letting failed loads through; replaced with an explicit per-navigation token, plus title back-fill via new `updateTitle` repo/DAO/use-case. `connectedDebugAndroidTest` 59/60 (one pre-existing Spec 007 flake, reproduces on `main`). **Pass 4**: G2/G4/G5/G6/G7/G8 all run on emulators; found + fixed a second defect — day-bucketing 10 K rows ran on the UI thread (`viewModelScope` = Main), typing p99 200 ms → 53 ms via `flowOn(dispatchers.default)`; added **90-day retention** (`MAX_HISTORY_DAYS`, `PruneOldHistoryUseCase`, swept at app start) after measuring ~300 B/row — Paging 3 rejected because SQL `LIKE` would break FR-013 literal matching. Retention verified on the **API 24** AVD (201 → 91 rows). SC-005 still undemonstrated (needs release build on real hardware). **Pass 5**: full `connectedDebugAndroidTest` **60/60** ×3 on the API 24 (minSdk) AVD — closing T102 required fixing a pre-existing Spec 007 flake (`loadingIndicator_appearsAndHidesOnFinish`, order-dependent on a warm WebView cache; reproduces on `main`). Only T103b (SC-005 on real hardware) + T111 (PR) remain. **Pass 6**: the pass-5 flake fix was insufficient (passed locally, still failed CI API 29); properly fixed by seeding `LoadingState.Failed` before `setContent` so `BrowserWebView` never loads and cannot race the assertion — new `BrowserScreenLoadingIndicatorTest`, full suite **61/61 ×3** under CI's own `swiftshader_indirect` flags, with the failure first reproduced on `main` locally |
| 015 | `downloads-manager` | DownloadManager integration + list | Download file → notification → mở | 007 | ✅ **Done 2026-09-11 — PR #16 merged vào `main`** (commit `e925f9d`, CI xanh cả 6 job) — **121/121 tasks** (mô tả PR #16 đã được viết lại sau merge để có đủ nội dung T121 yêu cầu). testDebugUnitTest ✅ **480/480** · lintDebug ✅ · detekt ✅ baseline UNCHANGED · ktlintCheck ✅ · assembleRelease ✅ · 16KB ✅ · APK **2.44 MB** (+80 KB, trong ngân sách) · **zero new dependencies**. ⚠️ **Migration schema đầu tiên của project** (v1→v2, additive, `2.json` đã commit). Nguồn sự thật hybrid: Room giữ identity/metadata, platform giữ trạng thái live, **join ở tầng use case** (§IV). Bottom bar 7 nút → 5 + overflow. ✅ **G9 = Outcome A**: dịch vụ tải của hệ thống tự thông báo dưới danh tính riêng nên **không khai** `POST_NOTIFICATIONS`; bảng permission trong Constitution đã sửa theo. `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` là deviation duy nhất. **3 lượt test trên máy (API 24 minSdk + API 36) tìm ra 4 lỗi thật, đã sửa hết**: "Unknown size"; "File deleted" khi file vẫn còn; fallback FR-024a dò content URI của provider — URI chết đúng lúc cần nên nhánh "còn file → Complete" **không bao giờ chạy được**; và FR-029 thiếu hẳn nút gỡ dòng rác. ⚠️ **SC-006 DEFERRED** — cần đo **release build trên máy thật cỡ Pixel 5**, không báo cáo số đo debug/emulator. |
| 016 | `settings-screen` | Theme/language/search engine/clear data | Đổi setting → áp dụng ngay + persist | 003, 004, 006 | ✅ **Done 2026-09-12 — PR #18 đã merge vào `main`** (merge commit `3083941`, CI xanh), **112/113 tasks**. Theme · dynamic color · ngôn ngữ app qua AndroidX per-app API (`MainActivity` → `AppCompatActivity`) · search engine · retention 7/30/90/180 ngày · clear browsing data kiểu Chrome (androidx.webkit, nhánh Complete/fallback) · About. testDebugUnitTest ✅ 557/557 · instrumented ✅ 109/109 (API 29 + AVD 16 KB API 36) · lint/detekt/ktlint ✅ · 16KB ✅ · APK 3,122,115 B (≤ 3,258,091 B) · appcompat **1.8.0** + webkit **1.17.0**, zero `.so`. Gate G1–G10 và G12 đạt, gồm Constitution Gate 8 trên AVD 16 KB; G11 đạt phần 8 locale, **TalkBack pass DEFERRED** (cần người thật). Sửa thêm: mất renderer không còn làm sập app; `HistorySeeder` hết ANR. |
| 017 | `splash-screen` | Splash API + branding | Cold start → splash → main | 002 | ✅ **Done 2026-09-15 — PR #20 merged (`cca5c0c`), 35/35 tasks.** `androidx.core:core-splashscreen` **1.2.0** (zero `.so`, verify bằng cách giải nén AAR). Thay **toàn bộ** artwork template Android Studio bằng mark ThirtySix (đĩa gradient + "36" tô đặc): vector tĩnh, vector động `drawable-v31/`, monochrome, 5 bitmap density, xoá hẳn biến thể round. testDebugUnitTest ✅ 557/557 · instrumented ✅ 109/109 trên AVD 16 KB API 36 · lint/detekt/ktlint ✅ · 16 KB ✅ 8 `.so` zero mới · APK **3,126,212 B** (+4,097 B, chỉ dùng 2% ngân sách 204,800 B) · cold start **−1,4%** (575 ms vs 583 ms). ⚠️ **Phát hiện quan trọng: theme splash KHÔNG được dùng parent AppCompat** — thư viện kế thừa `Theme.DeviceDefault` theo thiết kế; an toàn đến từ `postSplashScreenTheme` + thứ tự gọi `installSplashScreen()` trước `super.onCreate()` (research.md R3, chứng minh bằng bytecode). Còn lại: G3 + 3 phần gate deferred (screenrecord lỗi encoder trên AVD; TalkBack không script được) và PR. |
| 018 | `onboarding-flow` | 4 slide: chào + cam kết riêng tư · ngôn ngữ · theme · search engine | Lần đầu mở → onboarding → main; Skip mọi slide; đổi ngôn ngữ vẫn giữ đúng slide | 003, 004, 006, 017 | ✅ **Done 2026-09-15 — PR #24 merged (`492e4f3`), 41/41 tasks.** Không thêm dependency; Room vẫn v2. unit ✅ 579/579 (+22) · instrumented ✅ 109/109 trên AVD 16 KB · lint/detekt/ktlint ✅ · 16KB ✅ · APK **3,134,976 B** (+8,764 B, dùng 4,3% ngân sách) · **G1–G12 đều PASS**, gồm 4 gate blocking. ⚠️ Hai bẫy kỹ thuật: đọc cờ từ settings *flow* trong `setContent` sẽ đưa **mọi** lần mở vào onboarding (vì `NavHost` chốt `startDestination` ở lần compose đầu); và giữ chỉ số slide trong field ViewModel thường thì **sống qua xoay, chết khi đổi ngôn ngữ**. Còn lại: screen-reader pass (cần người) + PR. |
| 019 | `ui-redesign` | Đập đi xây lại toàn bộ giao diện theo [design system ThirtySix](https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys) (tím thay teal) + Compose Phase Discipline (Constitution v1.4.0 §V) | Mọi màn theo design system; chrome không recompose theo từng frame khi cuộn/tải/kéo | 001–018, design đã duyệt | ✅ **Done 2026-10-10 — PR #27 merged (`54e090a`, CI 6/6).** Chạy trên máy thật Galaxy A55 (2026-10-11, release): **SC-005 PASS** (p95 14/14/15/10 ms), Tabs 10 thẻ 104 ms, thẻ mới → Home ≈57 ms; sửa lỗi ô "Suchmaschine" bị ngắt giữa từ ở 360dp. ⚠️ Cold start lần đầu (onboarding) +30% so với Spec 018 — vượt ngưỡng +5% của SC-010, còn mở; người dùng quay lại +4,2% (đạt). Còn cần người: Layout Inspector, TalkBack, so canvas, dynamic color |
| 020 | `default-browser-entry-points` | Intent filter http/https `VIEW` + `BROWSABLE`; nhận link/text chia sẻ từ app khác; đề nghị đặt làm trình duyệt mặc định (`RoleManager` API 29+, màn cài đặt hệ thống dưới đó) | Bấm link trong app khác → mở bằng ThirtySix; đặt làm mặc định được | 001–019 | ✅ **Done 2026-10-11 — PR #30 merged (`1bb8c6a`, CI 6/6), 65/65.** Unit 688/688, instrumented 110/110 (AVD 16 KB), APK +28.480 B, không thêm thư viện/quyền. Gate G1–G18 đạt (emulator + Galaxy A55: mở link 209,5 ms cold / 97 ms warm). Phát hiện + sửa lỗi `ShowBrowser` dựng lại màn browser |
| 021 | `web-capabilities` | Chọn file khi upload · video toàn màn hình · popup/`window.open` · hộp thoại JS + HTTP auth · hỏi quyền camera/mic/vị trí theo từng trang (nhớ theo origin) · menu nhấn giữ link/ảnh | Upload ảnh, YouTube fullscreen, đăng nhập popup, Google Meet đều chạy | 020 | ✅ **Done 2026-10-11 — PR #32 merged (`ef9ae68`, CI 6/6), 70/70.** Unit 757/757; instrumented 132/135 (3 lỗi đã giải thích: 2 flake do tải, 1 lỗi môi trường AVD có sẵn từ Spec 019); APK 3.359.236 B (+95.592 B, ngân sách +150 KB); Room v3 (`site_permissions`); thêm 4 quyền nguy hiểm + `MODIFY_AUDIO_SETTINGS` (Constitution 1.5.1). Gate G1–G20 + SC-001 10/10 trên Galaxy A55. 6 lỗi tìm ra khi test, đã sửa (xem tasks.md) |
| 022 | `page-tools` | Tìm trong trang · chia sẻ · chế độ máy tính · cỡ chữ · in/lưu PDF · gợi ý khi gõ từ lịch sử + bookmark | Mỗi công cụ dùng được từ hub | 021 | ✅ **Done 2026-10-11 — PR #35 merged (`d0908a1`, CI 6/6), 68/68.** Unit 808/808, instrumented 156/156 (AVD 16 KB), APK +34.836 B (ngân sách +100 KB), không thêm thư viện, Room vẫn v3. Sửa lỗi mất phím khi gõ nhanh ở address bar (Spec 009). Còn mở: G7 (cần proxy), G17 Layout Inspector + screen reader (cần người) |
| 023 | `privacy-protection` | Chặn tracker (host list đóng gói trong APK) · tự nâng HTTPS · xoá tham số theo dõi + cắt referrer · Global Privacy Control; mọi lớp bảo vệ bật sẵn, tắt được theo từng trang | Request tới tracker bị chặn; `http://` được nâng lên HTTPS; link có `utm_*` bị xoá tham số | 022 | ✅ **Done 2026-10-12 — PR #37 merged (`ec631d1`, CI 6/6), 77/78.** Unit 882/882, instrumented 169 (CI), APK +232.529 B, Room v4, không thêm thư viện. Galaxy A55: 10 trang thật đều chặn 1–25 tracker và chạy bình thường, GPC tới cả tài nguyên con, tải trang nhanh hơn hoặc bằng, cold start −3,4%. Sửa 2 lỗi lùi về http. Còn: bắt traffic qua proxy, screen reader |
| 024 | `privacy-tools` | Nút Fire + trang fireproof · privacy dashboard theo trang · tự từ chối cookie banner + chống fingerprint bằng script đóng gói sẵn (Constitution §II) | Bấm Fire → xoá dữ liệu trừ trang fireproof; dashboard hiện số tracker đã chặn | 023 | 🟢 **Implemented 2026-10-13** trên branch `024-privacy-tools` (chưa mở PR). Unit 928/928 · instrumented 186 test trên AVD 16 KB (mọi test 024 xanh) · lint/detekt/ktlint sạch · APK +99,134 B (ngân sách 200 KB) · Room v5 · không thêm dependency · autoconsent 16.51.0 (MPL-2.0). Còn chờ A55: G5, G11, G12, G14. ⚠️ Phát hiện giới hạn có sẵn của Spec 012: phiên ẩn danh làm mất cookie của site không mở trong tab thường. |

## Ý tưởng cho bản sau (chưa có spec)

> Constitution v1.5.0 §X: tính năng mới nảy ra khi thiết kế được ghi ở đây, **không** gộp vào spec đang làm. Muốn làm thì phải thiết kế trong [design system](https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys) và được duyệt trước khi `/speckit-specify`.

| Ý tưởng | Nguồn | Ghi chú |
|---|---|---|
| **Pin a site** — ghim trang lên dải shortcut card ở Home | Thiết kế Spec 019, 2026-10-10 | Thẻ "Pin a site" đã có trong component ShortcutCard nhưng ẩn cho đến khi có spec; cần bảng lưu trang ghim (Room migration) |

## Thứ tự Implement (Recommended)

```
Constitution → 001 → 002
             → 003, 004, 005, 006 (parallel sau 002)
             → 007 → 008 → 009 → 010
             → 011 → 012
             → 013, 014, 015 (parallel sau 007)
             → 016, 017, 018
             → 019 ui-redesign
             → 020 → 021 → 022 → 023 → 024 (Phase 6)
```

### Test flow tích lũy

```
Sau 001: App build debug pass, lint clean, version catalog wired, 16KB-verified
Sau 002: App khởi động được, Hilt graph valid, base classes ready, navigation host
Sau 003: Toggle dark/light/system → toàn app theme-correct
Sau 004: Đổi locale qua Settings → strings update toàn app
Sau 005: Insert/query bookmark/history/tab qua DAO
Sau 006: Read/write settings, persist qua process death
Sau 007: Load `https://example.com` qua BrowserWebView
Sau 008: Back/Forward/Reload/Home hoạt động
Sau 009: Nhập URL hoặc query qua address bar → load đúng
Sau 010: Search query qua Google
Sau 011: Multi-tab tạo/đóng/chuyển, persist qua kill app
Sau 012: Incognito tab tách biệt cookie/history
Sau 013: Add/list/delete bookmark
Sau 014: History list theo ngày, search, clear
Sau 015: Download file qua DownloadManager
Sau 016: Settings screen full options
Sau 017: Branded splash screen
Sau 018: Onboarding lần đầu mở app
Sau 019: Giao diện mới theo design system, đạt Compose Phase Discipline
Sau 020: Mở link từ app khác bằng ThirtySix, đặt làm trình duyệt mặc định
Sau 021: Upload, fullscreen, popup, quyền camera/mic/vị trí theo trang chạy đúng
Sau 022: Tìm trong trang, chia sẻ, chế độ máy tính, cỡ chữ, in, gợi ý khi gõ
Sau 023: Chặn tracker, tự nâng HTTPS, xoá tham số theo dõi, GPC
Sau 024: Nút Fire, privacy dashboard, script cookie banner + fingerprint
```

## Quy ước đặt tên branch + spec folder

- Branch: `<NNN>-<kebab-name>` (ví dụ `001-project-init-build-config`)
- Folder: `specs/<NNN>-<kebab-name>/`
- Files mỗi folder:
  - `spec.md` — user stories với priority (P1/P2/P3), acceptance scenarios, edge cases
  - `plan.md` — technical approach, libraries, file layout
  - `tasks.md` — task list dependency-ordered
  - `data-model.md` — nếu có data layer thay đổi
  - `research.md` — nếu cần khảo sát giải pháp (đặc biệt verify 16KB cho thư viện mới)
  - `quickstart.md` — cách verify spec đã xong
  - `contracts/` — repository interfaces, use case signatures (nếu có)
  - `checklists/` — review checklist (nếu có)
