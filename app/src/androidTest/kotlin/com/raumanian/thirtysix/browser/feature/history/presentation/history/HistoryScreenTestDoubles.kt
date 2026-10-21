package com.raumanian.thirtysix.browser.feature.history.presentation.history

import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.ClipboardWriter
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.repository.HistoryRepository
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoTabRepository
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ClearBrowsingHistoryUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveAllTabsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.UpdateActiveTabUrlAndTitleUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.NoopTabSessionRepository
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The tests build [HistoryViewModel] over in-memory doubles instead of Hilt, avoiding the flaky
 * WebView and Room setup. Flows are hot [MutableStateFlow]s, never `flowOf`, because
 * [ObserveAllTabsUseCase] combines two streams that must both stay subscribed.
 */
internal class InstrumentedFakeHistoryRepository(
    seed: List<HistoryEntry> = emptyList(),
) : HistoryRepository {

    private val state: MutableStateFlow<List<HistoryEntry>> =
        MutableStateFlow(seed.sortedByDescending { it.visitedAt })
    private var nextId: Long = (seed.maxOfOrNull { it.id } ?: 0L) + 1L

    val entries: List<HistoryEntry> get() = state.value

    override suspend fun recordVisit(url: String, title: String, visitedAt: Long): Long {
        val id = nextId++
        state.update {
            (it + HistoryEntry(id, url, title, visitedAt)).sortedByDescending { e -> e.visitedAt }
        }
        return id
    }

    override fun observeAll(): Flow<List<HistoryEntry>> = state.asStateFlow()

    override suspend fun updateTitle(id: Long, title: String): Int {
        val hit = state.value.any { it.id == id }
        state.update { list -> list.map { if (it.id == id) it.copy(title = title) else it } }
        return if (hit) 1 else 0
    }

    override suspend fun deleteById(id: Long): Int {
        val before = state.value.size
        state.update { list -> list.filterNot { it.id == id } }
        return before - state.value.size
    }

    override suspend fun pruneOlderThan(cutoffMillis: Long): Int {
        val before = state.value.size
        state.update { list -> list.filterNot { it.visitedAt < cutoffMillis } }
        return before - state.value.size
    }

    override suspend fun clearAll(): Int {
        val before = state.value.size
        state.value = emptyList()
        return before
    }

    override suspend fun count(): Int = state.value.size
}

/** Minimal in-memory [TabRepository]; only the surface the History screen touches. */
internal class InstrumentedFakeTabRepository(
    initialTabs: List<Tab> = emptyList(),
) : TabRepository {

    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(initialTabs)
    private var nextId: Long = (initialTabs.maxOfOrNull(Tab::id) ?: 0L) + 1L

    val tabs: List<Tab> get() = state.value

    override fun observeTabs(): Flow<List<Tab>> = state.asStateFlow()

    override suspend fun createTab(url: String): Result<Tab> {
        val now = System.currentTimeMillis()
        val tab = Tab(
            id = nextId++,
            url = url,
            title = "",
            position = state.value.size,
            createdAt = now,
            lastActiveAt = now,
        )
        state.update { it + tab }
        return Result.Success(tab)
    }

    override suspend fun switchActiveTab(tabId: Long) = Unit

    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) {
        state.update { list ->
            list.map { if (it.id == tabId) it.copy(url = url, title = title) else it }
        }
    }

    override suspend fun closeTab(tabId: Long) = Unit

    override suspend fun closeAllTabs() = Unit
}

/** No-op [IncognitoTabRepository]: these screens start with no incognito tab. */
internal object InstrumentedNoopIncognitoRepository : IncognitoTabRepository {
    private val state: MutableStateFlow<List<Tab>> = MutableStateFlow(emptyList())
    override fun observeTabs(): Flow<List<Tab>> = state.asStateFlow()
    override suspend fun createTab(url: String): Result<Tab> =
        error("history screen must never create an incognito tab")
    override suspend fun switchActiveTab(tabId: Long) = Unit
    override suspend fun updateTabUrlAndTitle(tabId: Long, url: String, title: String) = Unit
    override suspend fun closeTab(tabId: Long) = Unit
    override suspend fun closeAll(): Boolean = true
}

/** Records what "Copy URL" put on the clipboard without touching the platform service. */
internal class InstrumentedRecordingClipboardWriter : ClipboardWriter {
    val copied: MutableList<String> = mutableListOf()

    override fun copyUrl(url: String, label: String): Boolean {
        copied += url
        return true
    }
}

internal object InstrumentedNoopFaviconCacheForHistory : FaviconCache {
    private val state = MutableStateFlow(0L)
    override val version: StateFlow<Long> = state.asStateFlow()
    override suspend fun save(url: String, writeImage: (OutputStream) -> Unit) = Unit
    override suspend fun clearAll() = Unit
    override suspend fun retainOnly(hosts: Set<String>) = Unit
    override fun fileFor(url: String): File? = null
}

/** Assembles a [HistoryViewModel] over the doubles above. */
internal fun buildHistoryViewModel(
    historyRepository: HistoryRepository,
    tabRepository: TabRepository = InstrumentedFakeTabRepository(),
    clipboardWriter: ClipboardWriter = InstrumentedRecordingClipboardWriter(),
): HistoryViewModel {
    val observeAllTabs = ObserveAllTabsUseCase(tabRepository, InstrumentedNoopIncognitoRepository)
    return HistoryViewModel(
        history = historyRepository,
        clearHistory = ClearBrowsingHistoryUseCase(historyRepository, NoopTabSessionRepository),
        observeActiveTab = ObserveActiveTabUseCase(observeAllTabs),
        updateActiveTabUrlAndTitle = UpdateActiveTabUrlAndTitleUseCase(
            tabRepository,
            InstrumentedNoopIncognitoRepository,
        ),
        tabs = tabRepository,
        clipboardWriter = clipboardWriter,
        dispatchers = InstrumentedImmediateDispatchers,
        faviconCache = InstrumentedNoopFaviconCacheForHistory,
    )
}

/** Everything on the main dispatcher so Compose test synchronisation stays deterministic. */
internal object InstrumentedImmediateDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}

internal const val INSTRUMENTED_HOME_URL: String = "https://home.example.com/"
