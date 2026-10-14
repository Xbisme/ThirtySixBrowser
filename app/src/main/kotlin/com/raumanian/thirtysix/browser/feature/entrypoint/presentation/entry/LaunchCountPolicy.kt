package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

/**
 * Spec 020 FR-024a / research R9 — whether an `onStart` counts as an app launch.
 *
 * It does, unless the activity is only coming back from a configuration change (rotation), or
 * from a system screen the app itself opened (the default-browser confirmation or the
 * default-apps screen). Pure, so the rule is tested on the JVM; the activity supplies the flags.
 */
object LaunchCountPolicy {
    fun shouldCount(stoppedForConfigChange: Boolean, returningFromOwnSystemScreen: Boolean): Boolean =
        !stoppedForConfigChange && !returningFromOwnSystemScreen
}
