package com.raumanian.thirtysix.browser.feature.browser.domain.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Greeting period boundaries. */
class GreetingPeriodTest {

    @Test
    fun boundaries() {
        mapOf(
            LocalTime.of(4, 59) to GreetingPeriod.Evening,
            LocalTime.of(5, 0) to GreetingPeriod.Morning,
            LocalTime.of(11, 59) to GreetingPeriod.Morning,
            LocalTime.NOON to GreetingPeriod.Afternoon,
            LocalTime.of(17, 59) to GreetingPeriod.Afternoon,
            LocalTime.of(18, 0) to GreetingPeriod.Evening,
            LocalTime.MIDNIGHT to GreetingPeriod.Evening,
        ).forEach { (time, expected) -> assertEquals("at $time", expected, GreetingPeriod.of(time)) }
    }
}
