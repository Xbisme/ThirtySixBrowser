package com.raumanian.thirtysix.browser.presentation.history

import android.graphics.Bitmap
import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.data.local.clipboard.ClipboardWriter
import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.domain.usecase.ClearAllHistoryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.CreateTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.DeleteHistoryEntryUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveHistoryEntriesUseCase
import com.raumanian.thirtysix.browser.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeHistoryRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.testdoubles.FakeTabRepository
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 014 — [HistoryViewModel] behaviour across every user story.
 *
 * Covers T028 (US1 listing + tap-replace), T050 (US2 search threshold + literal
 * matching), T068 (US3 long-press actions), T085 (US4 clear-all dialog), and
 * T099 (US5 empty → non-empty reactivity).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Fixed "now" so day-bucketing assertions never straddle a real midnight. */
    private val now: Long = System.currentTimeMillis()
    private val oneDay: Long = TimeUnit.DAYS.toMillis(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────── US1 — listing + tap-to-replace (T028) ────────

    @Test
    fun `entries flow drives groupedEntries in reverse-chronological order`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://old.example.com", "Old", now - 2_000L)
        repo.recordVisit("https://new.example.com", "New", now - 1_000L)
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.first { it.entries.size == 2 }
        assertEquals(1, state.groupedEntries.size)
        assertEquals(HistoryDayBucket.Today, state.groupedEntries.single().bucket)
        assertEquals(
            listOf("https://new.example.com", "https://old.example.com"),
            state.groupedEntries.single().entries.map { it.url },
        )
    }

    @Test
    fun `entries spanning two days produce two day groups`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://yesterday.example.com", "Yesterday", now - oneDay)
        repo.recordVisit("https://today.example.com", "Today", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.first { it.entries.size == 2 }
        assertEquals(
            listOf(HistoryDayBucket.Today, HistoryDayBucket.Yesterday),
            state.groupedEntries.map { it.bucket },
        )
    }

    @Test
    fun `onEntryTap forwards the entry to the active tab and raises openUrl`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now)
        val tabRepo = FakeTabRepository(homeUrl = HOME_URL)
        val vm = newViewModel(repo, tabRepo)
        advanceUntilIdle()

        val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()
        vm.onEntryTap(entry)
        advanceUntilIdle()

        assertEquals("https://example.com", vm.uiState.first { it.openUrl != null }.openUrl)
        val activeTab = tabRepo.observeTabs().first().maxByOrNull { it.lastActiveAt }
        assertEquals("https://example.com", activeTab?.url)
    }

    @Test
    fun `consumeOpenUrl clears the navigation signal`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()
        vm.onEntryTap(vm.uiState.first { it.entries.isNotEmpty() }.entries.single())
        advanceUntilIdle()

        vm.consumeOpenUrl()
        advanceUntilIdle()

        assertNull(vm.uiState.first { it.openUrl == null }.openUrl)
    }

    // ──────── US2 — search (T050) ────────

    @Test
    fun `empty query shows the full list`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()

        val state = vm.uiState.first { it.entries.size == THREE_SEEDED }
        assertEquals(THREE_SEEDED, state.groupedEntries.sumOf { it.entries.size })
    }

    @Test
    fun `single-character query does not filter (FR-011a)`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("e")
        advanceUntilIdle()

        val state = vm.uiState.first { it.searchQuery == "e" }
        assertEquals(THREE_SEEDED, state.groupedEntries.sumOf { it.entries.size })
        assertTrue(BrowserLimits.SEARCH_MIN_CHARS > "e".length)
    }

    @Test
    fun `two-character query filters case-insensitively on title or url`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("KO")
        advanceUntilIdle()

        val matched = vm.uiState.first { it.searchQuery == "KO" }
            .groupedEntries
            .flatMap { it.entries }
        // "Kotlin docs" matches by title; "https://kotlinlang.org" matches by URL.
        assertEquals(listOf("https://kotlinlang.org"), matched.map { it.url })
    }

    @Test
    fun `non-matching query yields zero groups while entries stay populated`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("xyznomatchhere")
        advanceUntilIdle()

        val state = vm.uiState.first { it.searchQuery == "xyznomatchhere" }
        assertTrue(state.groupedEntries.isEmpty())
        assertEquals(THREE_SEEDED, state.entries.size)
    }

    @Test
    fun `wildcard-looking characters are matched literally (FR-013)`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com/plain", "Plain page", now)
        repo.recordVisit("https://example.com/100%_off", "100%_off it's here", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()

        for (query in listOf("%_", "'s", "%_o")) {
            vm.onSearchQueryChange(query)
            advanceUntilIdle()
            val matched = vm.uiState.first { it.searchQuery == query }
                .groupedEntries
                .flatMap { it.entries }
            assertEquals(
                "query '$query' must match literally, never as a wildcard",
                listOf("https://example.com/100%_off"),
                matched.map { it.url },
            )
        }
    }

    @Test
    fun `query longer than the cap is truncated`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()

        vm.onSearchQueryChange("z".repeat(BrowserLimits.MAX_HISTORY_QUERY_LENGTH + 50))
        advanceUntilIdle()

        assertEquals(
            BrowserLimits.MAX_HISTORY_QUERY_LENGTH,
            vm.uiState.first { it.searchQuery.isNotEmpty() }.searchQuery.length,
        )
    }

    @Test
    fun `clearing the query restores the full grouped list (FR-016)`() = runTest {
        val vm = seededViewModel()
        advanceUntilIdle()
        vm.onSearchQueryChange("kotlin")
        advanceUntilIdle()

        vm.onSearchQueryClear()
        advanceUntilIdle()

        val state = vm.uiState.first { it.searchQuery.isEmpty() }
        assertEquals(THREE_SEEDED, state.groupedEntries.sumOf { it.entries.size })
    }

    @Test
    fun `a newly recorded matching entry joins the filtered list live (FR-017)`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://kotlinlang.org", "Kotlin docs", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()
        vm.onSearchQueryChange("kotlin")
        advanceUntilIdle()

        repo.recordVisit("https://play.kotlinlang.org", "Kotlin playground", now + 1_000L)
        advanceUntilIdle()

        val matched = vm.uiState.first { it.entries.size == 2 }
            .groupedEntries
            .flatMap { it.entries }
        assertEquals(2, matched.size)
    }

    // ──────── US3 — per-entry actions (T068) ────────

    @Test
    fun `long-press sets the action sheet target and dismiss clears it`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()
        val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()

        vm.onLongPressEntry(entry)
        assertEquals(entry, vm.uiState.value.pendingActionSheetTarget)

        vm.onActionSheetDismiss()
        assertNull(vm.uiState.value.pendingActionSheetTarget)
    }

    @Test
    fun `open in new tab always creates a normal tab even from an incognito context`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now)
        val tabRepo = FakeTabRepository(homeUrl = HOME_URL)
        val incognitoRepo = FakeIncognitoTabRepository()
        incognitoRepo.createTab("https://secret.example.com")
        val vm = newViewModel(repo, tabRepo, incognitoRepo)
        advanceUntilIdle()
        val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()

        vm.onOpenInNewTab(entry)
        advanceUntilIdle()

        val normalTabs = tabRepo.observeTabs().first()
        assertTrue(
            "the history URL must land in a NORMAL tab (Q2 / FR-019)",
            normalTabs.any { it.url == "https://example.com" },
        )
        assertNull(vm.uiState.value.pendingActionSheetTarget)
        assertEquals("https://example.com", vm.uiState.first { it.openUrl != null }.openUrl)
    }

    @Test
    fun `open in new tab at the cap emits TabCapReached and does not dismiss the screen`() =
        runTest {
            val repo = FakeHistoryRepository()
            repo.recordVisit("https://example.com", "Example", now)
            val tabRepo = FakeTabRepository(homeUrl = HOME_URL)
            repeat(BrowserLimits.MAX_TABS) { tabRepo.createTab("https://filler$it.example.com") }
            val vm = newViewModel(repo, tabRepo)
            advanceUntilIdle()
            val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()

            val events = mutableListOf<HistoryErrorEvent>()
            val job = collectEvents(vm, events)
            vm.onOpenInNewTab(entry)
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(HistoryErrorEvent.TabCapReached), events)
            assertNull(vm.uiState.value.openUrl)
        }

    @Test
    fun `delete entry removes only the targeted row and emits no error`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now - 1_000L)
        repo.recordVisit("https://example.com", "Example", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()
        val target = vm.uiState.first { it.entries.size == 2 }.entries.first()

        val events = mutableListOf<HistoryErrorEvent>()
        val job = collectEvents(vm, events)
        vm.onDeleteEntry(target)
        advanceUntilIdle()
        job.cancel()

        assertTrue(events.isEmpty())
        assertEquals(1, vm.uiState.first { it.entries.size == 1 }.entries.size)
    }

    @Test
    fun `delete entry emits DeletionFailed when the repository throws`() = runTest {
        val repo = ThrowingHistoryRepository()
        val vm = newViewModel(repo)
        advanceUntilIdle()

        val events = mutableListOf<HistoryErrorEvent>()
        val job = collectEvents(vm, events)
        vm.onDeleteEntry(HistoryEntry(1L, "https://example.com", "Example", now))
        advanceUntilIdle()
        job.cancel()

        assertTrue(events.single() is HistoryErrorEvent.DeletionFailed)
    }

    @Test
    fun `copy url writes to the clipboard and emits ClipboardCopied`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com/page", "Example", now)
        val clipboard = RecordingClipboardWriter()
        val vm = newViewModel(repo, clipboardWriter = clipboard)
        advanceUntilIdle()
        val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()

        val events = mutableListOf<HistoryErrorEvent>()
        val job = collectEvents(vm, events)
        vm.onCopyUrl(entry)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("https://example.com/page"), clipboard.copied)
        assertEquals(listOf(HistoryErrorEvent.ClipboardCopied), events)
        assertNull(vm.uiState.value.pendingActionSheetTarget)
    }

    @Test
    fun `copy url stays silent when the platform clipboard rejects the write`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com/page", "Example", now)
        val vm = newViewModel(repo, clipboardWriter = RecordingClipboardWriter(succeeds = false))
        advanceUntilIdle()
        val entry = vm.uiState.first { it.entries.isNotEmpty() }.entries.single()

        val events = mutableListOf<HistoryErrorEvent>()
        val job = collectEvents(vm, events)
        vm.onCopyUrl(entry)
        advanceUntilIdle()
        job.cancel()

        assertTrue(events.isEmpty())
    }

    // ──────── US4 — clear all (T085) ────────

    @Test
    fun `clear-all requested shows the dialog and cancelling hides it`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://example.com", "Example", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()

        vm.onClearAllRequested()
        assertTrue(vm.uiState.value.isClearAllDialogVisible)

        vm.onClearAllCancelled()
        assertFalse(vm.uiState.value.isClearAllDialogVisible)
        assertEquals(1, repo.recorded.size)
    }

    @Test
    fun `clear-all confirmed wipes history and closes the dialog`() = runTest {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://a.example.com", "A", now)
        repo.recordVisit("https://b.example.com", "B", now)
        val vm = newViewModel(repo)
        advanceUntilIdle()
        vm.onClearAllRequested()

        vm.onClearAllConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.first { it.entries.isEmpty() }
        assertFalse(state.isClearAllDialogVisible)
        assertTrue(state.groupedEntries.isEmpty())
        assertEquals(0, repo.recorded.size)
    }

    @Test
    fun `clear-all failure emits DeletionFailed and still closes the dialog`() = runTest {
        val vm = newViewModel(ThrowingHistoryRepository())
        advanceUntilIdle()
        vm.onClearAllRequested()

        val events = mutableListOf<HistoryErrorEvent>()
        val job = collectEvents(vm, events)
        vm.onClearAllConfirmed()
        advanceUntilIdle()
        job.cancel()

        assertTrue(events.single() is HistoryErrorEvent.DeletionFailed)
        assertFalse(vm.uiState.value.isClearAllDialogVisible)
    }

    // ──────── US5 — empty ↔ populated reactivity (T099) ────────

    @Test
    fun `groupedEntries flips from empty to a Today group when the first visit lands`() = runTest {
        val repo = FakeHistoryRepository()
        val vm = newViewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.groupedEntries.isEmpty())

        repo.recordVisit("https://example.com", "Example", now)
        advanceUntilIdle()

        val state = vm.uiState.first { it.entries.isNotEmpty() }
        assertEquals(HistoryDayBucket.Today, state.groupedEntries.single().bucket)
        assertNotNull(state.groupedEntries.single().entries.single())
    }

    // ──────── Helpers ────────

    /**
     * Starts collecting the one-shot snackbar channel and drains the scheduler so the
     * collector is *actually subscribed* before the caller triggers an action. Without
     * the drain, a synchronous emitter (such as `onCopyUrl`) would `tryEmit` into a
     * channel with zero subscribers and the event would be dropped (replay = 0).
     */
    private fun TestScope.collectEvents(
        vm: HistoryViewModel,
        sink: MutableList<HistoryErrorEvent>,
    ): Job {
        val job = launch { vm.historySnackbarEvent.collect { sink += it } }
        advanceUntilIdle()
        return job
    }

    private suspend fun seededViewModel(): HistoryViewModel {
        val repo = FakeHistoryRepository()
        repo.recordVisit("https://kotlinlang.org", "Kotlin docs", now - 2_000L)
        repo.recordVisit("https://developer.android.com", "Android Developers", now - 1_000L)
        repo.recordVisit("https://example.com", "Example Domain", now)
        return newViewModel(repo)
    }

    private fun newViewModel(
        historyRepository: HistoryRepository,
        tabRepo: FakeTabRepository = FakeTabRepository(homeUrl = HOME_URL),
        incognitoRepo: FakeIncognitoTabRepository = FakeIncognitoTabRepository(),
        clipboardWriter: ClipboardWriter = RecordingClipboardWriter(),
    ): HistoryViewModel {
        val observeAllTabs = ObserveAllTabsUseCase(tabRepo, incognitoRepo)
        return HistoryViewModel(
            observeHistoryEntries = ObserveHistoryEntriesUseCase(historyRepository),
            observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
            observeActiveTabIsIncognito = ObserveActiveTabIsIncognitoUseCase(observeAllTabs),
            updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(tabRepo, incognitoRepo),
            createTab = CreateTabUseCase(tabRepo, HOME_URL),
            deleteHistoryEntry = DeleteHistoryEntryUseCase(historyRepository),
            clearAllHistory = ClearAllHistoryUseCase(historyRepository),
            clipboardWriter = clipboardWriter,
            dispatchers = TestDispatchers(testDispatcher),
            faviconCache = NoopFaviconCache,
        )
    }

    /** Routes every dispatcher onto the test scheduler so `flowOn` stays deterministic. */
    private class TestDispatchers(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher get() = d
        override val io: CoroutineDispatcher get() = d
        override val default: CoroutineDispatcher get() = d
        override val unconfined: CoroutineDispatcher get() = d
    }

    private class RecordingClipboardWriter(
        private val succeeds: Boolean = true,
    ) : ClipboardWriter {
        val copied: MutableList<String> = mutableListOf()

        override fun copyUrl(url: String): Boolean {
            if (succeeds) copied += url
            return succeeds
        }
    }

    /** Fails every mutating call so the error branches are reachable without mocking. */
    private class ThrowingHistoryRepository : HistoryRepository {
        private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())

        override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long =
            error("boom")

        override fun observeAll() = state.asStateFlow()

        override suspend fun pruneOlderThan(cutoffMillis: Long): Int = 0

        override suspend fun updateTitle(id: Long, title: String): Int = 0
        override suspend fun deleteById(id: Long): Int = error("boom")

        override suspend fun clearAll(): Int = error("boom")

        override suspend fun count(): Int = 0
    }

    private object NoopFaviconCache : FaviconCache {
        private val state = MutableStateFlow(0L)
        override val version: StateFlow<Long> = state.asStateFlow()
        override suspend fun save(url: String, bitmap: Bitmap) = Unit
        override fun fileFor(url: String): File? = null
    }

    private companion object {
        const val HOME_URL = "https://home.example.com"
        const val THREE_SEEDED = 3
    }
}
