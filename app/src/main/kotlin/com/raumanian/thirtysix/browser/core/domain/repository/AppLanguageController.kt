package com.raumanian.thirtysix.browser.core.domain.repository

import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
/**
 * The app language, held by the platform's per-app language setting; the app keeps no copy.
 * Total: failure shows in the return value. Called on the main thread.
 */
interface AppLanguageController {

    /**
     * Matches the first platform locale by its primary language subtag (`zh-Hans-CN` → Chinese).
     * An empty list, an unsupported language or a failed read gives [AppLanguage.FollowSystem].
     */
    fun current(): AppLanguage

    /**
     * Call only after the activity's `onCreate`. Does not de-duplicate: callers compare with
     * [current] first, because on Android 13+ an identical request still reaches the framework.
     */
    fun apply(language: AppLanguage): Boolean
}
