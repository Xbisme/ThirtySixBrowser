package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile

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

    override fun sessionProfileName(): String = "fake-incognito-$begun"

    override fun collectLeftovers() = Unit

    override fun isSessionProfile(name: String): Boolean = name.startsWith("fake-incognito-")

    override fun existingProfileNames(): List<String> = emptyList()
}
