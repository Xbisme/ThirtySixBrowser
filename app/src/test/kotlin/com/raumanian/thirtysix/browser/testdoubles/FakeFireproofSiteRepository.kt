package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.domain.model.FireproofSite
import com.raumanian.thirtysix.browser.domain.repository.FireproofSiteRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Spec 024 — in-memory [FireproofSiteRepository]; [writes] counts every write (INV-4 checks). */
class FakeFireproofSiteRepository(initial: Set<String> = emptySet()) : FireproofSiteRepository {
    private val state = MutableStateFlow(initial.map { FireproofSite(it, Instant.EPOCH) })
    var writes: Int = 0
        private set

    override fun observeAll(): Flow<List<FireproofSite>> = state.map { it.reversed() }
    override fun currentSites(): Set<String> = state.value.mapTo(HashSet()) { it.site }

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
