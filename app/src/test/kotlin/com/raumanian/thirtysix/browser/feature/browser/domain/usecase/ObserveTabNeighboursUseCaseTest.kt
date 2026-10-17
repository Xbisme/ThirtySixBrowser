package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveTabNeighboursUseCaseTest {

    private val normal = FakeTabRepository()
    private val incognito = FakeIncognitoTabRepository()
    private val observe = ObserveTabNeighboursUseCase(ObserveAllTabsUseCase(normal, incognito))

    private fun tab(id: Long, position: Int, lastActiveAt: Long) = Tab(
        id = id,
        url = "https://t$id.example/",
        title = "T$id",
        position = position,
        createdAt = 0L,
        lastActiveAt = lastActiveAt,
        isIncognito = id < 0L,
    )

    @Test
    fun `neighbours follow the active tab in opening order`() = runTest {
        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 30), tab(3, 2, 20)))

        val neighbours = observe().first()

        assertEquals(1L, neighbours.previous?.id)
        assertEquals(3L, neighbours.next?.id)
    }

    @Test
    fun `a switch moves the neighbours with it`() = runTest {
        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 30), tab(3, 2, 20)))

        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 30), tab(3, 2, 40)))

        val neighbours = observe().first()
        assertEquals(2L, neighbours.previous?.id)
        assertNull(neighbours.next)
    }

    @Test
    fun `an active incognito tab only reaches incognito tabs`() = runTest {
        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 20)))
        incognito.emit(listOf(tab(-1, 0, 30), tab(-2, 1, 25)))

        val neighbours = observe().first()

        assertNull(neighbours.previous)
        assertEquals(-2L, neighbours.next?.id)
    }

    @Test
    fun `when the last incognito tab closes the neighbours are the normal head's`() = runTest {
        normal.emit(listOf(tab(1, 0, 10), tab(2, 1, 20)))
        incognito.emit(listOf(tab(-1, 0, 30)))

        incognito.emit(emptyList())

        val neighbours = observe().first()
        assertEquals(1L, neighbours.previous?.id)
        assertNull(neighbours.next)
    }
}
