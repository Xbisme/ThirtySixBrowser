package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

sealed interface TabSessionAction {
    class Save(val tabId: Long, val state: ByteArray) : TabSessionAction
}
