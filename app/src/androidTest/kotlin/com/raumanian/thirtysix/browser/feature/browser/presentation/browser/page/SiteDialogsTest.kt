package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 021 T058 / C2.2–C2.3 — JS dialogs, leave-page and HTTP sign-in answers. */
@RunWith(AndroidJUnit4::class)
class SiteDialogsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val answers = mutableListOf<String>()
    private var suppressed = 0

    private fun text(id: Int) = context.getString(id)

    private fun jsDialog(kind: JsDialogKind, showSuppress: Boolean = false, defaultText: String? = null) =
        PageRequest.JsDialog(
            tabId = 1L,
            generation = 1L,
            origin = "https://example.org",
            kind = kind,
            message = "Hello from the page",
            defaultText = defaultText,
            showSuppress = showSuppress,
            onConfirm = { answers += "ok:$it" },
            onCancel = { answers += "cancel" },
        )

    private fun render(request: PageRequest.JsDialog) = composeRule.setContent {
        ThirtySixTheme { SiteJsDialog(request, onSuppress = { suppressed++ }) { answers += "done" } }
    }

    @Test
    fun alert_namesTheSite_andHasOnlyOk() {
        render(jsDialog(JsDialogKind.Alert))

        composeRule.onNodeWithText(context.getString(R.string.site_dialog_from, "example.org")).assertExists()
        composeRule.onNodeWithText("Hello from the page").assertExists()
        composeRule.onAllNodesWithText(text(R.string.site_dialog_cancel)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(TEST_TAG_SITE_SUPPRESS).assertCountEquals(0)

        composeRule.onNodeWithText(text(R.string.site_dialog_ok)).performClick()
        assertEquals(listOf("ok:null", "done"), answers)
    }

    @Test
    fun confirm_cancelIsAnswered() {
        render(jsDialog(JsDialogKind.Confirm))

        composeRule.onNodeWithText(text(R.string.site_dialog_cancel)).performClick()
        assertEquals(listOf("cancel", "done"), answers)
    }

    @Test
    fun prompt_handsBackTheTypedText() {
        render(jsDialog(JsDialogKind.Prompt, defaultText = "draft"))

        composeRule.onNodeWithTag(TEST_TAG_SITE_PROMPT_FIELD).performTextClearance()
        composeRule.onNodeWithTag(TEST_TAG_SITE_PROMPT_FIELD).performTextInput("typed")
        composeRule.onNodeWithText(text(R.string.site_dialog_ok)).performClick()

        assertEquals(listOf("ok:typed", "done"), answers)
    }

    @Test
    fun secondDialog_offersSuppress_andTickingItStopsTheSite() {
        render(jsDialog(JsDialogKind.Alert, showSuppress = true))

        composeRule.onNodeWithTag(TEST_TAG_SITE_SUPPRESS).performClick()
        composeRule.onNodeWithText(text(R.string.site_dialog_ok)).performClick()

        assertEquals(1, suppressed)
    }

    @Test
    fun back_cancels() {
        render(jsDialog(JsDialogKind.Confirm))
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertEquals(listOf("cancel", "done"), answers)
    }

    @Test
    fun leavePage_leaveAndStay() {
        val request = PageRequest.LeavePage(1L, 1L, "https://example.org", { answers += "leave" }, { answers += "stay" })
        composeRule.setContent { ThirtySixTheme { SiteLeaveDialog(request) { answers += "done" } } }

        composeRule.onNodeWithText(text(R.string.site_leave_leave)).performClick()
        assertEquals(listOf("leave", "done"), answers)
    }

    @Test
    fun httpAuth_signInPassesTheTypedCredentials_once() {
        val request = PageRequest.HttpAuth(
            1L,
            1L,
            "auth.example",
            host = "auth.example",
            realm = "Members",
            onProceed = { u, p -> answers += "proceed:$u:$p" },
            onCancel = { answers += "cancel" },
        )
        composeRule.setContent { ThirtySixTheme { HttpAuthDialog(request) { answers += "done" } } }

        composeRule.onNodeWithText(context.getString(R.string.http_auth_title, "auth.example")).assertExists()
        composeRule.onNodeWithText(text(R.string.http_auth_not_saved)).assertExists()
        composeRule.onNodeWithTag(TEST_TAG_HTTP_AUTH_USERNAME).performTextInput("user")
        composeRule.onNodeWithTag(TEST_TAG_HTTP_AUTH_PASSWORD).performTextInput("secret")
        composeRule.onNodeWithText(text(R.string.http_auth_sign_in)).performClick()

        assertEquals(listOf("proceed:user:secret", "done"), answers)
    }

    @Test
    fun httpAuth_cancel() {
        val request = PageRequest.HttpAuth(1L, 1L, "a", "a", null, { _, _ -> answers += "proceed" }, { answers += "cancel" })
        composeRule.setContent { ThirtySixTheme { HttpAuthDialog(request) { answers += "done" } } }

        composeRule.onNodeWithText(text(R.string.http_auth_cancel)).performClick()
        assertEquals(listOf("cancel", "done"), answers)
    }
}
