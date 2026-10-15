package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import javax.inject.Inject

/** Whether the system names this app its default browser, read now. */
class IsDefaultBrowserUseCase @Inject constructor(
    private val controller: DefaultBrowserController,
) {
    operator fun invoke(): Boolean = controller.isDefaultBrowser()
}
