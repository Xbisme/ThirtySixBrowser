# Quickstart: Verifying Spec 004 — Multi-Language Localization Foundation

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-01

This document is the **acceptance playbook** — the steps a reviewer (or CI) runs to confirm Spec 004 is fully shipped. All steps must pass before the spec is marked Done.

## Prerequisites

- macOS / Linux / Windows host
- Android Studio Koala or newer (for emulator AVD management)
- One emulator AVD running **API 36** (system per-app picker available; recommended for full P2 coverage)
- One emulator AVD running **API 30 or earlier** (verifies device-locale fallback path on pre-API-33 — optional but recommended)
- Repo at branch `004-localization-multi-language` with all Phase 1 + Phase 2 changes merged locally
- `JAVA_HOME` set to JDK 17+ per [CLAUDE.md](../../CLAUDE.md) commands

## Gate 1 — Static analysis & lint pass

```bash
./gradlew clean
./gradlew lintDebug
```

**Expected**: BUILD SUCCESSFUL with zero `MissingTranslation` and zero `ExtraTranslation` reports. The `lintDebug` task completes in under 30s on a clean build.

**Negative test** (verifies the gate actually fires):

1. Temporarily delete one entry from `app/src/main/res/values-vi/strings.xml`, e.g., remove `<string name="tabs_screen_placeholder">Thẻ</string>`.
2. Re-run `./gradlew lintDebug`.
3. **Expected**: BUILD FAILED with a `MissingTranslation` error naming `tabs_screen_placeholder` and locale `vi`. Build stops; no APK produced.
4. Restore the deleted entry.
5. Re-run `./gradlew lintDebug` and verify the build is green again.

## Gate 2 — Build & install on emulator

```bash
./gradlew assembleDebug
./gradlew installDebug
```

**Expected**: BUILD SUCCESSFUL. APK installs on the emulator as `ThirtySix Browser` (note: the launcher icon label MUST read `ThirtySix Browser` with the space and correct spelling — Q1 clarification; if it still says `ThirdtySixBrowser`, Spec 004 is not done).

## Gate 3 — System per-app language picker (Android 13+)

Run on the API 36 emulator:

1. Open device **Settings** → **Apps** → scroll to or search for **ThirtySix Browser**.
2. Tap **ThirtySix Browser** → **Language**.
3. **Expected**: A picker screen titled "App language" appears with exactly **9 entries** in this order (Android sorts by display name in current device locale, so order may vary):
   - System default
   - English
   - Tiếng Việt
   - Deutsch
   - Русский
   - 한국어
   - 日本語
   - 中文
   - Français

**SC-002 acceptance**: 8 supported locales + 1 "System default" = 9 entries. Zero spurious entries (e.g., no Spanish, no Hindi).

## Gate 4 — Per-locale visual verification (P1 + P2 acceptance)

For each of the seven non-English locales, perform the following steps:

### Step 4a — Set per-app language

1. In Settings → Apps → ThirtySix Browser → Language, tap the target locale (e.g., **Tiếng Việt**).
2. The app may automatically relaunch; if not, manually open the app from the launcher.

### Step 4b — Verify launcher and screen titles

1. Long-press the launcher icon → **App info** → confirm the app's display name (top of the App info screen) matches the expected per-locale value from [data-model.md § per-locale value table](data-model.md#entity-2--baseline-string-catalog).
2. Open the app. The default destination renders the placeholder Screen for that destination.
3. Navigate to all seven placeholder destinations (Browser, Tabs, Bookmarks, History, Downloads, Settings, Onboarding) and verify each shows the locale-translated label.

### Per-locale expected values (visual checklist)

