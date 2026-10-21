package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.di.DefaultDispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.model.Bookmark
import com.raumanian.thirtysix.browser.core.domain.util.AppConstants
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.BookmarkFileDataSource
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkParser
import com.raumanian.thirtysix.browser.feature.bookmarks.data.datasource.local.NetscapeBookmarkWriter
import com.raumanian.thirtysix.browser.feature.bookmarks.data.repository.BookmarkFileRepositoryImpl
import com.raumanian.thirtysix.browser.feature.bookmarks.data.repository.BookmarkRepositoryImpl
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ExportBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.domain.usecase.ImportBookmarksUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component.TEST_TAG_BOOKMARK_IMPORT_SUMMARY
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Import and export end to end through the real file and database layers; the system pickers are stubbed. */
@RunWith(AndroidJUnit4::class)
class BookmarkTransferTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = DefaultDispatcherProvider()
    private lateinit var db: AppDatabase
    private lateinit var bookmarks: BookmarkRepositoryImpl
    private lateinit var viewModel: BookmarkTransferViewModel
    private val openedFolders = CopyOnWriteArrayList<Long>()
    private val messages = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        Intents.init()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        bookmarks = BookmarkRepositoryImpl(db.bookmarkDao(), db.bookmarkFolderDao(), db, dispatchers)
        val files = BookmarkFileRepositoryImpl(
            BookmarkFileDataSource(context, dispatchers),
            NetscapeBookmarkParser(),
            NetscapeBookmarkWriter(),
            dispatchers,
        )
        viewModel = BookmarkTransferViewModel(
            ImportBookmarksUseCase(files, bookmarks),
            ExportBookmarksUseCase(bookmarks, files),
            bookmarks,
        )
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            BookmarkTransferHost(
                state = state,
                events = viewModel.events,
                onAction = viewModel::onAction,
                onOpenFolder = { openedFolders += it },
                showMessage = { messages += it },
            )
        }
    }

    @After
    fun tearDown() {
        Intents.release()
        db.close()
        File(context.cacheDir, AppConstants.CAPTURE_CACHE_DIR).deleteRecursively()
    }

    private fun shareable(name: String, content: String): Uri {
        val dir = File(context.cacheDir, AppConstants.CAPTURE_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, name).apply { writeText(content) }
        val authority = context.packageName + AppConstants.FILE_PROVIDER_AUTHORITY_SUFFIX
        return FileProvider.getUriForFile(context, authority, file)
    }

    private fun stubPicker(action: String, uri: Uri?) {
        val result = if (uri == null) {
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        } else {
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
        }
        intending(hasAction(action)).respondWith(result)
    }

    private fun openSheetAndTap(label: Int) {
        composeRule.runOnIdle { viewModel.onAction(BookmarkTransferAction.SheetOpen) }
        composeRule.onNodeWithText(context.getString(label)).performClick()
    }

    @Test
    fun importingAFile_showsTheSummaryAndOpensTheNewFolder() {
        stubPicker(Intent.ACTION_OPEN_DOCUMENT, shareable("bookmarks.html", SAMPLE))

        openSheetAndTap(R.string.bookmarks_transfer_import)

        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag(TEST_TAG_BOOKMARK_IMPORT_SUMMARY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.bookmarks_import_open_folder)).performClick()
        composeRule.waitUntil(TIMEOUT_MS) { openedFolders.isNotEmpty() }
        val folder = runBlocking { bookmarks.observeAllFolders().first().find { it.id == openedFolders.single() } }!!
        assertTrue(folder.name.isNotBlank())
        assertEquals(2, runBlocking { bookmarks.countBookmarks() })
    }

    @Test
    fun aFileThatIsNotBookmarks_addsNothingAndSaysSo() {
        stubPicker(Intent.ACTION_OPEN_DOCUMENT, shareable("note.html", "just a note"))

        openSheetAndTap(R.string.bookmarks_transfer_import)

        composeRule.waitUntil(TIMEOUT_MS) { messages.isNotEmpty() }
        assertEquals(context.getString(R.string.bookmarks_import_error_not_bookmarks), messages.single())
        assertEquals(0, runBlocking { bookmarks.countFolders() })
    }

    @Test
    fun cancellingThePicker_doesNothing() {
        stubPicker(Intent.ACTION_OPEN_DOCUMENT, null)

        openSheetAndTap(R.string.bookmarks_transfer_import)
        composeRule.waitForIdle()

        assertTrue(messages.isEmpty())
        assertEquals(BookmarkTransferProgress.Idle, viewModel.uiState.value.progress)
    }

    @Test
    fun exporting_writesAFileThatReadsBack() {
        runBlocking { bookmarks.addBookmark(Bookmark(0L, "Example & Co", "https://example.com/", null, 1L, 1L)) }
        val target = shareable("export.html", "")
        stubPicker(Intent.ACTION_CREATE_DOCUMENT, target)

        openSheetAndTap(R.string.bookmarks_transfer_export)

        composeRule.waitUntil(TIMEOUT_MS) { messages.isNotEmpty() }
        val written = context.contentResolver.openInputStream(target)!!.use { it.readBytes().decodeToString() }
        val parsed = NetscapeBookmarkParser().parse(written, "untitled")!!
        assertEquals(1, parsed.linkCount)
        assertTrue("Example &amp; Co" in written)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        val SAMPLE = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><H3>Work</H3>
                <DL><p>
                    <DT><A HREF="https://a.example/">A</A>
                </DL><p>
                <DT><A HREF="https://b.example/">B</A>
                <DT><A HREF="javascript:void(0)">Bookmarklet</A>
            </DL><p>
        """.trimIndent()
    }
}
