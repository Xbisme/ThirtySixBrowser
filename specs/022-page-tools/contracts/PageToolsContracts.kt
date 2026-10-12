// Spec 022 — contracts. Signatures only; bodies belong to implementation.
// Package names are where each declaration lands in app/src/main/kotlin/.../browser/.

// ───────────────────────── domain/model ─────────────────────────

/** C1 — data-model §1. Percentages come from BrowserLimits constants, never literals. */
enum class TextSize(val percent: Int) {
    Percent50, Percent75, Percent90, Percent100, Percent110, Percent125, Percent150, Percent175, Percent200;
    companion object { fun fromPercentOrDefault(value: Int?): TextSize }
}

/** C2 — data-model §4. */
enum class SuggestionSource { Bookmark, History }
data class AddressSuggestion(val url: String, val title: String, val source: SuggestionSource)
data class AddressSuggestionSnapshot(val candidates: List<AddressSuggestion>)

// ───────────────────────── domain/repository (additions) ─────────────────────────

interface SettingsRepository {
    // … existing members …
    /** C3 — writes StorageKeys.TEXT_ZOOM_PERCENT. Failure is in the Result, never thrown. */
    suspend fun setTextSize(size: TextSize): Result<Unit>
}

interface BookmarkRepository {
    // … existing members …
    /** C4 — every bookmark, newest saved first. One-shot read; no user text in the query. */
    suspend fun getAllBookmarks(): List<Bookmark>
}

interface HistoryRepository {
    // … existing members …
    /** C5 — one entry per url (its most recent visit), newest first. No user text in the query. */
    suspend fun getLatestVisitPerUrl(): List<HistoryEntry>
}

// ───────────────────────── domain/usecase ─────────────────────────

/** C6 */
class SetTextSizeUseCase { suspend operator fun invoke(size: TextSize): Result<Unit> }

/** C7 — bookmarks first, then history minus bookmarked urls; one entry per url. Off the main thread. */
class LoadAddressSuggestionsSourceUseCase { suspend operator fun invoke(): AddressSuggestionSnapshot }

/** C8 — pure. Literal, Unicode case-insensitive substring on title or url; order kept; capped. */
class FilterAddressSuggestionsUseCase {
    operator fun invoke(snapshot: AddressSuggestionSnapshot, query: String, limit: Int): List<AddressSuggestion>
}

// ───────────────────────── presentation/browser ─────────────────────────

/** C9 — additions to the existing Compose-side handle (Spec 008). All default to no-ops; detach() resets them. */
internal class WebViewActionsHandle {
    // … existing members …
    var findAll: (String) -> Unit
    var findNext: (forward: Boolean) -> Unit
    var clearMatches: () -> Unit
    /** Reconfigures UA, viewport and UA metadata (research R2), then reloads. */
    var setDesktopMode: (Boolean) -> Unit
    /** Opens the system print screen for the live page (research R8). */
    var print: (jobName: String) -> Unit
}

/** C10 — data-model §3. Its own StateFlow on BrowserViewModel. */
@Immutable
data class FindInPageState(val isOpen: Boolean = false, val query: String = "", val current: Int = 0, val total: Int = 0)

/** C11 — BrowserViewModel additions. */
interface BrowserViewModelAdditions {
    val findInPage: StateFlow<FindInPageState>
    val textZoomPercent: StateFlow<Int>
    fun onFindOpened()
    fun onFindQueryChanged(query: String)          // truncates at MAX_FIND_QUERY_LENGTH
    fun onFindResult(activeOrdinal: Int, total: Int)
    fun onFindClosed()
    fun onDesktopModeToggled()                      // for the active tab
    // BrowserUiState gains: val isDesktopMode: Boolean
}

/** C12 — the five hub tools, and which are offered (research R9). */
enum class PageTool { Find, Share, DesktopSite, TextSize, Print }
object PageToolsAvailability { fun of(isHomeContent: Boolean, loadingState: LoadingState): Set<PageTool> }

/** C13 — HubCallbacks gains one entry; QuickSetting gains TextSize (routes through QuickSettingsHost). */
internal data class HubCallbacksAdditions(val onPageTool: (PageTool) -> Unit)

/** C14 — pure helpers. */
object DesktopUserAgent { fun from(defaultUserAgent: String): String }
data class ShareContent(val text: String, val subject: String?) { companion object { fun of(url: String, title: String?): ShareContent } }
object PrintJobName { fun of(title: String?, url: String): String }

// ───────────────────────── presentation/browser/suggestions ─────────────────────────

/** C15 — separate ViewModel, like Spec 021's PopupTabs / LongPressActions. Writes nothing, anywhere. */
@HiltViewModel
class AddressSuggestionsViewModel {
    val uiState: StateFlow<AddressSuggestionsUiState>
    fun onEditingStarted()                                   // loads the snapshot off-main
    fun onTextChanged(text: String, currentPageUrl: String?) // empty, or == currentPageUrl → no suggestions (FR-021)
    fun onEditingEnded()                                     // drops snapshot and suggestions
}

@Immutable
data class AddressSuggestionsUiState(val suggestions: List<AddressSuggestion> = emptyList()) // stable via compose_stability.conf

// ───────────────────────── presentation/components ─────────────────────────

/** C16 — QuickTile gains two defaulted parameters; existing call sites unchanged. */
@Composable
fun QuickTile(
    label: String,
    @DrawableRes icon: Int,
    tone: QuickTileTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null, // non-null → toggleable semantics, "on"/"off" state description
    value: String? = null,     // second line, e.g. "125%"
)
