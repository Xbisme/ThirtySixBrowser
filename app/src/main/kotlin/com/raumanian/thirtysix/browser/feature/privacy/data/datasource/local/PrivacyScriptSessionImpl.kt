package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [PrivacyScriptSession] backed by [SecureRandom]; never written anywhere. */
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
