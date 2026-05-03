package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.result.Result
import com.raumanian.thirtysix.browser.domain.model.Tab
import com.raumanian.thirtysix.browser.domain.repository.TabRepository
import javax.inject.Inject
import javax.inject.Named

/**
 * Spec 011 — open a new tab (FR-002 / FR-003 / FR-016).
 *
 * Default URL is the project's home URL (Spec 008 — currently
 * `https://www.google.com/`), injected via the existing `@Named("default_home_url")`
 * binding from `UrlConfigModule` (Spec 007). Callers MAY override the URL
 * (e.g., a future "open link in new tab" hook from Spec 011.x).
 *
 * Cap-reached failures arrive as `Result.Error(throwable = MaxTabsReachedException, ...)`.
 * Detection pattern at the call site:
 * `(result as? Result.Error)?.throwable is MaxTabsReachedException`.
 *
 * On success, the new tab's `lastActiveAt` is set so it becomes the active
 * tab immediately — callers do NOT need a separate `SwitchActiveTabUseCase`
 * call.
 */
class CreateTabUseCase @Inject constructor(
    private val repository: TabRepository,
    @param:Named("default_home_url") private val homeUrl: String,
) {
    suspend operator fun invoke(url: String = homeUrl): Result<Tab> =
        repository.createTab(url)
}
