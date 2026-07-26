import java.util.Properties

plugins {
    id("longcare.android.application")
    id("longcare.kotlin.common")
    id("longcare.android.app.signing-txface")
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    id("kotlin-parcelize")
}

apply(from = "$projectDir/dependencies.gradle.kts")

private val BASE_URL = "https://careapi.ytone.cn"
private val PUBLIC_KEY =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk45Er/DSjJwRNhReRT+4lINV6GanR3FwNutADNBwVoNQgY33bM/adLN5ZDmb8CwCeRJ4iBdcIX0co+2cm169HSHtJvOHUm864UbT63BrxKtnJCR+GkmsB3dj7YMwDbYArg7ymGP3EhWsiqMPdnR15+4LYIfK3l74nOZqPIPp8XkUKbbvJeieyslBIVSux2eytUGQjY8EPTE7nOHbAh8boWhiekFKevmx24dQBLoOrKrpTIv4pNiFSPxWCdBayCXjyr3Vq6Eg+vEDYN1+sxXWAj4bo/91TIbGQzdPCcCiZUQ1d7EgBp1JJKAsTTzkd+CusSTVpmmz/uVwjOaEHNzqWwIDAQAB"

val appCompileSdkVersion = rootProject.extra["appCompileSdkVersion"] as Int
val appTargetSdkVersion = rootProject.extra["appTargetSdkVersion"] as Int
val appMinSdkVersion = rootProject.extra["appMinSdkVersion"] as Int
val appJdkVersion = rootProject.extra["appJdkVersion"] as Int
val appVersionCode = rootProject.extra["appVersionCode"] as Int
val appVersionName = rootProject.extra["appVersionName"] as String

val baselineEnableX86_64 =
    providers
        .gradleProperty("baseline.enableX86_64")
        .orElse("false")
        .map { it.equals("true", ignoreCase = true) }
        .get()
val debugUseMockData =
    providers
        .gradleProperty("debug.useMockData")
        .orElse("true")
        .map { it.equals("true", ignoreCase = true) }
        .get()
val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
val qlzSdkKey =
    providers
        .gradleProperty("QLZ_SDK_KEY")
        .orElse(providers.environmentVariable("QLZ_SDK_KEY"))
        .orElse(localProperties.getProperty("QLZ_SDK_KEY").orEmpty())
        .get()
val qlzProductionSdkKey =
    providers
        .gradleProperty("QLZ_PRODUCTION_SDK_KEY")
        .orElse(providers.environmentVariable("QLZ_PRODUCTION_SDK_KEY"))
        .orElse(localProperties.getProperty("QLZ_PRODUCTION_SDK_KEY").orEmpty())
        .get()
val qlzTestMode =
    providers
        .gradleProperty("QLZ_TEST_MODE")
        .orElse(providers.environmentVariable("QLZ_TEST_MODE"))
        .orElse(localProperties.getProperty("QLZ_TEST_MODE") ?: "true")
        .map { it.equals("true", ignoreCase = true) }
        .get()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.ytone.longcare"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        applicationId = "com.ytone.longcare"
        minSdk = appMinSdkVersion
        targetSdk = appTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PUBLIC_KEY", "\"$PUBLIC_KEY\"")
        ndk {
            val enabledAbis = mutableListOf("arm64-v8a")
            if (baselineEnableX86_64) {
                enabledAbis += "x86_64"
            }
            abiFilters += enabledAbis
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(appJdkVersion)
        targetCompatibility = JavaVersion.toVersion(appJdkVersion)
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
            buildConfigField("String", "BASE_URL", "\"$BASE_URL\"")
            buildConfigField("boolean", "USE_MOCK_DATA", "false")
            buildConfigField(
                "String",
                "QLZ_SDK_KEY",
                qlzProductionSdkKey.asBuildConfigString(),
            )
            buildConfigField("boolean", "QLZ_TEST_MODE", "false")
        }

        debug {
            manifestPlaceholders["faceCaptureTestActivityEnabled"] = "true"
            buildConfigField("String", "BASE_URL", "\"$BASE_URL\"")
            buildConfigField("boolean", "USE_MOCK_DATA", debugUseMockData.toString())
            buildConfigField("String", "QLZ_SDK_KEY", qlzSdkKey.asBuildConfigString())
            buildConfigField("boolean", "QLZ_TEST_MODE", qlzTestMode.toString())
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
                    "**/libapssdk.so",
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
}

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension>("kotlin") {
    jvmToolchain(appJdkVersion)
}

baselineProfile {
    warnings {
        maxAgpVersion = false
    }
}

configurations.configureEach {
    if (name.startsWith("hiltAnnotationProcessor")) {
        exclude(group = "com.squareup.moshi", module = "moshi-kotlin-codegen")
    }
}
