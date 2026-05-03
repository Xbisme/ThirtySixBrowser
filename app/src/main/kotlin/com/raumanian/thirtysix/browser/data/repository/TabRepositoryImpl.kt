package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.data.local.dao.TabDao
import com.raumanian.thirtysix.browser.data.local.entity.TabEntity
import com.raumanian.thirtysix.browser.data.mapper.toDomain
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spec 011 — concrete [TabRepository].
 *
 * Wraps Spec 005's [TabDao]. Adds three behaviors that the bare DAO does not
 * provide: (1) auto-seed on empty (FR-019 / R5), (2) `MAX(last_active_at)`-
 * derived active-tab pointer + DESC ordering by `lastActiveAt` then ASC by
 * `id` (FR-014 / R1 / H1) — done as a client-side resort because Spec 005's
 * `TabDao.observeAll()` orders by `position` ASC, (3) cap-guarded
 * [createTab] returning a [MaxTabsReachedException] sentinel inside
 * `Result.Error` (C2 / FR-016).
 *
 * Empty-state seeding + close-all atomicity are guarded by a single internal
 * [Mutex] to serialize concurrent collectors / writes (R5).
 */
class TabRepositoryImpl @Inject constructor(
    private val tabDao: TabDao,
    @param:Named("default_home_url") private val homeUrl: String,
    private val dispatchers: DispatcherProvider,
) : TabRepository {

    private val seedMutex: Mutex = Mutex()

    override fun observeTabs(): Flow<List<Tab>> = tabDao.observeAll()
        .onStart { ensureAtLeastOneTab() }
        .map { entities ->
            entities
                .map(TabEntity::toDomain)
                .sortedWith(
                    compareByDescending(Tab::lastActiveAt).thenBy(Tab::id),
                )
        }
        .flowOn(dispatchers.io)

    override suspend fun createTab(url: String): Result<Tab> {
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
            lastActiveAt = now,
        )
        val newId = tabDao.insert(entity)
        return Result.Success(entity.copy(id = newId).toDomain())
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
        val tab = tabDao.getById(tabId) ?: return
        seedMutex.withLock {
            tabDao.delete(tab)
            if (tabDao.count() == 0) {
                seedHomeTabLocked()
            }
        }
    }

    override suspend fun closeAllTabs() {
        seedMutex.withLock {
            tabDao.deleteAll()
            seedHomeTabLocked()
        }
    }

    override suspend fun getTabCount(): Int = tabDao.count()

    private suspend fun ensureAtLeastOneTab() {
        // Fast path — most cold starts already have ≥ 1 tab.
        if (tabDao.count() > 0) return
        seedMutex.withLock {
            // Double-check inside the lock to avoid double-seeding under concurrent collectors.
            if (tabDao.count() == 0) seedHomeTabLocked()
        }
    }

    /** Caller MUST hold [seedMutex]. Inserts a fresh home tab at position 0. */
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
