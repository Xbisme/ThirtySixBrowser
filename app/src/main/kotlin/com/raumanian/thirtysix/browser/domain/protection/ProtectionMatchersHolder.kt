package com.raumanian.thirtysix.browser.domain.protection

import com.raumanian.thirtysix.browser.core.domain.model.ProtectionData
import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import com.raumanian.thirtysix.browser.domain.usecase.IsSiteProtectedUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 023 research R10 — builds the matchers once, after the bundled lists have loaded.
 * [matchersOrNull] never blocks (main thread); [awaitMatchers] is for background threads only.
 */
@Singleton
class ProtectionMatchersHolder @Inject constructor(
    private val data: ProtectionDataRepository,
    private val isSiteProtected: IsSiteProtectedUseCase,
    private val failures: HttpsUpgradeFailures,
) {
    class Matchers(
        val suffixes: PublicSuffixMatcher,
        val trackers: TrackerMatcher,
        val preparer: NavigationPreparer,
    )

    @Volatile
    private var matchers: Matchers? = null

    fun matchersOrNull(): Matchers? = matchers ?: data.currentDataOrNull()?.let(::build)

    fun awaitMatchers(timeoutMs: Long): Matchers? = matchers ?: data.awaitData(timeoutMs)?.let(::build)

    @Synchronized
    private fun build(loaded: ProtectionData): Matchers = matchers ?: run {
        val suffixes = PublicSuffixMatcher(loaded.suffixes, BrowserLimits.REGISTRABLE_DOMAIN_CACHE_SIZE)
        Matchers(
            suffixes = suffixes,
            trackers = TrackerMatcher(loaded.trackers, suffixes),
            preparer = NavigationPreparer(
                stripper = TrackingParamStripper(loaded.params),
                suffixes = suffixes,
                isSiteProtected = { site, incognito -> isSiteProtected(site, incognito) },
                failedHosts = { failures.snapshot() },
            ),
        ).also { matchers = it }
    }
}
