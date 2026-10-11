package com.raumanian.thirtysix.browser.data.repository

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.data.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.data.local.datastore.createTestSettingsDataStore
import com.raumanian.thirtysix.browser.domain.repository.InviteState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec 020 T035 — invitation state over a real DataStore file (data-model §8, INV-8). */
class DefaultBrowserInviteRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repository() =
        DefaultBrowserInviteRepositoryImpl(SettingsDataStore(createTestSettingsDataStore(folder.root)))

    @Test
    fun `a fresh file reads the defaults`() = runBlocking {
        assertEquals(InviteState(dismissed = false, launchCount = 0), repository().observeInviteState().first())
    }

    @Test
    fun `launches stop counting at the threshold`() = runBlocking {
        val repo = repository()
        repeat(5) { repo.recordLaunch() }
        assertEquals(BrowserLimits.DEFAULT_BROWSER_INVITE_MIN_LAUNCHES, repo.observeInviteState().first().launchCount)
    }

    @Test
    fun `dismissal is sticky`() = runBlocking {
        val repo = repository()
        repo.markInviteDismissed()
        repo.recordLaunch()
        repo.markInviteDismissed()
        assertTrue(repo.observeInviteState().first().dismissed)
    }
}
