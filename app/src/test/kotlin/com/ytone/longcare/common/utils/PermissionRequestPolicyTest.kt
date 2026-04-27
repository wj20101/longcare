package com.ytone.longcare.common.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestPolicyTest {

    @Test
    fun `camera permission is not launched from composition side effects`() {
        val files = listOf(
            "src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraPermissionGate.kt",
            "src/main/kotlin/com/ytone/longcare/features/face/ui/ManualFaceCaptureEffects.kt",
            "src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureScreen.kt"
        )

        files.forEach { path ->
            val source = File(path).readText()
            assertFalse(
                "$path must not request camera permission from LaunchedEffect",
                source.contains("LaunchedEffect(Unit)") &&
                    source.contains("launch(Manifest.permission.CAMERA)")
            )
        }
    }

    @Test
    fun `camera watermark screen does not request location permission on resume`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreenContentLifecycle.kt"
        ).readText()

        assertFalse(source.contains("launcher.launch(locationPermissions)"))
        assertFalse(source.contains("RequestMultiplePermissions"))
    }

    @Test
    fun `camera permission gate initializes from system permission`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraPermissionGate.kt"
        ).readText()

        assertTrue(source.contains("ContextCompat.checkSelfPermission"))
        assertTrue(source.contains("Manifest.permission.CAMERA"))
        assertTrue(source.contains("PackageManager.PERMISSION_GRANTED"))
    }
}
