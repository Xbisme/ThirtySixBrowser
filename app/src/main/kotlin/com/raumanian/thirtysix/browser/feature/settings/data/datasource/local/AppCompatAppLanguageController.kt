package com.raumanian.thirtysix.browser.feature.settings.data.datasource.local

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.repository.AppLanguageController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 13+ stores the language in the framework LocaleManager; below that AppCompat persists it
 * via autoStoreLocales in the manifest. Never throws, and never logs the chosen locale.
 */
@Singleton
class AppCompatAppLanguageController @Inject constructor() : AppLanguageController {

    override fun current(): AppLanguage = runCatching {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            AppLanguage.FollowSystem
        } else {
            AppLanguage.fromLanguageTagOrFollowSystem(locales[0]?.toLanguageTag())
        }
    }.getOrElse { error ->
        Log.w(LOG_TAG, "Reading the app language failed", error)
        AppLanguage.FollowSystem
    }

    override fun apply(language: AppLanguage): Boolean = runCatching {
        val locales = language.tag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }.onFailure { error ->
        Log.w(LOG_TAG, "Applying the app language failed", error)
    }.isSuccess

    private companion object {
        const val LOG_TAG: String = "AppLanguageController"
    }
}
