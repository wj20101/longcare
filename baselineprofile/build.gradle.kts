plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.baselineprofile"
    compileSdk = appCompileSdkVersion

    kotlin {
        // 根据您的 constants.gradle.kts 文件，您使用的是 Java 21
        // 这里直接设置 jvmToolchain 为 21
        jvmToolchain(appJdkVersion)
    }

    defaultConfig {
        minSdk = appMinSdkVersion
        targetSdk = appTargetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            keepDebugSymbols +=
                setOf(
                    "**/libbenchmarkNative.so",
                    "**/libtracing_perfetto.so",
                )
        }
    }

    testOptions.managedDevices.localDevices {
        create("pixel6Api33") {
            device = "Pixel 6"
            apiLevel = 33
            systemImageSource = "aosp"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

//    flavorDimensions += listOf("environment")
//    productFlavors {
//        create("dev") { dimension = "environment" }
//        create("prod") { dimension = "environment" }
//    }

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices.clear()
    managedDevices += "pixel6Api33"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        v.instrumentationRunnerArguments.put("targetAppId", "com.ytone.longcare")
    }
}
