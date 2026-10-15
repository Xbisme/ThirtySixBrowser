package com.raumanian.thirtysix.browser.feature.browser.domain.model

/** What the person long-pressed. */
enum class LongPressHit { Link, Image, ImageLink }

/** The actions a long-press menu can offer, in display order. */
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
 * The menu to show. [header] is the address shown at the top; [linkUrl] and [imageUrl] are what
 * the actions act on.
 */
data class LongPressMenu(
    val header: String,
    val linkUrl: String?,
    val imageUrl: String?,
    val actions: List<LongPressAction>,
)
