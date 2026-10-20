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

    @Implements(DownloadManager::class)
    class CountingShadow : ShadowDownloadManager() {
        @Implementation
        override fun query(query: DownloadManager.Query): Cursor {
            querySizes += Shadow.extract<ShadowQuery>(query).ids.size
            return super.query(query)
        }
    }

    private companion object {
        const val HANDLES = 1_200
        const val MIN_QUERIES = 3
        val querySizes = mutableListOf<Int>()
    }
}
