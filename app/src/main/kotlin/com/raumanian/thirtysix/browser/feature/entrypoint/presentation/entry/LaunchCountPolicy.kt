package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

/**
 * Whether an `onStart` counts as an app launch: not when returning from a configuration change or
 * from a system screen the app itself opened (default-browser dialog, default-apps screen).
 */
object LaunchCountPolicy {
    fun shouldCount(stoppedForConfigChange: Boolean, returningFromOwnSystemScreen: Boolean): Boolean =
        !stoppedForConfigChange && !returningFromOwnSystemScreen
}
