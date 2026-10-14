@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.domain.usecase.SitePermissionChoice
import kotlinx.coroutines.launch

private enum class PermissionStage { Choose, System, Blocked }

/**
 * Spec 021 US1 / C2.1 — asks the person about a site's camera / microphone / location request,
 * then, only after an allow choice, the Android permission (FR-002). A permanently denied Android
 * permission gets an explanation with Open settings (FR-006).
 */
@Composable
fun SitePermissionDialog(request: PageRequest.Permission, gate: SitePermissionGate, done: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember(request) {
        mutableStateOf(if (request.needsSystemPermissionOnly) PermissionStage.System else PermissionStage.Choose)
    }
    val finish: (Boolean) -> Unit = { allow ->
        if (allow) request.grant() else request.deny()
        done()
    }
    val systemPermissions = request.capabilities.flatMap { it.androidPermissions() }.distinct()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (gate.holdsSystemPermissions(request.capabilities)) {
            finish(true)
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                systemPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            if (canAskAgain) finish(false) else stage = PermissionStage.Blocked
        }
    }
    when (stage) {
        PermissionStage.Choose -> ChooseDialog(request) { choice ->
            scope.launch {
                val effect = gate.apply(choice, request)
                when {
                    !effect.allowNow -> finish(false)
                    gate.holdsSystemPermissions(request.capabilities) -> finish(true)
                    else -> stage = PermissionStage.System
                }
            }
        }
        PermissionStage.System -> LaunchedEffect(request) { launcher.launch(systemPermissions.toTypedArray()) }
        PermissionStage.Blocked -> BlockedDialog(
            capabilities = request.capabilities,
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts(PACKAGE_SCHEME, context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finish(false)
            },
            onNotNow = { finish(false) },
        )
    }
}

@Composable
private fun ChooseDialog(request: PageRequest.Permission, onChoice: (SitePermissionChoice) -> Unit) {
    val alwaysAllow = SiteDialogAction(
        stringResource(R.string.site_permission_always_allow),
        PillStyle.Filled,
        { onChoice(SitePermissionChoice.AlwaysAllow) },
    )
    val allowOnce = SiteDialogAction(
        stringResource(R.string.site_permission_allow_once),
        if (request.isIncognito) PillStyle.Filled else PillStyle.Tonal,
        { onChoice(SitePermissionChoice.AllowOnce) },
    )
    val deny = SiteDialogAction(
        stringResource(R.string.site_permission_deny),
        PillStyle.Text,
        { onChoice(SitePermissionChoice.Deny) },
    )
    SiteDialogFrame(
        title = stringResource(
            R.string.site_permission_title,
            SiteOrigin.display(request.origin),
            capabilityList(request.askedCapabilities),
        ),
        // FR-004 — incognito never remembers, so "Always allow" is not offered.
        actions = if (request.isIncognito) listOf(allowOnce, deny) else listOf(alwaysAllow, allowOnce, deny),
        onDismiss = { onChoice(SitePermissionChoice.Deny) },
        glyph = request.askedCapabilities.minByOrNull { it.ordinal }?.iconRes() ?: AppIcons.Shield,
    )
}

@Composable
private fun BlockedDialog(capabilities: Set<SiteCapability>, onOpenSettings: () -> Unit, onNotNow: () -> Unit) {
    val names = capabilityList(capabilities)
    SiteDialogFrame(
        title = stringResource(R.string.site_permission_blocked_title, names.capitalizedForLocale()),
        body = stringResource(R.string.site_permission_blocked_body, names),
        actions = listOf(
            SiteDialogAction(stringResource(R.string.site_permission_open_settings), PillStyle.Filled, onOpenSettings),
            SiteDialogAction(stringResource(R.string.site_permission_not_now), PillStyle.Text, onNotNow),
        ),
        onDismiss = onNotNow,
        glyph = AppIcons.Settings,
    )
}

private const val PACKAGE_SCHEME: String = "package"
