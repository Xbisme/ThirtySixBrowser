package com.raumanian.thirtysix.browser

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Spec 007 — instrumented-test runner that boots [HiltTestApplication] instead
 * of the production `@HiltAndroidApp` Application. Required for `@HiltAndroidTest`
 * + `@TestInstallIn` (TestUrlConfigModule) to work in `androidTest`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
