package com.raumanian.thirtysix.browser.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spec 019 — a cached image file (tab screenshot, favicon) decoded off the main thread. Starts
 * `null` and fills in once decoded; a missing or empty file stays `null`, so callers show
 * their fallback art instead of a broken image (spec edge case "preview missing").
 */
@Composable
fun rememberFileImage(file: File?): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, file?.absolutePath, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() && it.length() > 0L }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ?.asImageBitmap()
        }
    }
