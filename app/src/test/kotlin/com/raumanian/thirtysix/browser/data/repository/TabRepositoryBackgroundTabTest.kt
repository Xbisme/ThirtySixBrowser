package com.raumanian.thirtysix.browser.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.MaxIncognitoTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.repository.MaxTabsReachedException
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoWebProfile
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.data.local.protection.IncognitoProtectionExceptionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 021 T041 / research R9 — a tab opened behind the active one, normal and incognito. */
@RunWith(AndroidJUnit4::class)
class TabRepositoryBackgroundTabTest {

    private val incognitoProfile = FakeIncognitoWebProfile()

    private val db: AppDatabase = inMemoryAppDatabase()
    private val tabs = TabRepositoryImpl(tabDao = db.tabDao(), homeUrl = HOME, dispatchers = Direct)
    private val incognito = IncognitoTabRepositoryImpl(
        cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
        snapshotOrigins = IncognitoSnapshotOrigins(
            FakeTabRepository(),
            FakeHistoryRepository(),
            FakeFireproofSiteRepository(),
        ),
        dispatchers = Direct,
        incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
        incognitoProfile = incognitoProfile,
    )

    @After
    fun tearDown() = db.close()

    private fun active(list: List<Tab>) = list.maxBy(Tab::lastActiveAt)

    @Test
    fun `a normal background tab leaves the active tab active and sits second`() = runTest {
        val current = (tabs.createTab(A) as Result.Success).data

        val background = (tabs.createBackgroundTab(B, current.lastActiveAt) as Result.Success).data

        val list = tabs.observeTabs().first()
        assertEquals(current.id, active(list).id)
        assertEquals(current.lastActiveAt - 1, background.lastActiveAt)
        assertEquals(listOf(A, B), list.sortedByDescending(Tab::lastActiveAt).take(2).map(Tab::url))
    }

    @Test
    fun `a normal background tab respects the cap`() = runTest {
        repeat(BrowserLimits.MAX_TABS) { tabs.createTab("https://t$it.example") }

        val result = tabs.createBackgroundTab(B, System.currentTimeMillis())

        assertTrue((result as Result.Error).throwable is MaxTabsReachedException)
    }

    @Test
    fun `an incognito background tab never becomes active`() = runTest {
        val activeAt = System.currentTimeMillis()

        val background = (incognito.createBackgroundTab(B, activeAt) as Result.Success).data

        assertTrue(background.isIncognito)
        assertEquals(activeAt - 1, background.lastActiveAt)
    }

    @Test
    fun `an incognito background tab respects the incognito cap`() = runTest {
        repeat(BrowserLimits.MAX_INCOGNITO_TABS) { incognito.createTab("https://i$it.example") }

        val result = incognito.createBackgroundTab(B, System.currentTimeMillis())

        assertTrue((result as Result.Error).throwable is MaxIncognitoTabsReachedException)
    }

    private object Direct : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        const val HOME = "thirtysix:home"
        const val A = "https://a.example/"
        const val B = "https://b.example/"
    }
}
