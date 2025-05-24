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
    alias(libs.plugins.firebasePerf)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ytone.longcare"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        create("config") {
            keyAlias = "longcare"
            keyPassword = "REMOVED_RELEASE_KEY_PASSWORD"
            storeFile = file("../keystore.jks")
            storePassword = "longcare~!@#\$%"
        }
    }

    defaultConfig {
        applicationId = "com.ytone.longcare"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PUBLIC_KEY", "\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk45Er/DSjJwRNhReRT+4lINV6GanR3FwNutADNBwVoNQgY33bM/adLN5ZDmb8CwCeRJ4iBdcIX0co+2cm169HSHtJvOHUm864UbT63BrxKtnJCR+GkmsB3dj7YMwDbYArg7ymGP3EhWsiqMPdnR15+4LYIfK3l74nOZqPIPp8XkUKbbvJeieyslBIVSux2eytUGQjY8EPTE7nOHbAh8boWhiekFKevmx24dQBLoOrKrpTIv4pNiFSPxWCdBayCXjyr3Vq6Eg+vEDYN1+sxXWAj4bo/91TIbGQzdPCcCiZUQ1d7EgBp1JJKAsTTzkd+CusSTVpmmz/uVwjOaEHNzqWwIDAQAB\"")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://api.qianyuwl168.cn/\"") // 生产环境 URL
            signingConfig = signingConfigs.getByName("config")
        }
        
        debug {
            isDebuggable = true
            buildConfigField("String", "BASE_URL", "\"https://api.qianyuwl168.cn/\"") // 测试环境 URL
            signingConfig = signingConfigs.getByName("config")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }

    // Custom APK naming
    applicationVariants.all {
        val variant = this
        val versionName = variant.versionName

        outputs.configureEach {
            val date = SimpleDateFormat("MMdd-HH", Locale.getDefault()).format(Date())
            val fileName = buildString {
                append("app_")
                append(date)
                append(variant.productFlavors.joinToString("-") { it.name })
                append("-${variant.buildType.name}")
                append("-${versionName}")
                append(".apk")
            }

            if (this is ApkVariantOutputImpl) {
                outputFileName = fileName
            }
        }
    }
}

dependencies {

    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (using BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling) // For UI Tooling (like Previews in debug)
    implementation(libs.compose.ui.tooling.preview) // For Previews

    // AndroidX Startup
    implementation(libs.startup.runtime)

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
    implementation(libs.mmkv)

    // Work
    implementation(libs.work.runtime.ktx)

    // Window
    implementation(libs.window)

    // Firebase
    implementation(libs.firebase.perf)

    // Image Loading
    implementation(libs.bundles.coil)

    // Permissions
    implementation(libs.accompanist.permissions)

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