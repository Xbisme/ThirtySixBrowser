package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.testing.AssetPageServer
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageBar
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.FindInPageCallbacks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_FIND_COUNT
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_FIND_FIELD
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_FIND_NEXT
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component.TEST_TAG_FIND_PREVIOUS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Find in page against `find.html` (exactly 50 matches) through the real [BrowserWebView] and
 * [FindInPageBar]. The state reducer mirrors the view model's, which `FindInPageViewModelTest` covers.
 */
@RunWith(AndroidJUnit4::class)
class FindInPageInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: AssetPageServer
    private val find = MutableStateFlow(FindInPageState(isOpen = true))

    @Volatile private var loaded = false

    @Before
    fun setUp() {
        server = AssetPageServer()
        val url = server.url("find.html")
        composeRule.setContent {
            val actions = remember { WebViewActionsHandle() }
            ThirtySixTheme {
                Column {
                    FindInPageBar(
                        stateFlow = find,
                        isIncognito = false,
                        callbacks = FindInPageCallbacks(
                            onQueryChange = { text ->
                                find.update { it.copy(query = text, current = 0, total = 0) }
                                if (text.isEmpty()) actions.clearMatches() else actions.findAll(text)
                            },
                            onPrevious = { actions.findNext(false) },
                            onNext = { actions.findNext(true) },
                            onClose = {},
                        ),
                    )
                    BrowserWebView(
                        incognitoProfile = IncognitoProfiles,
                        state = remember { BrowserUiState(currentUrl = url, loadingState = LoadingState.Idle) },
                        actions = actions,
                        callbacks = BrowserWebViewCallbacks(
                            onLoadStarted = {},
                            onProgressChanged = {},
                            onLoadFinished = { loaded = true },
                            onLoadFailed = {},
                            onDownloadRequested = {},
                            onFindResult = { ordinal, total ->
                                find.update { state ->
                                    if (state.query.isEmpty() || total <= 0) {
                                        state.copy(current = 0, total = 0)
                                    } else {
                                        state.copy(current = (ordinal + 1).coerceIn(1, total), total = total)
                                    }
                                }
                            },
                        ),
                        navigationCallbacks = BrowserNavigationCallbacks(
                            onUrlChange = {},
                            onCanGoBackChange = {},
                            onCanGoForwardChange = {},
                            onTitleChange = {},
                            onIconReceived = { _, _ -> },
                            onScreenshotReady = {},
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { loaded }
    }

    @After
    fun tearDown() {
        // The find field raises the keyboard; leaving it up breaks the next test class's lookups.
        Espresso.closeSoftKeyboard()
        server.close()
    }

    private fun type(text: String) = composeRule.onNodeWithTag(TEST_TAG_FIND_FIELD).performTextReplacement(text)

    private fun awaitCount(current: Int, total: Int) {
        composeRule.waitUntil(TIMEOUT_MS) { find.value.current == current && find.value.total == total }
        composeRule.onNodeWithTag(TEST_TAG_FIND_COUNT, useUnmergedTree = true).assertTextEquals("$current/$total")
    }

    @Test
    fun fiftyMatches_countFromOne() {
        type(WORD)
        awaitCount(1, MATCHES)
        composeRule.onNodeWithTag(TEST_TAG_FIND_NEXT).assertIsEnabled()
    }

    @Test
    fun next_wrapsFromTheLastMatchToTheFirst_andPreviousWrapsBack() {
        type(WORD)
        awaitCount(1, MATCHES)
        repeat(MATCHES - 1) { composeRule.onNodeWithTag(TEST_TAG_FIND_NEXT).performClick() }
        awaitCount(MATCHES, MATCHES)
        composeRule.onNodeWithTag(TEST_TAG_FIND_NEXT).performClick()
        awaitCount(1, MATCHES)
        composeRule.onNodeWithTag(TEST_TAG_FIND_PREVIOUS).performClick()
        awaitCount(MATCHES, MATCHES)
    }

    @Test
    fun noMatch_readsZero_andCannotStep() {
        type("zzzq")
        awaitCount(0, 0)
        composeRule.onNodeWithTag(TEST_TAG_FIND_NEXT).assertIsNotEnabled()
        composeRule.onNodeWithTag(TEST_TAG_FIND_PREVIOUS).assertIsNotEnabled()
    }

    @Test
    fun matching_ignoresCase_inVietnameseAndRussian() {
        type("TIẾNG")
        awaitCount(1, 1)
        type("ПРИВЕТ")
        awaitCount(1, 1)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val WORD = "lorem"
        const val MATCHES = 50
    }
}
