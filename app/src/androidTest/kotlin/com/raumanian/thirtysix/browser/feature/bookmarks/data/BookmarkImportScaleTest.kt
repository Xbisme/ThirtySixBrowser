package com.raumanian.thirtysix.browser.feature.bookmarks.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.fixture.LargeBookmarkFile
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reading a bookmark file takes time in line with its size, even when no tag is ever closed. */
@RunWith(AndroidJUnit4::class)
class BookmarkImportScaleTest {

    private val parser = NetscapeBookmarkParser()

    @Test
    fun tenThousandLinksParseUnderTheBudget() {
        val text = LargeBookmarkFile.text()
        assertEquals(LargeBookmarkFile.LINKS, checkNotNull(parser.parse(text, UNTITLED)).linkCount)

        assertMedianUnder("10k", TEN_K_BUDGET_MS) { parser.parse(text, UNTITLED) }
    }

    @Test
    fun aTenMegabyteFileWithoutCloseTagsParsesUnderTheBudget() {
        val text = buildString(UNCLOSED_CHARS) {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL>")
            var i = 0
            while (true) {
                val entry = "<DT><A HREF=\"https://s${i++}.example/\">t"
                // Growing past the capacity would double the buffer and run out of heap on API 29.
                if (length + entry.length > UNCLOSED_CHARS) break
                append(entry)
            }
        }
        assertTrue(checkNotNull(parser.parse(text, UNTITLED)).linkCount > 0)

        assertMedianUnder("10 MB unclosed", UNCLOSED_BUDGET_MS) { parser.parse(text, UNTITLED) }
    }

    private fun assertMedianUnder(label: String, budgetMs: Long, block: () -> Unit) {
        val times = List(RUNS) { measureTimeMillis(block) }.sorted()
        val median = times[RUNS / 2]
        Log.i(TAG, "$label median ${median}ms of $times")
        assertTrue("$label median $median ms of $times", median < budgetMs)
    }

    private companion object {
        const val TAG = "BookmarkImportScale"
        const val UNTITLED = "untitled"
        const val RUNS = 5
        const val TEN_K_BUDGET_MS = 2_000L
        const val UNCLOSED_CHARS = 10_000_000
        const val UNCLOSED_BUDGET_MS = 5_000L
    }
}
