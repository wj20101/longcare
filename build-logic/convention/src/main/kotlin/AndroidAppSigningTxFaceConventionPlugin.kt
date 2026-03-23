import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.SigningConfig
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.configure
import java.io.File

private val RELEASE_STORE_FILE_KEYS = listOf("LONGCARE_RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
private val RELEASE_STORE_FILE_ENV_KEYS = listOf("LONGCARE_ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PATH")
private val RELEASE_STORE_PASSWORD_KEYS = listOf("LONGCARE_RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
private val RELEASE_KEY_ALIAS_KEYS = listOf("LONGCARE_RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
private val RELEASE_KEY_PASSWORD_KEYS = listOf("LONGCARE_RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")

private data class ReleaseSigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

private data class TxFaceSdkDependencyConfig(
    val source: String,
    val liveAar: File? = null,
    val normalAar: File? = null,
    val liveCoordinate: String? = null,
    val normalCoordinate: String? = null
)

class AndroidAppSigningTxFaceConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("com.android.application") {
            val releaseSigning = target.resolveReleaseSigningConfigOrNull()
            val requiresReleaseSigning = target.requiresReleaseSigning()
            val allowUnsignedRelease = target.allowUnsignedReleaseFallback()
            if (releaseSigning == null && requiresReleaseSigning && !allowUnsignedRelease) {
                throw GradleException(
                    "Missing release signing config. Configure LONGCARE_RELEASE_* or RELEASE_* in ~/.gradle/gradle.properties or environment."
                )
            }

            target.extensions.configure<ApplicationExtension> {
                val releaseBuildType = buildTypes.getByName("release")
                if (releaseSigning != null) {
                    val releaseSigningConfig = signingConfigs.findByName("release") ?: signingConfigs.create("release")
                    releaseSigningConfig.applyReleaseSigning(releaseSigning)
                    releaseBuildType.signingConfig = releaseSigningConfig
                } else if (allowUnsignedRelease) {
                    val debugSigningConfig = signingConfigs.findByName("debug")
                    if (debugSigningConfig != null) {
                        releaseBuildType.signingConfig = debugSigningConfig
                        target.logger.lifecycle(
                            "Using debug signing config for release tasks because LONGCARE release signing is unavailable in this CI context."
                        )
                    } else if (requiresReleaseSigning) {
                        throw GradleException(
                            "Unable to configure debug-signing fallback for release tasks. Debug signing config was not found."
                        )
                    }
                }

                if (requiresReleaseSigning) {
                    val effectiveReleaseSigningConfig = releaseBuildType.signingConfig
                    val debugStorePath = signingConfigs.findByName("debug")?.storeFile?.absolutePath
                    val releaseStorePath = effectiveReleaseSigningConfig?.storeFile?.absolutePath
                    if (
                        !isSafeReleaseSigningConfig(
                            signingConfigName = effectiveReleaseSigningConfig?.name,
                            signingStorePath = releaseStorePath,
                            debugStorePath = debugStorePath
                        )
                    ) {
                        throw GradleException(
                            "Unsafe release signing configuration detected. Release variants must not use the debug signing config or debug.keystore."
                        )
                    }
                }
            }

            val txFaceConfig = target.resolveTxFaceSdkDependencyConfig()
            target.addTxFaceDependencies(txFaceConfig)
        }
    }
}

private fun SigningConfig.applyReleaseSigning(config: ReleaseSigningConfig) {
    keyAlias = config.keyAlias
    keyPassword = config.keyPassword
    storeFile = config.storeFile
    storePassword = config.storePassword
}

private fun Project.addTxFaceDependencies(config: TxFaceSdkDependencyConfig) {
    when (config.source) {
        "local" -> {
            dependencies.add("implementation", files(requireNotNull(config.liveAar)))
            dependencies.add("implementation", files(requireNotNull(config.normalAar)))
        }

        "maven" -> {
            dependencies.add("implementation", requireNotNull(config.liveCoordinate))
            dependencies.add("implementation", requireNotNull(config.normalCoordinate))
        }
    }
}

private fun firstNonBlank(vararg candidates: String?): String? =
    candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

private fun ProviderFactory.resolveGradleOrEnv(
    gradleKeys: List<String>,
    envKeys: List<String>
): String? {
    val gradleValues = gradleKeys.map { gradleProperty(it).orNull }
    val envValues = envKeys.map { environmentVariable(it).orNull }
    return firstNonBlank(*(gradleValues + envValues).toTypedArray())
}

private fun Project.resolveReleaseSigningConfigOrNull(): ReleaseSigningConfig? {
    val storeFilePath = providers.resolveGradleOrEnv(RELEASE_STORE_FILE_KEYS, RELEASE_STORE_FILE_ENV_KEYS)
    val storePassword = providers.resolveGradleOrEnv(RELEASE_STORE_PASSWORD_KEYS, RELEASE_STORE_PASSWORD_KEYS)
    val keyAlias = providers.resolveGradleOrEnv(RELEASE_KEY_ALIAS_KEYS, RELEASE_KEY_ALIAS_KEYS)
    val keyPassword = providers.resolveGradleOrEnv(RELEASE_KEY_PASSWORD_KEYS, RELEASE_KEY_PASSWORD_KEYS)

    val storeFile =
        storeFilePath
            ?.let { rawPath ->
                val candidate = File(rawPath)
                if (candidate.isAbsolute) candidate else rootProject.file(rawPath)
            }
            ?.takeIf(File::exists)

    if (storeFile == null || storePassword.isNullOrBlank() || keyAlias.isNullOrBlank() || keyPassword.isNullOrBlank()) {
        return null
    }

    return ReleaseSigningConfig(
        storeFile = storeFile,
        storePassword = storePassword,
        keyAlias = keyAlias,
        keyPassword = keyPassword
    )
}

private fun Project.requiresReleaseSigning(): Boolean {
    val requestedTasks = gradle.startParameter.taskNames
    if (requestedTasks.isEmpty()) {
        return false
    }
    val signingRequiredReleaseTasks =
        listOf(
            "assemblerelease",
            "bundlerelease",
            "packagerelease",
            "publishrelease",
            "signrelease",
            "validatesigningrelease"
        )
    return requestedTasks.any { taskName ->
        val normalized = taskName.substringAfterLast(':').lowercase()
        signingRequiredReleaseTasks.any { keyword -> normalized.contains(keyword) }
    }
}

private fun String.toBooleanStrictish(): Boolean? =
    when (trim().lowercase()) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        else -> null
    }

