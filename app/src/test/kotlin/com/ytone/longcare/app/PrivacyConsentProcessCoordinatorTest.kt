package com.ytone.longcare.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyConsentProcessCoordinatorTest {
    @Test
    fun `first consent persists and initializes exactly once across repeated UI callbacks`() {
        var persistCount = 0
        var initializationCount = 0
        val coordinator = PrivacyConsentProcessCoordinator(
            persistConsent = { persistCount += 1 },
            initializeAfterConsent = { initializationCount += 1 },
        )

        coordinator.grantConsent()
        coordinator.grantConsent()

        assertEquals(1, persistCount)
        assertEquals(1, initializationCount)
    }

    @Test
    fun `configuration recreation cannot repeat process initialization`() {
        var initializationCount = 0
        val coordinator = PrivacyConsentProcessCoordinator(
            persistConsent = {},
            initializeAfterConsent = { initializationCount += 1 },
        )

        coordinator.grantConsent()
        coordinator.initializeForExistingConsent(isConsented = true)

        assertEquals(1, initializationCount)
    }

    @Test
    fun `consented cold start initializes without rewriting consent`() {
        var persistCount = 0
        var initializationCount = 0
        val coordinator = PrivacyConsentProcessCoordinator(
            persistConsent = { persistCount += 1 },
            initializeAfterConsent = { initializationCount += 1 },
        )

        coordinator.initializeForExistingConsent(isConsented = true)
        coordinator.initializeForExistingConsent(isConsented = true)

        assertEquals(0, persistCount)
        assertEquals(1, initializationCount)
    }

    @Test
    fun `unconsented cold start does not initialize`() {
        var initializationCount = 0
        val coordinator = PrivacyConsentProcessCoordinator(
            persistConsent = {},
            initializeAfterConsent = { initializationCount += 1 },
        )

        coordinator.initializeForExistingConsent(isConsented = false)

        assertEquals(0, initializationCount)
    }
}
