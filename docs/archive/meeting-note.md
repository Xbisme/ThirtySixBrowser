# ThirtySixBrowser — Meeting Note & Roadmap

**Created**: 2026-04-30
**Owner**: Xbism3
**Status**: Draft v1 (approved scope, ready to start spec 001)

---

## 1. Tổng quan project

- **Tên**: ThirtySixBrowser
- **Package**: `com.raumanian.thirtysix.browser`
- **Loại**: Android Browser (giống DuckDuckGo nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn)
- **Stack**: Kotlin + Jetpack Compose + Material3
- **Kiến trúc**: Clean Architecture + MVVM
- **minSdk**: 24 (Android 7.0+)
- **targetSdk**: 36
- **compileSdk**: 36 (release with minorApiLevel 1)
- **Java**: 11

## 2. Tech stack chốt

| Concern | Lựa chọn |
|---------|----------|
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Local DB | Room |
| Settings storage | DataStore (Preferences) |
| Async | Coroutines + Flow (StateFlow / SharedFlow) |
| WebView | `android.webkit.WebView` bọc trong `AndroidView` |
| Download | Android `DownloadManager` |
| Splash | `androidx.core:core-splashscreen` |
| Test | JUnit + Compose UI Test + Espresso |
| CI | GitHub Actions (build / test / lint cho mỗi spec) |

## 3. Quyết định kiến trúc

- **Layer**: `data / domain / presentation / di / core`
  - `data`: Room entities/DAOs, repositories impl, data sources, DataStore
  - `domain`: Models thuần Kotlin, repository interfaces, use cases
  - `presentation`: ViewModels, Compose screens, UI state
  - `di`: Hilt modules
  - `core`: Base classes, DispatcherProvider, Result wrapper, extensions
- **WebView**: dùng `android.webkit.WebView` (không GeckoView, không Custom Tabs)
- **Build types**: chỉ `debug` và `release` — KHÔNG dùng build flavors (dev/staging/prod)
- **Theme**: Light / Dark / System (dynamic color trên Android 12+)
- **Search engine mặc định**: Google (đổi được trong Settings)
- **Private/Incognito mode**: có
- **Onboarding**: có (lần đầu mở app)
- **Tracker blocker**: optional, để cuối cùng (có thể bỏ)

## 4. Ngôn ngữ UI hỗ trợ

VN, EN, DE, RU, KR, JA, ZH, FR (8 locale)

## 5. Lộ trình spec (18 specs core + 1 optional)

### Giai đoạn 1 — Foundation

| # | Spec | Mô tả ngắn |
|---|------|------------|
| 001 | `project-init-build-config` | Setup Gradle Kotlin DSL, version catalog, plugins (Hilt, KSP, kotlin-parcelize), buildTypes debug/release, signing placeholder, Compose compiler, Java 11 |
| 002 | `clean-architecture-skeleton-di` | Module structure `data/domain/presentation/di/core`, Hilt `Application`, base `ViewModel`, base `UseCase`, `Result` wrapper, `DispatcherProvider` |
| 003 | `theme-typography-darkmode` | Material3 ColorScheme (light/dark/dynamic), Typography, Shape, theme switcher theo system |
| 004 | `localization-multi-language` | Resource strings cho VN/EN/DE/RU/KR/JA/ZH/FR, locale switcher trong settings, `LocaleManager` |
| 005 | `room-database-schema` | Entities: `BookmarkEntity`, `HistoryEntity`, `TabEntity`, DAOs, `AppDatabase`, migration strategy |
| 006 | `datastore-settings` | Preferences DataStore cho theme, language, search engine, default home page |

### Giai đoạn 2 — Core Browser

