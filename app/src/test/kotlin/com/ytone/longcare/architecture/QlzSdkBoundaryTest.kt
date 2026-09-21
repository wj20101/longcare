package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzSdkBoundaryTest {
    @Test
    fun `all app variants use the fixed AAR production default without environment overrides`() {
        val root = File("..").canonicalFile
        val source = File(root, "app/src/main/kotlin/com/ytone/longcare/integration/qlz")
            .walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(source.contains("setTestMode("))
        assertFalse(source.contains("setLocalMode("))
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertFalse(gradle.contains("QLZ_TEST_MODE"))
        assertFalse(gradle.contains("TEMPORARY_QLZ"))
        java.util.zip.ZipFile(File(root, "app/libs/qlzsdk-1.3.0.5-protobufLiteRelease-ui.aar")).use { aar ->
            java.util.jar.JarInputStream(aar.getInputStream(aar.getEntry("classes.jar"))).use { jar ->
                val classes = mutableMapOf<String, String>()
                var entry = jar.nextJarEntry
                while (entry != null) {
                    if (entry.name in setOf("com/evenmed/sdk/call/l.class", "com/evenmed/sdk/call/k.class")) {
                        classes[entry.name] = jar.readBytes().toString(Charsets.ISO_8859_1)
                    }
                    entry = jar.nextJarEntry
                }
                assertTrue(classes.getValue("com/evenmed/sdk/call/l.class").contains("https://openapi.qiaolz.com"))
                assertFalse(classes.getValue("com/evenmed/sdk/call/l.class").contains("test.qiaolz.com"))
                assertTrue(classes.getValue("com/evenmed/sdk/call/k.class").contains("/sdk/assess/upload"))
            }
        }
    }

    @Test
    fun `vendor and Bluetooth device types stay inside the QLZ integration adapter`() {
        val root = File("..").canonicalFile
        val businessRoots =
            listOf(
                File(root, "app/src/main/kotlin/com/ytone/longcare/features/sales"),
                File(root, "app/src/main/kotlin/com/ytone/longcare/platform/sales"),
            )
        val violations =
            businessRoots
                .flatMap { directory ->
                    directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                }
                .filter { file ->
                    val source = file.readText()
                    "import com.evenmed." in source ||
                        "import com.falth." in source ||
                        "import android.bluetooth.BluetoothDevice" in source
                }

        assertTrue("QLZ vendor types escaped into Sales code: $violations", violations.isEmpty())
    }

    @Test
    fun `production path does not open vendor UI or retain demo identifiers`() {
        val root = File("..").canonicalFile
        val sourceRoot = File(root, "app/src/main")
        val source =
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "xml") }
                .joinToString("\n") { it.readText() }

        assertFalse(source.contains("SDKCall.openByToken"))
        assertFalse(source.contains("BM-S100004"))
        assertFalse(source.contains("A4:C1:38:7F:69:5E"))
        assertFalse(source.contains("浙江杭州ABCDEFG"))
    }
}
