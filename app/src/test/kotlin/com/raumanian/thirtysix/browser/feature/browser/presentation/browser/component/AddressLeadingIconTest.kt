package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.ErrorReason
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.LoadingState
import org.junit.Assert.assertEquals
import org.junit.Test

class AddressLeadingIconTest {

    @Test
    fun `a loaded https page shows the lock`() {
        assertEquals(AppIcons.Lock, addressLeadingIcon(false, false, "https://a.example/", LoadingState.Loaded))
    }

    @Test
    fun `a failed https load shows no lock`() {
        val failed = LoadingState.Failed(ErrorReason.SslError)
        assertEquals(AppIcons.Search, addressLeadingIcon(false, false, "https://expired.example/", failed))
    }

    @Test
    fun `incognito and focus win over the lock`() {
        assertEquals(AppIcons.Incognito, addressLeadingIcon(true, false, "https://a.example/", LoadingState.Loaded))
        assertEquals(AppIcons.Search, addressLeadingIcon(false, true, "https://a.example/", LoadingState.Loaded))
        assertEquals(AppIcons.Search, addressLeadingIcon(false, false, "http://a.example/", LoadingState.Loaded))
    }
}
