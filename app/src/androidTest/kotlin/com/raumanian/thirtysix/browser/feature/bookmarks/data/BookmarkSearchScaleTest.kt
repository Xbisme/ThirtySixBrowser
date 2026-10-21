package com.raumanian.thirtysix.browser.feature.bookmarks.data

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.di.DefaultDispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.data.repository.BookmarkRepositoryImpl
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.SearchBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.LargeBookmarkFile
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Search over a full bookmark store stays within one frame budget for the person typing. */
@RunWith(AndroidJUnit4::class)
class BookmarkSearchScaleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var search: SearchBookmarksUseCase

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val bookmarks =
            BookmarkRepositoryImpl(db.bookmarkDao(), db.bookmarkFolderDao(), db, DefaultDispatcherProvider())
        val file = checkNotNull(NetscapeBookmarkParser().parse(LargeBookmarkFile.text(), "untitled"))
        assertEquals(LargeBookmarkFile.LINKS, file.linkCount)
        assertTrue(bookmarks.importTree("Imported", file.root) is Result.Success)
        search = SearchBookmarksUseCase(bookmarks)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun searchingTenThousandBookmarksTakesUnderTheBudget() = runBlocking {
        search(QUERY).first()
        val times = List(RUNS) { measureTimeMillis { search(QUERY).first() } }.sorted()
        val median = times[RUNS / 2]
        Log.i(TAG, "median ${median}ms of $times")

        assertTrue("median $median ms of $times", median < BUDGET_MS)
    }

    private companion object {
        const val TAG = "BookmarkSearchScale"
        const val QUERY = "ht"
        const val RUNS = 5
        const val BUDGET_MS = 300L
    }
}
