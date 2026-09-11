package com.raumanian.thirtysix.browser.domain.repository

import com.raumanian.thirtysix.browser.domain.model.AppLanguage

/**
 * Spec 016 — reads and writes the app language held by the platform's per-app language
 * setting, which is the single source of truth (FR-016, FR-017). The app keeps no copy of its
 * own.
 *
 * Kept in `domain/repository/` beside `CookieJarSnapshotManager` — the placement Spec 012 chose
 * for a singleton platform-state manager that use cases consume. The interface is pure Kotlin;
 * only the implementation touches `androidx.appcompat`.
 *
 * Mechanism (research R1): `AppCompatDelegate.getApplicationLocales()` /
 * `AppCompatDelegate.setApplicationLocales(...)`, with `AppLocalesMetadataHolderService`
 * declared in the manifest with `autoStoreLocales = true` so the choice persists below
 * Android 13; the framework persists it on Android 13+.
 *
 * Every member is total: failure is expressed in the return value, never thrown.
 * Both members are called on the main thread.
 */
interface AppLanguageController {

    /**
     * The language the platform currently holds for this app.
     *
     *  - An empty platform locale list ("no app-specific locale") → [AppLanguage.FollowSystem].
     *  - A non-empty list → the entry whose tag matches the first locale's **primary language
     *    subtag** (so `zh-Hans-CN` resolves to `Chinese`).
     *  - A language outside the eight supported → [AppLanguage.FollowSystem].
     *  - A platform read that throws → [AppLanguage.FollowSystem], logged.
     */
    fun current(): AppLanguage

    /**
     * Asks the platform to apply [language].
     *
     *  - [AppLanguage.FollowSystem] → the empty locale list, which resets to the system language.
     *  - Any other value → a single-entry locale list built from its tag.
     *
     * MUST be called on the main thread and only after the activity's `onCreate` (R1). The
     * platform applies the change by recreating the activity, so the page on screen may reload
     * (spec A6).
     *
     * The implementation does NOT de-duplicate: callers compare with [current] first (FR-006),
     * because on Android 13+ an identical request is still forwarded to the framework and its
     * effect is unverified (R1).
     *
     * @return `true` if the platform accepted the request; `false` if the call threw.
     */
    fun apply(language: AppLanguage): Boolean
}
