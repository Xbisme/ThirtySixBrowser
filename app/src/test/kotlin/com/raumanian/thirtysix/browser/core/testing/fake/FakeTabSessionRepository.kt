package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeTabSessionRepository : TabSessionRepository {
    val sessions = mutableMapOf<Long, TabSession>()
    private val clearCount = MutableStateFlow(0L)

    override val clears: StateFlow<Long> = clearCount.asStateFlow()

    override suspend fun get(tabId: Long): TabSession? = sessions[tabId]

    override suspend fun save(session: TabSession) {
        sessions[session.tabId] = session
    }

    override suspend fun retainOnly(tabIds: Set<Long>) {
        sessions.keys.retainAll(tabIds)
    }

    override suspend fun clearAll() {
        sessions.clear()
        clearCount.update { it + 1 }
    }
}
