import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.wire)
    alias(libs.plugins.baselineprofile)
}

wire {
    kotlin {
        javaInterop = true
    }
}

val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJavaVersion: JavaVersion by rootProject.extra
val appKotlinJvmTarget: String by rootProject.extra
val appVersionCode: Int by rootProject.extra
val appVersionName: String by rootProject.extra

android {
    namespace = "com.ytone.longcare"
    compileSdk = appCompileSdkVersion

    signingConfigs {
        create("config") {
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
        signingConfig = signingConfigs.getByName("config")
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
            )
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 生产环境 URL
        }

        debug {
            buildConfigField("String", "BASE_URL", "\"https://careapi.ytone.cn\"") // 测试环境 URL
        }
    }
    compileOptions {
        sourceCompatibility = appJavaVersion
        targetCompatibility = appJavaVersion
    }
    kotlinOptions {
        jvmTarget = appKotlinJvmTarget
    }
    buildFeatures {
        buildConfig = true
        compose = true
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

        outputs.configureEach {
            val date = SimpleDateFormat("MMdd-HH", Locale.getDefault()).format(Date())
            val fileName =
                buildString {
                    append("app")
                    append("-v$versionName")
                    append("-$date")
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
    implementation(libs.androidx.navigation.compose)

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
    implementation(libs.kotlinx.serialization.json)

    // Data Storage
    implementation(libs.androidx.datastore.preferences)

    // Work
    implementation(libs.work.runtime.ktx)

    // Window
    implementation(libs.window)

    // Image Loading
    implementation(libs.bundles.coil)

    // kotlinx datetime
    implementation(libs.kotlinx.datetime)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom)) // For Compose tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // For Compose tests
    debugImplementation(libs.androidx.compose.ui.test.manifest) // For Compose tests
}
