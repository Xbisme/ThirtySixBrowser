// Spec 022 — the app-wide text size of web pages (FR-029 – FR-032).

package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/**
 * Spec 022 data-model §1 — one text size for every page, as a percentage of the page's own size.
 *
 * Exactly nine levels (FR-029). Persisted as [percent]; [fromPercentOrDefault] maps anything else
 * (a missing key, a corrupt value, a value written by a future build) back to the default, so
 * the web engine only ever receives one of the nine (I-1).
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
        /** Decodes a stored percentage; anything outside the nine levels yields the default. */
        fun fromPercentOrDefault(value: Int?): TextSize =
            entries.firstOrNull { it.percent == value } ?: AppDefaults.TEXT_SIZE
    }
}
