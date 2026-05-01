// Spec 003 — Theme mode enum (originally `presentation/theme/`).
// Spec 006 — Relocated to `domain/model/` because theme mode is a domain concept
// (a user preference). Persistence layer lives in `data/local/datastore/`; the
// presentation layer (Spec 003 ThirtySixTheme) only renders the chosen mode.

package com.raumanian.thirtysix.browser.domain.model

import com.raumanian.thirtysix.browser.core.constants.AppDefaults

enum class ThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        /**
         * Map an on-disk storage value back to a ThemeMode variant.
         *
         * Falls back to [AppDefaults.THEME_MODE] when:
         *  - value is null (key absent on disk → first-launch default)
         *  - value does not match any known variant (FR-009 unknown-enum fallback,
         *    e.g., user downgraded from a future build that wrote `"midnight"`)
         */
        fun fromStorageValueOrDefault(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.THEME_MODE
    }
}
