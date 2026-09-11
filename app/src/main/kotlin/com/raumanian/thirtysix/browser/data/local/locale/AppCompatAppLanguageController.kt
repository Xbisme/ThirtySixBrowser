package com.raumanian.thirtysix.browser.data.local.locale

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.repository.AppLanguageController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec 016 — [AppLanguageController] over AndroidX's per-app language API (research R1).
 *
 * On Android 13+ AppCompat forwards both calls to the framework `LocaleManager`, which stores
 * the value and keeps it in sync with the system Settings app. Below Android 13 AppCompat keeps
 * the value itself, persisted through `AppLocalesMetadataHolderService` with
 * `autoStoreLocales = true` in the manifest, and applies it by recreating the
 * `AppCompatActivity`.
 *
 * Every platform call is wrapped in `runCatching`, so this seam never throws (FR-043).
 * Failures log only which operation failed — never a locale the user chose.
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
