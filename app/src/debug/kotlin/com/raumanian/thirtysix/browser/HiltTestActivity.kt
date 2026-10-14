package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * `@AndroidEntryPoint` host for instrumented Compose tests. In `debug/`, not `androidTest/`, so it
 * runs in the app process; an activity declared in the test APK fails `ActivityScenarioRule` with
 * "resolved to different process".
 */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
