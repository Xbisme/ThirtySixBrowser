@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PrivacyStageBanner
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.feature.browser.domain.model.PageProtectionStatus
internal const val TEST_TAG_HUB_PROTECTION_SWITCH: String = "hub_protection_switch"
internal const val TEST_TAG_HUB_NOT_SECURE: String = "hub_not_secure"

/**
 * On a web page: the blocked-tracker count, the per-site switch and, on `http://`, "Connection not
 * secure". On Home and the incognito start page only the plain privacy banner shows.
 */
@Composable
internal fun HubProtectionSection(
    status: PageProtectionStatus,
    onBanner: () -> Unit,
    onProtectionSwitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!status.isWebPage) {
        PrivacyStageBanner(text = stringResource(R.string.hub_privacy_banner), onClick = onBanner, modifier = modifier)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier) {
        PrivacyStageBanner(
            text = pluralStringResource(
                R.plurals.hub_protection_trackers_blocked,
                status.blockedTrackerCount,
                status.blockedTrackerCount,
            ),
            onClick = onBanner,
        )
        ListRow(
            title = stringResource(R.string.hub_protection_site_title),
            subtitle = "",
            below = {
                WrappedSubtitle(
                    if (status.protectionOn) {
                        stringResource(R.string.hub_protection_site_on)
                    } else {
                        stringResource(R.string.hub_protection_site_off, status.site.orEmpty())
                    },
                )
            },
            leading = { ListRowIcon(icon = AppIcons.Shield, accent = status.protectionOn) },
            trailing = { Switch(checked = status.protectionOn, onCheckedChange = null) },
            modifier = Modifier
                .testTag(TEST_TAG_HUB_PROTECTION_SWITCH)
                .toggleable(value = status.protectionOn, role = Role.Switch, onValueChange = onProtectionSwitch),
        )
        if (!status.connectionSecure) {
            ListRow(
                title = stringResource(R.string.hub_protection_not_secure_title),
                subtitle = "",
                below = {
                    WrappedSubtitle(
                        stringResource(R.string.hub_protection_not_secure_body),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leading = { ListRowIcon(icon = AppIcons.Warning) },
                modifier = Modifier.testTag(TEST_TAG_HUB_NOT_SECURE),
            )
        }
    }
}

/** A row subtitle that wraps instead of being cut to one line, for the longer protection texts. */
@Composable
private fun WrappedSubtitle(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}
