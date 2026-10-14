package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 024 T015 — the fireproof-site rules (Constitution §VI). */
class FireproofUseCasesTest {

    private val repository = FakeFireproofSiteRepository()
    private val isFireproof = IsSiteFireproofUseCase(repository)

    @Test
    fun `subdomains of a fireproof site are fireproof`() = runTest {
        SetSiteFireproofUseCase(repository)("example.com", true)

        assertTrue(isFireproof("example.com"))
        assertTrue(isFireproof("www.example.com"))
        assertTrue(isFireproof("mail.example.com"))
        assertTrue(isFireproof("MAIL.Example.COM."))
        assertFalse(isFireproof("notexample.com"))
        assertFalse(isFireproof("example.com.evil.test"))
    }

    @Test
    fun `a multi-label public suffix site matches only itself and its subdomains`() = runTest {
        SetSiteFireproofUseCase(repository)("example.co.uk", true)

        assertTrue(isFireproof("shop.example.co.uk"))
        assertFalse(isFireproof("other.co.uk"))
    }

    @Test
    fun `sites under a private suffix stay separate`() = runTest {
        SetSiteFireproofUseCase(repository)("alice.github.io", true)

        assertTrue(isFireproof("alice.github.io"))
        assertFalse(isFireproof("bob.github.io"))
        assertFalse(isFireproof("github.io"))
    }

    @Test
    fun `an IP address or a single-label host is its own site`() = runTest {
        SetSiteFireproofUseCase(repository)("127.0.0.1", true)
        SetSiteFireproofUseCase(repository)("localhost", true)

        assertTrue(isFireproof("127.0.0.1"))
        assertTrue(isFireproof("localhost"))
        assertFalse(isFireproof("127.0.0.2"))
        assertFalse(isFireproof(""))
    }

    @Test
    fun `turning fireproofing off and removing all empty the list`() = runTest {
        val set = SetSiteFireproofUseCase(repository)
        set("a.example", true)
        set("b.example", true)
        set("c.example", true)

        set("a.example", false)
        assertEquals(setOf("b.example", "c.example"), repository.currentSites())

        RemoveFireproofSitesUseCase(repository)("b.example")
        assertEquals(listOf("c.example"), ObserveFireproofSitesUseCase(repository)().first().map { it.site })

        RemoveFireproofSitesUseCase(repository)(null)
        assertTrue(repository.currentSites().isEmpty())
        assertFalse(isFireproof("c.example"))
    }

    @Test
    fun `the site is stored lower-case`() = runTest {
        SetSiteFireproofUseCase(repository)("Example.COM", true)

        assertEquals(setOf("example.com"), repository.currentSites())
    }
}
