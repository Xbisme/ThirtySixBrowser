package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController
import javax.inject.Inject

/**
 * Spec 016 FR-003, FR-016 — the app language the platform currently holds.
 *
 * Read through the platform seam, never through `SettingsRepository`: the platform is the
 * single source of truth, so a language changed from Android 13+ system settings is what this
 * returns the next time the Settings screen starts.
 */
class GetAppLanguageUseCase @Inject constructor(private val controller: AppLanguageController) {
    operator fun invoke(): AppLanguage = controller.current()
}
