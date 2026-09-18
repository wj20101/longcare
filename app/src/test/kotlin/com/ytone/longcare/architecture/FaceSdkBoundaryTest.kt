package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceSdkBoundaryTest {
    @Test
    fun `tencent SDK imports stay inside the integration adapter`() {
        val root = File("..").canonicalFile
        val allowed = "integration/txface/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt"
        assertTrue(File(root, allowed).isFile)
        val violations = listOf("app", "assistant", "core", "feature", "integration")
            .flatMap { File(root, it).walkTopDown().onEnter { dir -> dir.name != "build" }.filter { file ->
                file.isFile && file.extension == "kt" && "/src/main/" in file.invariantSeparatorsPath
            }.toList() }
            .filter { file ->
                file.readLines().any { it.trim().startsWith("import com.tencent.cloud.huiyansdkface") } &&
                    file.relativeTo(root).invariantSeparatorsPath != allowed
            }
        assertTrue("Tencent imports outside integration: $violations", violations.isEmpty())
    }
}
