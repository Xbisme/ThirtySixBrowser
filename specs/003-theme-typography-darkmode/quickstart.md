# Quickstart — Verify Spec 003 Theme + Typography + Dark Mode

> Cách verify Spec 003 đã implement xong + 10 Success Criteria pass. Chạy theo thứ tự.

## Prerequisites

- Branch `003-theme-typography-darkmode` checkout (đã có spec.md, plan.md, research.md, data-model.md)
- Implementation tasks (`tasks.md`) đã chạy xong (sau `/speckit-tasks` + Copilot/Claude implement)
- Android emulator API 36 với 16KB page size (recommended) HOẶC physical device API 31+
- JDK 17+ launcher (AGP 9.x requirement)

---

## Step 1 — Build verify (SC-001..005, SC-007)

```bash
# Bootstrap clean
./gradlew clean

# 6 quality gate (mirror CI)
./gradlew :app:assembleDebug                    # Build pass
./gradlew :app:assembleRelease                  # R8 pass + signing fallback
./gradlew :app:testDebugUnitTest                # 4+ new test pass + 8 existing Spec 002 test pass
./gradlew :app:lintDebug                        # 0 warning
./gradlew detekt                                # 0 violation, baseline empty
./gradlew ktlintCheck                           # 0 violation

# 16KB alignment script
bash .specify/scripts/bash/verify-16kb-alignment.sh
# Expected: all `.so` align 0x4000 (chỉ libandroidx.graphics.path.so từ Compose)

# APK size delta vs Spec 002 baseline
du -h app/build/outputs/apk/release/app-release.apk
# Compare với Spec 002 baseline trong CLAUDE.md "Recent Changes" (Spec 002 = baseline + 88KB)
# Expected delta Spec 003: ≤ 500KB (SC-007)
```

**Detekt baseline verify**:
```bash
cat app/detekt-baseline.xml
# Expected:
# <?xml version="1.0" ?>
# <SmellBaseline>
#   <ManuallySuppressedIssues/>
#   <CurrentIssues/>
# </SmellBaseline>
```

---

## Step 2 — No-Hardcode verify (SC-008)

```bash
# 0 occurrence of Color(0xFF...) outside Color.kt
grep -rn "Color(0x" app/src/main/kotlin/ \
  | grep -v "presentation/theme/Color.kt" \
  | grep -v "Color.Transparent\|Color.Unspecified"
# Expected: empty output

# 0 occurrence of TextStyle( or fontSize = outside Type.kt
grep -rn -E "TextStyle\(|fontSize\s*=\s*[0-9]" app/src/main/kotlin/ \
  | grep -v "presentation/theme/Type.kt"
# Expected: empty output

# 0 occurrence of .padding(N.dp) literal outside Spacing.kt (allows .size for icons)
grep -rnE "\.padding\([0-9]+\.dp\)" app/src/main/kotlin/ \
  | grep -v "presentation/theme/Spacing.kt"
# Expected: empty output (padding qua Spacing.*)
```

---

## Step 3 — WCAG contrast verify (SC-010)

