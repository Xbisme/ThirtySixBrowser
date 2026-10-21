package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

/** A certificate error fails the page only for the main page's own address. */
internal fun isMainFrameSslError(errorUrl: String?, mainFrameUrl: String?): Boolean =
    mainFrameUrl == null || errorUrl == mainFrameUrl
