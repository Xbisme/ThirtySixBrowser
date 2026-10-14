package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.ProtectionData
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.data.local.protection.ProtectionDataSource
import javax.inject.Inject

/** Spec 023 — [ProtectionDataRepository] over the bundled assets. */
class ProtectionDataRepositoryImpl @Inject constructor(
    private val source: ProtectionDataSource,
) : ProtectionDataRepository {
    override fun preload() = source.preload()

    override fun currentDataOrNull(): ProtectionData? = source.currentDataOrNull()

    override fun awaitData(timeoutMs: Long): ProtectionData? = source.awaitData(timeoutMs)
}
