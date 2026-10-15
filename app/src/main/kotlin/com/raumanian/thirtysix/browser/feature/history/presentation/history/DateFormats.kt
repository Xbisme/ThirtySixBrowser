package com.raumanian.thirtysix.browser.feature.history.presentation.history

import java.time.format.FormatStyle

// Format styles, not patterns: formatters are built from the current locale at composition time.
object DateFormats {
    val HISTORY_DAY_HEADER_STYLE: FormatStyle = FormatStyle.MEDIUM

    val HISTORY_TIME_OF_VISIT_STYLE: FormatStyle = FormatStyle.SHORT
}
