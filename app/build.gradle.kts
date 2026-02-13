plugins {
    id("longcare.android.application")
    id("longcare.kotlin.common")
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
    id("kotlin-parcelize")
}

val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra
val appVersionCode: Int by rootProject.extra
val appVersionName: String by rootProject.extra
val baselineEnableX86_64 =
    providers
        .gradleProperty("baseline.enableX86_64")
        .orElse("false")
        .map { it.equals("true", ignoreCase = true) }
        .get()
val releaseStoreFilePath =
    providers
        .gradleProperty("LONGCARE_RELEASE_STORE_FILE")
        .orElse(providers.gradleProperty("RELEASE_STORE_FILE"))
        .orElse(providers.environmentVariable("LONGCARE_ANDROID_KEYSTORE_PATH"))
        .orElse(providers.environmentVariable("ANDROID_KEYSTORE_PATH"))
val releaseStorePassword =
    providers
        .gradleProperty("LONGCARE_RELEASE_STORE_PASSWORD")
        .orElse(providers.gradleProperty("RELEASE_STORE_PASSWORD"))
        .orElse(providers.environmentVariable("LONGCARE_RELEASE_STORE_PASSWORD"))
        .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
val releaseKeyAlias =
    providers
        .gradleProperty("LONGCARE_RELEASE_KEY_ALIAS")
        .orElse(providers.gradleProperty("RELEASE_KEY_ALIAS"))
        .orElse(providers.environmentVariable("LONGCARE_RELEASE_KEY_ALIAS"))
        .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
val releaseKeyPassword =
    providers
        .gradleProperty("LONGCARE_RELEASE_KEY_PASSWORD")
        .orElse(providers.gradleProperty("RELEASE_KEY_PASSWORD"))
        .orElse(providers.environmentVariable("LONGCARE_RELEASE_KEY_PASSWORD"))
        .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))
val releaseStoreFile = releaseStoreFilePath.orNull?.let(::file)
val hasReleaseSigning =
    releaseStoreFile?.exists() == true &&
        !releaseStorePassword.orNull.isNullOrBlank() &&
        !releaseKeyAlias.orNull.isNullOrBlank() &&
        !releaseKeyPassword.orNull.isNullOrBlank()
val txFaceSdkSource =
    providers
        .gradleProperty("TX_FACE_SDK_SOURCE")
        .orElse(providers.environmentVariable("TX_FACE_SDK_SOURCE"))
        .orElse("local")
        .map { raw -> raw.trim().ifBlank { "local" }.lowercase() }
        .get()
val txFaceLiveCoordinate =
    providers
        .gradleProperty("TX_FACE_LIVE_COORD")
        .orElse(providers.environmentVariable("TX_FACE_LIVE_COORD"))
val txFaceNormalCoordinate =
    providers
        .gradleProperty("TX_FACE_NORMAL_COORD")
        .orElse(providers.environmentVariable("TX_FACE_NORMAL_COORD"))
val txFaceLiveAar = file("libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar")
val txFaceNormalAar = file("libs/WbCloudNormal-v5.1.10-4e3e198.aar")

data class TxFaceSdkDependencyConfig(
    val source: String,
    val liveAar: File? = null,
    val normalAar: File? = null,
    val liveCoordinate: String? = null,
    val normalCoordinate: String? = null
)

fun resolveTxFaceSdkDependencyConfig(
    source: String,
    liveAar: File,
    normalAar: File,
    liveCoordinate: String?,
    normalCoordinate: String?
): TxFaceSdkDependencyConfig {
    val normalizedSource = source.trim().ifBlank { "local" }.lowercase()
    return when (normalizedSource) {
        "local" -> {
            if (!liveAar.exists() || !normalAar.exists()) {
                throw GradleException(
                    "Local Tencent face AAR files are missing. " +
                        "Expected: ${liveAar.path}, ${normalAar.path}"
                )
            }
            TxFaceSdkDependencyConfig(
                source = "local",
                liveAar = liveAar,
                normalAar = normalAar
            )
        }
        "maven" -> {
            val liveCoord = liveCoordinate?.trim().orEmpty()
            val normalCoord = normalCoordinate?.trim().orEmpty()
            if (liveCoord.isBlank() || normalCoord.isBlank()) {
                throw GradleException(
                    "When TX_FACE_SDK_SOURCE=maven, TX_FACE_LIVE_COORD and TX_FACE_NORMAL_COORD must be provided."
                )
            }
            TxFaceSdkDependencyConfig(
                source = "maven",
                liveCoordinate = liveCoord,
                normalCoordinate = normalCoord
            )
        }
        else -> {
            throw GradleException(
                "Unsupported TX_FACE_SDK_SOURCE=$normalizedSource. Expected: local or maven."
            )
        }
    }
}

val txFaceDependencyConfig = resolveTxFaceSdkDependencyConfig(
    source = txFaceSdkSource,
    liveAar = txFaceLiveAar,
    normalAar = txFaceNormalAar,
    liveCoordinate = txFaceLiveCoordinate.orNull,
    normalCoordinate = txFaceNormalCoordinate.orNull
)

