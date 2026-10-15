package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import javax.inject.Inject

/**
 * Whether [host] belongs to a fireproof site. Entries are registrable domains, so a plain suffix
 * test is exact and needs no public-suffix data (it works before the protection lists load).
 */
class IsSiteFireproofUseCase @Inject constructor(
    private val repository: FireproofSiteRepository,
) {
    operator fun invoke(host: String): Boolean = matches(host, repository.currentSites())

    companion object {
        /** [host] is one of [sites] or a subdomain of one; case-insensitive, a trailing dot ignored. */
        fun matches(host: String, sites: Set<String>): Boolean {
            val name = host.trim().trimEnd('.').lowercase()
            return name.isNotEmpty() && sites.any { site -> name == site || name.endsWith(".$site") }
        }
    }
}
