package com.ytone.longcare.platform.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkPackageVerifierTest {
    @Test
    fun `single signer rotation is accepted when installed signer is in candidate history`() {
        val installed = SigningIdentity(
            currentSigners = setOf("old"),
            signingHistory = setOf("old"),
            hasMultipleSigners = false,
        )
        val candidate = SigningIdentity(
            currentSigners = setOf("new"),
            signingHistory = setOf("old", "new"),
            hasMultipleSigners = false,
        )

        assertTrue(isSigningIdentityCompatible(installed, candidate))
    }

    @Test
    fun `unrelated signer is rejected`() {
        val installed = SigningIdentity(setOf("release"), setOf("release"), false)
        val candidate = SigningIdentity(setOf("attacker"), setOf("attacker"), false)

        assertFalse(isSigningIdentityCompatible(installed, candidate))
    }

    @Test
    fun `multiple signer packages require exact current signer set`() {
        val installed = SigningIdentity(setOf("one", "two"), setOf("one", "two"), true)
        val candidate = SigningIdentity(setOf("one"), setOf("one", "two"), false)

        assertFalse(isSigningIdentityCompatible(installed, candidate))
    }
}
