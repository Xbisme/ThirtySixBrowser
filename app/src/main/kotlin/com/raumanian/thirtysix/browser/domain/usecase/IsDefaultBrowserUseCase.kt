package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.repository.DefaultBrowserController
import javax.inject.Inject

/** Spec 020 FR-020 / FR-022 — whether the system names this app its default browser, read now. */
class IsDefaultBrowserUseCase @Inject constructor(
    private val controller: DefaultBrowserController,
) {
    operator fun invoke(): Boolean = controller.isDefaultBrowser()
}
