package com.ytone.longcare.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps privacy persistence and consent-dependent process initialization idempotent.
 * The instance is owned by [MainApplication], so configuration changes cannot reset it.
 */
internal class PrivacyConsentProcessCoordinator(
    private val persistConsent: () -> Unit,
    private val initializeAfterConsent: () -> Unit,
) {
    private val consentRecorded = AtomicBoolean(false)
    private val initializationCompleted = AtomicBoolean(false)

    fun initializeForExistingConsent(isConsented: Boolean) {
        if (!isConsented) return
        consentRecorded.set(true)
        initializeOnce()
    }

    fun grantConsent() {
        if (consentRecorded.compareAndSet(false, true)) {
            runCatching(persistConsent).getOrElse { failure ->
                consentRecorded.set(false)
                throw failure
            }
        }
        initializeOnce()
    }

    private fun initializeOnce() {
        if (!initializationCompleted.compareAndSet(false, true)) return
        initializeAfterConsent()
    }
}
