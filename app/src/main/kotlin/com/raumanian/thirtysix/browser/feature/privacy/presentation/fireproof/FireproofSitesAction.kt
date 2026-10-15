package com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof

sealed interface FireproofSitesAction {
    data class Remove(val site: String) : FireproofSitesAction

    data object RemoveAllClick : FireproofSitesAction

    data object RemoveAllConfirmed : FireproofSitesAction

    data object RemoveAllDismissed : FireproofSitesAction
}
