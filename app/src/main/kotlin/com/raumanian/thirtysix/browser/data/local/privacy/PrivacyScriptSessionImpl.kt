package com.raumanian.thirtysix.browser.data.local.privacy

import com.raumanian.thirtysix.browser.core.constants.BrowserLimits
import com.raumanian.thirtysix.browser.domain.repository.PrivacyScriptSession
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Spec 024 — [PrivacyScriptSession] backed by [SecureRandom]; never written anywhere. */
@Singleton
class PrivacyScriptSessionImpl @Inject constructor() : PrivacyScriptSession {
    private val random = SecureRandom()
    private val state = MutableStateFlow(fresh())

    override val secret: StateFlow<String> = state.asStateFlow()

    override fun regenerate() {
        state.value = fresh()
    }

    private fun fresh(): String {
        val bytes = ByteArray(BrowserLimits.FIRE_SECRET_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
