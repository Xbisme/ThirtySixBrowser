@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.fireproof

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
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.settings.components.SettingsTopBar
/**
 * Spec 024 FR-012 — Settings → Privacy → Fireproof sites. Each row removes one site; "Remove all"
 * asks first; an empty list explains how to add one. Built from Spec 019 components (design
 * waiver, spec Assumptions).
 */
@Composable
fun FireproofSitesScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: FireproofSitesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateBack: () -> Unit = { navController.popBackStack() }
    BackHandler(onBack = navigateBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SettingsTopBar(
                onBackClick = navigateBack,
                title = stringResource(R.string.settings_fireproof_sites),
                action = if (state.sites.isEmpty()) {
                    null
                } else {
                    {
                        PillButton(
                            label = stringResource(R.string.fireproof_sites_remove_all),
                            onClick = viewModel::onRemoveAllClick,
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
                FireproofRow(site = site, onRemove = { viewModel.onRemove(site) })
            }
        }
    }

    if (state.isConfirmingRemoveAll) {
        ConfirmDialog(
            title = stringResource(R.string.fireproof_sites_remove_all_confirm_title),
            body = stringResource(R.string.fireproof_sites_remove_all_confirm_body),
            confirmLabel = stringResource(R.string.fireproof_sites_remove_all),
            cancelLabel = stringResource(R.string.settings_action_cancel),
            onConfirm = viewModel::onRemoveAllConfirmed,
            onDismiss = viewModel::onRemoveAllDismissed,
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
