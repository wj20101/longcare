import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAppSigningTxFaceConventionPluginTest {

    @Test
    fun `unsigned release fallback should stay disabled without explicit override`() {
        assertFalse(resolveUnsignedReleaseFallback(null))
    }

    @Test
    fun `unsigned release fallback should enable only for explicit true override`() {
        assertTrue(resolveUnsignedReleaseFallback("true"))
        assertTrue(resolveUnsignedReleaseFallback("1"))
        assertTrue(resolveUnsignedReleaseFallback("yes"))
    }

    @Test
    fun `unsigned release fallback should stay disabled for false or invalid override`() {
        assertFalse(resolveUnsignedReleaseFallback("false"))
        assertFalse(resolveUnsignedReleaseFallback("0"))
        assertFalse(resolveUnsignedReleaseFallback("unexpected"))
    }

    @Test
    fun `release signing should be rejected when signing config is debug`() {
        assertFalse(
            isSafeReleaseSigningConfig(
                signingConfigName = "debug",
                signingStorePath = null,
                debugStorePath = "/Users/test/.android/debug.keystore"
            )
        )
    }

    @Test
    fun `release signing should be rejected when store file points to debug keystore`() {
        assertFalse(
            isSafeReleaseSigningConfig(
                signingConfigName = "release",
                signingStorePath = "/Users/test/.android/debug.keystore",
                debugStorePath = "/Users/test/.android/debug.keystore"
            )
        )
    }

    @Test
    fun `release signing should be accepted for non debug keystore`() {
        assertTrue(
            isSafeReleaseSigningConfig(
                signingConfigName = "release",
                signingStorePath = "/Users/test/.gradle/keystores/longcare/longcare-release.jks",
                debugStorePath = "/Users/test/.android/debug.keystore"
            )
        )
    }
}
