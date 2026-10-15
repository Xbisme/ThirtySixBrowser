@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ChoiceDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ConfirmDialog
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsIconTone
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsSectionHeader
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsTopBar
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsValueRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.util.capitalizedForLocale
import com.raumanian.thirtysix.browser.core.presentation.util.iconRes
import com.raumanian.thirtysix.browser.core.presentation.util.labelRes
/** Remembered decisions grouped by site. Incognito tabs never write anything shown here. */
@Composable
fun SitePermissionsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: SitePermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigateBack: () -> Unit = { navController.popBackStack() }
    BackHandler(onBack = navigateBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SettingsTopBar(
                onBackClick = navigateBack,
                title = stringResource(R.string.site_permissions_title),
                action = if (state.sites.isEmpty()) {
                    null
                } else {
                    {
                        PillButton(
                            label = stringResource(R.string.site_permissions_remove_all),
                            onClick = viewModel::onRemoveAllClick,
                            style = PillStyle.Danger,
                            modifier = Modifier.testTag(TEST_TAG_SITE_PERMISSIONS_REMOVE_ALL),
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
                .testTag(TEST_TAG_SITE_PERMISSIONS_LIST),
        ) {
            if (state.isLoaded && state.sites.isEmpty()) {
                item(key = KEY_EMPTY) {
                    Text(
                        text = stringResource(R.string.site_permissions_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
            state.sites.forEachIndexed { index, site ->
                siteGroup(site, showDivider = index > 0, viewModel = viewModel)
            }
        }
    }

    SitePermissionsDialogs(state = state, viewModel = viewModel)
}

private fun LazyListScope.siteGroup(
    site: SitePermissionsSite,
    showDivider: Boolean,
    viewModel: SitePermissionsViewModel,
) {
    item(key = "site:${site.origin}", contentType = CONTENT_TYPE_HEADER) {
        SettingsSectionHeader(title = SiteOrigin.display(site.origin), showDivider = showDivider)
    }
    site.decisions.forEach { (capability, decision) ->
        item(key = "site:${site.origin}:${capability.storageValue}", contentType = CONTENT_TYPE_ROW) {
            SettingsValueRow(
                title = stringResource(capability.labelRes()).capitalizedForLocale(),
                value = decisionLabel(decision),
                icon = capability.iconRes(),
                tone = SettingsIconTone.Primary,
                onClick = { viewModel.onDecisionClick(site.origin, capability, decision) },
            )
        }
    }
    item(key = "site:${site.origin}:remove", contentType = CONTENT_TYPE_ACTION) {
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.sm),
        ) {
            PillButton(
                label = stringResource(R.string.site_permissions_remove_site),
                onClick = { viewModel.onRemoveSite(site.origin) },
                style = PillStyle.Danger,
            )
        }
    }
}

@Composable
private fun SitePermissionsDialogs(state: SitePermissionsUiState, viewModel: SitePermissionsViewModel) {
    state.editing?.let { edit ->
        ChoiceDialog(
            title = stringResource(
                R.string.site_permissions_change_title,
                stringResource(edit.capability.labelRes()).capitalizedForLocale(),
                SiteOrigin.display(edit.origin),
            ),
            options = listOf(SiteDecision.Allow, SiteDecision.Deny),
            selected = edit.current,
            optionLabel = { decisionLabel(it) },
            onSelect = viewModel::onDecisionChosen,
            onDismiss = viewModel::onEditDismissed,
            cancelLabel = stringResource(R.string.settings_action_cancel),
        )
    }
    if (state.isConfirmingRemoveAll) {
        ConfirmDialog(
            title = stringResource(R.string.site_permissions_remove_all_confirm_title),
            body = stringResource(R.string.site_permissions_remove_all_confirm_body),
            confirmLabel = stringResource(R.string.site_permissions_remove_all_confirm_action),
            cancelLabel = stringResource(R.string.settings_action_cancel),
            onConfirm = viewModel::onRemoveAllConfirmed,
            onDismiss = viewModel::onRemoveAllDismissed,
        )
    }
}

@Composable
private fun decisionLabel(decision: SiteDecision): String = stringResource(
    when (decision) {
        SiteDecision.Allow -> R.string.site_decision_allowed
        SiteDecision.Deny -> R.string.site_decision_denied
    },
)

private const val KEY_EMPTY: String = "empty"
private const val CONTENT_TYPE_HEADER: String = "header"
private const val CONTENT_TYPE_ROW: String = "row"
private const val CONTENT_TYPE_ACTION: String = "action"

const val TEST_TAG_SITE_PERMISSIONS_LIST: String = "site_permissions_list"
const val TEST_TAG_SITE_PERMISSIONS_REMOVE_ALL: String = "site_permissions_remove_all"
