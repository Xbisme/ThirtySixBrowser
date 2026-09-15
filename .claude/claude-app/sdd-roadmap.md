# ThirtySixBrowser — Lộ Trình Spec (SDD Roadmap)

> Cập nhật lần cuối: 2026-09-15
> v1.0: Android Native Browser — ✅ Specs 001–016 done (013, 014, 015, 016 đều đã merge vào `main`; 016 qua PR #18, merge commit `3083941`, 2026-09-12). **Phase 3 3/3 · Phase 4 1/3** (112/113 — còn TalkBack pass của T109, cần người thật). **Tiếp theo: 017 `splash-screen`.**

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
                          └── Phase 5 (Optional)
                                │
                                └── 019 tracker-blocker-hostlist
```

## Spec List (v1.0)

| # | Tên | User Story chính | Test được gì? | Dependencies | Status |
|---|-----|-----------------|---------------|-------------|--------|
| — | Constitution | Nguyên tắc nền tảng v1.3.0 (§II permissions khớp kết quả Spec 015) | — | — | ✅ Done 2026-05-01 · amended 2026-09-11 |
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
| 017 | `splash-screen` | Splash API + branding | Cold start → splash → main | 002 | ⬜ |
| 018 | `onboarding-flow` | 3–4 slides chọn ngôn ngữ/theme/search | Lần đầu mở → onboarding → main | 003, 004, 006, 017 | ⬜ |
| 019 | `tracker-blocker-hostlist` | (Optional) Block request via host list | Toggle on → request đến host trong list bị block | 011–018 | ⬜ Optional |

## Thứ tự Implement (Recommended)

```
Constitution → 001 → 002
             → 003, 004, 005, 006 (parallel sau 002)
             → 007 → 008 → 009 → 010
             → 011 → 012
             → 013, 014, 015 (parallel sau 007)
             → 016, 017, 018
             → [019 optional]
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
Sau 019 (optional): Tracker blocker toggle hoạt động
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
