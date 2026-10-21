package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.testing.fake.FakeProtectionExceptionRepository
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-site protection in normal and incognito browsing. */
class SiteProtectionUseCasesTest {
    private val repository = FakeProtectionExceptionRepository()
    private val incognito = IncognitoProtectionExceptionStore()
    private val isProtected = IsSiteProtectedUseCase(repository, incognito)
    private val setProtection = SetSiteProtectionUseCase(repository, incognito)

    @Test
    fun `every site is protected by default`() {
        assertTrue(isProtected("shop.example", isIncognito = false))
        assertTrue(isProtected("shop.example", isIncognito = true))
    }

    @Test
    fun `a normal choice is saved and never read by incognito`() = runTest {
        setProtection("shop.example", on = false, isIncognito = false)

        assertFalse(isProtected("shop.example", isIncognito = false))
        assertTrue("incognito ignores the saved list", isProtected("shop.example", isIncognito = true))
        assertEquals(listOf("shop.example"), repository.observeAll().first().map { it.site })

        setProtection("shop.example", on = true, isIncognito = false)
        assertTrue(isProtected("shop.example", isIncognito = false))
    }

    @Test
    fun `an incognito choice never touches the saved list`() = runTest {
        setProtection("shop.example", on = false, isIncognito = true)

        assertFalse(isProtected("shop.example", isIncognito = true))
        assertTrue(isProtected("shop.example", isIncognito = false))
        assertTrue(repository.observeAll().first().isEmpty())

        setProtection("shop.example", on = true, isIncognito = true)
        assertTrue(isProtected("shop.example", isIncognito = true))
    }
}
