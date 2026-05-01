// Spec 006 — Language override domain model (Q2 clarification).
//
// "No override" is a first-class value (FollowSystem object), NOT the absence of
// a value (i.e., not `String?`). Consumers branch on the sealed variants
// exhaustively — no null-checks. Storage layer maps absent/null/blank disk values
// to FollowSystem inside SettingsMapper.

package com.raumanian.thirtysix.browser.domain.model

sealed interface LanguageOverride {
    data object FollowSystem : LanguageOverride
    data class Explicit(val bcp47: String) : LanguageOverride
}
