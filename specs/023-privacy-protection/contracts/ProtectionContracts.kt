// Spec 023 — contracts. Signatures only; bodies belong to implementation.
// Package names are where each declaration lands in app/src/main/kotlin/.../browser/.

// ───────────────────────── domain/model ─────────────────────────

/** C1 — data-model "Rule data". Immutable once built by the parser. */
class TrackerHostSet(hosts: Set<String>) { fun containsHostOrParent(host: String, stopAt: String): Boolean }
class PublicSuffixRules(normal: Set<String>, wildcard: Set<String>, exception: Set<String>)
class TrackingParamRules(names: Set<String>, prefixes: List<String>)
data class ProtectionData(
    val trackers: TrackerHostSet,
    val suffixes: PublicSuffixRules,
    val params: TrackingParamRules,
)

/** C2 — hub state for the current page (FR-010, FR-011, FR-022a). */
data class PageProtectionStatus(
    val blockedTrackerCount: Int,
    val protectionOn: Boolean,
    val connectionSecure: Boolean,
    val isWebPage: Boolean,
) { companion object { val NOT_A_WEB_PAGE: PageProtectionStatus } }

/** C3 — one remembered site without protection (normal browsing only). */
data class SiteProtectionException(val site: String, val addedAt: java.time.Instant)

enum class ClearBrowsingDataCategory {
    History, CookiesAndSiteData, CachedImagesAndFiles, SitePermissions,
    /** C4 — new; NOT in DEFAULT_SELECTION (opt-in, FR-016a). */
    SitesWithoutProtection,
}

// ───────────────────────── domain/protection (pure Kotlin) ─────────────────────────

/** C5 — data-model rule 1. Bounded cache inside; thread-safe. */
class PublicSuffixMatcher(rules: PublicSuffixRules, cacheSize: Int) {
    fun registrableDomain(host: String): String
    fun isThirdParty(requestHost: String, topHost: String): Boolean
}

/** C6 — data-model rule 3. Called from a WebView IO thread; no I/O, no locks. */
class TrackerMatcher(trackers: TrackerHostSet, suffixes: PublicSuffixMatcher, neverBlock: Set<String>) {
    fun shouldBlock(requestHost: String, topHost: String, protectionOn: Boolean): Boolean
}

/** C7 — data-model rule 4. Returns the same instance when nothing is removed. */
class TrackingParamStripper(rules: TrackingParamRules) { fun strip(url: String): String }

/** C8 — data-model rule 5. `null` = do not upgrade. */
object HttpsUpgradePolicy { fun upgrade(url: String, failedHosts: Set<String>): String? }

/** C9 — data-model rule 6. Idempotent: prepare(prepare(u)) == prepare(u). */
class NavigationPreparer(
    stripper: TrackingParamStripper,
    suffixes: PublicSuffixMatcher,
    isExcepted: (site: String, isIncognito: Boolean) -> Boolean,
    failedHosts: () -> Set<String>,
) {
    data class Prepared(val url: String, val upgradedFrom: String?)
    fun prepare(url: String, isIncognito: Boolean): Prepared
}

// ───────────────────────── domain/repository ─────────────────────────

interface ProtectionDataRepository {
    /** Starts loading if not started; never blocks the caller. */
    fun preload()
    /** Waits at most [timeoutMs]; `null` on timeout or parse failure (fail open). Call off the main thread. */
    fun awaitData(timeoutMs: Long): ProtectionData?
    /** Never blocks; safe on the main thread. */
    fun currentDataOrNull(): ProtectionData?
}

