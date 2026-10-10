package com.raumanian.thirtysix.browser.domain.model

import java.time.LocalTime

/**
 * Spec 019 FR-011 — the Home page greeting by time of day (never a name: there is no account).
 * Morning 05:00–11:59, afternoon 12:00–17:59, evening otherwise.
 */
enum class GreetingPeriod {
    Morning,
    Afternoon,
    Evening,
    ;

    companion object {
        private val MORNING_START: LocalTime = LocalTime.of(5, 0)
        private val AFTERNOON_START: LocalTime = LocalTime.NOON
        private val EVENING_START: LocalTime = LocalTime.of(18, 0)

        fun of(time: LocalTime): GreetingPeriod = when {
            time < MORNING_START -> Evening
            time < AFTERNOON_START -> Morning
            time < EVENING_START -> Afternoon
            else -> Evening
        }
    }
}
