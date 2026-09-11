package com.raumanian.thirtysix.browser.presentation.browser

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

/**
 * Spec 015 FR-010 – FR-012 — the storage-permission gate in front of every download.
 *
 * Android 10 (API 29) and newer write to the public Downloads folder without any
 * permission, so on those devices this gate is a straight pass-through and **no permission
 * is ever requested** (FR-011, SC-005). Only Android 7.0–9.0 — where the legacy
 * external-storage write permission is genuinely required — see a prompt, and only at the
 * moment of the first download rather than at launch, which is what keeps the app honest
 * about Constitution §I's "MUST NOT request runtime permissions it does not actively use".
 *
 * Returns the lambda `BrowserWebView`'s download listener should call. The gate is
 * deliberately the *caller* of the ViewModel rather than something the ViewModel consults:
 * only the composition can launch a permission request, and pushing that knowledge into the
 * ViewModel would drag an `Activity` across the layer boundary.
 */
@Composable
internal fun rememberDownloadRequestHandler(
    onGranted: (url: String, userAgent: String, contentDisposition: String?, mimeType: String?) -> Unit,
    onDenied: (permanently: Boolean) -> Unit,
): (String, String, String?, String?) -> Unit {
    val context = LocalContext.current

    // Held across the permission round trip so the download the user actually asked for is
    // the one that starts once they grant. Dropping it would silently swallow the request.
    val pending = remember { mutableStateOf<PendingDownload?>(null) }

    // FR-010 — shown before re-requesting, which is the point at which the platform's own
    // dialog stops being self-explanatory: the user has already said no once and needs to
    // know why the browser is asking again.
    val showRationale = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending.value
        pending.value = null
        when {
            granted && request != null ->
                onGranted(request.url, request.userAgent, request.contentDisposition, request.mimeType)
            // A denial with the rationale flag now false means the platform will not show
            // the dialog again, so the message must point at system settings instead of
            // re-prompting into a void (FR-012).
            else -> onDenied(!context.shouldShowStorageRationale())
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
                onDenied(false)
            },
        )
    }

    return remember(onGranted, onDenied) {
        { url, userAgent, contentDisposition, mimeType ->
            when {
                !isLegacyStoragePermissionRequired() || context.hasLegacyStoragePermission() ->
                    onGranted(url, userAgent, contentDisposition, mimeType)

                context.shouldShowStorageRationale() -> {
                    pending.value = PendingDownload(url, userAgent, contentDisposition, mimeType)
                    showRationale.value = true
                }

                else -> {
                    pending.value = PendingDownload(url, userAgent, contentDisposition, mimeType)
                    launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }
}

/** Spec 015 FR-010 — the explanation shown before re-requesting storage access. */
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

private data class PendingDownload(
    val url: String,
    val userAgent: String,
    val contentDisposition: String?,
    val mimeType: String?,
)

/**
 * True only on Android 7.0–9.0. From API 29 the public Downloads folder is writable
 * without a permission, and the manifest declaration carries a `maxSdkVersion` ceiling so
 * the permission does not even exist to request there.
 */
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

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
