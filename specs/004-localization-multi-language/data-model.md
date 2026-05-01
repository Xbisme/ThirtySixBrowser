# Data Model: Multi-Language Localization Foundation

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-01

> Spec 004 ships **no Room entities, no domain models, no DataStore keys**. The "data" in this feature is static APK resource content — string resource files and a single locales-config XML. This document captures the structural definition of those resources so reviewers can verify completeness without reading every translation file.

## Entity 1 — Supported Locale Set

**What it is**: The fixed list of eight BCP-47 language tags the app commits to translating into. Defines membership for both the OS-level per-app language picker and the lint-enforced translation completeness gate.

**Storage**: `app/src/main/res/xml/locales_config.xml` (canonical declaration) — referenced from `AndroidManifest.xml` via `<application android:localeConfig="@xml/locales_config" ...>`.

**Schema**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="vi" />
    <locale android:name="de" />
    <locale android:name="ru" />
    <locale android:name="ko" />
    <locale android:name="ja" />
    <locale android:name="zh" />
    <locale android:name="fr" />
</locale-config>
```

**Members** (sorted by feature spec request order, not alphabetically):

| BCP-47 tag | Language | Resource folder | Region matching |
|------------|----------|-----------------|-----------------|
| `en` | English (default) | `res/values/` | All English variants (`en-US`, `en-GB`, `en-AU`, etc.) fall back here |
| `vi` | Vietnamese | `res/values-vi/` | `vi-VN` matches |
| `de` | German | `res/values-de/` | `de-DE`, `de-AT`, `de-CH` all match |
| `ru` | Russian | `res/values-ru/` | `ru-RU`, `ru-KZ`, etc. all match |
| `ko` | Korean | `res/values-ko/` | `ko-KR`, `ko-KP` match |
| `ja` | Japanese | `res/values-ja/` | `ja-JP` matches |
| `zh` | Chinese (Simplified) | `res/values-zh/` | All Chinese variants match (`zh-CN`, `zh-TW`, `zh-HK`, `zh-Hans`, `zh-Hant`) — see [research.md § R2](research.md#r2--chinese-locale-tag-form-zh-vs-zh-hans-vs-zh-cn) |
| `fr` | French | `res/values-fr/` | `fr-FR`, `fr-CA`, `fr-BE`, `fr-CH` all match |

**Invariants**:
- The eight `<locale>` entries in `locales_config.xml` MUST exactly match the eight resource folders that exist under `app/src/main/res/`. Lint enforces this by failing if any non-English locale folder is missing keys.
- The order of `<locale>` entries does not affect the system Settings picker (Android sorts by display name in user's current locale).
- Adding or removing a locale is a multi-file change: `locales_config.xml` + add/remove `values-<locale>/strings.xml` + manifest unchanged.

**Validation rules**:
- BCP-47 tag form: lowercase, no region (per [research R7](research.md#r7--resource-folder-naming-values-zh-vs-values-zh-rcn-etc)).
- No empty-tag entry; no duplicate tag.
- File MUST be at exact path `res/xml/locales_config.xml` to be resolvable as `@xml/locales_config`.

**State transitions**: None — set is fixed at compile time. Mutation requires a code change + spec.

## Entity 2 — Baseline String Catalog

**What it is**: The minimal set of user-facing strings shipped with this feature. Establishes the "shape" all eight locale files must conform to. Future feature specs extend this catalog, each carrying the obligation to translate any new string into all eight locales.

**Storage**: One `strings.xml` per locale folder, eight files total:
- `app/src/main/res/values/strings.xml` (English baseline)
- `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` (seven translations)

**Keys** (8 total in this spec):

| Key | English value | Used by | Naming convention rationale |
|-----|---------------|---------|----------------------------|
| `app_name` | `ThirtySix Browser` | Launcher icon label, system Settings app entry, per-app language picker title | Android-standard key; matches Google Play listing per Q1 clarification |
| `browser_screen_placeholder` | `Browser` | `BrowserScreen.kt` placeholder Text | `feature_section_purpose` per FR-007 |
| `tabs_screen_placeholder` | `Tabs` | `TabsScreen.kt` placeholder Text | Same |
| `bookmarks_screen_placeholder` | `Bookmarks` | `BookmarksScreen.kt` placeholder Text | Same |
| `history_screen_placeholder` | `History` | `HistoryScreen.kt` placeholder Text | Same |
| `downloads_screen_placeholder` | `Downloads` | `DownloadsScreen.kt` placeholder Text | Same |
| `settings_screen_placeholder` | `Settings` | `SettingsScreen.kt` placeholder Text | Same |
| `onboarding_screen_placeholder` | `Onboarding` | `OnboardingScreen.kt` placeholder Text | Same |

**Note on `*_screen_placeholder` keys**: These keys were introduced by Spec 002 and intentionally use the `placeholder` segment to signal "this label exists for build/navigation testing only and will be replaced when the feature spec implements the real Screen." When a future spec (e.g., Spec 011 `tabs-management`) implements the actual tabs UI, that spec OWNS renaming `tabs_screen_placeholder` → `tabs_screen_title` (or whichever semantic key fits the real UI), updating all eight locale files in the same change.

**Per-locale value table** (final values to ship):

| Key | en | vi | de | ru | ko | ja | zh | fr |
|-----|----|----|----|----|----|----|----|----|
| `app_name` | ThirtySix Browser | Trình duyệt ThirtySix | ThirtySix Browser | ThirtySix Браузер | ThirtySix 브라우저 | ThirtySix ブラウザ | ThirtySix 浏览器 | Navigateur ThirtySix |
| `browser_screen_placeholder` | Browser | Trình duyệt | Browser | Браузер | 브라우저 | ブラウザ | 浏览器 | Navigateur |
| `tabs_screen_placeholder` | Tabs | Thẻ | Tabs | Вкладки | 탭 | タブ | 标签页 | Onglets |
| `bookmarks_screen_placeholder` | Bookmarks | Dấu trang | Lesezeichen | Закладки | 북마크 | ブックマーク | 书签 | Favoris |
| `history_screen_placeholder` | History | Lịch sử | Verlauf | История | 기록 | 履歴 | 历史 | Historique |
| `downloads_screen_placeholder` | Downloads | Tải xuống | Downloads | Загрузки | 다운로드 | ダウンロード | 下载 | Téléchargements |
| `settings_screen_placeholder` | Settings | Cài đặt | Einstellungen | Настройки | 설정 | 設定 | 设置 | Paramètres |
| `onboarding_screen_placeholder` | Onboarding | Giới thiệu | Einführung | Знакомство | 소개 | はじめに | 引导 | Présentation |

**Brand-name treatment** (`app_name` row): Per [research R6](research.md#r6--app_name-translation-strategy-across-8-locales-q1-clarification-implementation), the brand "ThirtySix" stays in Latin script across all eight locales; only the descriptor "Browser" / "Navigator" is translated.

**Invariants**:
- Every key present in `values/strings.xml` MUST be present in all seven `values-*/strings.xml` files. Enforced at error severity by Android Lint check `MissingTranslation`.
- No key may be present in a non-English file but missing from the English baseline. Enforced by Lint check `ExtraTranslation`.
- All values in non-English files MUST be valid UTF-8 — verified by the build system at compile time.
- Key names follow the `feature_section_purpose` convention (snake_case) per FR-007.

**Validation rules**:
- File path: `app/src/main/res/values{,-<locale>}/strings.xml` (exact).
- Root element: `<resources>`.
- Each entry: `<string name="key_name">value</string>`. No `translatable="false"` flag — all eight keys are translatable.
- No string formatting placeholders (`%s`, `%1$d`) in the baseline catalog — placeholder Screen titles are static labels.
- No HTML/CDATA — plain text values only.

**State transitions**: None — values are fixed at compile time. Mutation requires a code change.

**Extension model** (for future specs):
- A future spec adding a new user-facing string MUST add the key to `values/strings.xml` AND all seven `values-*/strings.xml` files in the same change. Lint will block the build otherwise.
- A future spec replacing a placeholder Screen with the real Screen MUST update all eight locale files in the same change (rename or replace key value).
- A future spec adding a new locale (e.g., adding Spanish `es`) MUST add `<locale android:name="es" />` to `locales_config.xml` AND create `values-es/strings.xml` with all current keys translated. Lint will fail otherwise.
- A future spec adding a new key MUST verify the key name follows the `feature_section_purpose` naming convention (FR-007) at PR review time. The Android Lint `MissingTranslation` / `ExtraTranslation` checks validate **coverage** but NOT **naming style** — convention compliance is enforced manually during code review.

## What Spec 004 deliberately does NOT define

- **No Room entities, no DataStore keys, no domain models** — all out of scope; defer to Spec 005 / 006 / per-feature.
- **No `LocaleManager` helper class** — defer to Spec 016 (Settings Screen); no in-app switcher in this milestone.
- **No persistence of user-selected locale** — defer to Spec 006 (DataStore); the Android 13+ system picker persists its own selection at OS level, no app-side storage required.
- **No content descriptions** for Composable elements — the seven placeholder Screens have only a single Text element; per-feature specs add `contentDescription` keys when they introduce icons/buttons. Future spec extension obligation per the "Extension model" section above will pick these up.
