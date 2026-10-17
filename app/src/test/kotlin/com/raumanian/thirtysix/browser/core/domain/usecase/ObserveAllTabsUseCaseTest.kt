package com.raumanian.thirtysix.browser.core.domain.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoWebProfile
import com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local.IncognitoProtectionExceptionStore
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.IncognitoSnapshotOrigins
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.IncognitoTabRepositoryImpl
import com.raumanian.thirtysix.browser.feature.tabs.data.repository.TabRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real normal and incognito repositories together, over an in-memory database. */
@RunWith(AndroidJUnit4::class)
class ObserveAllTabsUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var normal: TabRepositoryImpl
    private lateinit var incognito: IncognitoTabRepositoryImpl
    private lateinit var observeAllTabs: ObserveAllTabsUseCase

    @Before
    fun setup() {
        db = inMemoryAppDatabase()
        val dispatchers = TestDispatcherProvider()
        normal = TabRepositoryImpl(tabDao = db.tabDao(), homeUrl = HOME_URL, dispatchers = dispatchers)
        incognito = IncognitoTabRepositoryImpl(
            cookieJarSnapshotManager = FakeCookieJarSnapshotManager(),
            snapshotOrigins = IncognitoSnapshotOrigins(normal, FakeHistoryRepository(), FakeFireproofSiteRepository()),
            dispatchers = dispatchers,
            incognitoProtectionExceptions = IncognitoProtectionExceptionStore(),
            incognitoProfile = FakeIncognitoWebProfile(),
        )
        observeAllTabs = ObserveAllTabsUseCase(normal, incognito)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `closing the active normal tab behind an incognito one keeps the incognito tab active`() = runTest {
        val first = (normal.createTab("https://a.com/") as Result.Success).data.id
        val second = (normal.createTab("https://b.com/") as Result.Success).data.id
        Thread.sleep(STAMP_GAP_MS)
        normal.switchActiveTab(first)
        Thread.sleep(STAMP_GAP_MS)
        val private = (incognito.createTab("https://p.com/") as Result.Success).data.id

        normal.closeTab(first)

        val tabs = observeAllTabs().first()
        assertEquals(private, tabs.first().id)
        assertEquals(second, tabs.first { !it.isIncognito }.id)
    }

    private companion object {
        const val HOME_URL: String = "https://www.google.com/"
        const val STAMP_GAP_MS: Long = 5L
    }

    private class TestDispatcherProvider : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }
}
