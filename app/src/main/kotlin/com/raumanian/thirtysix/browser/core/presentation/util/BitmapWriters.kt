package com.raumanian.thirtysix.browser.core.presentation.util

import android.graphics.Bitmap
import java.io.OutputStream

private const val PNG_QUALITY = 100
private const val WEBP_QUALITY = 75

/** Lossless PNG, the format of the on-disk favicon cache. */
fun Bitmap.pngWriter(): (OutputStream) -> Unit = { out -> compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out) }

/** WebP at quality 75 (about 3–8 KB at 480×270), the format of the on-disk screenshot cache. */
@Suppress("DEPRECATION") // WEBP_LOSSY needs API 30; minSdk is 24.
fun Bitmap.webpWriter(): (OutputStream) -> Unit = { out -> compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out) }
