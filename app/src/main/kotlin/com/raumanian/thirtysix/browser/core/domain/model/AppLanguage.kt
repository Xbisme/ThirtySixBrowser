package com.raumanian.thirtysix.browser.core.domain.model

import java.util.Locale

/**
 * Never persisted by the app: the platform's per-app language setting is the source of truth.
 * [tag] is null for [FollowSystem]. The eight tags must equal `res/xml/locales_config.xml`'s
 * entries; `AppLanguageTest` enforces it.
 */
enum class AppLanguage(val tag: String?) {
    FollowSystem(null),
    English("en"),
    Vietnamese("vi"),
    German("de"),
    Russian("ru"),
    Korean("ko"),
    Japanese("ja"),
    Chinese("zh"),
    French("fr"),
    ;

    companion object {
        // Matches the primary subtag only, so `zh-Hans-CN` is Chinese and `fr-CA` French.
        fun fromLanguageTagOrFollowSystem(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return FollowSystem
            val primary = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { it.tag.equals(primary, ignoreCase = true) } ?: FollowSystem
        }
    }
}
