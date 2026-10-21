package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.Result
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [IncognitoTabRepository] without the cookie-snapshot side effects; tests of the
 * snapshot lifecycle use the production impl with a [FakeCookieJarSnapshotManager].
 */
class FakeIncognitoTabRepository : IncognitoTabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    private val nextId: AtomicLong = AtomicLong(0L)

    fun emit(list: List<Tab>) = state.update { list }

    fun snapshot(): List<Tab> = state.value

    override fun observeTabs(): Flow<List<Tab>> = state.asStateFlow()

    override suspend fun createTab(url: String): Result<Tab> {
        if (state.value.size >= BrowserLimits.MAX_INCOGNITO_TABS) {
            return Result.Error(throwable = MaxIncognitoTabsReachedException())
        }
        val now = System.currentTimeMillis()
        val tab = Tab(
            id = nextId.decrementAndGet(),
            url = url,
            title = "",
            position = state.value.size,
            createdAt = now,
            lastActiveAt = now,
            isIncognito = true,
        )
        state.update { it + tab }
        return Result.Success(tab)
    }

    override suspend fun switchActiveTab(tabId: Long) {
        state.update { list ->
            list.map { if (it.id == tabId) it.copy(lastActiveAt = System.currentTimeMillis()) else it }
        }
    }

    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        state.update { list ->
            list.map { if (it.id == tabId) it.copy(url = url, title = title) else it }
        }
    }

    override suspend fun closeTab(tabId: Long) {
        state.update { it.filterNot { tab -> tab.id == tabId } }
    }

    override suspend fun closeAll() {
        state.update { emptyList() }
    }
}
