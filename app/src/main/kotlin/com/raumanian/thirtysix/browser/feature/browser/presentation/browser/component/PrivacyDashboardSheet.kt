@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.protection.PageDashboardState
import kotlinx.coroutines.flow.StateFlow

internal const val TEST_TAG_PRIVACY_DASHBOARD: String = "privacy_dashboard"
internal const val TEST_TAG_DASHBOARD_PROTECTION_SWITCH: String = "privacy_dashboard_protection"
internal const val TEST_TAG_DASHBOARD_FIREPROOF_SWITCH: String = "privacy_dashboard_fireproof"
internal const val TEST_TAG_DASHBOARD_GROUP: String = "privacy_dashboard_group"

private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_GROUP = "group"
private const val CONTENT_TYPE_SWITCH = "switch"

/**
 * Spec 024 US3 — the privacy dashboard for the page on screen, opened from the hub's
 * blocked-tracker line (FR-015). The site, whether the connection is encrypted, the blocked
 * tracker hosts grouped by site, the protection switch and — in normal tabs only — the fireproof
 * switch (FR-016). [dashboardFlow] is collected only here, so the sheet follows the page while
 * open (FR-018) without recomposing the browser. Built from Spec 019 components (design waiver).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyDashboardSheet(
    dashboardFlow: StateFlow<PageDashboardState?>,
    onDismiss: () -> Unit,
    onProtectionSwitched: (Boolean) -> Unit,
    onFireproofSwitched: (Boolean) -> Unit,
) {
    val dashboard by dashboardFlow.collectAsStateWithLifecycle()
    val state = dashboard ?: return
    SheetFrame(onDismiss = onDismiss, modifier = Modifier.testTag(TEST_TAG_PRIVACY_DASHBOARD)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.padding(bottom = Spacing.lg),
        ) {
            item(key = "title", contentType = CONTENT_TYPE_HEADER) { DashboardTitle(state) }
            item(key = "connection", contentType = CONTENT_TYPE_HEADER) { ConnectionRow(state.connectionSecure) }
            item(key = "count", contentType = CONTENT_TYPE_HEADER) { BlockedCount(state.totalBlockedHosts) }
            items(state.blockedGroups, key = { "group:" + it.site }, contentType = { CONTENT_TYPE_GROUP }) { group ->
                ListRow(
                    title = group.site,
                    subtitle = "",
                    below = if (group.hosts != listOf(group.site)) {
                        { WrappedText(group.hosts.joinToString(separator = "\n")) }
                    } else {
                        null
                    },
                    leading = { ListRowIcon(icon = AppIcons.Shield) },
                    modifier = Modifier.testTag(TEST_TAG_DASHBOARD_GROUP),
                )
            }
            item(key = "protection", contentType = CONTENT_TYPE_SWITCH) {
                SwitchRow(
                    title = stringResource(R.string.hub_protection_site_title),
                    body = if (state.protectionOn) {
                        stringResource(R.string.hub_protection_site_on)
                    } else {
                        stringResource(R.string.hub_protection_site_off, state.site)
                    },
                    checked = state.protectionOn,
                    onChange = onProtectionSwitched,
                    icon = AppIcons.Shield,
                    tag = TEST_TAG_DASHBOARD_PROTECTION_SWITCH,
                )
            }
            state.fireproof?.let { fireproof ->
                item(key = "fireproof", contentType = CONTENT_TYPE_SWITCH) {
                    SwitchRow(
                        title = stringResource(R.string.dashboard_fireproof_title),
                        body = stringResource(R.string.dashboard_fireproof_help),
                        checked = fireproof,
                        onChange = onFireproofSwitched,
                        icon = AppIcons.Fire,
                        tag = TEST_TAG_DASHBOARD_FIREPROOF_SWITCH,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardTitle(state: PageDashboardState) {
    Text(
        text = stringResource(R.string.dashboard_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .semantics { heading() },
    )
    Text(
        text = state.site,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.md),
    )
}

@Composable
private fun ConnectionRow(secure: Boolean) {
    ListRow(
        title = stringResource(
            if (secure) R.string.dashboard_connection_secure else R.string.hub_protection_not_secure_title,
        ),
        subtitle = "",
        subtitleColor = MaterialTheme.colorScheme.error,
        below = if (secure) {
            null
        } else {
            { WrappedText(stringResource(R.string.hub_protection_not_secure_body), error = true) }
        },
        leading = { ListRowIcon(icon = if (secure) AppIcons.Lock else AppIcons.Warning, accent = secure) },
    )
}

@Composable
private fun BlockedCount(count: Int) {
    Text(
        text = if (count == 0) {
            stringResource(R.string.dashboard_no_trackers)
        } else {
            pluralStringResource(R.plurals.hub_protection_trackers_blocked, count, count)
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

@Composable
@Suppress("LongParameterList")
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: Int,
    tag: String,
) {
    ListRow(
        title = title,
        subtitle = "",
        below = { WrappedText(body) },
        leading = { ListRowIcon(icon = icon, accent = checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun WrappedText(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
