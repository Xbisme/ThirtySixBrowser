package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearBrowsingHistoryUseCaseTest {

    @Test
    fun `history rows and every tab's saved back and forward are cleared`() = runTest {
        val history = FakeHistoryRepository()
        val tabSessions = FakeTabSessionRepository()
        history.recordVisit("https://a.test/", "A", 1L)
        tabSessions.save(TabSession(1L, byteArrayOf(1), 1L))
        tabSessions.save(TabSession(-1L, byteArrayOf(2), 1L))

        ClearBrowsingHistoryUseCase(history, tabSessions)()

        assertTrue(history.recorded.isEmpty())
        assertTrue(tabSessions.sessions.isEmpty())
        assertEquals(1L, tabSessions.clears.value)
    }
}
