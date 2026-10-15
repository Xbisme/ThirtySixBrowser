package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/**
 * One text size for every page, as a percentage of the page's own size. Any stored value outside
 * the nine levels decodes to the default, so the web engine only ever receives one of them.
 */
enum class TextSize(val percent: Int) {
    Percent50(BrowserLimits.TEXT_SIZE_PERCENT_50),
    Percent75(BrowserLimits.TEXT_SIZE_PERCENT_75),
    Percent90(BrowserLimits.TEXT_SIZE_PERCENT_90),
    Percent100(BrowserLimits.TEXT_SIZE_PERCENT_100),
    Percent110(BrowserLimits.TEXT_SIZE_PERCENT_110),
    Percent125(BrowserLimits.TEXT_SIZE_PERCENT_125),
    Percent150(BrowserLimits.TEXT_SIZE_PERCENT_150),
    Percent175(BrowserLimits.TEXT_SIZE_PERCENT_175),
    Percent200(BrowserLimits.TEXT_SIZE_PERCENT_200),
    ;

    companion object {
        fun fromPercentOrDefault(value: Int?): TextSize =
            entries.firstOrNull { it.percent == value } ?: AppDefaults.TEXT_SIZE
    }
}
