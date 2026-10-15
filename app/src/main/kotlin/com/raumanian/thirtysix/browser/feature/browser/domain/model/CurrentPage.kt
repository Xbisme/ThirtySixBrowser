package com.raumanian.thirtysix.browser.feature.browser.domain.model

import com.raumanian.thirtysix.browser.core.domain.model.Tab

/**
 * What the browser screen's holders share about the page on screen. [generation] changes on every
 * new load and every switch of active tab.
 */
data class CurrentPage(
    val url: String,
    val title: String = "",
    val activeTab: Tab? = null,
    val isIncognito: Boolean = false,
    val generation: Long = 0L,
)
