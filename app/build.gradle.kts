plugins {
    id("longcare.android.application")
    id("longcare.kotlin.common")
    id("longcare.android.app.signing-txface")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baseline.profile)
}

private val BASE_URL = "https://careapi.ytone.cn"
private val PUBLIC_KEY =
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk45Er/DSjJwRNhReRT+4lINV6GanR3FwNutADNBwVoNQgY33bM/adLN5ZDmb8CwCeRJ4iBdcIX0co+2cm169HSHtJvOHUm864UbT63BrxKtnJCR+GkmsB3dj7YMwDbYArg7ymGP3EhWsiqMPdnR15+4LYIfK3l74nOZqPIPp8XkUKbbvJeieyslBIVSux2eytUGQjY8EPTE7nOHbAh8boWhiekFKevmx24dQBLoOrKrpTIv4pNiFSPxWCdBayCXjyr3Vq6Eg+vEDYN1+sxXWAj4bo/91TIbGQzdPCcCiZUQ1d7EgBp1JJKAsTTzkd+CusSTVpmmz/uVwjOaEHNzqWwIDAQAB"
// TODO(QLZ): Remove this fixed test configuration after the Sale API returns the SDK key.
private val TEMPORARY_QLZ_SDK_KEY = "qlz235624a5adc96ccb"
private val TEMPORARY_QLZ_TEST_MODE = true
private val QLZ_SDK_AAR_FILE_NAME =
    "qlzsdk-1.3.0.5-protobufLiteRelease-ui.aar"

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
val txFaceSdkSource =
    providers
        .gradleProperty("TX_FACE_SDK_SOURCE")
        .orElse(providers.environmentVariable("TX_FACE_SDK_SOURCE"))
        .orElse("local")
        .map { it.trim().lowercase() }
val txFaceLiveCoordinate =
    providers
        .gradleProperty("TX_FACE_LIVE_COORD")
        .orElse(providers.environmentVariable("TX_FACE_LIVE_COORD"))
        .orElse("")
val knownUnsafeFaceSdkPresent =
    providers.provider {
        txFaceSdkSource.get() == "local" ||
            txFaceLiveCoordinate.get().contains("6.6.2-8e4718fc", ignoreCase = true)
    }
val knownUnsafeQlzSdkPresent =
    providers.provider {
        file("libs/$QLZ_SDK_AAR_FILE_NAME").exists()
    }

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
        buildConfigField(
            "String",
            "QLZ_SDK_KEY",
            TEMPORARY_QLZ_SDK_KEY.asBuildConfigString(),
        )
        buildConfigField("boolean", "QLZ_TEST_MODE", TEMPORARY_QLZ_TEST_MODE.toString())
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                // Tencent consumer rules are supplied by :integration:txface.
            )
            buildConfigField("String", "BASE_URL", "\"$BASE_URL\"")
            buildConfigField("boolean", "USE_MOCK_DATA", "false")
        }

        debug {
            buildConfigField("String", "BASE_URL", "\"$BASE_URL\"")
            buildConfigField("boolean", "USE_MOCK_DATA", debugUseMockData.toString())
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Run the same vendor protobuf contract on the JVM and Android; never package it in the app.
    sourceSets {
        getByName("test").java.srcDir("src/testShared/java")
        getByName("androidTest").java.srcDir("src/testShared/java")
    }

    // Opt-in offline QLZ checks against the real minified target; normal UI tests stay on Debug.
    if (providers.gradleProperty("test.qlzRelease").orNull == "true") {
        testBuildType = "release"
        defaultConfig.testInstrumentationRunner =
            "com.ytone.longcare.integration.qlz.QlzReleaseTestRunner"
        sourceSets.getByName("androidTest") {
            java.setSrcDirs(listOf("src/testShared/java", "src/qlzReleaseTest/java"))
            kotlin.setSrcDirs(emptyList<String>())
        }
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

val verifyReleaseConfiguration =
    tasks.register<Exec>("verifyReleaseConfiguration") {
        group = "verification"
        description =
            "Reports explicitly accepted release vendor/configuration risks."
        commandLine(
            "bash",
            rootProject.file("scripts/quality/verify_release_config.sh").absolutePath,
            "--temporary-qlz-key-present",
            TEMPORARY_QLZ_SDK_KEY.isNotBlank().toString(),
            "--qlz-test-mode",
            TEMPORARY_QLZ_TEST_MODE.toString(),
            "--known-unsafe-qlz-sdk-present",
            knownUnsafeQlzSdkPresent.get().toString(),
            "--known-unsafe-face-sdk-present",
            knownUnsafeFaceSdkPresent.get().toString(),
        )
    }

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach {
        dependsOn(verifyReleaseConfiguration)
    }

configurations.configureEach {
    if (name.startsWith("hiltAnnotationProcessor")) {
        exclude(group = "com.squareup.moshi", module = "moshi-kotlin-codegen")
    }
}

dependencies {
    baselineProfile(project(":baselineprofile"))

    implementation(project(":integration:txface"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:login"))
    implementation(project(":feature:carddiagnostics"))
    implementation(project(":feature:home"))
    implementation(project(":feature:identification"))
    implementation(project(":feature:location"))
    implementation(project(":feature:photoupload"))
    implementation(project(":feature:servicecountdown"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.startup.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.dagger.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okio.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.gson)
    // Vendor sample baseline: 4.28.3. Keep the catalog runtime covered by QLZ message/Gzip tests.
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime.ktx)
    implementation(libs.bundles.coil)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.face.detection)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.datetime)
    implementation(libs.crashreport)

    ksp(libs.dagger.hilt.compiler)
    ksp(libs.hilt.compiler)
    ksp(libs.moshi.kotlin.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(files("libs/$QLZ_SDK_AAR_FILE_NAME"))
}
