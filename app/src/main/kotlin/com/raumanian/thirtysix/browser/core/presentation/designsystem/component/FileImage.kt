package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.raumanian.thirtysix.browser.core.presentation.util.LocalIoDispatcher
import java.io.File
import kotlinx.coroutines.withContext

/**
 * A cached image file (tab screenshot, favicon) decoded off the main thread. A missing or empty
 * file stays `null`, so callers show their fallback art instead of a broken image.
 */
@Composable
fun rememberFileImage(file: File?): State<ImageBitmap?> {
    val ioDispatcher = LocalIoDispatcher.current
    // Callers hand a new File when the image may have changed, so the instance joins the path as a key.
    return produceState<ImageBitmap?>(initialValue = null, file?.absolutePath, file?.let(System::identityHashCode)) {
        value = withContext(ioDispatcher) {
            file?.takeIf { it.exists() && it.length() > 0L }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ?.asImageBitmap()
        }
    }
}
