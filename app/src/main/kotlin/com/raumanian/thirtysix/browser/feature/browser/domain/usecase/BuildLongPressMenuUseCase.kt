package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressMenu
import javax.inject.Inject

/**
 * The long-press menu for what was pressed: link actions, image actions, or both for an image in
 * a link. A non-http(s) address (`javascript:`, `data:`) only offers Copy. `null` means no menu,
 * so text selection keeps working.
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
