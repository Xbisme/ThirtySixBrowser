package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressAction
import com.raumanian.thirtysix.browser.feature.browser.domain.model.LongPressHit
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.BuildLongPressMenuUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The long-press sheet's header, rows, choice and Back. */
@RunWith(AndroidJUnit4::class)
class LongPressSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val chosen = mutableListOf<LongPressAction>()
    private var dismissed = 0

    private fun render(hit: LongPressHit, link: String?, image: String?) {
        val menu = requireNotNull(BuildLongPressMenuUseCase()(hit, link, image))
        composeRule.setContent {
            ThirtySixTheme { LongPressSheet(menu, onAction = { chosen += it }, onDismiss = { dismissed++ }) }
        }
    }

    @Test
    fun imageLink_showsAddressAndBothGroups() {
        render(LongPressHit.ImageLink, LINK, IMAGE)

        composeRule.onNodeWithText(LINK).assertExists()
        listOf(
            R.string.long_press_open_new_tab,
            R.string.long_press_open_incognito_tab,
            R.string.long_press_copy_link,
            R.string.long_press_share_link,
            R.string.long_press_download_link,
            R.string.long_press_open_image,
            R.string.long_press_download_image,
            R.string.long_press_copy_image,
            R.string.long_press_share_image,
        ).forEach { composeRule.onNodeWithText(context.getString(it)).assertExists() }
    }

    @Test
    fun javascriptLink_offersCopyOnly() {
        render(LongPressHit.Link, "javascript:void(0)", null)

        composeRule.onNodeWithText(context.getString(R.string.long_press_copy_link)).assertExists()
        composeRule.onAllNodesWithText(context.getString(R.string.long_press_open_new_tab)).assertCountEquals(0)
    }

    @Test
    fun choosingARow_reportsItsAction() {
        render(LongPressHit.Link, LINK, null)

        composeRule.onNodeWithText(context.getString(R.string.long_press_copy_link)).performClick()

        assertEquals(listOf(LongPressAction.CopyLink), chosen)
    }

    @Test
    fun back_closesWithoutActing() {
        render(LongPressHit.Link, LINK, null)
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertTrue(chosen.isEmpty())
        assertEquals(1, dismissed)
    }

    private companion object {
        const val LINK = "https://a.example/page"
        const val IMAGE = "https://a.example/i.png"
    }
}
