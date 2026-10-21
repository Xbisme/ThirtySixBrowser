package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
/**
 * Storage permission in front of every download, asked only on Android 7–9 and only at the first
 * download; from API 29 it passes straight through. It lives in composition, the only place that
 * can launch a permission request, and calls the view model rather than being consulted by it.
 */
@Composable
internal fun rememberDownloadRequestHandler(
    onGrant: (DownloadRequestAction.Requested) -> Unit,
    onDeny: (permanently: Boolean) -> Unit,
): (DownloadRequestAction.Requested) -> Unit {
    val context = LocalContext.current

    // Held across the permission round trip so the download the person asked for starts once granted.
    val pending = remember { mutableStateOf<DownloadRequestAction.Requested?>(null) }

    // Shown before re-requesting: the person already said no once and needs to know why we ask again.
    val showRationale = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending.value
        pending.value = null
        when {
            granted && request != null -> onGrant(request)
            // No rationale after a denial means the platform will not ask again: point at system settings.
            else -> onDeny(!context.shouldShowStorageRationale())
        }
    }

    if (showRationale.value) {
        StoragePermissionRationaleDialog(
            onConfirm = {
                showRationale.value = false
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            },
            onDismiss = {
                showRationale.value = false
                pending.value = null
                onDeny(false)
            },
        )
    }

    return remember(onGrant, onDeny) {
        { request ->
            when {
                !isLegacyStoragePermissionRequired() || context.hasLegacyStoragePermission() ->
                    onGrant(request)

                context.shouldShowStorageRationale() -> {
                    pending.value = request
                    showRationale.value = true
                }

                else -> {
                    pending.value = request
                    launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_storage_permission_rationale_title)) },
        text = { Text(stringResource(R.string.downloads_storage_permission_rationale_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.downloads_storage_permission_rationale_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.downloads_storage_permission_rationale_cancel))
            }
        },
    )
}

// From API 29 Downloads is writable without permission, and the manifest caps the declaration there.
private fun isLegacyStoragePermissionRequired(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

private fun Context.hasLegacyStoragePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowStorageRationale(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
}
