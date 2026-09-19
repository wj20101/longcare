import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TencentFaceConventionPluginTest {
    @Test fun `blank source defaults to local and names are normalized`() {
        assertEquals("local", normalizeTxFaceSource(" "))
        assertEquals("local", normalizeTxFaceSource(" Local "))
        assertEquals("maven", normalizeTxFaceSource("MAVEN"))
    }

    @Test fun `unknown source fails closed`() {
        assertFailsWith<GradleException> { normalizeTxFaceSource("download") }
    }

    @Test fun `both Maven coordinates are mandatory`() {
        assertFailsWith<GradleException> { validateTxFaceMavenCoordinates("", "vendor:normal:1") }
        assertFailsWith<GradleException> { validateTxFaceMavenCoordinates("vendor:live:1", " ") }
        validateTxFaceMavenCoordinates("vendor:live:1", "vendor:normal:1")
    }
}