| Locale picked | Launcher / App info shows | Screen titles render in |
|---------------|---------------------------|-------------------------|
| Tiếng Việt (`vi`) | `Trình duyệt ThirtySix` | Trình duyệt / Thẻ / Dấu trang / Lịch sử / Tải xuống / Cài đặt / Giới thiệu |
| Deutsch (`de`) | `ThirtySix Browser` | Browser / Tabs / Lesezeichen / Verlauf / Downloads / Einstellungen / Einführung |
| Русский (`ru`) | `ThirtySix Браузер` | Браузер / Вкладки / Закладки / История / Загрузки / Настройки / Знакомство |
| 한국어 (`ko`) | `ThirtySix 브라우저` | 브라우저 / 탭 / 북마크 / 기록 / 다운로드 / 설정 / 소개 |
| 日本語 (`ja`) | `ThirtySix ブラウザ` | ブラウザ / タブ / ブックマーク / 履歴 / ダウンロード / 設定 / はじめに |
| 中文 (`zh`) | `ThirtySix 浏览器` | 浏览器 / 标签页 / 书签 / 历史 / 下载 / 设置 / 引导 |
| Français (`fr`) | `Navigateur ThirtySix` | Navigateur / Onglets / Favoris / Historique / Téléchargements / Paramètres / Présentation |
| English (`en`) — control | `ThirtySix Browser` | Browser / Tabs / Bookmarks / History / Downloads / Settings / Onboarding |

**SC-001 acceptance**: 100% of the listed strings render in the target locale. Zero strings fall back to English when a non-English locale is selected.

**SC-003 acceptance**: While ThirtySix Browser is in (e.g.) Japanese, the device-wide UI (Settings, status bar, other apps) remains in English. Only ThirtySix Browser is affected.

### Step 4c — Reset

After each locale's verification, return to Settings → Apps → ThirtySix Browser → Language and select **System default** to reset for the next iteration.

## Gate 5 — Device-wide locale fallback (covers Android 7–12)

Run on the API 30 emulator (or the API 36 emulator with per-app language reset to "System default"):

1. Open device Settings → System → Languages & input → Languages.
2. Add Vietnamese as a system language and move it to the top of the language list.
3. Reopen ThirtySix Browser (cold start).
4. **Expected**: Launcher icon label is `Trình duyệt ThirtySix`; all visible screens render in Vietnamese — same outcome as Gate 4 for the `vi` locale.

**P1 acceptance**: Zero in-app interaction needed; the app respects device-wide locale automatically.

## Gate 6 — Unsupported locale fallback (covers SC-001 negative path)

1. On the API 30 emulator, switch device system language to a locale **not** in the supported set (e.g., Portuguese `pt-BR`).
2. Reopen ThirtySix Browser.
3. **Expected**: All strings render in **English** (fallback to `values/strings.xml`). No crash, no missing-string placeholder, no untranslated keys visible.

**SC-001 negative-path acceptance**: Unsupported locale → graceful English fallback.

## Gate 7 — Regional variant fallback (covers SC-006)

1. On the API 30 emulator, switch device system language to **Quebec French** (`fr-CA`).
2. Reopen ThirtySix Browser.
3. **Expected**: All strings render in **French** (resolves to `values-fr/`, not English).

Repeat for `zh-TW` (should resolve to Simplified Chinese in `values-zh/`) and `de-AT` (should resolve to `values-de/`).

**SC-006 acceptance**: Regional variants resolve to closest supported language.

## Gate 8 — Unit + instrumented test gates

```bash
./gradlew testDebugUnitTest
./gradlew detekt ktlintCheck
```

**Expected**: All existing tests pass (no test added by Spec 004; no test removed). Detekt and ktlint pass with zero new violations. Detekt baseline file unchanged from Spec 003.

## Gate 9 — APK size sanity check

```bash
./gradlew assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
```

**Expected**: APK size delta vs Spec 003 baseline is ≤ 50KB (each locale's `strings.xml` is ~1KB, eight files compiled into `resources.arsc` adds minimal overhead). The change does not introduce native libraries; 16KB alignment status is unchanged.

## Gate 10 — `git grep` smoke test (no remaining typo)

```bash
git grep -n -i "thirdty"
```

**Expected**: Zero matches in the source tree. The Spec 003-era theme rename + Spec 004's `app_name` fix close out the last typo from the Android Studio template.

## Done criteria

Spec 004 is **Done** when:

- ✅ Gates 1, 2, 8, 9, 10 pass on the local dev box
- ✅ Gates 3, 4 (all 8 locales), 5, 6, 7 pass on at least one Android 13+ emulator/device
- ✅ Gate 5 (device-wide fallback) passes on at least one Android 7–12 emulator/device
- ✅ CI's `lintDebug` job is green on the PR
- ✅ Manual visual verification of `app_name` displays as `ThirtySix Browser` on the launcher (English) and per-locale equivalents

If any gate fails, fix the underlying issue and re-run from Gate 1. Do NOT add a lint baseline cover for `MissingTranslation` — it is a hard error gate by design.
