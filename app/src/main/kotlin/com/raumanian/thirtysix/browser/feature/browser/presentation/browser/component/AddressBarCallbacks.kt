package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

/** What [AddressBar] reports; the host dismisses the keyboard and clears focus before loading. */
internal data class AddressBarCallbacks(
    val onTextChange: (String) -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    val onSubmit: () -> Unit,
    val onClear: () -> Unit,
    /** A chosen suggestion loads as if it had been typed and submitted. */
    val onSuggestion: (String) -> Unit = {},
)
