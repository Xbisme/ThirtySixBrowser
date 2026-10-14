package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.FireproofSiteRepository
import javax.inject.Inject

/**
 * Spec 024 data-model "Fireproof matching rule" — whether [host] belongs to a fireproof site.
 *
 * Saved entries are registrable domains, so "the host's registrable domain is in the list" is the
 * same as "the host is an entry or a subdomain of one": a host always ends with its own registrable
 * domain, and `bob.github.io` never ends with `.alice.github.io`. The suffix test needs no public
 * suffix data, so it also works before Spec 023's lists have loaded. Synchronous; safe on any thread.
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
