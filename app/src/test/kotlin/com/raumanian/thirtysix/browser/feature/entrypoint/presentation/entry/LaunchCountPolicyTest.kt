package com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 020 T041 — FR-024a's launch rule, all four combinations. */
class LaunchCountPolicyTest {

    @Test
    fun `a plain start counts`() {
        assertTrue(LaunchCountPolicy.shouldCount(stoppedForConfigChange = false, returningFromOwnSystemScreen = false))
    }

    @Test
    fun `a rotation does not count`() {
        assertFalse(LaunchCountPolicy.shouldCount(stoppedForConfigChange = true, returningFromOwnSystemScreen = false))
    }

    @Test
    fun `returning from the default-browser screen does not count`() {
        assertFalse(LaunchCountPolicy.shouldCount(stoppedForConfigChange = false, returningFromOwnSystemScreen = true))
    }

    @Test
    fun `both at once does not count`() {
        assertFalse(LaunchCountPolicy.shouldCount(stoppedForConfigChange = true, returningFromOwnSystemScreen = true))
    }
}
