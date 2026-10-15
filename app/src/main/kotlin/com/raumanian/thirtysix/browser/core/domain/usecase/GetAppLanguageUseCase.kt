package com.raumanian.thirtysix.browser.core.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import javax.inject.Inject

/**
 * The app language the platform currently holds. Read from the platform, not `SettingsRepository`,
 * so a language changed in Android 13+ system settings is reflected here.
 */
class GetAppLanguageUseCase @Inject constructor(private val controller: AppLanguageController) {
    operator fun invoke(): AppLanguage = controller.current()
}
