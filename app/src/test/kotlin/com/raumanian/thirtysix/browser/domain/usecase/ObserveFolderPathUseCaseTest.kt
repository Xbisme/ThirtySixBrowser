package com.raumanian.thirtysix.browser.domain.usecase

import app.cash.turbine.test
import com.raumanian.thirtysix.browser.testdoubles.FakeBookmarkRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveFolderPathUseCaseTest {

    @Test
    fun `null folder emits empty path`() = runTest {
        val repo = FakeBookmarkRepository()
        val useCase = ObserveFolderPathUseCase(repo)
        useCase(null).test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `nested folder emits root-to-leaf chain`() = runTest {
        val repo = FakeBookmarkRepository()
        val a = (repo.createFolder("A", null) as com.raumanian.thirtysix.browser.core.result.Result.Success).data
        val b = (repo.createFolder("B", a) as com.raumanian.thirtysix.browser.core.result.Result.Success).data
        val c = (repo.createFolder("C", b) as com.raumanian.thirtysix.browser.core.result.Result.Success).data
        val useCase = ObserveFolderPathUseCase(repo)
        useCase(c).test {
            val chain = awaitItem()
            assertEquals(listOf("A", "B", "C"), chain.map { it.name })
            awaitComplete()
        }
    }
}
