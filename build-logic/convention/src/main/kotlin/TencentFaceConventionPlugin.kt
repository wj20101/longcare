import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import java.io.File

private data class TxFaceSdkDependencyConfig(
    val source: String,
    val liveAar: File? = null,
    val normalAar: File? = null,
    val liveCoordinate: String? = null,
    val normalCoordinate: String? = null
)

class TencentFaceConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("com.android.library") {
            target.addTxFaceDependencies(target.resolveTxFaceSdkDependencyConfig())
        }
    }
}

private fun Project.addTxFaceDependencies(config: TxFaceSdkDependencyConfig) {
    when (config.source) {
        "local" -> {
            dependencies.add("implementation", dependencies.project(mapOf("path" to ":integration:txface-live")))
            dependencies.add("implementation", dependencies.project(mapOf("path" to ":integration:txface-normal")))
        }

        "maven" -> {
            dependencies.add("implementation", requireNotNull(config.liveCoordinate))
            dependencies.add("implementation", requireNotNull(config.normalCoordinate))
        }
    }
}

private fun Project.resolveTxFaceSdkDependencyConfig(): TxFaceSdkDependencyConfig {
    val source =
        providers
            .gradleProperty("TX_FACE_SDK_SOURCE")
            .orElse(providers.environmentVariable("TX_FACE_SDK_SOURCE"))
            .orElse("local")
            .map(::normalizeTxFaceSource)
            .get()

    val liveAar = rootProject.file("app/libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar")
    val normalAar = rootProject.file("app/libs/WbCloudNormal-v5.1.10-4e3e198.aar")

    return when (source) {
        "local" -> {
            if (!liveAar.exists() || !normalAar.exists()) {
                throw GradleException(
                    "Local Tencent face AAR files are missing. Expected: ${liveAar.path}, ${normalAar.path}"
                )
            }
            TxFaceSdkDependencyConfig(source = source, liveAar = liveAar, normalAar = normalAar)
        }

        "maven" -> {
            val liveCoord =
                providers
                    .gradleProperty("TX_FACE_LIVE_COORD")
                    .orElse(providers.environmentVariable("TX_FACE_LIVE_COORD"))
                    .orNull
                    ?.trim()
                    .orEmpty()
            val normalCoord =
                providers
                    .gradleProperty("TX_FACE_NORMAL_COORD")
                    .orElse(providers.environmentVariable("TX_FACE_NORMAL_COORD"))
                    .orNull
                    ?.trim()
                    .orEmpty()

            validateTxFaceMavenCoordinates(liveCoord, normalCoord)

            TxFaceSdkDependencyConfig(
                source = source,
                liveCoordinate = liveCoord,
                normalCoordinate = normalCoord
            )
        }

        else -> throw GradleException("Unsupported TX_FACE_SDK_SOURCE=$source. Expected: local or maven.")
    }
}

internal fun normalizeTxFaceSource(value: String): String {
    val normalized = value.trim().ifBlank { "local" }.lowercase()
    if (normalized !in setOf("local", "maven")) {
        throw GradleException("Unsupported TX_FACE_SDK_SOURCE=$normalized. Expected: local or maven.")
    }
    return normalized
}

internal fun validateTxFaceMavenCoordinates(live: String, normal: String) {
    if (live.isBlank() || normal.isBlank()) {
        throw GradleException("When TX_FACE_SDK_SOURCE=maven, TX_FACE_LIVE_COORD and TX_FACE_NORMAL_COORD must be provided.")
    }
}