android {
    namespace = "com.ytone.longcare"
    compileSdk = appCompileSdkVersion

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = releaseKeyAlias.orNull
                keyPassword = releaseKeyPassword.orNull
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword.orNull
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.ytone.longcare"
        minSdk = appMinSdkVersion
        targetSdk = appTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "PUBLIC_KEY",
            "\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk45Er/DSjJwRNhReRT+4lINV6GanR3FwNutADNBwVoNQgY33bM/adLN5ZDmb8CwCeRJ4iBdcIX0co+2cm169HSHtJvOHUm864UbT63BrxKtnJCR+GkmsB3dj7YMwDbYArg7ymGP3EhWsiqMPdnR15+4LYIfK3l74nOZqPIPp8XkUKbbvJeieyslBIVSux2eytUGQjY8EPTE7nOHbAh8boWhiekFKevmx24dQBLoOrKrpTIv4pNiFSPxWCdBayCXjyr3Vq6Eg+vEDYN1+sxXWAj4bo/91TIbGQzdPCcCiZUQ1d7EgBp1JJKAsTTzkd+CusSTVpmmz/uVwjOaEHNzqWwIDAQAB\"",
        )

        ndk {
            val enabledAbis = mutableListOf("arm64-v8a")
            if (baselineEnableX86_64) {
                enabledAbis += "x86_64"
            }
            abiFilters += enabledAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            manifestPlaceholders["faceCaptureTestActivityEnabled"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "txkyc-face-consumer-proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 生产环境 URL
            // 在 release 版本中，定义 USE_MOCK_DATA 常量为 false
            buildConfigField("boolean", "USE_MOCK_DATA", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            manifestPlaceholders["faceCaptureTestActivityEnabled"] = "true"
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 测试环境 URL
            // 在 debug 版本中，当前仍使用线上数据
            buildConfigField("boolean", "USE_MOCK_DATA", "false")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols +=
                setOf(
                    "**/libBugly_Native.so",
                    "**/libBugly_Native_idasc.so",
                    "**/libYTCommonLiveness.so",
                    "**/libandroidx.graphics.path.so",
                    "**/libdatastore_shared_counter.so",
                    "**/libface_detector_v2_jni.so",
                    "**/libimage_processing_util_jni.so",
                    "**/libkyctoolkit.so",
                    "**/libsurface_util_jni.so",
                    "**/libturingmfa.so",
                    "**/libweconvert.so",
                    "**/libweyuv.so",
                )
        }
    }

//    flavorDimensions += "environment"
//    productFlavors {
//        create("dev") { dimension = "environment" }
//        create("prod") { dimension = "environment" }
//    }
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>("kotlin") {
    // Kotlin toolchain should be configured on the project-level Kotlin extension.
    jvmToolchain(appJdkVersion)
}

extensions.configure<androidx.room.gradle.RoomExtension>("room") {
    schemaDirectory("$projectDir/schemas")
}

baselineProfile {
    warnings {
        maxAgpVersion = false
    }
}

// Hilt's Java compile classpath should not pick up Moshi's processor.
// Keep Moshi codegen on KSP only to avoid kapt deprecation warnings.
configurations.configureEach {
    if (name.startsWith("hiltAnnotationProcessor")) {
        exclude(group = "com.squareup.moshi", module = "moshi-kotlin-codegen")
    }
}

dependencies {

    "baselineProfile"(project(":baselineprofile"))
    implementation(project(":feature:login"))
    implementation(project(":feature:home"))
    implementation(project(":feature:identification"))

    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.compose)

    // Jetpack Compose (using BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    debugImplementation(libs.compose.ui.tooling) // For UI Tooling (like Previews in debug)
    implementation(libs.compose.ui.tooling.preview) // For Previews

    // AndroidX Startup
    implementation(libs.startup.runtime)

    // AndroidX Profile Installer
    implementation(libs.androidx.profileinstaller)

    // Navigation
    implementation(libs.androidx.navigation3.ui)

    // Hilt
    implementation(libs.bundles.hilt)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okio.core)

    //Json
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.kotlinx.serialization.json)

    // Data Storage
    implementation(libs.androidx.datastore.preferences)

    // Work
    implementation(libs.work.runtime.ktx)

    // Window
    implementation(libs.window)

    // Image Loading
    implementation(libs.bundles.coil)

    // CameraX - Updated for modern face capture
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.camera.compose)
    
    // ML Kit Face Detection for face capture feature
    implementation(libs.face.detection)
    
    // Additional Lifecycle components for modern state management
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // constraintlayout
    implementation(libs.androidx.constraintlayout)

    // kotlinx datetime
    implementation(libs.kotlinx.datetime)

    // 腾讯人脸（通过 TX_FACE_SDK_SOURCE 在 local 与 maven 之间切换）
    when (txFaceDependencyConfig.source) {
        "local" -> {
            val liveAar = requireNotNull(txFaceDependencyConfig.liveAar)
            val normalAar = requireNotNull(txFaceDependencyConfig.normalAar)
            implementation(files(liveAar))
            implementation(files(normalAar))
        }
        "maven" -> {
            val liveCoord = requireNotNull(txFaceDependencyConfig.liveCoordinate)
            val normalCoord = requireNotNull(txFaceDependencyConfig.normalCoordinate)
            implementation(liveCoord)
            implementation(normalCoord)
        }
    }
    
    // Support Library compatibility for Tencent SDK
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.legacy.support.core.utils)

    // 腾讯云COS
    implementation(libs.tencent.cos.android)

    // 高德地图定位SDK
    implementation(libs.amap.location)

    // Bugly
    implementation(libs.crashreport)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom)) // For Compose tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // For Compose tests
    debugImplementation(libs.androidx.compose.ui.test.manifest) // For Compose tests
}
