@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.settings.protectionexceptions

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
 * Spec 023 FR-015 — Settings → Privacy → Sites without protection. Each row turns protection
 * back on for one site; "Turn all back on" asks first. Built from Spec 019 components (design
 * waiver, spec Assumptions).
 */
@Composable
fun ProtectionExceptionsScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: ProtectionExceptionsViewModel = hiltViewModel(),
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
                title = stringResource(R.string.settings_protection_exceptions),
                action = if (state.sites.isEmpty()) {
                    null
                } else {
                    {
                        PillButton(
                            label = stringResource(R.string.protection_exceptions_turn_all_on),
                            onClick = viewModel::onTurnAllOnClick,
                            style = PillStyle.Danger,
                            modifier = Modifier.testTag(TEST_TAG_PROTECTION_EXCEPTIONS_TURN_ALL_ON),
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
                .testTag(TEST_TAG_PROTECTION_EXCEPTIONS_LIST),
        ) {
            if (state.isLoaded && state.sites.isEmpty()) {
                item(key = KEY_EMPTY, contentType = CONTENT_TYPE_EMPTY) {
                    Text(
                        text = stringResource(R.string.protection_exceptions_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md).testTag(TEST_TAG_PROTECTION_EXCEPTIONS_EMPTY),
                    )
                }
            }
            items(state.sites, key = { it }, contentType = { CONTENT_TYPE_ROW }) { site ->
                ExceptionRow(site = site, onTurnOn = { viewModel.onTurnOn(site) })
            }
        }
    }

    if (state.isConfirmingTurnAllOn) {
        ConfirmDialog(
            title = stringResource(R.string.protection_exceptions_turn_all_on_confirm_title),
            body = stringResource(R.string.protection_exceptions_turn_all_on_confirm_body),
            confirmLabel = stringResource(R.string.protection_exceptions_turn_on),
            cancelLabel = stringResource(R.string.settings_action_cancel),
            onConfirm = viewModel::onTurnAllOnConfirmed,
            onDismiss = viewModel::onTurnAllOnDismissed,
        )
    }
}

@Composable
private fun ExceptionRow(site: String, onTurnOn: () -> Unit) {
    val description = stringResource(R.string.protection_exceptions_turn_on_cd, site)
    ListRow(
        title = site,
        subtitle = "",
        leading = { ListRowIcon(icon = AppIcons.Shield) },
        trailing = {
            PillButton(
                label = stringResource(R.string.protection_exceptions_turn_on),
                onClick = onTurnOn,
                style = PillStyle.Tonal,
                modifier = Modifier.semantics { contentDescription = description },
            )
        },
    )
}

private const val KEY_EMPTY: String = "empty"
private const val CONTENT_TYPE_EMPTY: String = "empty"
private const val CONTENT_TYPE_ROW: String = "row"

const val TEST_TAG_PROTECTION_EXCEPTIONS_LIST: String = "protection_exceptions_list"
const val TEST_TAG_PROTECTION_EXCEPTIONS_EMPTY: String = "protection_exceptions_empty"
const val TEST_TAG_PROTECTION_EXCEPTIONS_TURN_ALL_ON: String = "protection_exceptions_turn_all_on"
