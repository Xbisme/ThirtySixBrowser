package com.raumanian.thirtysix.browser.presentation.browser.suggestions

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.BookmarkEntity
import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.HistoryEntryEntity
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.usecase.FilterAddressSuggestionsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.LoadAddressSuggestionsSourceUseCase
import com.raumanian.thirtysix.browser.feature.bookmarks.data.repository.BookmarkRepositoryImpl
import com.raumanian.thirtysix.browser.feature.history.data.repository.HistoryRepositoryImpl
import com.raumanian.thirtysix.browser.presentation.browser.BrowserUiState
import com.raumanian.thirtysix.browser.presentation.browser.LoadingState
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBar
import com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarCallbacks
import com.raumanian.thirtysix.browser.presentation.browser.components.TEST_TAG_ADDRESS_BAR
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 022 T031a — the address bar input flow with suggestions (Constitution §VI): the real
 * [AddressBar], suggestion list and [AddressSuggestionsViewModel], over real repositories on an
 * in-memory Room database. Submitting is recorded instead of loading a page; the browser screen
 * wires the same two calls to the WebView.
 */
@RunWith(AndroidJUnit4::class)
class AddressBarSuggestionsFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: AppDatabase
    private val submitted = mutableListOf<String>()

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.IO
        override val default: CoroutineDispatcher get() = Dispatchers.Default
        override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            database.bookmarkDao().insert(
                BookmarkEntity(title = "Kotlin docs", url = BOOKMARK, parentFolderId = null, createdAt = 1L, sortOrder = 1L),
            )
            database.historyDao().insert(HistoryEntryEntity(0L, HISTORY, "News", 2L))
        }
        val viewModel = AddressSuggestionsViewModel(
            loadSource = LoadAddressSuggestionsSourceUseCase(
                BookmarkRepositoryImpl(database.bookmarkDao(), database.bookmarkFolderDao(), database, dispatchers),
                HistoryRepositoryImpl(database.historyDao(), dispatchers),
            ),
            filter = FilterAddressSuggestionsUseCase(),
            dispatchers = dispatchers,
        )
        composeRule.setContent {
            var ui by remember { mutableStateOf(BrowserUiState(currentUrl = PAGE, loadingState = LoadingState.Loaded)) }
            val focusManager = LocalFocusManager.current
            val callbacks = AddressBarCallbacks(
                onTextChange = { text ->
                    ui = ui.copy(addressBarText = text)
                    viewModel.onTextChanged(text, ui.currentUrl)
                },
                onFocusChange = { focused ->
                    ui = ui.copy(isAddressBarFocused = focused)
                    viewModel.onFocusChanged(focused)
                },
                onSubmit = {
                    submitted += ui.addressBarText
                    focusManager.clearFocus()
                },
                onClear = {},
                onSuggestion = { url ->
                    submitted += url
                    focusManager.clearFocus()
                },
            )
            ThirtySixTheme {
                Column {
                    AddressBar(state = ui, callbacks = callbacks)
                    BrowserAddressSuggestions(
                        visible = ui.isAddressBarFocused,
                        topBarHeight = 0.dp,
                        stateFlow = viewModel.uiState,
                        onPick = { callbacks.onSuggestion(it.url) },
                    )
                }
            }
        }
    }

    @After
    fun tearDown() {
        Espresso.closeSoftKeyboard()
        database.close()
    }

    private fun rows() = composeRule.onAllNodesWithTag(TEST_TAG_ADDRESS_SUGGESTION_ROW)

    private fun awaitRows(count: Int) =
        composeRule.waitUntil(TIMEOUT_MS) { rows().fetchSemanticsNodes().size == count }

    @Test
    fun focusingOnALoadedPage_showsNoSuggestions() {
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performClick()
        composeRule.waitForIdle()
        rows().assertCountEquals(0)
    }

    @Test
    fun typing_showsMatches_andTappingOneSubmitsItsAddress() {
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performClick()
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performTextReplacement("kot")
        awaitRows(2)
        rows()[0].performClick()
        assertEquals(listOf(BOOKMARK), submitted)
        awaitRows(0)
    }

    @Test
    fun pressingGo_submitsTheTypedTextUnchanged() {
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performClick()
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performTextReplacement("kot")
        awaitRows(2)
        composeRule.onNodeWithTag(TEST_TAG_ADDRESS_BAR).performImeAction()
        assertEquals(listOf("kot"), submitted)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val PAGE = "https://example.com/"
        const val BOOKMARK = "https://kotlinlang.org/docs"
        const val HISTORY = "https://news.example/kotlin"
    }
}
