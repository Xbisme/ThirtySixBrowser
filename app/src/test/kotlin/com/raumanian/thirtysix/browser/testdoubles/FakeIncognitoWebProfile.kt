package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.data.local.webview.IncognitoWebProfile

/** Records incognito profile sessions. [isolated] `false` sends the repository down Spec 012's snapshot path. */
class FakeIncognitoWebProfile(var isolated: Boolean = false) : IncognitoWebProfile {
    var begun = 0
        private set
    var ended = 0
        private set

    override fun isIsolated(): Boolean = isolated

    override fun beginSession() {
        begun++
    }

    override fun endSession() {
        ended++
    }
}