| # | Spec | Mô tả ngắn |
|---|------|------------|
| 007 | `webview-compose-wrapper` | `BrowserWebView` Composable bọc WebView, expose state (url, title, canGoBack/Forward, progress, favicon), `WebViewClient` / `WebChromeClient` cơ bản |
| 008 | `navigation-controls` | Back / Forward / Reload / Stop / Home, predictive back gesture |
| 009 | `address-bar-omnibox` | TextField address bar, parse URL vs query, suggest từ history/bookmarks |
| 010 | `search-engine-google` | Build search URL, redirect query → search results, đổi engine được sau (Google default) |
| 011 | `tabs-management` | Multi-tab, tab switcher screen (grid preview), tạo/đóng/chuyển tab, persist tab state qua process death |
| 012 | `private-incognito-mode` | Tab incognito riêng, không lưu history/cookie/cache, UI khác biệt (icon/màu), clear khi đóng tab |

### Giai đoạn 3 — Tính năng dữ liệu

| # | Spec | Mô tả ngắn |
|---|------|------------|
| 013 | `bookmarks-crud` | Thêm/sửa/xóa bookmark, folder bookmark, màn bookmark list, search |
| 014 | `history-view` | Lưu history, màn history group theo ngày, search, xóa từng item / xóa toàn bộ |
| 015 | `downloads-manager` | Bắt download event từ WebView, dùng `DownloadManager`, màn download list (in-progress/done) |

### Giai đoạn 4 — Settings & Polish

| # | Spec | Mô tả ngắn |
|---|------|------------|
| 016 | `settings-screen` | Settings root: theme, language, search engine, default home, clear data (cache/cookies/history), about |
| 017 | `splash-screen` | Splash dùng `androidx.core:core-splashscreen`, branding, fade vào MainActivity |
| 018 | `onboarding-flow` | 3–4 màn welcome lần đầu mở app, set ngôn ngữ + theme + search engine |

### Giai đoạn 5 — Optional

| # | Spec | Mô tả ngắn |
|---|------|------------|
| 019 | `tracker-blocker-hostlist` | (Optional) Chặn request qua `shouldInterceptRequest` với host blocklist tĩnh, toggle on/off trong settings |

## 6. Phụ thuộc giữa các spec

```
001 → 002 → 003, 004, 005, 006
005, 006 → 007 → 008 → 009 → 010
007 → 011 → 012
007 + 005 → 013, 014
007 → 015
003, 004, 006 → 016
002 → 017 → 018
hết 011–018 → 019 (optional)
```

## 7. Cấu trúc mỗi spec (chuẩn speckit)

Mỗi spec là 1 thư mục `specs/NNN-kebab-name/` chứa:

- `spec.md` — user stories với priority (P1/P2/P3), acceptance scenarios, edge cases
- `plan.md` — technical approach, libraries, file layout
- `tasks.md` — task list dependency-ordered
- `data-model.md` — nếu có data layer thay đổi
- `research.md` — nếu cần khảo sát giải pháp
- `quickstart.md` — cách verify spec đã xong
- `contracts/` — interfaces, repository contracts (nếu có)
- `checklists/` — review checklist (nếu có)

## 8. CI

GitHub Actions hiện có sẵn `.github/workflows/`. Mỗi spec khi merge sẽ chạy:

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew connectedDebugAndroidTest` (instrumented)

## 9. Định nghĩa "Done" cho cả project

- Tất cả 18 spec core (001–018) hoàn thành, lint sạch, test pass
- App build debug + release thành công
- 8 locale đầy đủ string resource
- Light / Dark / System theme hoạt động đúng
- WebView load đúng URL, search, multi-tab, incognito, bookmarks, history, downloads
- Onboarding chạy lần đầu, settings persist qua process death
- Spec 019 (tracker blocker) là optional — có thể bỏ

## 10. Câu hỏi đã chốt

| # | Câu hỏi | Quyết định |
|---|---------|------------|
| 1 | Build flavors? | Không — chỉ debug/release |
| 2 | Splash screen API native? | Có (`androidx.core:core-splashscreen`) |
| 3 | Onboarding? | Có |
| 4 | CI cho mỗi spec? | Có |
| 5 | Scope spec breakdown? | OK — 18 core + 1 optional |

## 11. Bước tiếp theo

Bắt đầu spec **001 — `project-init-build-config`**. Trước khi viết spec.md, sẽ bàn lại scope chi tiết của 001 trong cuộc trò chuyện tiếp theo.
