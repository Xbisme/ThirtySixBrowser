package com.raumanian.thirtysix.browser.feature.browser.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentPageRepositoryImplTest {

    private val store = CurrentPageRepositoryImpl(homeUrl = HOME)

    @Test
    fun `starts on the home address with nothing else known`() {
        val page = store.page.value
        assertEquals(HOME, page.url)
        assertEquals("", page.title)
        assertNull(page.activeTab)
        assertFalse(page.isIncognito)
        assertEquals(0L, page.generation)
    }

    @Test
    fun `every write is visible at once`() {
        store.setUrl("https://a.example/")
        store.setTitle("A")
        store.setIncognito(true)

        assertEquals("https://a.example/", store.page.value.url)
        assertEquals("A", store.page.value.title)
        assertTrue(store.page.value.isIncognito)
    }

    @Test
    fun `a new load moves the generation on and drops the title`() {
        store.setTitle("Old")

        store.startNewPage()

        assertEquals(1L, store.page.value.generation)
        assertEquals("", store.page.value.title)
    }

    @Test
    fun `a different tab is a new page, the same tab is not`() {
        store.setActiveTab(tab(1L))
        val afterFirst = store.page.value.generation
        store.setTitle("Kept")

        store.setActiveTab(tab(1L).copy(url = "https://moved.example/"))
        assertEquals(afterFirst, store.page.value.generation)
        assertEquals("Kept", store.page.value.title)
        assertEquals("https://moved.example/", store.page.value.activeTab?.url)

        store.setActiveTab(tab(2L))
        assertEquals(afterFirst + 1, store.page.value.generation)
        assertEquals("", store.page.value.title)
    }

    private fun tab(id: Long) =
        Tab(id = id, url = "https://t$id.example/", title = "", position = 0, createdAt = 0L, lastActiveAt = 0L)

    private companion object {
        const val HOME = "thirtysix:home"
    }
}
