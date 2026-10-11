@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.raumanian.thirtysix.browser.R
import java.io.File

/** Whether the camera may be offered: decided by asking for `CAMERA` only when capture is wanted. */
private sealed interface ChooserStage {
    data object AskCamera : ChooserStage

    data class Pick(val withCamera: Boolean) : ChooserStage
}

/**
 * Spec 021 US2 / research R3 — answers a page's file input with the system picker, plus the
 * camera when the page wants a capture (FR-011 – FR-013). The camera permission is requested at
 * this moment only; refusing it still leaves the picker (FR-013). The page is always answered,
 * including when no app can handle the picker.
 */
@Composable
fun FileChooserLauncher(request: PageRequest.FileChooser, done: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.file_chooser_title)
    val wantsCapture = remember(request) { FileChooserIntents.wantsCapture(request.request) }
    val cameraHeld = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    var stage by remember(request) {
        mutableStateOf(if (wantsCapture && !cameraHeld) ChooserStage.AskCamera else ChooserStage.Pick(wantsCapture))
    }
    var photo by remember(request) { mutableStateOf<Pair<File, Uri>?>(null) }
    val finish: (Array<Uri>?) -> Unit = { uris ->
        request.answer(uris)
        done()
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val written = photo?.first?.let { it.exists() && it.length() > 0 } == true
        finish(FileChooserIntents.result(result.resultCode, result.data, photo?.second, written))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        stage = ChooserStage.Pick(withCamera = granted)
    }
    when (val current = stage) {
        ChooserStage.AskCamera -> LaunchedEffect(request) { cameraPermission.launch(Manifest.permission.CAMERA) }
        is ChooserStage.Pick -> LaunchedEffect(request, current) {
            val output = if (current.withCamera) CaptureFiles.newPhoto(context) else null
            photo = output
            val captures = output?.let { FileChooserIntents.captures(request.request, it.second) }.orEmpty()
            val intent = FileChooserIntents.chooser(FileChooserIntents.picker(request.request), captures, title)
            try {
                chooser.launch(intent)
            } catch (_: ActivityNotFoundException) {
                finish(null)
            }
        }
    }
}
