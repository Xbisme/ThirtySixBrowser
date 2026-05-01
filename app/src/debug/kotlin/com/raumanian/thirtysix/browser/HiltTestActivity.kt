package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Spec 007 — minimal `@AndroidEntryPoint` Activity for instrumented Compose tests.
 *
 * Lives in `app/src/debug/` (not `androidTest/`) so the class is bundled into
 * the debug APK and the activity intent resolves within the main app process —
 * `androidTest/`-only declarations land in the test APK process and cause
 * `RuntimeException: Intent ... resolved to different process` from
 * `ActivityScenarioRule` (the original Spec 007 CI failure mode).
 *
 * Hosts a single Composable injected by the test via `setContent { ... }`. Avoids
 * pulling MainActivity's full nav graph + theme observation when the test only
 * cares about one screen.
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
