package com.raumanian.thirtysix.browser.feature.browser.domain.usecase

import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.CopyImageAddress
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.CopyLink
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.DownloadImage
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.DownloadLink
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.OpenImageInNewTab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.OpenInIncognitoTab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.OpenInNewTab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.ShareImageLink
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction.ShareLink
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every row of the long-press menu for links, images and image links. */
class BuildLongPressMenuUseCaseTest {

    private val build = BuildLongPressMenuUseCase()
    private val linkActions = listOf(OpenInNewTab, OpenInIncognitoTab, CopyLink, ShareLink, DownloadLink)
    private val imageActions = listOf(OpenImageInNewTab, DownloadImage, CopyImageAddress, ShareImageLink)

    @Test
    fun `link offers the five link actions`() {
        assertEquals(LongPressMenu(LINK, LINK, null, linkActions), build(LongPressHit.Link, LINK, null))
    }

    @Test
    fun `image offers the four image actions`() {
        assertEquals(LongPressMenu(IMAGE, null, IMAGE, imageActions), build(LongPressHit.Image, null, IMAGE))
    }

    @Test
    fun `image inside a link offers link actions then image actions, headed by the link`() {
        assertEquals(
            LongPressMenu(LINK, LINK, IMAGE, linkActions + imageActions),
            build(LongPressHit.ImageLink, LINK, IMAGE),
        )
    }

    @Test
    fun `javascript link offers copy only`() {
        val js = "javascript:void(0)"
        assertEquals(LongPressMenu(js, js, null, listOf(CopyLink)), build(LongPressHit.Link, js, null))
    }

    @Test
    fun `data image offers copy only`() {
        val data = "data:image/png;base64,AAAA"
        assertEquals(
            LongPressMenu(data, null, data, listOf(CopyImageAddress)),
            build(LongPressHit.Image, null, data),
        )
    }

    @Test
    fun `image link whose link url is still unknown offers the image group`() {
        assertEquals(LongPressMenu(IMAGE, null, IMAGE, imageActions), build(LongPressHit.ImageLink, null, IMAGE))
    }

    @Test
    fun `nothing to act on means no menu`() {
        assertNull(build(LongPressHit.Link, null, null))
        assertNull(build(LongPressHit.Link, "", null))
        assertNull(build(LongPressHit.Image, null, " "))
    }

    private companion object {
        const val LINK = "https://a.org/x"
        const val IMAGE = "https://a.org/i.png"
    }
}
