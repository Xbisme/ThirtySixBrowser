package com.raumanian.thirtysix.browser.core.presentation.util

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.raumanian.thirtysix.browser.MainActivity
/**
 * Spec 020 contracts C3.2 / research R8 — the action behind "make ThirtySix the default browser",
 * shared by the Settings row and the Home invitation.
 *
 *  - Android 10+ with the browser role available and not held: the system's own confirmation
 *    (`RoleManager.createRequestRoleIntent`).
 *  - Otherwise, including Android 7–9: the system's default-apps settings screen.
 *  - If the device can open neither, [onUnavailable] runs so the person is told (FR-023).
 *
 * The result is ignored on purpose: the callers re-read the state on resume (FR-022), which also
 * covers a change made later in system settings. Before launching, the activity is told not to
 * count the return as an app launch (FR-024a).
 */
@Composable
fun rememberDefaultBrowserRequest(onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return remember(context, launcher) {
        {
            (context.findActivity() as? MainActivity)?.markLeavingForDefaultBrowserScreen()
            val launched = defaultBrowserIntents(context).any { intent ->
                runCatching { launcher.launch(intent) }
                    .onFailure { if (it !is ActivityNotFoundException) throw it }
                    .isSuccess
            }
            if (!launched) currentOnUnavailable.value()
        }
    }
}

/** The screens to try, best first. */
private fun defaultBrowserIntents(context: Context): List<Intent> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = runCatching { context.getSystemService(RoleManager::class.java) }.getOrNull()
        val canRequestRole = roles != null &&
            roles.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        if (canRequestRole) add(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
    }
    add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}
