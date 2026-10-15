@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsTopBar
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/** Settings → Privacy → Fireproof sites. "Remove all" asks first. */
@Composable
fun FireproofSitesRoute(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: FireproofSitesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateBack: () -> Unit = { navController.popBackStack() }
    BackHandler(onBack = navigateBack)
    FireproofSitesScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = navigateBack,
        modifier = modifier,
    )
}

@Composable
fun FireproofSitesScreen(
    state: FireproofSitesUiState,
    onAction: (FireproofSitesAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SettingsTopBar(
                onBackClick = onBack,
                title = stringResource(R.string.settings_fireproof_sites),
                action = if (state.sites.isEmpty()) {
                    null
                } else {
                    {
                        PillButton(
                            label = stringResource(R.string.fireproof_sites_remove_all),
                            onClick = { onAction(FireproofSitesAction.RemoveAllClick) },
                            style = PillStyle.Danger,
                            modifier = Modifier.testTag(TEST_TAG_FIREPROOF_SITES_REMOVE_ALL),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag(TEST_TAG_FIREPROOF_SITES_LIST),
        ) {
            if (state.isLoaded && state.sites.isEmpty()) {
                item(key = KEY_EMPTY, contentType = CONTENT_TYPE_EMPTY) {
                    Text(
                        text = stringResource(R.string.fireproof_sites_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md).testTag(TEST_TAG_FIREPROOF_SITES_EMPTY),
                    )
                }
            }
            items(state.sites, key = { it }, contentType = { CONTENT_TYPE_ROW }) { site ->
                FireproofRow(site = site, onRemove = { onAction(FireproofSitesAction.Remove(site)) })
            }
        }
    }

    if (state.isConfirmingRemoveAll) {
        ConfirmDialog(
            title = stringResource(R.string.fireproof_sites_remove_all_confirm_title),
            body = stringResource(R.string.fireproof_sites_remove_all_confirm_body),
            confirmLabel = stringResource(R.string.fireproof_sites_remove_all),
            cancelLabel = stringResource(R.string.settings_action_cancel),
            onConfirm = { onAction(FireproofSitesAction.RemoveAllConfirmed) },
            onDismiss = { onAction(FireproofSitesAction.RemoveAllDismissed) },
        )
    }
}

@Composable
private fun FireproofRow(site: String, onRemove: () -> Unit) {
    val description = stringResource(R.string.fireproof_sites_remove_cd, site)
    ListRow(
        title = site,
        subtitle = "",
        leading = { ListRowIcon(icon = AppIcons.Fire) },
        trailing = {
            PillButton(
                label = stringResource(R.string.fireproof_sites_remove),
                onClick = onRemove,
                style = PillStyle.Tonal,
                modifier = Modifier.semantics { contentDescription = description },
            )
        },
    )
}

private const val KEY_EMPTY: String = "empty"
private const val CONTENT_TYPE_EMPTY: String = "empty"
private const val CONTENT_TYPE_ROW: String = "row"

const val TEST_TAG_FIREPROOF_SITES_LIST: String = "fireproof_sites_list"
const val TEST_TAG_FIREPROOF_SITES_EMPTY: String = "fireproof_sites_empty"
const val TEST_TAG_FIREPROOF_SITES_REMOVE_ALL: String = "fireproof_sites_remove_all"
