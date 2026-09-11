package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Spec 016 FR-013 – FR-015 — switch the app language through the platform.
 *
 *  - Choosing the language the platform already holds returns `true` without calling it, so
 *    nothing is rebuilt and no page reloads (FR-006). This is also why the controller itself
 *    does not de-duplicate: on Android 13+ an identical request would still reach the framework.
 *  - Otherwise the change is applied on the main dispatcher, which the platform requires
 *    (research R1), and the result says whether it was accepted. `false` becomes
 *    `LanguageNotApplied` in the view model.
 *  - Never throws: the controller is total.
 */
class SetAppLanguageUseCase @Inject constructor(
    private val controller: AppLanguageController,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(language: AppLanguage): Boolean = withContext(dispatchers.main) {
        if (language == controller.current()) true else controller.apply(language)
    }
}
