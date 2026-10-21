package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.repository.CaptureCache

class FakeCaptureCache : CaptureCache {
    var clearCount: Int = 0
        private set

    override suspend fun clear() {
        clearCount++
    }
}
