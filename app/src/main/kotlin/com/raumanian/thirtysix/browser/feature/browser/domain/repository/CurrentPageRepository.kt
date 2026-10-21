package com.raumanian.thirtysix.browser.feature.browser.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.CurrentPage
import kotlinx.coroutines.flow.StateFlow

/** The one source of the page state the browser screen's holders share; every write is visible at once. */
interface CurrentPageRepository {
    val page: StateFlow<CurrentPage>

    fun setUrl(url: String)

    fun setTitle(title: String)

    /** A different tab id starts a new page: the generation moves on and the title is cleared. */
    fun setActiveTab(tab: Tab?)

    /** A load started: the generation moves on and the previous page's title is dropped. */
    fun startNewPage()
}
