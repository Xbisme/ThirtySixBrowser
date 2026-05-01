# Quickstart: Spec 006 — DataStore Settings

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

This document is the **verification playbook** for Spec 006. After implementation, walk every gate below and confirm green before opening the PR.

---

## Gate 0 — Build & static analysis

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew detekt
./gradlew ktlintCheck
```

**Expected**:
- All tasks ✅
- Detekt baseline file (`app/detekt-baseline.xml`) UNCHANGED (SC-007)
- Lint zero warnings (SC-007)
- Unit test count = previous baseline (51 from Spec 005) + ~10 new = ~61 total

---

## Gate 1 — 16 KB native library alignment (SC-006)

```bash
./gradlew assembleRelease
unzip -l app/build/outputs/apk/release/app-release.apk | grep '\.so$'
unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so 2>/dev/null | \
  objdump -p - 2>/dev/null | grep LOAD | awk '{print $NF}' | sort -u
```

**Expected**:
- Same `.so` files as Spec 005 (only `libandroidx.graphics.path.so` from Compose, 4 ABIs).
- Every alignment value = `0x4000` or larger.
- DataStore Preferences introduced ZERO new `.so` (verified at plan time).

---

## Gate 2 — APK size delta (SC-008)

```bash
ls -lh app/build/outputs/apk/release/app-release.apk
```

**Expected**:
- Spec 005 baseline: 1.4 MB.
- Spec 006 target: ≤ 1.6 MB (delta < 200 KB).
- DataStore Preferences runtime adds ~100–150 KB dexed; R8 should shrink further.

---

## Gate 3 — `git grep` checks (SC-010, SC-011)

### SC-010 — no view-layer code reads/writes settings file directly

```bash
# Should return ZERO matches outside data/local/datastore/ and di/
git grep -n "DataStore<Preferences>\|preferencesDataStore\|PreferenceDataStoreFactory" -- \
  ':(exclude)app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/datastore/' \
  ':(exclude)app/src/main/kotlin/com/raumanian/thirtysix/browser/di/' \
  ':(exclude)app/src/test/'
```

**Expected**: zero matches. Any hit is a Constitution §IV violation.

### SC-011 — no in-memory MutableState<ThemeMode> in MainActivity

```bash
# Should return ZERO matches in MainActivity
git grep -n "mutableStateOf(ThemeMode" -- 'app/src/main/kotlin/'
```

**Expected**: zero matches. The Spec 003 placeholder is gone.

### Bonus — no presentation/theme/ThemeMode.kt remains

```bash
test -f app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/ThemeMode.kt && echo "FAIL — file should be moved" || echo "PASS — file moved"
test -f app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/ThemeMode.kt && echo "PASS — file at new location" || echo "FAIL — file missing"
```

**Expected**: PASS / PASS.

---

## Gate 4 — Backup posture verification (SC-005)

Inspect the two XML files for the asymmetric policy:

```bash
grep -A 1 "settings\|preferences_pb" app/src/main/res/xml/backup_rules.xml
grep -A 1 "settings\|preferences_pb" app/src/main/res/xml/data_extraction_rules.xml
```

**Expected**:
- Both files contain `<include domain="file" path="datastore/thirtysix_settings.preferences_pb"/>` (FR-013).
- DB excludes from Spec 005 still present.
- Comment block at top of each file documents the asymmetry (FR-014).

---

## Gate 5 — Concurrent-write robustness (SC-004)

The `SettingsDataStoreTest#concurrentWrites_bothPersist` test runs 100 iterations of two-coroutine writes to two different keys; assert both values present on every iteration.

```bash
./gradlew testDebugUnitTest --tests \
  com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStoreTest.concurrentWrites_bothPersist
```

**Expected**: PASS, 100/100 iterations green.

---

## Gate 6 — Process-death survival (US1, US3, US4, US5)

This gate covers the persistence guarantee at the unit-test level. **Real-device kill -9 + relaunch** is a manual gate (deferred to user verification per spec Assumptions).

```bash
./gradlew testDebugUnitTest --tests \
  com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStoreTest
```

**Expected**:
- `defaultRead_returnsDocumentedDefaults` ✅
- `writeThemeMode_then_freshReadReturnsThemeMode` ✅ (simulates restart by re-creating the DataStore over the same temp file)
- `writeLanguageOverrideExplicit_then_freshReadReturnsExplicit` ✅
- `writeLanguageOverrideFollowSystem_after_explicit_clears_to_FollowSystem` ✅
- `writeSearchEngine_then_freshReadReturnsSearchEngine` ✅
- `writeOnboardingCompleted_then_freshReadReturnsTrue` ✅

---

## Gate 7 — Schema-evolution rule visible in source (FR-019, US7)

```bash
head -25 app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/StorageKeys.kt
```

**Expected**: Doc-comment block matching the rule text in [research.md R7](research.md#r7--schema-evolution-rule-fr-019--implementation).

```bash
./gradlew testDebugUnitTest --tests \
  com.raumanian.thirtysix.browser.data.mapper.SettingsMapperTest.renamedKey_returnsDefault_oldValueGone
```

**Expected**: PASS.

---

## Gate 8 — Manual smoke on emulator (visual)

> **Optional but recommended** for the SC-001 first-frame guarantee. Cannot be fully automated without instrumented tests (deferred to Spec 007/011).

1. Install debug APK on Android 13+ emulator: `./gradlew installDebug`
2. Launch app → observe theme matches device system theme (default `System` mode).
3. Use ADB shell or future Spec 016 toggle to write `theme_mode = "dark"`:
   ```bash
   # Manual write (debugging only — production users cannot do this):
   adb shell run-as com.raumanian.thirtysix.browser.debug \
     ls files/datastore/
   # Confirm thirtysix_settings.preferences_pb exists after first run.
   ```
4. Force-stop the app: `adb shell am force-stop com.raumanian.thirtysix.browser.debug`
5. Re-launch app → theme MUST match the persisted value on the very first composition (no flash of the prior default).

**Expected**: visually confirmed by user. SC-001 satisfied.

---

## Cumulative Quality Gate Summary

| Gate | Spec section | Verifies |
|---|---|---|
| 0 | All | Build + static analysis pass |
| 1 | SC-006 | 16 KB CI gate green |
| 2 | SC-008 | APK size < +200 KB |
| 3 | SC-010, SC-011 | No view-layer DataStore access; MutableState gone |
| 4 | SC-005 | Backup posture asymmetric and explicit |
| 5 | SC-004, US6 | Concurrent writes robust |
| 6 | US1–US5 | Persistence + default-read correct |
| 7 | FR-019, US7 | Schema-evolution rule visible + tested |
| 8 | SC-001 | Manual visual no-flash on cold start |

After Gates 0–7 are green and Gate 8 is user-confirmed, the spec is ready to merge.
