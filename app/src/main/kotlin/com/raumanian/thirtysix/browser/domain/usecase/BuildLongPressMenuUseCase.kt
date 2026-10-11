package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.domain.model.LongPressMenu
import javax.inject.Inject

/**
 * Spec 021 contracts C1.4 — the long-press menu for what was pressed: link actions, image
 * actions, or both for an image inside a link (FR-018, FR-019). An address that is not http(s)
 * (`javascript:`, `data:`) only offers Copy (FR-023). `null` means no menu, so text selection
 * keeps working.
 */
class BuildLongPressMenuUseCase @Inject constructor() {
    operator fun invoke(hit: LongPressHit, linkUrl: String?, imageUrl: String?): LongPressMenu? {
        val link = linkUrl?.takeIf { it.isNotBlank() && hit != LongPressHit.Image }
        val image = imageUrl?.takeIf { it.isNotBlank() && hit != LongPressHit.Link }
        val actions = link?.let(::linkActions).orEmpty() + image?.let(::imageActions).orEmpty()
        val header = link ?: image
        return if (header == null || actions.isEmpty()) null else LongPressMenu(header, link, image, actions)
    }

    private fun linkActions(url: String): List<LongPressAction> =
        if (isWeb(url)) {
            listOf(
                LongPressAction.OpenInNewTab,
                LongPressAction.OpenInIncognitoTab,
                LongPressAction.CopyLink,
                LongPressAction.ShareLink,
                LongPressAction.DownloadLink,
            )
        } else {
            listOf(LongPressAction.CopyLink)
        }

    private fun imageActions(url: String): List<LongPressAction> =
        if (isWeb(url)) {
            listOf(
                LongPressAction.OpenImageInNewTab,
                LongPressAction.DownloadImage,
                LongPressAction.CopyImageAddress,
                LongPressAction.ShareImageLink,
            )
        } else {
            listOf(LongPressAction.CopyImageAddress)
        }

    private fun isWeb(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        return scheme == HTTP || scheme == HTTPS
    }

    private companion object {
        const val HTTP = "http"
        const val HTTPS = "https"
    }
}
