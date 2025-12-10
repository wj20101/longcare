import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.wire)
    alias(libs.plugins.baselineprofile)
    id("kotlin-parcelize")
}

wire {
    kotlin {
        javaInterop = true
    }
}

val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra
val appVersionCode: Int by rootProject.extra
val appVersionName: String by rootProject.extra

android {
    namespace = "com.ytone.longcare"
    compileSdk = appCompileSdkVersion

    signingConfigs {
        create("release") {
            keyAlias = "longcare"
            keyPassword = "longcare^&*()"
            storeFile = file("../keystore.jks")
            storePassword = "longcare~!@#\$%"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
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
            abiFilters += listOf("arm64-v8a")
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "txkyc-face-consumer-proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 生产环境 URL
            // 在 release 版本中，定义 USE_MOCK_DATA 常量为 false
            buildConfigField("boolean", "USE_MOCK_DATA", "false")
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 测试环境 URL
            // 在 debug 版本中，定义 USE_MOCK_DATA 常量为 true
            buildConfigField("boolean", "USE_MOCK_DATA", "true")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    kotlin {
        // 这里直接设置 jvmToolchain 为 21
        jvmToolchain(appJdkVersion)
    }
    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

//    flavorDimensions += "environment"
//    productFlavors {
//        create("dev") { dimension = "environment" }
//        create("prod") { dimension = "environment" }
//    }

    // Custom APK naming
    applicationVariants.all {
        val variant = this
        val versionName = variant.versionName
        val versionCode = variant.versionCode

        outputs.configureEach {
            val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
            val fileName =
                buildString {
                    append("app")
                    append("-v$versionName")
                    append("-$date")
                    append("-$versionCode")
//                    append("-")
//                    append(variant.productFlavors.joinToString("-") { it.name })
                    append("-${variant.buildType.name}")
                    append(".apk")
                }

            if (this is ApkVariantOutputImpl) {
                outputFileName = fileName
            }
        }
    }
}

dependencies {

    "baselineProfile"(project(":baselineprofile"))

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

    // Constraintlayout Compose
    implementation(libs.constraintlayout.compose)

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
    implementation(libs.wire.moshi.adapter)
    implementation(libs.retrofit.converter.wire)
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

    // 腾讯人脸
    implementation(files("libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar"))
    implementation(files("libs/WbCloudNormal-v5.1.10-4e3e198.aar"))
    
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom)) // For Compose tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // For Compose tests
    debugImplementation(libs.androidx.compose.ui.test.manifest) // For Compose tests
}
