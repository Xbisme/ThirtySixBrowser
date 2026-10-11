package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.SiteProtectionException
import com.raumanian.thirtysix.browser.domain.repository.ProtectionExceptionRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Spec 023 — in-memory [ProtectionExceptionRepository]. */
class FakeProtectionExceptionRepository : ProtectionExceptionRepository {
    private val rows = MutableStateFlow<List<SiteProtectionException>>(emptyList())
    private var clock = 0L

    override fun observeAll(): Flow<List<SiteProtectionException>> =
        rows.map { list -> list.sortedByDescending { it.addedAt } }

    override fun currentSites(): Set<String> = rows.value.mapTo(HashSet()) { it.site }

    override suspend fun add(site: String): Result<Unit> {
        val added = SiteProtectionException(site, Instant.ofEpochMilli(++clock))
        rows.value = rows.value.filterNot { it.site == site } + added
        return Result.Success(Unit)
    }

    override suspend fun remove(site: String): Result<Unit> {
        rows.value = rows.value.filterNot { it.site == site }
        return Result.Success(Unit)
    }

    override suspend fun clearAll(): Result<Unit> {
        rows.value = emptyList()
        return Result.Success(Unit)
    }
}