1. Mở [Material Theme Builder](https://m3.material.io/theme-builder) với cùng config (primary `#0F766E`, tertiary `#0891B2`).
2. Tab "Accessibility" → screenshot 24+ pair với badge ✓.
3. So sánh với `research.md` ratio table — tất cả pair phải pass AA.
4. Nếu có pair fail → adjust luminance + re-export Color.kt + re-test.

**Optional manual double-check**:
```bash
# Sample 6 critical pair qua webaim.org/resources/contrastchecker
# - #FFFFFF on #0F766E (light onPrimary on primary)  → expected ≥ 4.5
# - #003733 on #5EEAD4 (dark onPrimary on primary)   → expected ≥ 4.5
# - #FFFFFF on #DC2626 (onError on error)             → expected ≥ 4.5
# ... (pair còn lại tương tự)
```

---

## Step 4 — Manual UI verify trên device (US1, US2, US3)

### Cài app

```bash
./gradlew installDebug
# Hoặc Android Studio Run config với device API 36
```

### US1 — Light/Dark/System toggle (SC-001, SC-002)

1. **System Light**: Setup → Display → Theme = Light → mở app
   - Verify `BrowserScreen` placeholder render với background sáng (`~#FAFAF9`), text dark (`~#1F2937`)
   - Verify font heading "Browser" dùng Poppins SemiBold (so sánh visually với Roboto trước đây — Poppins có chữ rounded hơn)
2. **System Dark**: Quick Settings → toggle Dark mode → app re-compose
   - Verify background dark (`~#0A0F0E`), text sáng
   - Verify re-compose ≤ 100ms (tap-and-watch — không có lag visible)
3. **Cold start dark mode**:
   - Force-stop app: `adb shell am force-stop com.raumanian.thirtysix.browser`
   - Re-launch từ launcher
   - Verify KHÔNG flash trắng (window bg dark từ `values-night/themes.xml`)
   - Cold start time ≤ 1.5s (perceptual; có thể đo bằng `adb shell am start -W -n com.raumanian.thirtysix.browser/.MainActivity`)
4. **Programmatic** (qua test code hoặc dev shortcut):
   - `setContent { ThirtySixTheme(darkTheme = true, dynamicColor = false) { Text("X") } }` → render dark Deep Teal scheme bất kể system

### US2 — Dynamic color Android 12+ (SC-003)

1. Trên emulator API 36 → Settings → Wallpaper → chọn wallpaper màu cam đậm
2. Mở app → verify `colorScheme.primary` tilt cam (KHÔNG Deep Teal)
   - Cách verify visual: `BrowserScreen` placeholder text/accent màu cam
3. Setup → Wallpaper → chọn wallpaper màu xanh lá → re-launch app → verify primary tilt xanh lá
4. **Fallback test**: Trên emulator API 24 hoặc 26 → mở app → verify primary = `#0F766E` (Deep Teal seed, không có dynamic)
5. **Disable dynamic test**: Programmatic `ThirtySixTheme(dynamicColor = false)` trên Android 12+ → verify Deep Teal seed dùng (KHÔNG wallpaper-derived)

### US3 — Design system consumption (SC-006, SC-009)

1. Verify `MaterialTheme.colorScheme.primary` resolved đúng trong DevTools Compose Inspector hoặc qua test:
   ```kotlin
   @Test
   fun colorScheme_primary_isDeepTeal() {
       // composeTestRule.setContent { ThirtySixTheme(dynamicColor = false) {
       //     val color = MaterialTheme.colorScheme.primary
       //     assertEquals(Color(0xFF0F766E), color)
       // } }
   }
   ```
2. Code reviewer check:
   - Mở `Color.kt` → identify primary seed `#0F766E` trong < 30s
   - Mở `Spacing.kt` → identify thêm token mới ở dòng nào trong < 30s
   - Mở `Type.kt` → identify đổi font heading bằng cách đổi `Poppins` constant trong < 30s

---

## Step 5 — Test suite verify (SC-005, FR-023)

```bash
./gradlew :app:testDebugUnitTest --tests "com.raumanian.thirtysix.browser.presentation.theme.*"
```

**Expected output**:
```
SpacingTest > xs_is_4dp PASSED
SpacingTest > sm_is_8dp PASSED
SpacingTest > md_is_16dp PASSED
SpacingTest > lg_is_24dp PASSED
SpacingTest > xl_is_32dp PASSED
TypographyTest > headlineMedium_uses_Poppins PASSED
TypographyTest > bodyLarge_uses_Inter PASSED
TypographyTest > titleMedium_uses_Inter PASSED
TypographyTest > titleLarge_uses_Poppins PASSED
ThemeModeTest > has_three_values PASSED
ThemeModeTest > contains_Light_Dark_System PASSED
```

Plus 8 test từ Spec 002 (`ResultTest`, `BaseViewModelTest`) vẫn pass.

---

## Step 6 — CI gate verify (SC-004)

```bash
git push -u origin 003-theme-typography-darkmode
```

**Expected GitHub Actions result**: 5 job xanh + 1 job (instrumented-test) skipped due to `if: false`:
- ✅ build (assembleDebug)
- ✅ unit-test (testDebugUnitTest)
- ✅ lint (lintDebug)
- ✅ static-analysis (detekt + ktlintCheck)
- ✅ verify-16kb (assembleRelease + alignment script)
- ⏭️ instrumented-test (skipped — Spec 007/011 sẽ enable)

---

## Step 7 — Pre-merge final checklist

- [ ] `tasks.md` 100% checked
- [ ] All 10 SC verified (Step 1-6)
- [ ] PR description bao gồm:
  - Material Theme Builder export config screenshot
  - WCAG contrast ratio table (24+ pair) với pass/fail status
  - Font SHA256 hashes
  - APK size delta vs Spec 002 (concrete number, not estimate)
  - Detekt baseline diff (cleared from 9 → 0 entries)
- [ ] Code review approve qua user (Xbism3)
- [ ] CI 5/6 green + 1 skipped
- [ ] No new permission added (verify `AndroidManifest.xml`)
- [ ] No `addJavascriptInterface` (Constitution §I)

## Post-merge follow-up (per dev-workflow Bước 8)

- [ ] Update `CLAUDE.md` "Code Style" section: bỏ "NO custom font for v1.0" → thay bằng "Poppins (heading) + Inter (body) bundled local trong `res/font/`"
- [ ] Update `.claude/claude-app/project-context.md` cùng note
- [ ] Update `.claude/claude-app/sdd-roadmap.md` mark Spec 003 ✅ Done
- [ ] Update `CLAUDE.md` `<!-- SPECKIT START -->` Active Spec → Spec 004 hoặc 005 (parallel paths after 003)
- [ ] Update "Recent Changes" log trong CLAUDE.md với 1 dòng tóm tắt Spec 003

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Build fail "unresolved reference: dynamicDarkColorScheme" | Material3 < 1.4.0 | Verify `./gradlew :app:dependencies \| grep material3` ≥ 1.4.0; bump Compose BOM nếu cần |
| Build fail "unresolved reference: R.font.poppins_medium" | Font file thiếu hoặc tên sai | Verify `app/src/main/res/font/poppins_medium.ttf` tồn tại + tên lowercase + underscore |
| App crash launch trên API 24-29 với "Resource not found: dynamicDarkColorScheme" | Quên API guard | Verify `Theme.kt` có `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` check |
| White flash cold start dark mode | `values-night/themes.xml` thiếu hoặc `windowBackground` chưa set | Verify file tồn tại + `<item name="android:windowBackground">@color/window_background_dark</item>` |
| Detekt fail với MagicNumber trên Color.kt | Color.kt chưa rewrite hoặc còn template hex | Verify rewrite hoàn tất + baseline empty |
| WCAG pair fail (vd outline 2.8:1) | Auto-generated M3 neutral không pass | Override manually trong Color.kt: bump luminance value (vd `outline = #71717A` → `#5A5A65`) |
| APK size > Spec 002 + 500KB | Bundle thừa weight font hoặc variable font | Verify chỉ 4 weight (Poppins M+SB, Inter R+M); KHÔNG bundle Bold/ExtraBold |
