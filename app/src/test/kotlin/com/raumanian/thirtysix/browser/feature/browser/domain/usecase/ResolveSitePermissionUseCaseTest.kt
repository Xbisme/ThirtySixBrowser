package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability.Camera
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability.Location
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability.Microphone
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 021 T009 — rows 1–10 of contracts C1.1. */
class ResolveSitePermissionUseCaseTest {

    private val repo = FakeSitePermissionRepository()
    private val resolve = ResolveSitePermissionUseCase(repo)
    private val site = "https://meet.example"

    @Test
    fun `1 an insecure site is refused without asking`() = runTest {
        assertEquals(SitePermissionOutcome.Deny, resolve("http://meet.example", setOf(Camera), isIncognito = false))
    }

    @Test
    fun `2 an unsupported capability refuses the whole request`() = runTest {
        assertEquals(SitePermissionOutcome.Deny, resolve(site, emptySet(), isIncognito = false, hasUnsupported = true))
    }

    @Test
    fun `3 remembered allow for everything grants`() = runTest {
        repo.save(site, Camera, SiteDecision.Allow)
        repo.save(site, Microphone, SiteDecision.Allow)
        assertEquals(SitePermissionOutcome.Grant, resolve(site, setOf(Camera, Microphone), isIncognito = false))
    }

    @Test
    fun `4 a remembered deny for any capability denies`() = runTest {
        repo.save(site, Camera, SiteDecision.Allow)
        repo.save(site, Microphone, SiteDecision.Deny)
        assertEquals(SitePermissionOutcome.Deny, resolve(site, setOf(Camera, Microphone), isIncognito = false))
    }

    @Test
    fun `5 nothing remembered asks for everything`() = runTest {
        assertEquals(SitePermissionOutcome.Ask(setOf(Location)), resolve(site, setOf(Location), isIncognito = false))
    }

    @Test
    fun `6 only the undecided capabilities are asked`() = runTest {
        repo.save(site, Camera, SiteDecision.Allow)
        assertEquals(
            SitePermissionOutcome.Ask(setOf(Microphone)),
            resolve(site, setOf(Camera, Microphone), isIncognito = false),
        )
    }

    @Test
    fun `7 a one-time grant for this page grants`() = runTest {
        assertEquals(
            SitePermissionOutcome.Grant,
            resolve(site, setOf(Camera), isIncognito = false, grantedOnce = { it == Camera }),
        )
    }

    @Test
    fun `8 without a one-time grant for this page it asks`() = runTest {
        assertEquals(SitePermissionOutcome.Ask(setOf(Camera)), resolve(site, setOf(Camera), isIncognito = false))
    }

    @Test
    fun `9 incognito ignores remembered decisions`() = runTest {
        repo.save(site, Camera, SiteDecision.Allow)
        assertEquals(SitePermissionOutcome.Ask(setOf(Camera)), resolve(site, setOf(Camera), isIncognito = true))
        repo.save(site, Camera, SiteDecision.Deny)
        assertEquals(SitePermissionOutcome.Ask(setOf(Camera)), resolve(site, setOf(Camera), isIncognito = true))
    }

    @Test
    fun `10 incognito honours a one-time grant for this page`() = runTest {
        assertEquals(
            SitePermissionOutcome.Grant,
            resolve(site, setOf(Camera), isIncognito = true, grantedOnce = { true }),
        )
    }
}
