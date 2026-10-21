package com.raumanian.thirtysix.browser.feature.downloads.data.datasource.local

import android.app.Application
import android.app.DownloadManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.raumanian.thirtysix.browser.feature.downloads.domain.model.DownloadLimits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDownloadManager

/** The platform caps how many ids one query may bind, so a long download list is asked for in parts. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [AndroidDownloadManagerGatewayTest.CountingShadow::class])
class AndroidDownloadManagerGatewayTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        querySizes.clear()
        failingQuery = NO_FAILING_QUERY
    }

    @Test
    fun `statuses for many downloads are queried in parts that cover every handle`() = runTest {
        val manager = checkNotNull(app.getSystemService<DownloadManager>())
        val handles = List(HANDLES) { manager.enqueue(DownloadManager.Request(Uri.parse("https://x.example/$it"))) }
        querySizes.clear()

        val statuses = AndroidDownloadManagerGateway(app).queryStatuses(handles)

        assertTrue("queries: $querySizes", querySizes.size >= MIN_QUERIES)
        assertTrue("queries: $querySizes", querySizes.all { it <= DownloadLimits.STATUS_QUERY_CHUNK })
        assertEquals(handles.toSet(), statuses.keys)
    }

    @Test
    fun `a download the service did not remove leaves a same-named file alone`() = runTest {
        val file = DownloadedFileProbe.publicDownloadFile(FILE_NAME).apply {
            parentFile?.mkdirs()
            writeText("kept")
        }

        val deleted = AndroidDownloadManagerGateway(app).deleteFile(UNKNOWN_HANDLE, FILE_NAME)

        assertTrue(deleted)
        assertTrue(file.exists())
    }

    @Test
    fun `one failing status query keeps the statuses of the other parts`() = runTest {
        val manager = checkNotNull(app.getSystemService<DownloadManager>())
        val handles = List(HANDLES) { manager.enqueue(DownloadManager.Request(Uri.parse("https://x.example/$it"))) }
        querySizes.clear()
        failingQuery = SECOND_QUERY

        val statuses = AndroidDownloadManagerGateway(app).queryStatuses(handles)

        val chunks = handles.chunked(DownloadLimits.STATUS_QUERY_CHUNK)
        assertEquals((chunks[0] + chunks[2]).toSet(), statuses.keys)
    }

    @Implements(DownloadManager::class)
    class CountingShadow : ShadowDownloadManager() {
        @Implementation
        override fun query(query: DownloadManager.Query): Cursor {
            querySizes += Shadow.extract<ShadowQuery>(query).ids.size
            check(querySizes.size != failingQuery) { "query failed" }
            return super.query(query)
        }
    }

    private companion object {
        const val HANDLES = 1_200
        const val MIN_QUERIES = 3
        const val NO_FAILING_QUERY = -1
        const val SECOND_QUERY = 2
        const val UNKNOWN_HANDLE = 9_999L
        const val FILE_NAME = "report.pdf"
        val querySizes = mutableListOf<Int>()
        var failingQuery = NO_FAILING_QUERY
    }
}
