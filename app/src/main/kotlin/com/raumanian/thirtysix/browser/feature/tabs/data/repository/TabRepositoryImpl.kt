package com.raumanian.thirtysix.browser.feature.tabs.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.TabDao
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.TabEntity
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.domain.util.TabOrder
import com.raumanian.thirtysix.browser.feature.tabs.data.mapper.toDomain
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Normal tabs over [TabDao], adding seeding of one home tab when empty, ordering by
 * `lastActiveAt` descending then `id` (the DAO orders by `position`), and a cap that fails
 * [createTab] with [MaxTabsReachedException]. Seeding, creating and closing share one [Mutex].
 */
@Suppress("TooManyFunctions") // One function per contract member.
class TabRepositoryImpl @Inject constructor(
    private val tabDao: TabDao,
    @param:Named("default_home_url") private val homeUrl: String,
    private val dispatchers: DispatcherProvider,
) : TabRepository {

    private val seedMutex: Mutex = Mutex()

    override fun observeTabs(): Flow<List<Tab>> = tabDao.observeAll()
        .onStart { ensureAtLeastOneTab() }
        .map { entities ->
            // A malformed row left by a newer or downgraded build is dropped rather than
            // crashing cold start.
            entities
                .mapNotNull { entity -> runCatching { entity.toDomain() }.getOrNull() }
                .sortedWith(
                    compareByDescending(Tab::lastActiveAt).thenBy(Tab::id),
                )
        }
        .flowOn(dispatchers.io)

    override suspend fun createTab(url: String): Result<Tab> = insertTab(url, lastActiveAt = null)

    override suspend fun createBackgroundTab(url: String, activeAt: Long): Result<Tab> =
        insertTab(url, lastActiveAt = activeAt - 1)

    /** [lastActiveAt] `null` → now, making the new tab the active one. */
    private suspend fun insertTab(url: String, lastActiveAt: Long?): Result<Tab> = seedMutex.withLock {
        if (tabDao.count() >= BrowserLimits.MAX_TABS) {
            return Result.Error(throwable = MaxTabsReachedException())
        }
        val now = System.currentTimeMillis()
        val nextPosition = (tabDao.maxPosition() ?: -1) + 1
        val entity = TabEntity(
            url = url,
            title = "",
            position = nextPosition,
            createdAt = now,
            lastActiveAt = lastActiveAt ?: now,
        )
        val newId = tabDao.insert(entity)
        Result.Success(entity.copy(id = newId).toDomain())
    }

    override suspend fun switchActiveTab(tabId: Long) {
        val tab = tabDao.getById(tabId) ?: return
        tabDao.update(tab.copy(lastActiveAt = System.currentTimeMillis()))
    }

    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        val tab = tabDao.getById(tabId) ?: return
        if (tab.url == url && tab.title == title) return
        tabDao.update(tab.copy(url = url, title = title))
    }

    override suspend fun closeTab(tabId: Long) {
        seedMutex.withLock {
            val rows = tabDao.getAll()
            val tab = rows.firstOrNull { it.id == tabId } ?: return
            tabDao.delete(tab)
            if (rows.size == 1) {
                seedHomeTabLocked()
            } else if (tab.lastActiveAt >= rows.maxOf(TabEntity::lastActiveAt)) {
                handActiveMark(rows, tab)
            }
        }
    }

    /**
     * The heir inherits the closed tab's stamp instead of "now", so a normal heir never jumps ahead
     * of an incognito tab that is active in front of it.
     */
    private suspend fun handActiveMark(rows: List<TabEntity>, closed: TabEntity) {
        val tabs = rows.mapNotNull { entity -> runCatching { entity.toDomain() }.getOrNull() }
        val heirId = TabOrder.heirOf(tabs, closed.id)?.id ?: return
        val heir = rows.first { it.id == heirId }
        tabDao.update(heir.copy(lastActiveAt = maxOf(heir.lastActiveAt, closed.lastActiveAt)))
    }

    override suspend fun closeAllTabs() {
        seedMutex.withLock {
            tabDao.deleteAll()
            seedHomeTabLocked()
        }
    }

    private suspend fun ensureAtLeastOneTab() {
        // Fast path — most cold starts already have ≥ 1 tab.
        if (tabDao.count() > 0) return
        seedMutex.withLock {
            // Double-check inside the lock to avoid double-seeding under concurrent collectors.
            if (tabDao.count() == 0) seedHomeTabLocked()
        }
    }

    /** Caller must hold [seedMutex]. Inserts a fresh home tab at position 0. */
    private suspend fun seedHomeTabLocked() {
        val now = System.currentTimeMillis()
        tabDao.insert(
            TabEntity(
                url = homeUrl,
                title = "",
                position = 0,
                createdAt = now,
                lastActiveAt = now,
            ),
        )
    }
}
