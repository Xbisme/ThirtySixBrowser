package com.raumanian.thirtysix.browser.core.presentation.util

/**
 * One-shot tab-cap event, shared by the tab switcher and the browser screen so both show the
 * same snackbar message.
 */
sealed class TabsErrorEvent {
    /** Creating a normal tab hit the tab cap; consumers clear the event once shown. */
    data object MaxTabsReached : TabsErrorEvent()

    /** Creating an incognito tab hit its own, separate cap, which has its own message. */
    data object MaxIncognitoTabsReached : TabsErrorEvent()
}
