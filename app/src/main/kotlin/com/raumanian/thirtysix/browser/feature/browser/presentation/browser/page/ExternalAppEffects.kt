@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import kotlinx.coroutines.flow.collectLatest

/**
 * Offers a page's untapped attempt to open another app, latest attempt only, until its page is
 * replaced. Open counts as the tap: a normal tab opens the app, an incognito tab asks first.
 * [loadFallback] loads a web fallback.
 */
@Composable
fun ExternalAppEffects(
    session: PageSession,
    snackbarHostState: SnackbarHostState,
    pageUrl: () -> String,
    loadFallback: (String) -> Unit,
) {
    val message = stringResource(R.string.external_app_offer)
    val open = stringResource(R.string.popup_open)
    val context = LocalContext.current
    val currentPageUrl by rememberUpdatedState(pageUrl)
    val currentLoadFallback by rememberUpdatedState(loadFallback)
    LaunchedEffect(session) {
        session.externalAppOffer.collectLatest { offer ->
            if (offer == null) return@collectLatest
            val result = snackbarHostState.showSnackbar(message, open, duration = SnackbarDuration.Long)
            val page = session.context()?.takeIf { it.generation == offer.generation }
            if (result != SnackbarResult.ActionPerformed || page == null) return@collectLatest
            session.handOffTapped(offer.url, origin = { SiteOrigin.of(currentPageUrl()).orEmpty() }) {
                ExternalAppLauncher.launch(context, offer.url)?.let(currentLoadFallback)
            }
        }
    }
}
