package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Switches the app language on the main thread, as the platform requires; returns whether it was
 * accepted. Re-choosing the current language returns `true` without a call, because on Android 13+
 * an identical request would still reach the framework and rebuild the screen.
 */
class SetAppLanguageUseCase @Inject constructor(
    private val controller: AppLanguageController,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(language: AppLanguage): Boolean = withContext(dispatchers.main) {
        if (language == controller.current()) true else controller.apply(language)
    }
}
