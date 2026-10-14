// Spec 024 — contracts. Signatures only; bodies belong to implementation.
// Package names are where each declaration lands in app/src/main/kotlin/.../browser/.
// "+" = new declaration, "~" = existing declaration gaining members.

// ───────────────────────── domain/model ─────────────────────────

/** + C1 — one fireproof site (normal browsing only, FR-009). */
data class FireproofSite(val site: String, val addedAt: java.time.Instant)

/** + C2 — data-model "FireState". */
sealed interface FireState {
    data object Idle : FireState
    data object Running : FireState
    data class Finished(val result: FireResult) : FireState
}

enum class FireStep { CollectHosts, CloseIncognito, CloseTabs, History, CookiesAndSiteData, WebCache, SitePermissions, Favicons, SessionTraces } // the home tab comes from CloseTabs (Spec 011 closeAllTabs)
enum class SiteDataPath { Complete, PerSite, Fallback }
data class FireResult(val failedSteps: Set<FireStep>, val siteDataPath: SiteDataPath)

/** ~ C3 — Spec 016/021/023 enum gains an opt-in category (FR-013). NOT in DEFAULT_SELECTION. */
enum class ClearBrowsingDataCategory {
    History, CookiesAndSiteData, CachedImagesAndFiles, SitePermissions, SitesWithoutProtection,
    FireproofSites, // +
}

// ───────────────────────── domain/repository ─────────────────────────

/** + C4 — Room-backed, mirrors ProtectionExceptionRepository (Spec 023). */
interface FireproofSiteRepository {
    /** Newest first. */
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<FireproofSite>>
    /** Synchronous snapshot; never touches the database. */
    fun currentSites(): Set<String>
    suspend fun add(site: String): Result<Unit>
    suspend fun remove(site: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
}

/** ~ C5 — Spec 016 seam gains the fireproof-aware path (research R2–R4). */
interface WebDataCleaner {
    suspend fun clearCookiesAndSiteData(): SiteDataClearOutcome   // unchanged
    suspend fun clearWebCache(): Boolean                          // unchanged

    /** + Origins currently holding quota-managed storage (`WebStorage.getOrigins`). Empty on failure. */
    suspend fun storageOrigins(): Set<String>

    /**
     * + Clears cookies and site data of every site except [keepSites] (registrable domains).
     * [candidateHosts] are hosts whose site data may exist (data-model step 1); [isKept] decides by
     * registrable domain. Never throws; failure is reported in the outcome.
     */
    suspend fun clearExcept(
        keepSites: Set<String>,
        candidateHosts: Set<String>,
        isKept: (host: String) -> Boolean,
    ): SiteDataPath?   // null = failed
}

/** ~ C6 — Spec 014 repository: hosts for data-model step 1. */
interface HistoryRepository {
    suspend fun distinctHosts(): Set<String> // +
}

/** ~ C7 — Spec 023 in-memory store. */
interface HttpsUpgradeFailures {
    fun markFailed(host: String)
    fun snapshot(): Set<String>
    fun clear() // + FR-003a
}

// ───────────────────────── data/local ─────────────────────────

/** ~ C8 — Spec 011 cache: keep only icons of [hosts] (FR-008). */
interface FaviconCache {
    suspend fun retainOnly(hosts: Set<String>) // +
}

/** ~ C9 — Spec 021 store. */
class OneTimeGrantStore {
    fun retainOnly(keep: (origin: String) -> Boolean) // + FR-004a
}

/** + C10b — the bundled script texts, loaded once off the main thread (research R6). Impl in data/local/privacy/. */
interface PrivacyScriptBundles {
    /** Install order: fingerprint shield, then cookie-banner engine. Empty list on failure or timeout (fail open). */
    suspend fun await(): List<String>
}

/** + C10 — session secret for fingerprinting noise (FR-026, FR-003a). @Singleton. */
interface PrivacyScriptSession {
    val secret: kotlinx.coroutines.flow.StateFlow<String>
    fun regenerate()
}

// ───────────────────────── domain/usecase ─────────────────────────

/** + C11 — runs data-model "Fire run" steps 1–10 in order; marker handled by FireCoordinator. */
class RunFireUseCase { suspend operator fun invoke(): FireResult }

/** + C12 — process-wide entry point (INV-1, INV-5). */
interface FireCoordinator {
    val state: kotlinx.coroutines.flow.StateFlow<FireState>
    /** Ignored unless Idle. Writes the marker, runs RunFireUseCase, clears the marker. */
    fun fire()
    /** Called once before the first tab is shown; re-runs Fire if the marker is set (FR-007). */
    suspend fun resumeIfInterrupted()
    /** UI acknowledges the Finished state after its animation; → Idle. */
    fun acknowledge()
}

/** + C13 */ class SetSiteFireproofUseCase { suspend operator fun invoke(site: String, fireproof: Boolean): Result<Unit> }
/** + C14 */ class ObserveFireproofSitesUseCase { operator fun invoke(): kotlinx.coroutines.flow.Flow<List<FireproofSite>> }
/** + C15 */ class RemoveFireproofSitesUseCase { suspend operator fun invoke(site: String?): Result<Unit> } // null = all
/** + C16 */ class IsSiteFireproofUseCase { operator fun invoke(host: String): Boolean }

/** ~ C17 — ClearBrowsingDataUseCase gains the FireproofSites branch (INV-10). */

// ───────────────────────── domain/privacy (pure Kotlin) ─────────────────────────

/** + C18 — builds the installed script text (research R5). No Android imports. */
object PrivacyScriptComposer {
    /**
     * @param secret hex, from PrivacyScriptSession
     * @param exceptedSites registrable domains with protection off for this WebView's mode
     * @param bundles bundled asset texts in install order: fingerprint shield, cookie-banner engine, rules
     * @return prelude (JSON literal of the three inputs, sites validated as hostnames) + bundles
     */
    fun compose(secret: String, exceptedSites: Set<String>, bundles: List<String>): String
}

/** + C19 — groups blocked hosts for the dashboard (FR-016). */
object BlockedHostGrouper {
    fun group(hosts: Collection<String>, registrableDomain: (String) -> String): List<BlockedSiteGroup>
}
data class BlockedSiteGroup(val site: String, val hosts: List<String>)

// ───────────────────────── presentation ─────────────────────────

/** + C20 — one per WebView (research R6). */
class PrivacyScriptInstaller {
    /** Installs (or re-installs) when inputs change; no-op when DOCUMENT_START_SCRIPT is unsupported (FR-022). */
    fun attach(webView: android.webkit.WebView, incognito: Boolean)
    fun detach()
}

/** ~ C21 — Spec 023 monitor gains the host list for the dashboard. */
class PageProtectionMonitor {
    val blockedHostsFlow: kotlinx.coroutines.flow.StateFlow<List<String>> // +
}

/** + C22 — dashboard sheet state (data-model PageDashboardState). Own small ViewModel, as Spec 022/023. */
class PrivacyDashboardViewModel

/** + C23 — Settings → Privacy → Fireproof sites (mirrors protectionexceptions/). */
class FireproofSitesViewModel

// ───────────────────────── core/constants ─────────────────────────
// ~ StorageKeys.FIRE_IN_PROGRESS · ~ AssetPaths.PRIVACY_SCRIPTS_* · ~ BrowserLimits.FIRE_OVERLAY_MS / SITE_DATA_DELETE_TIMEOUT_MS
// ~ BrowserLimits.FIRE_SECRET_BYTES · ~ AppDestination.FireproofSites
