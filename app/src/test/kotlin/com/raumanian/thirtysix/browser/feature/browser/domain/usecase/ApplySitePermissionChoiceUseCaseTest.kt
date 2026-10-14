package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability.Camera
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability.Microphone
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 021 T010 — contracts C1.2 (INV-1: incognito never writes). */
class ApplySitePermissionChoiceUseCaseTest {

    private val repo = FakeSitePermissionRepository()
    private val apply = ApplySitePermissionChoiceUseCase(repo)
    private val site = "https://meet.example"
    private val both = setOf(Camera, Microphone)

    @Test
    fun `always allow in a normal tab remembers allow for every capability`() = runTest {
        val effect = apply(SitePermissionChoice.AlwaysAllow, site, both, isIncognito = false)
        assertEquals(SiteChoiceEffect(allowNow = true, grantOnce = false), effect)
        assertEquals(mapOf(Camera to SiteDecision.Allow, Microphone to SiteDecision.Allow), repo.decisionsFor(site))
    }

    @Test
    fun `allow this time stores nothing and grants for the page`() = runTest {
        val effect = apply(SitePermissionChoice.AllowOnce, site, both, isIncognito = false)
        assertEquals(SiteChoiceEffect(allowNow = true, grantOnce = true), effect)
        assertTrue(repo.rows.value.isEmpty())
    }

    @Test
    fun `don't allow in a normal tab remembers deny`() = runTest {
        val effect = apply(SitePermissionChoice.Deny, site, both, isIncognito = false)
        assertEquals(SiteChoiceEffect(allowNow = false, grantOnce = false), effect)
        assertEquals(mapOf(Camera to SiteDecision.Deny, Microphone to SiteDecision.Deny), repo.decisionsFor(site))
    }

    @Test
    fun `incognito never writes, whatever the choice`() = runTest {
        assertEquals(
            SiteChoiceEffect(allowNow = true, grantOnce = true),
            apply(SitePermissionChoice.AlwaysAllow, site, both, isIncognito = true),
        )
        assertEquals(
            SiteChoiceEffect(allowNow = true, grantOnce = true),
            apply(SitePermissionChoice.AllowOnce, site, both, isIncognito = true),
        )
        assertEquals(
            SiteChoiceEffect(allowNow = false, grantOnce = false),
            apply(SitePermissionChoice.Deny, site, both, isIncognito = true),
        )
        assertTrue(repo.rows.value.isEmpty())
    }
}
