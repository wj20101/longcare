package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSdkBoundaryTest {

    @Test
    fun `tencent face sdk imports should stay inside manager adapter`() {
        val sourceRoot = File("src/main/kotlin")
        assertTrue("Source root not found: ${sourceRoot.path}", sourceRoot.exists())

        val allowedFiles = setOf(
            "com/ytone/longcare/common/utils/FaceVerificationManager.kt"
        )

        val violations = mutableListOf<String>()
        sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
                val hasTencentFaceImport = file.useLines { lines ->
                    lines.any { it.trim().startsWith("import com.tencent.cloud.huiyansdkface") }
                }
                if (hasTencentFaceImport && relativePath !in allowedFiles) {
                    violations += relativePath
                }
            }

        assertTrue(
            "Tencent face SDK imports found outside adapter boundary: ${violations.joinToString()}",
            violations.isEmpty()
        )
    }
}
