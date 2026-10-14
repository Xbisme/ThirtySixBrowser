package com.raumanian.thirtysix.browser.presentation.browser.protection

import com.raumanian.thirtysix.browser.core.domain.model.ProtectionData
import com.raumanian.thirtysix.browser.core.domain.model.PublicSuffixRules
import com.raumanian.thirtysix.browser.core.domain.model.TrackerHostSet
import com.raumanian.thirtysix.browser.core.domain.model.TrackingParamRules
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.protection.HttpsUpgradeFailureStore
import com.raumanian.thirtysix.browser.data.local.protection.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.domain.protection.ProtectionMatchersHolder
import com.raumanian.thirtysix.browser.domain.usecase.IsSiteProtectedUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeProtectionExceptionRepository
import kotlinx.coroutines.Dispatchers

/** A [ProtectionEngine] over small in-memory rules, for the Spec 023 presentation tests. */
internal class ProtectionTestEngine(loaded: Boolean = true) {
    val exceptions = FakeProtectionExceptionRepository()
    val incognito = IncognitoProtectionExceptionStore()
    val failures = HttpsUpgradeFailureStore()

    private val data = ProtectionData(
        trackers = TrackerHostSet(setOf("tracker.example", "ads.net")),
        suffixes = PublicSuffixRules(
            normal = setOf("com", "example", "net", "org"),
            wildcard = emptySet(),
            exception = emptySet(),
        ),
        params = TrackingParamRules(names = setOf("fbclid"), prefixes = listOf("utm_")),
    )

    private val repository = object : ProtectionDataRepository {
        override fun preload() = Unit

        override fun currentDataOrNull(): ProtectionData? = if (loaded) data else null

        override fun awaitData(timeoutMs: Long): ProtectionData? = if (loaded) data else null
    }

    val engine = ProtectionEngine(
        matchers = ProtectionMatchersHolder(repository, IsSiteProtectedUseCase(exceptions, incognito), failures),
        isSiteProtected = IsSiteProtectedUseCase(exceptions, incognito),
        failures = failures,
        dispatchers = Direct,
    )

    private object Direct : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
