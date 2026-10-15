package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.core.domain.repository.TabRepository
import com.raumanian.thirtysix.browser.core.domain.util.Result
import javax.inject.Inject
import javax.inject.Named

/**
 * Opens a new tab, at the home address by default, and makes it active. At the tab cap the result
 * is a `Result.Error` whose throwable is `MaxTabsReachedException`.
 */
class CreateTabUseCase @Inject constructor(
    private val repository: TabRepository,
    @param:Named("default_home_url") private val homeUrl: String,
) {
    suspend operator fun invoke(url: String = homeUrl): Result<Tab> =
        repository.createTab(url)
}
