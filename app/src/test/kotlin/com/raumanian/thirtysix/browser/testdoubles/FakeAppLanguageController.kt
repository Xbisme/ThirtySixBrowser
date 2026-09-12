package com.raumanian.thirtysix.browser.testdoubles

import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController

/**
 * Spec 016 T052 — in-memory [AppLanguageController].
 *
 * [currentLanguage] is what the "platform" holds. Every [apply] is recorded in [applied]; when
 * [applyResult] is `true` the request also becomes the current language, as the real platform
 * would report it afterwards.
 */
class FakeAppLanguageController(
    var currentLanguage: AppLanguage = AppLanguage.FollowSystem,
    var applyResult: Boolean = true,
) : AppLanguageController {

    val applied: MutableList<AppLanguage> = mutableListOf()

    override fun current(): AppLanguage = currentLanguage

    override fun apply(language: AppLanguage): Boolean {
        applied += language
        if (applyResult) currentLanguage = language
        return applyResult
    }
}
