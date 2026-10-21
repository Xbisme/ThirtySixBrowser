package com.raumanian.thirtysix.browser.core.domain.repository

/** Photos taken with the camera for a page's file upload, kept only until the upload is answered. */
interface CaptureCache {
    suspend fun clear()
}