interface ProtectionExceptionRepository {
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SiteProtectionException>>
    /** Synchronous snapshot for the IO-thread intercept path. */
    fun currentSites(): Set<String>
    suspend fun add(site: String): Result<Unit>
    suspend fun remove(site: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
}

// ───────────────────────── domain/usecase ─────────────────────────

class ObserveProtectionExceptionsUseCase { operator fun invoke(): kotlinx.coroutines.flow.Flow<List<SiteProtectionException>> }
/** Normal → repository (persisted); incognito → IncognitoProtectionExceptionStore (memory only, FR-014). */
class SetSiteProtectionUseCase { suspend operator fun invoke(site: String, on: Boolean, isIncognito: Boolean): Result<Unit> }
/** `site == null` removes all. */
class RemoveProtectionExceptionsUseCase { suspend operator fun invoke(site: String?): Result<Unit> }
/** Synchronous, any thread. Normal → Room snapshot; incognito → incognito store only (FR-014). The only way presentation reads protection state (§IV). */
class IsSiteProtectedUseCase { operator fun invoke(site: String, isIncognito: Boolean): Boolean }
/** Domain view of the in-memory incognito store, implemented in data/local/protection. */
interface IncognitoProtectionExceptions { fun contains(site: String): Boolean }

// ───────────────────────── data/local ─────────────────────────

@androidx.room.Dao
interface SiteProtectionExceptionDao {
    @androidx.room.Upsert suspend fun upsert(entity: SiteProtectionExceptionEntity)
    @androidx.room.Query("SELECT * FROM site_protection_exceptions ORDER BY added_at DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<SiteProtectionExceptionEntity>>
    @androidx.room.Query("DELETE FROM site_protection_exceptions WHERE site = :site") suspend fun delete(site: String): Int
    @androidx.room.Query("DELETE FROM site_protection_exceptions") suspend fun deleteAll(): Int
}

/** In-memory, cleared on the incognito 1 → 0 transition and by Clear browsing data. */
class IncognitoProtectionExceptionStore {
    fun add(site: String); fun remove(site: String); fun contains(site: String): Boolean; fun clear()
}

/** In-memory, process lifetime (FR-020). */
class HttpsUpgradeFailureStore { fun markFailed(host: String); fun snapshot(): Set<String> }

/** Builds the matchers once after data loads. */
class ProtectionMatchersHolder {
    data class Matchers(val suffixes: PublicSuffixMatcher, val trackers: TrackerMatcher, val preparer: NavigationPreparer)
    fun matchersOrNull(): Matchers?                 // main thread OK
    fun awaitMatchers(timeoutMs: Long): Matchers?   // background threads only
}

// ───────────────────────── presentation/browser/protection ─────────────────────────

/**
 * One per WebView. Thread rules: onMainFrameRequest/onSubresource run on WebView IO threads;
 * everything else on the main thread. Publishes a conflated status flow read only by the hub.
 */
class PageProtectionMonitor {
    val status: kotlinx.coroutines.flow.StateFlow<PageProtectionStatus>
    fun onPageChanged(topUrl: String, isWebPage: Boolean)   // PageSession.advance(): UI fields; resets only for restores
    fun onMainFrameRequest(url: String)                     // IO thread: per-page reset + protectionOn
    fun blockedHosts(): Set<String>                         // tests
    /** IO thread. Returns a blocked response or null. */
    fun onSubresource(requestUrl: String): android.webkit.WebResourceResponse?
    fun beginUpgrade(prepared: NavigationPreparer.Prepared)
    /** Returns the http URL to fall back to, or null if [failedUrl] is not the pending upgrade. */
    fun onMainFrameFailure(failedUrl: String): String?
    fun onRedirect(toUrl: String): Boolean                                         // true = was an https→http downgrade
    fun onCommitted(url: String)
}

/** Feature-detected; installs Sec-GPC once per process. Returns true when app-wide headers are active. */
object GpcHeaderInstaller { fun install(webView: android.webkit.WebView): Boolean }

class ProtectionViewModel {
    val status: kotlinx.coroutines.flow.StateFlow<PageProtectionStatus>
    /** Switch for the current page's site; emits a one-shot reload event afterwards. */
    fun onProtectionSwitched(on: Boolean)
    val reloadEvents: kotlinx.coroutines.flow.SharedFlow<Unit>
}
