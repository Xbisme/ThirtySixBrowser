package com.raumanian.thirtysix.browser.presentation.browser

/**
 * Spec 009 — sealed result produced by [classifyAddressBarInput]
 * (in [AddressBarInputClassifier.kt]). Pure-Kotlin, no Android imports.
 *
 * Variants:
 *  - [Empty]: trimmed input is empty / whitespace-only; submit is a no-op
 *    (FR-012).
 *  - [Url]: input matched the URL heuristic; [Url.target] is the fully-resolved
 *    URL with a scheme (FR-009 — `https://` prepended if missing).
 *  - [Query]: input did NOT match the URL heuristic; treat as a search query.
 *    [Query.text] is the trimmed query — NOT yet URL-encoded (encoding happens
 *    in [BrowserViewModel.onAddressBarSubmit] so a future Spec 010
 *    `SearchEngineRepository` refactor only touches the ViewModel).
 *
 * Lives in its own file (not next to the classifier function) to satisfy
 * detekt `MatchingDeclarationName` — same precedent as Spec 008's
 * [com.raumanian.thirtysix.browser.presentation.browser.components.NavigationBottomBarCallbacks].
 */
internal sealed interface AddressBarSubmitResult {
    data object Empty : AddressBarSubmitResult
    data class Url(val target: String) : AddressBarSubmitResult
    data class Query(val text: String) : AddressBarSubmitResult
}
