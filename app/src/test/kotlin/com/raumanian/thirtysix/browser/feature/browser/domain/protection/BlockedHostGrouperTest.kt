package com.raumanian.thirtysix.browser.feature.browser.domain.protection

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 024 T047 — blocked hosts grouped by site (FR-016, clarified). */
class BlockedHostGrouperTest {
    private val site: (String) -> String = { host -> host.split('.').takeLast(2).joinToString(".") }

    @Test
    fun `hosts are grouped by site, largest group first, names sorted`() {
        val groups = BlockedHostGrouper.group(
            listOf("stats.b.test", "a.test", "pixel.b.test", "ads.c.test", "x.a.test", "pixel.b.test"),
            site,
        )

        assertEquals(
            listOf(
                BlockedSiteGroup("a.test", listOf("a.test", "x.a.test")),
                BlockedSiteGroup("b.test", listOf("pixel.b.test", "stats.b.test")),
                BlockedSiteGroup("c.test", listOf("ads.c.test")),
            ),
            groups,
        )
    }

    @Test
    fun `no hosts give no groups`() {
        assertEquals(emptyList<BlockedSiteGroup>(), BlockedHostGrouper.group(emptyList(), site))
    }
}
