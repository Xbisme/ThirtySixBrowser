# Quickstart — Verify Spec 001 Done

> Run these checks **after** all tasks in `tasks.md` are completed. Every command MUST pass with the indicated outcome before marking spec complete and merging.

## Prerequisites on the verifying machine

- JDK ≥ 17 installed and on `PATH` (Gradle daemon launcher requirement — AGP 9.x)
- Android SDK with API 36 platform installed (`ANDROID_HOME` set or `local.properties` `sdk.dir=...`)
- No release keystore configured locally (so we can verify the debug-fallback path; once verified, you may add a real keystore)

## Acceptance commands (run in order from repo root)

### 1. Catalog migration verification

```bash
# No version literals in any *.gradle.kts file (FR-002, SC-002)
grep -rE '"[0-9]+\.[0-9]+\.[0-9]+"' --include="*.gradle.kts" . \
    | grep -vE '(applicationIdSuffix|compileSdkExtension|namespace|//|/\*)' \
    || echo "✅ no version literals found"
```

**Expected**: no matching lines → exit message "✅ no version literals found".

### 2. Source set migration verification

```bash
# java/ should be empty or absent; kotlin/ should hold migrated files (FR-027a/b/c)
test ! -d app/src/main/java/com/raumanian/thirtysix/browser \
    || test -z "$(ls -A app/src/main/java/com/raumanian/thirtysix/browser 2>/dev/null)" \
    && echo "✅ java/ migrated"

test -f app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt \
    && test -f app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt \
    && echo "✅ kotlin/ source set in place + AppConstants stub present"
```

### 3. Build pipeline (P1 stories — US1, US2, US3)

```bash
./gradlew clean
./gradlew assembleDebug          # US1 → exit 0
./gradlew testDebugUnitTest      # baseline tests → exit 0
./gradlew lintDebug              # US2 strict → exit 0 (no warnings, no Lint baseline)
./gradlew detekt                 # US2 strict → exit 0 (baseline accepted if generated)
./gradlew ktlintCheck            # US2 strict → exit 0
./gradlew assembleRelease        # US3 fallback → exit 0 with debug-signing warning
```

**Expected outcomes**:

- All six commands return exit code 0.
- `assembleRelease` log contains the literal warning string `"⚠️ release built with DEBUG signature — NOT for distribution"` (or equivalent per FR-013).
- Output APKs exist:
  - `app/build/outputs/apk/debug/app-debug.apk`
  - `app/build/outputs/apk/release/app-release.apk`

### 4. Release APK installability (US3 acceptance #1)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Expected**: install succeeds (debug-keystore signature is recognized by `adb`).

> Skip this step if no device/emulator is connected; the build itself is sufficient evidence for the gate.

### 5. 16KB verify script (FR-016, SC-005 partial)

```bash
# Run the same script CI uses (path will be set up in tasks.md)
.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
```

**Expected (Spec 001 baseline, no .so)**:
```
[16kb] no native libraries to verify (skip) — exit 0
```

### 6. Catalog single-source-of-truth (SC-007)

Spot-check `gradle/libs.versions.toml`:

- `[versions]` section contains entries for: `agp`, `kotlin`, `composeBom`, `coreKtx`, `lifecycleRuntimeKtx`, `activityCompose`, `material3` (or omitted if BOM-resolved), `junit`, `junitExt`, `espressoCore`, `detekt`, `ktlint`.
- `[libraries]` references each version via `version.ref = "..."`.
- `[plugins]` includes `android-application`, `kotlin-android`, `kotlin-compose`, `detekt`, `ktlint`.
- `[bundles]` includes `compose` aggregating Compose UI deps.

### 7. Static analysis correctness (SC-006)

```bash
# Add a deliberate violation to a temp file, verify detection
cat > app/src/main/kotlin/com/raumanian/thirtysix/browser/_temp_violation.kt <<'EOF'
package com.raumanian.thirtysix.browser
val deliberateMagicNumber = 4242
EOF

./gradlew detekt
# Expected: FAIL with MagicNumber violation reported
rm app/src/main/kotlin/com/raumanian/thirtysix/browser/_temp_violation.kt
```

**Expected**: detekt reports the violation (proves baseline does not mask new code) — then cleanup removes the file. Re-run `./gradlew detekt` → exit 0.

### 8. CI green on PR (US4)

Push the branch and open a pull request. Verify in GitHub Actions UI:

- Job "build" runs and contains exactly five Gradle steps: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck` — all green.
- The 16KB verify step runs and logs "no native libraries to verify (skip)".
- Job `instrumented-test` shows "skipped" (per `if: false`).
- Total workflow duration **< 10 minutes** on `ubuntu-latest`.

### 9. Documentation updates (FR-028, FR-029, FR-030)

After CI green:

```bash
# CLAUDE.md "Recent Changes" has a new dated entry
grep -A1 "Recent Changes" CLAUDE.md | grep "001" \
    && echo "✅ CLAUDE.md updated"

# project-context.md Key Decisions Log has version pin
grep -E "AGP 9\\.2|Kotlin 2\\.3" .claude/claude-app/project-context.md \
    && echo "✅ project-context.md decisions logged"

# sdd-roadmap.md marks 001 as Done
grep "001.*✅" .claude/claude-app/sdd-roadmap.md \
    && echo "✅ sdd-roadmap.md status updated"
```

## Done definition

Spec 001 is **DONE** when:

1. ✅ Steps 1–9 above all pass on a clean clone.
2. ✅ Quality checklist [requirements.md](checklists/requirements.md) all items checked.
3. ✅ PR merged into `main`.
4. ✅ CLAUDE.md, project-context.md, sdd-roadmap.md updated with concrete versions chosen (AGP 9.2.0, Kotlin 2.3.21, Gradle 9.5, BOM 2026.04.01).
5. ✅ No outstanding TODO comments in any spec-introduced code.

## If something fails

| Failure | Most likely cause | First action |
|---------|-------------------|--------------|
| `assembleDebug` fails on first run | JDK 17 launcher not on PATH | `java -version`; install/select JDK 17+ |
| `assembleDebug` fails with "JDK 11 not found" | Toolchain auto-download disabled | Add `org.gradle.java.installations.auto-download=true` to `gradle.properties` (default true, but verify) |
| `lintDebug` reports warnings | Template code triggered something | Per Q4 (no Lint baseline) — fix in code; do NOT add baseline |
| `detekt` crashes / false positives | CV-05 Kotlin 2.3 ↔ Detekt 1.23.8 incompat | Try `detekt { toolVersion = "1.23.8" }` decoupled; or escalate to bump to Detekt 2.x |
| `ktlintCheck` fails on template | Default ktlint rule mismatch | Run `./gradlew ktlintFormat` to auto-fix; commit |
| `assembleRelease` fails (not warns) without keystore | Signing config misconfigured | Re-check FR-013 implementation: must fall back to `signingConfigs.debug`, not fail |
| 16KB step fails on Spec 001 | Script not fail-soft | Verify script handles `lib/` absence per FR-016 — should exit 0 with skip message |
| CI step "instrumented-test" runs (not skipped) | `if: false` was removed | Restore `if: false` on the job per FR-023 |
