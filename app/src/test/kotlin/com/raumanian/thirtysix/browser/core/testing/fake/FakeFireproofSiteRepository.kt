package com.raumanian.thirtysix.browser.core.testing.fake

import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [FireproofSiteRepository]; [writes] counts every write. With [cacheLoaded] false,
 * [currentSites] answers empty, like the real cache before its first Room read.
 */
class FakeFireproofSiteRepository(initial: Set<String> = emptySet()) : FireproofSiteRepository {
    private val state = MutableStateFlow(initial.map { FireproofSite(it, Instant.EPOCH) })
    var writes: Int = 0
        private set
    var cacheLoaded: Boolean = true

    override fun observeAll(): Flow<List<FireproofSite>> = state.map { it.reversed() }
    override fun currentSites(): Set<String> =
        if (cacheLoaded) state.value.mapTo(HashSet()) { it.site } else emptySet()

    override suspend fun add(site: String): Result<Unit> {
        writes++
        if (state.value.none { it.site == site }) state.value = state.value + FireproofSite(site, Instant.now())
        return Result.Success(Unit)
    }

    override suspend fun remove(site: String): Result<Unit> {
        writes++
        state.value = state.value.filterNot { it.site == site }
        return Result.Success(Unit)
    }

    override suspend fun clearAll(): Result<Unit> {
        writes++
        state.value = emptyList()
        return Result.Success(Unit)
    }
}
