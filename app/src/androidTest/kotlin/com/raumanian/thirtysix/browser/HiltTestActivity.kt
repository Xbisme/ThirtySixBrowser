package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Spec 007 — minimal `@AndroidEntryPoint` Activity for instrumented Compose tests.
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
