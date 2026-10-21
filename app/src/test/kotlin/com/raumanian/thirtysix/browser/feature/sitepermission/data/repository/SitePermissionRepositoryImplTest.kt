package com.raumanian.thirtysix.browser.feature.sitepermission.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.inMemoryAppDatabase
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SitePermissionRepositoryImpl

    @Before
    fun setUp() {
        db = inMemoryAppDatabase()
        repository = SitePermissionRepositoryImpl(db.sitePermissionDao(), TestDispatchers)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a decision is saved and replaced per origin and capability`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(A, SiteCapability.Camera, SiteDecision.Deny)
        repository.save(A, SiteCapability.Location, SiteDecision.Allow)

        assertEquals(
            mapOf(SiteCapability.Camera to SiteDecision.Deny, SiteCapability.Location to SiteDecision.Allow),
            repository.decisionsFor(A),
        )
    }

    @Test
    fun `an insecure origin is never stored`() = runTest {
        val result = repository.save("http://a.org", SiteCapability.Camera, SiteDecision.Allow)
        assertTrue(result is Result.Error)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `removing a site leaves the others`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(B, SiteCapability.Microphone, SiteDecision.Deny)

        repository.removeSite(A)

        assertEquals(listOf(B), repository.observeAll().first().map { it.origin })
    }

    @Test
    fun `removing several sites at once leaves the others`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(B, SiteCapability.Microphone, SiteDecision.Deny)

        val result = repository.removeSites(listOf(A, A))

        assertTrue(result is Result.Success)
        assertEquals(listOf(B), repository.observeAll().first().map { it.origin })
    }

    @Test
    fun `clear all removes everything`() = runTest {
        repository.save(A, SiteCapability.Camera, SiteDecision.Allow)
        repository.save(B, SiteCapability.Location, SiteDecision.Allow)

        repository.clearAll()

        assertTrue(repository.observeAll().first().isEmpty())
    }

    private object TestDispatchers : DispatcherProvider {
        override val main = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val unconfined = Dispatchers.Unconfined
    }

    private companion object {
        const val A = "https://a.org"
        const val B = "https://b.org"
    }
}
