package com.raumanian.thirtysix.browser.core.domain.model

import com.raumanian.thirtysix.browser.core.domain.util.AppDefaults
enum class ThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        // An unknown value (e.g. written by a newer build) falls back to the default.
        fun fromStorageValueOrDefault(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: AppDefaults.THEME_MODE
    }
}
