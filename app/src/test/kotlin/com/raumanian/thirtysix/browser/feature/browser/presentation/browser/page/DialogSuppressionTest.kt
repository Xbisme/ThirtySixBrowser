package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import org.junit.Assert.assertEquals
import org.junit.Test

/** Suppressing a page's repeated dialogs, per page and per tab. */
class DialogSuppressionTest {

    private val dialogs = DialogSuppression()

    @Test
    fun `the first dialog has no checkbox, the second and later do`() {
        assertEquals(DialogAdmission.Show(offerSuppress = false), dialogs.admit(1, 1))
        assertEquals(DialogAdmission.Show(offerSuppress = true), dialogs.admit(1, 1))
        assertEquals(DialogAdmission.Show(offerSuppress = true), dialogs.admit(1, 1))
    }

    @Test
    fun `once suppressed, the page's dialogs are not shown`() {
        dialogs.admit(1, 1)
        dialogs.admit(1, 1)
        dialogs.suppress(1, 1)

        assertEquals(DialogAdmission.Suppressed, dialogs.admit(1, 1))
        assertEquals(DialogAdmission.Suppressed, dialogs.admit(1, 1))
    }

    @Test
    fun `a new page starts again`() {
        dialogs.admit(1, 1)
        dialogs.admit(1, 1)
        dialogs.suppress(1, 1)

        assertEquals(DialogAdmission.Show(offerSuppress = false), dialogs.admit(1, 2))
    }

    @Test
    fun `another tab starts again`() {
        dialogs.admit(1, 1)
        dialogs.suppress(1, 1)

        assertEquals(DialogAdmission.Show(offerSuppress = false), dialogs.admit(2, 1))
    }
}
