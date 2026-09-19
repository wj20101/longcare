package com.ytone.longcare.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzSdkBoundaryTest {
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
