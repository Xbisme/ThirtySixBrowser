package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.TabSession
import com.raumanian.thirtysix.browser.core.domain.repository.TabSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NoopTabSessionRepository : TabSessionRepository {
    override val clears: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun get(tabId: Long): TabSession? = null
    override suspend fun save(session: TabSession) = Unit
    override suspend fun retainOnly(tabIds: Set<Long>) = Unit
    override suspend fun clearAll() = Unit
}
