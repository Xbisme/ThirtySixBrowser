package com.raumanian.thirtysix.browser.feature.browser.domain.model

/** Spec 021 research R7 — what the person long-pressed. */
enum class LongPressHit { Link, Image, ImageLink }

/** Spec 021 FR-018 / FR-019 — the actions a long-press menu can offer, in display order. */
enum class LongPressAction {
    OpenInNewTab,
    OpenInIncognitoTab,
    CopyLink,
    ShareLink,
    DownloadLink,
    OpenImageInNewTab,
    DownloadImage,
    CopyImageAddress,
    ShareImageLink,
}

/**
 * Spec 021 data-model §7 — the menu to show. [header] is the address shown at the top; [linkUrl]
 * and [imageUrl] are what the actions act on.
 */
data class LongPressMenu(
    val header: String,
    val linkUrl: String?,
    val imageUrl: String?,
    val actions: List<LongPressAction>,
)
