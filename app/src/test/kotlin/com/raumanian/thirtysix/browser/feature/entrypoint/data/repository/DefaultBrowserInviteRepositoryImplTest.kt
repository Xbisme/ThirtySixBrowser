package com.raumanian.thirtysix.browser.feature.entrypoint.data.repository

import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.SettingsDataStore
import com.raumanian.thirtysix.browser.core.data.datasource.local.datastore.createTestSettingsDataStore
import com.raumanian.thirtysix.browser.core.domain.repository.InviteState
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Invitation state over a real DataStore file. */
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