private fun Project.allowUnsignedReleaseFallback(): Boolean {
    val explicitOverride =
        providers.resolveGradleOrEnv(
            gradleKeys = listOf("LONGCARE_ALLOW_UNSIGNED_RELEASE", "ALLOW_UNSIGNED_RELEASE"),
            envKeys = listOf("LONGCARE_ALLOW_UNSIGNED_RELEASE", "ALLOW_UNSIGNED_RELEASE")
        )
    return resolveUnsignedReleaseFallback(explicitOverride)
}

internal fun resolveUnsignedReleaseFallback(explicitOverride: String?): Boolean {
    return explicitOverride?.toBooleanStrictish() ?: false
}

internal fun isSafeReleaseSigningConfig(
    signingConfigName: String?,
    signingStorePath: String?,
    debugStorePath: String?
): Boolean {
    if (signingConfigName.equals("debug", ignoreCase = true)) {
        return false
    }
    if (!signingStorePath.isNullOrBlank() && !debugStorePath.isNullOrBlank()) {
        return signingStorePath != debugStorePath
    }
    return true
}

private fun Project.resolveTxFaceSdkDependencyConfig(): TxFaceSdkDependencyConfig {
    val source =
        providers
            .gradleProperty("TX_FACE_SDK_SOURCE")
            .orElse(providers.environmentVariable("TX_FACE_SDK_SOURCE"))
            .orElse("local")
            .map { it.trim().ifBlank { "local" }.lowercase() }
            .get()

    val liveAar = file("libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar")
    val normalAar = file("libs/WbCloudNormal-v5.1.10-4e3e198.aar")

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

            if (liveCoord.isBlank() || normalCoord.isBlank()) {
                throw GradleException(
                    "When TX_FACE_SDK_SOURCE=maven, TX_FACE_LIVE_COORD and TX_FACE_NORMAL_COORD must be provided."
                )
            }

            TxFaceSdkDependencyConfig(
                source = source,
                liveCoordinate = liveCoord,
                normalCoordinate = normalCoord
            )
        }

        else -> throw GradleException("Unsupported TX_FACE_SDK_SOURCE=$source. Expected: local or maven.")
    }
}
