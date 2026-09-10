package com.raumanian.thirtysix.browser.core.constants

import java.time.format.FormatStyle

/**
 * Spec 014 — locale-aware date/time format style constants used by the History screen.
 *
 * Reserved per Constitution §III table row "Date/time formats → core/constants/DateFormats.kt".
 * Future date format anchors (e.g., download list, history retention preview) live here as
 * the corresponding specs ship.
 *
 * The actual `DateTimeFormatter` instances are derived at composition time using the current
 * device locale via `DateTimeFormatter.ofLocalizedDate(...)` / `ofLocalizedTime(...)` so the
 * format follows FR-029 (locale conventions) without us hard-coding pattern strings here.
 */
object DateFormats {
    /**
     * Spec 014 FR-008 — explicit-date day-header bucket. Medium style (e.g. "Jan 12, 2026"
     * in EN, "12 janv. 2026" in FR, "2026年1月12日" in JA) keeps the header readable but
     * compact in a list section header.
     */
    val HISTORY_DAY_HEADER_STYLE: FormatStyle = FormatStyle.MEDIUM

    /**
     * Spec 014 FR-009 / FR-029 — time-of-visit shown on each row. Short style (e.g. "14:32"
     * in 24-hour locales, "2:32 PM" in EN US) follows the user's locale convention.
     */
    val HISTORY_TIME_OF_VISIT_STYLE: FormatStyle = FormatStyle.SHORT
}
