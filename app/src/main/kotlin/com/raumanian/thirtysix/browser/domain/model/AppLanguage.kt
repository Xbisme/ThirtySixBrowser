// Spec 016 — the app language as the Settings screen offers it (FR-013, FR-014).

package com.raumanian.thirtysix.browser.domain.model

import java.util.Locale

/**
 * Spec 016 — "Follow system" plus the eight languages the app ships (data-model §3).
 *
 * Never persisted by the app: the platform's per-app language setting holds the value and is
 * the single source of truth (FR-016, FR-017). [tag] is the BCP-47 language tag handed to the
 * platform, or `null` for [FollowSystem], which corresponds to the platform holding no
 * app-specific language.
 *
 * Invariant: the eight non-null tags equal the `<locale>` entries of
 * `res/xml/locales_config.xml`. `AppLanguageTest` enforces it, so a locale added in one place
 * and not the other fails the build.
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
        /**
         * Maps a platform-reported language tag to an entry by its **primary language subtag**,
         * case-insensitively — so `zh-Hans-CN` is [Chinese] and `fr-CA` is [French]. A null,
         * blank or unsupported tag is [FollowSystem] rather than a failure.
         */
        fun fromLanguageTagOrFollowSystem(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return FollowSystem
            val primary = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { it.tag.equals(primary, ignoreCase = true) } ?: FollowSystem
        }
    }
}
